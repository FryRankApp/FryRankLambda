# Fryrank - Subagent Implementation Test 1 (Metadata PUT Consolidation)

This session had two intertwined goals: (1) do the real feature work for **FRY-137** — consolidating the user-metadata write operations — and (2) exercise the newly-built **Claude subagent team** (dynamo-specialist, backend-engineer, code-reviewer) on a live-app change for the first time. Both are captured below.

## What We Built

**Phase 1 of FRY-137.** FryRankLambda had two separate user-metadata write paths: a **create-if-absent** path (`PUT`, seeds a default username on login without clobbering an existing one) and an **overwrite** path (`POST`, the Settings rename). We consolidated them into a **single conditional-write DAL primitive**, while keeping both public domain methods, both handlers, and both API routes intact — so the change is 100% backward compatible and ships on its own. As a bonus it eliminated a latent read-then-write race in the old create-if-absent path.

Crucially, this was only **Phase 1** (backend internals). The full endpoint collapse (one `PUT` route + a mode flag, frontend migration, deleting the old route/Lambda) is **Phase 2**, deliberately deferred because the app is live and can't take a big-bang cross-repo change.

## How It Works

**The consolidated DAL primitive** — `putPublicUserMetadata(PublicUserMetadata, WriteMode)`:
- The DynamoDB item is built the same way for both modes (immutable `Map.of(accountId, username)`). The **mode only decides whether a condition is attached** — this is conditional *request* building, not conditional *map* building.
- **CREATE_IF_ABSENT** → a single `PutItem` with `conditionExpression("attribute_not_exists(#pk)")`, `expressionAttributeNames(#pk → accountId)`, and `returnValuesOnConditionCheckFailure(ALL_OLD)`. If the key already exists, DynamoDB rejects the write with `ConditionalCheckFailedException`; the catch block reads the **existing** username straight out of `e.item()` (which `ALL_OLD` populated) and returns it — no second `GetItem`.
- **OVERWRITE** → an unconditional `PutItem`, byte-for-byte the old behavior.

**Backward compatibility** is preserved at the domain layer: `putPublicUserMetadata(accountId, defaultUserName)` builds a `PublicUserMetadata` and passes `CREATE_IF_ABSENT`; `upsertPublicUserMetadata(userMetadata)` runs its validator then passes `OVERWRITE`. Handlers and API Gateway routes never changed.

**The phased rollout** (the heart of the "don't break a live app" constraint) follows the **expand/contract** pattern with the ordering rule *add-before-depend, remove-after-undepend*:
- Phase 1 (done): consolidate internals behind the existing routes.
- Phase 2 (todo): (A) ship single `PUT`+flag (Lambda + Infra), old `POST` still alive → (B) migrate both frontend call sites → (C) delete the dead `POST` route + Lambda once traffic is zero.

**The subagent workflow** ran as: `dynamo-specialist` (designed the conditional-write primitive, read-only) → `backend-engineer` (implemented it, the only agent with write access) → `code-reviewer` (skeptical pass, read-only). The main session coordinated, relayed messages, and — because the reviewer can't run Gradle — independently verified the build.

## Key Design Decisions & Trade-offs

