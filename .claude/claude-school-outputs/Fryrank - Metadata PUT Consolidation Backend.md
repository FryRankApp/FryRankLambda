# Fryrank - Metadata PUT Consolidation Backend

## What We Built
Step 2 of FRY-137, the backend half tracked as FRY-155. FryRank had two near-duplicate ways to write a user's public metadata:
- `PUT /api/userMetadata?accountId=&defaultUsername=` was a read-then-write. If a record existed it returned the existing username and did not write, so it was safe to call on every login.
- `POST /api/userMetadata` with a JSON body always wrote. The Settings rename flow used it.

Step 1 moved the "don't clobber an existing username" check to the frontend: it now does a GET and only PUTs when no record exists. That meant the backend no longer needed two write modes. We collapsed both routes onto **one domain method and one DAL method that always overwrites**. We also added Google auth to the PUT handler, because PUT is now destructive. The POST route stays alive, pointed at the same method, because the deployed frontend still calls it. Work is on branch `UserMetaData_Consolidate_Phase2`, off `master`.

## How It Works
**PUT path, now authorized:**
1. `PutPublicUserMetadataHandler` runs `APIGatewayRequestValidator`, which requires the `accountId` and `defaultUsername` query params.
2. It pulls the bearer token with `HeaderUtils.extractBearerToken` and calls `authorizer.authorizeAndGetAccountId(token)`.
   - **Authorized:** the account ID from the Google token replaces the `accountId` query param. The client can no longer choose whose record gets written.
   - **`NotAuthorizedException`:** returns 401 and never reaches the domain.
   - **`AuthorizationDisabledException`** (the `DISABLE_AUTH` flag in SSM): logs it and falls back to the query-param `accountId`.
3. `UserMetadataDomain.putPublicUserMetadata(accountId, username)` builds a `PublicUserMetadata` and runs `UserMetadataValidator`. A null field throws `ValidatorException`, which becomes a 400.
4. `UserMetadataDALImpl.putPublicUserMetadata(userMetadata)` sends a plain DynamoDB `PutItem` with no `ConditionExpression`, so it always overwrites. It returns the username it wrote.

**POST path, kept alive with no auth:** `UpsertPublicUserMetadataHandler` parses the JSON body as before, then calls the same `putPublicUserMetadata(accountId, username)`. Step 4 deletes it after the frontend stops calling it.

