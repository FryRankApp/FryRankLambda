# FryRank - Pagination Refinements

## What We Built

Polished the cursor-based pagination feature on the `getReviewsRefactor` branch. The core pagination mechanism was already in place (DynamoDB seek method, URL-encoded `isoDateTime` cursor), but several correctness and robustness issues were identified and fixed: limit enforcement was tightened (max cap + proper default), the JSON serialization name of `nextCursor` was corrected, and a full test suite was written for both the DAL pagination paths and the request validator.

## How It Works

**Limit handling (before vs. after):**
- Before: `limit` was parsed as `Integer` (nullable); if absent, null was passed through and logged as "fetching all reviews."
- After: `limit` is always an `int`. If omitted, it defaults to `DEFAULT_PAGE_LIMIT` (10). If provided, it's clamped to `MAX_PAGE_LIMIT` (100) via `Math.min()`. The validator still only checks format (positive integer), not the cap — clamping is the handler's job.

**Serialization fix:**
- `GetAllReviewsOutput.nextCursor` had `@SerializedName("next_cursor")` which forced snake_case in the JSON response. Removing it lets Gson serialize it as `nextCursor` (camelCase), consistent with the rest of the API.

**`items.getLast()` cleanup:**
- `ReviewDALImpl` used the verbose `items.get(items.size() - 1)` to get the last item when building the next cursor. Replaced with `items.getLast()` (Java 21+), which is cleaner and semantically clearer.

**`final` in validator:**
- `int limit` in `GetAllReviewsRequestValidator` was made `final` — a minor but correct signal that the parsed value is not reassigned.

## Key Design Decisions & Trade-offs

- **Validator does not enforce MAX_PAGE_LIMIT** — A limit of 200 passes validation without error. The handler clamps it silently. This keeps the validator's scope narrow (format only), and avoids a validator-vs-handler disagreement about what counts as "valid."
- **`limit` now always defaults** — Earlier design intentionally kept `limit` nullable to preserve backward compatibility (no limit = return everything). That stance was reversed: the handler now always applies at least `DEFAULT_PAGE_LIMIT`. This is a breaking change for any caller expecting unbounded results, but the tradeoff was judged acceptable.
- **`nextCursor` in camelCase** — Removing `@SerializedName` means the JSON field name changed from `next_cursor` to `nextCursor`. Any frontend already consuming this field would need updating, but since pagination is new and not yet wired to the frontend, the cost is zero.
- **Cursor derivation uses `items.getLast()`** — Equivalent to `items.get(items.size() - 1)` but throws `NoSuchElementException` on empty lists (vs. `IndexOutOfBoundsException`). The surrounding guard (`!items.isEmpty()`) makes both equivalent here; `getLast()` is just idiomatic Java 21.

## Concepts to Remember

- **`Math.min()` for capping** — A clean one-liner for enforcing an upper bound on a user-supplied integer: `Math.min(Integer.parseInt(param), MAX)`. No if-statement needed.
- **Primitive vs. boxed types** — Changing `Integer limit` (nullable) to `int limit` (primitive) forces a default to be chosen at the call site. This is a design signal: primitives say "this always has a value," boxed types say "this might be absent."
- **`@SerializedName` in Gson** — Overrides the field name used during JSON serialization/deserialization. Removing it reverts to Gson's default (field name as-is). If you want snake_case everywhere, configure a `GsonBuilder` with `setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)` instead of annotating each field.
- **`List.getLast()`** — Available since Java 21. Equivalent to `list.get(list.size() - 1)` but more readable. Throws `NoSuchElementException` if the list is empty.
- **Validator scope** — Validators should check *format* (is this parseable? is it in range of what the type allows?), not *business logic* (is this within our self-imposed cap?). Business logic belongs in the handler or domain layer.

## Files Changed

| File | What Changed |
|------|--------------|
| `Constants.java` | Added `MAX_PAGE_LIMIT = 100` |
| `GetAllReviewsHandler.java` | `limit` is now `int` with `Math.min()` cap and `DEFAULT_PAGE_LIMIT` default; added `MAX_PAGE_LIMIT` import |
| `GetAllReviewsOutput.java` | Removed `@SerializedName("next_cursor")` and its import |
| `ReviewDALImpl.java` | `items.get(items.size() - 1)` → `items.getLast()` |
| `GetAllReviewsRequestValidator.java` | Added `final` to `int limit` |
| `TestConstants.java` | Updated comment: `next_cursor` → `nextCursor` |
| `ReviewDALTests.java` | Added 8 pagination tests (cursor generation, LEK edge cases, key condition expression assertions for both query paths) |
| `GetAllReviewsRequestValidatorTest.java` | New file — 9 tests covering null, empty, valid, zero, negative, non-numeric, decimal, above-max limits, and `supports()` |

## Testing Approach

**`GetAllReviewsRequestValidatorTest` (new, 9 tests):**
- Null limit → no errors
- Empty string limit → no errors
- Valid positive integer (`"10"`, `"1"`) → no errors
- Zero → error (limit must be > 0)
- Negative (`"-1"`) → error
- Non-numeric (`"abc"`) → error
- Decimal (`"5.5"`) → error (parseInt throws, caught as format error)
- Above-max (`"200"`) → no errors (validator doesn't enforce the cap)
- `supports()` returns true for `GetAllReviewsRequest`, false for other classes

**`ReviewDALTests` pagination additions (8 tests):**
- Non-empty LEK + non-empty items → `nextCursor` is non-null and URL-encoded correctly
- Empty LEK → `nextCursor` is null
- Non-empty LEK + empty items → `nextCursor` is null, reviews list is empty
- Non-empty LEK + last item missing `isoDateTime` → `nextCursor` is null
- `getAllReviewsByRestaurantId` with cursor → `KeyConditionExpression` includes `AND #dt < :cursor`
- `getAllReviewsByRestaurantId` without cursor → `KeyConditionExpression` is `#key = :value` only
- `getAllReviewsByAccountId` with cursor → correct expression, correct index name, correct `:value`