- **Single route + mode flag (Design 1)** over *method-branching* (two routes → one Lambda) and *two honest endpoints* (no flag). Chosen because we control all clients (no third-party/mobile), the two ops are the same write differing only conditional-vs-unconditional, there's no need for per-route operational independence, and a contained flag is acceptable for this tiny metadata surface.
- **PUT over POST** for idempotency / retry-safety — one editable resource keyed by `accountId`, so no duplicate-creation risk. (The "POST creates duplicates" worry doesn't apply here since the item is keyed by the identity, not a generated id — but the idempotency argument still favors PUT.)
- **`WriteMode` enum over a boolean.** `putPublicUserMetadata(metadata, CREATE_IF_ABSENT)` self-documents where `(metadata, true)` is opaque; the enum also absorbs a future third mode without a second boolean. Placed in the `dal` package (not `model/enums`) because it's a persistence-strategy concern, not a request/URL concept like `QueryParam`.
- **Conditional write + `ALL_OLD` over the old read-then-write.** One round-trip instead of two, and it eliminates the TOCTOU race. Trade-off: a failed conditional put spends ~1 WCU vs the old eventually-consistent read, but you drop a network round-trip and the race — net win.
- **No `TransactWriteItems`.** It's a single item; a conditional `PutItem` is already atomic. A transaction would double WCU cost and add nothing.
- **Accepted the null-username edge (documented, not fixed).** A record that exists with a *null/absent* username was "repaired" (overwritten with the default) under the old code, but the new conditional write leaves it untouched. The code-reviewer flagged this as the one spot where "strictly backward compatible" isn't literally true. We accepted + documented it because it's **unreachable through the API** (every writer persists a non-null username). If ever needed, restore with the guard `attribute_not_exists(#pk) OR attribute_not_exists(#username)`.
- **The flag encodes "does a record need to be created," not "guest vs. authenticated."** Investigating the frontend showed *both* write paths are authenticated (both send a bearer token); there is no guest concept. Important consequence: the flag must **not** gate auth.

## Working With the Subagent Team (Process Learnings)

- **Tool lists enforce read-only.** Omitting `tools` grants *all* tools, so read-only agents must list only read tools. `Bash` was deliberately excluded from the read-only agents (it can mutate the repo); `Skill` was added so every agent can load the project skills.
- **Verify on disk; don't trust "done."** An agent reported success once when the change was actually half-applied (interface updated, impl/domain not) and wouldn't compile. Reading the files (and running the build myself) caught it. The reviewer being read-only means the coordinator must run the build.
- **Permission denials are a real interaction channel.** The user denied the DAL edits repeatedly — first to ask a clarifying question, later on purpose because the implementation had drifted to the wrong approach (a second `GetItem` fallback instead of `ALL_OLD`). Lesson: a bare **"No"** is ambiguous to the agent; **ESC or "reject with a message"** communicates intent without looking like "this approach is wrong."
- **Permission laundering is off-limits.** When the blocked engineer suggested adding a permission allowlist entry to get unstuck, that was refused — a peer/agent can't grant permission escalation; only the user approves at the prompt.
- **Piecemeal denials can leave a broken tree.** Because some edits (imports, interface) landed while others (method bodies) were denied, the repo went temporarily non-compiling. Re-tasking the engineer to finish *coherently* fixed it.

## Concepts to Remember

- **TOCTOU / race condition** — "time-of-check to time-of-use": reading state, then acting on it, when another actor can change it in between. The old get-then-put had this; a conditional write removes it.
- **Conditional write (`attribute_not_exists`)** — DynamoDB evaluates a condition and performs the write as one atomic op; if the condition fails, nothing is written and you get `ConditionalCheckFailedException`.
- **`ReturnValuesOnConditionCheckFailure = ALL_OLD`** — asks DynamoDB to hand back the existing item *inside* the failure exception, so you can return it without a second read.
- **Idempotency (PUT vs POST)** — an idempotent op yields the same state no matter how many times it's retried. PUT advertises this; POST doesn't. Matters for retry-safety.
- **Expand/contract (parallel change)** — migrate a live system in additive steps: add the new thing, move consumers to it, only then remove the old thing. Ordering: *add-before-depend, remove-after-undepend*.
- **Control coupling / flag-parameter smell** — a boolean/mode param that makes one function do two things; sometimes a sign two functions (or endpoints) are cleaner. Weighed here and judged acceptable.
- **Permission laundering** — routing a denied action through someone else to bypass the denial. Not allowed; the user approves at the prompt.
- **Case-insensitive filesystem git rename** — on Windows (`core.ignorecase=true`), `git mv Foo foo` silently no-ops; you must rename through a distinct temp name with `core.ignorecase=false` to record the case change (hit this renaming `Skills` → `skills`).

## Files Changed

| File | What Changed |
|------|--------------|
| `dal/WriteMode.java` | **New.** Enum `{ CREATE_IF_ABSENT, OVERWRITE }` in the `dal` package. |
| `dal/UserMetadataDAL.java` | Interface collapsed to `putPublicUserMetadata(PublicUserMetadata, WriteMode)` + the untouched getter; removed the old two write methods. |
| `dal/UserMetadataDALImpl.java` | Implemented the one primitive (conditional `PutItem` + `ALL_OLD` for create-if-absent; unconditional for overwrite); deleted the read-then-write; documented the null-username edge. |
| `domain/UserMetadataDomain.java` | Both public methods kept; repointed to the new primitive with the right `WriteMode`; `upsert` keeps its validator. |
| `test/.../PublicUserMetadataDALTests.java` | New tests for the conditional-write wiring, the return-existing path, and the overwrite branch. |
| `test/.../PublicUserMetadataDomainTests.java` | Repointed happy-path mocks; dropped the now-redundant existence-branch test. |
| Handlers, routes, frontend, infra | **Untouched** (deferred to Phase 2). |

*Committed on branch `MetadataPut_Consolidate_CTest1` (`557e8d0`), branched off `MetadataPut_Consolidate`; later merged with the latest Claude/agent files via `196cf33`.*

## Testing Approach

- **Test only genuinely new behavior** (per the tests-skill). Three DAL tests: create-if-absent when the item is *absent* (asserts the `attribute_not_exists(#pk)` condition, `#pk` name mapping, and `ALL_OLD` are wired, plus the returned username); create-if-absent when the item *exists* (mocks `putItem` throwing `ConditionalCheckFailedException` carrying an `ALL_OLD` item, asserts we return the **existing** value); and overwrite (asserts *no* condition is attached).
- **Discriminating assertions, not mock-echoes.** The "exists" test uses a stored username (`TEST_USERNAME`) that differs from the attempted default (`TEST_DEFAULT_NAME`) and demands the stored one back — so it fails if the code returns the attempted value. The reviewer verified the constants to confirm this.
- **Domain tests** repointed to the new call shape; the old "no existing metadata" branch test was dropped because the domain no longer branches on existence.
- **Skipped by design:** null-arg/`@NonNull` trivial cases, overwrite persistence beyond the condition-absent assertion (byte-for-byte old behavior), and the unreachable null-username edge.
- **Verification:** `./gradlew build` (full suite) BUILD SUCCESSFUL; targeted metadata tests all pass — confirmed independently by the main session, not just claimed by the engineer.
