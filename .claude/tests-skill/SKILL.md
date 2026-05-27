---
name: tests-skill
description: Use when writing or planning tests for FryRankLambda. Always consult the engineer before adding tests, explain what each test verifies, avoid over-engineering. Only test new behavior — skip cases already covered elsewhere or that won't break the app.
version: 1.0.0
---

# Tests Skill

Principles for writing tests in FryRankLambda. Built up over real implementation sessions.

## Core Principles

### 1. Consult The Engineer Before Adding Tests

Before adding a batch of tests, briefly list:
- What each test verifies (input → expected behavior).
- Which code path it exercises.
- Why it's needed (and what would break in production if it wasn't there).

The engineer may have you cut the list to essentials. Welcome that.

**Real session example:** During the categorization feature, an early draft proposed 8 candidate DAL tests. The engineer asked: *"are these tests to see if a valid tag has been passed in?"* — a single clarifying question that revealed several of the tests were redundant or testing the wrong thing. The list got cut to 3 essentials.

If you can't explain in one sentence what each test catches that the others don't, you have too many tests.

### 2. Test New Behavior Only

Only test the code paths the new feature added.

**Skip tests for:**
- Cases already covered by `APIGatewayRequestValidatorTest`.
- Cases already covered by existing pagination, limit, or auth tests.
- "Did I break the old behavior?" — that's what running the existing test suite is for, not what new tests are for.
- Trivial null-checks and early returns that wouldn't break the app if they regressed.

### 3. Don't Over-Engineer Tests

If a code path is trivially correct and a bug there wouldn't break the app, skip it.

Test the things that, if broken, would cause **real user-visible failures**:
- Query construction (the SQL/DDB expression you send to the DB).
- Persistence (the data actually being written).
- Round-tripping data through layers (write → read returns the same value).
- Auth flows.
- Error response codes.

Don't test:
- "Method returns null when input is null" if null is a valid no-op path that's designed-in.
- "Did the optional param get a default?" if the absence-of-param path is the original, already-tested behavior.
- Every combination of optional parameters — pick the representative case.
- **Silent-fallback paths for malformed optional input.** If the design says "bad `limit` → default" or "bad `cursor` → empty results", you don't need a test that asserts the bad input → fallback. The fallback is the same code path as the default, which is already exercised.
- **Encoded/decoded value equality across layers.** If the handler URL-decodes and the DAL receives the decoded value, one test that asserts the handler decoded correctly is enough — don't re-assert the decoded value at every layer.

**Real session example:** When adding tag filtering to review queries, we wrote 3 tests:
1. Filter expression injection (the new query construction).
2. Tags persisted to DDB on write (the new write path).
3. Tags read back from DDB (the new read path).

We did **not** write tests for:
- Tag being null → the system is designed so tag is optional; null tag = identical to before, already covered by all existing tests.
- Tag being an unknown value → bad queries are allowed by design and silently return empty results.
- Backend tag-value validation → the frontend handles it; there is nothing to test on the backend.

### 4. Name Tests By What They Verify

Test method names should describe input, condition, and expected outcome.

Format: `test<Method>_<Condition>_<ExpectedOutcome>`

Good: `testGetAllReviewsByRestaurantId_withTag_injectsContainsTagsFilter`
Bad: `testTags` / `testFilter1` / `testCategoryFeature`

A reader should know what the test catches without opening the body.

### 5. Frontend Is Responsible For Valid Input

The backend should not defensively test bad-input cases that the frontend will never send.

If the frontend forces users into a selection (dropdown, required field, etc.), don't write backend tests for "what if they bypass it" — unless there's a real security boundary at risk (e.g., authorization, data tampering).

Bad inputs that just return empty results aren't bugs — they're the designed behavior. No test needed.

### 6. Tests Already Covered Elsewhere Are Not Yours To Duplicate

Before adding a test, check:
- Does `APIGatewayRequestValidatorTest` already cover this?
- Does an existing `ReviewDomainTests` or `ReviewDALTests` case already exercise this code path?
- Is this a property of the framework (Mockito, Lombok, Gson) rather than of our code?

If yes → skip.

**Concrete example:** When pagination was added, there was no separate "no cursor → base key condition" test — that scenario was already covered by every existing non-pagination DAL test (which all pass `null` as the cursor). Likewise no separate "limit defaults to 10" test, because every existing test that omits the limit param already proves that.

### 7. Capture Internal Construction Only When It IS The New Behavior

Use `ArgumentCaptor` to inspect the request sent to DynamoDB (or any downstream call) **only when the request shape itself is what the test is verifying.**

- **Right reason to capture:** the test asserts that a new filter expression or attribute binding is correctly injected. The captured expression *is* the new behavior. Example: `testGetAllReviewsByRestaurantId_withTag_injectsContainsTagsFilter` captures the `QueryRequest` because the `contains(#tags, :tag)` clause is the new wiring.
- **Wrong reason to capture:** the test exercises an existing well-tested helper. Asserting the exact `KeyConditionExpression` string or the cursor encoding for the Nth time just re-verifies code that other tests already cover, and makes the test fragile to harmless refactors.

If two tests would capture the same thing, the second one is probably unnecessary.

## Test Placement

- Put a new test in the **existing test file that owns the behavior being tested** (`ReviewDALTests`, `ReviewDomainTests`, `GetAllReviewsHandlerTests`, etc.) — don't always create a new file per feature.
- Handler tests go in `src/test/.../handler/`; mock the domain layer with `@InjectMocks`.
- DAL tests mock `DynamoDbClient` and capture requests with `ArgumentCaptor` to inspect expression strings, attribute values, and transact items.

## When To Pause And Ask

- About to add more than 2–3 tests for a single feature → list them and sanity-check with the engineer first.
- A test requires significant new mocking scaffolding beyond what's already in the test file → that's often a signal the test is at the wrong layer.
- About to add a "validation" test for input → confirm the validation actually exists in code (not just in your head).

## See Also

- `implementation-skill/SKILL.md` for implementation principles these tests support.
- `dal-pagination-planning/SKILL.md` for pagination-specific test guidance (this skill borrows several principles from there).
- `claude-school/SKILL.md` for the post-session learning export format.