**Migration order** (from the ticket), where each step must be deployed before the next starts:
1. Frontend GETs before PUTting (shipped, PR #184).
2. Backend PUT always overwrites and requires auth (this session).
3. Frontend rename flow moves from POST to PUT.
4. Delete UPSERT from the Lambda and the infra.

Doing step 3 before step 2 breaks renames, because the old PUT refuses to overwrite. Doing step 4 before step 3 returns 404 on renames from cached frontend bundles.

## Key Design Decisions & Trade-offs
- **Branched off `master`, not `MetadataPut_Consolidate_Phase1`.** Phase 1 holds the abandoned `WriteMode` / `attribute_not_exists` design. It is deployed to the live Lambda, but nothing new builds on it, and this deploy replaces it.
- **Unconditional overwrite, no mode flag.** The integrity check now lives on the client: GET, then PUT only on a missing record. This was accepted knowingly at near-zero traffic. The trade-offs:
  - A small check-then-act race window remains permanently.
  - During rollout, stale bundles could blindly PUT over a renamed username.
- **Auth goes on PUT in this PR.** Neither metadata write handler checked the token before, and there's no API Gateway authorizer in FryRankInfra. That hole already existed, since POST was an unauthenticated arbitrary overwrite. But this step is what makes PUT destructive, so the fix ships with it.
- **The token's account ID wins over the client-supplied one.** This matches `AddNewReviewForRestaurantHandler`: the Google account ID is the only identity we trust.
- **POST gets no auth.** It's temporary and dies in step 4. Changing its behavior mid-migration risks breaking the un-migrated frontend.
- **No wire change.** PUT keeps `defaultUsername` as the param name, even though it now also carries deliberate renames. Renaming it is a coordinated follow-up after step 4, not a breaking change mid-migration.
- **Removed `@NonNull` from the domain method's params. This departs from the ticket's snippet.** Lombok's `@NonNull` throws `NullPointerException` at method entry, before the validator can run. Keeping it would have:
  - broken the ticket's own "null username → `ValidatorException`" test;
  - regressed a POST with a missing field from 400 (validator) to 500 (NPE).

  With the annotation removed, the validator is the single null gate.
- **The 401 mirrors `AddNewReviewForRestaurantHandler` exactly, so it has no CORS headers.** Consistent with the existing handler, but a browser can't read a CORS-less error response. It's flagged as a follow-up for both handlers rather than fixed in one.
- **No handler tests, by the user's call.** The ticket scoped tests to the DAL and domain. The new auth path is verified by the live 401 check after deploy.

## Concepts to Remember
- **Expand/contract migration ("add before you depend, remove after you undepend"):** change a live API in ordered, individually deployable steps. Add the new behavior, move clients onto it, then delete the old path. Each deploy stays compatible with whatever clients are currently live.
- **Conditional vs. unconditional write:** a DynamoDB `PutItem` with a `ConditionExpression` (e.g. `attribute_not_exists(pk)`) only writes if the condition holds, atomically on the server. Without one, it always replaces the item.
- **Check-then-act race:** reading state and then acting on it in a separate request. Another writer can slip in between. Server-side conditional writes close that gap; client-side checks can't.
- **Server-derived identity (preventing IDOR):** never trust a client-sent user ID for writes. Take it from the verified token. Otherwise any caller can edit anyone's record: an Insecure Direct Object Reference.
- **Fail-fast annotations vs. validators:** Lombok `@NonNull` generates a null check that throws before your method body runs. If you want nulls reported as a structured 400, the validator has to be the first thing that sees them.
- **CORS on error responses:** the browser hides any response without `Access-Control-Allow-Origin` from JavaScript, including a 401. The frontend just sees a network error.
- **Asserting an absence in a test:** capturing the `PutItemRequest` and asserting `conditionExpression()` is `null` locks in "this write is unconditional". Checking that `getItem` is never called locks in "no read-before-write".
- **CRLF vs. LF line endings:** Windows editors may rewrite line endings, so the diff looks like every line changed. `git diff --ignore-space-at-eol` shows the real change. Git's `autocrlf` normalizes on commit.

## Files Changed
| File | What Changed |
|------|--------------|
| `dal/UserMetadataDAL.java` | Two write methods replaced by one `putPublicUserMetadata(PublicUserMetadata)` |
| `dal/UserMetadataDALImpl.java` | Deleted read-then-write `putPublicUserMetadataForAccountId`; renamed the unconditional upsert to `putPublicUserMetadata`; removed both `TODO(FRY-137)` comments |
| `domain/UserMetadataDomain.java` | One `putPublicUserMetadata(accountId, username)` that builds the model, validates it, and writes; deleted `upsertPublicUserMetadata`; no `@NonNull` on params |
| `handler/PutPublicUserMetadataHandler.java` | Added `Authorizer` wiring: token account ID used for writes, 401 on bad token, query-param fallback when auth is disabled |
| `handler/UpsertPublicUserMetadataHandler.java` | Now calls the consolidated domain method; otherwise unchanged (no auth) |
| `test/.../dal/PublicUserMetadataDALTests.java` | Removed the old put tests (including "returns existing username"); added `testPutPublicUserMetadata_writesUnconditionally` |
| `test/.../domain/PublicUserMetadataDomainTests.java` | Put + upsert tests folded into happy path + null `accountId` / null `username` → `ValidatorException` (DAL never touched) |
| `test/.../TestConstants.java` | Removed now-unused `TEST_DEFAULT_NAME` and `TEST_PUBLIC_USER_METADATA_OUTPUT_WITH_DEFAULT_NAME` |

## Testing Approach
- **DAL:** one test captures the `PutItemRequest` and asserts:
  - no `ConditionExpression`;
  - the item holds the right `accountId` and `username`;
  - `getItem` is never called;
  - the output is the username that was written.

  This replaces the old "existing username is returned" test, which described a contract that no longer exists.
- **Domain:** a happy-path test checks that the DAL receives the built `PublicUserMetadata`. Two validator tests check that a null `accountId` or a null `username` throws `ValidatorException` and never touches the DAL. The old null → NPE tests were dropped because that behavior was deliberately removed.
- **Skipped on purpose:** handler auth tests (user's call; ticket scope) and `UserMetadataValidatorTests` (untouched).
- **Result:** `./gradlew build` passes with all 141 tests green.
- **Still to do after deploy** (from the ticket):
  - PUT with a valid token overwrites an existing username (200).
  - PUT with no or invalid token returns 401.
  - POST still returns 200 and still writes.
  - GET is unchanged.
  - **The regression that matters most:** log out and back in on the deployed frontend and confirm a renamed username isn't overwritten by the Google name. This deploy removes the server-side guard the login seed path used to lean on.
