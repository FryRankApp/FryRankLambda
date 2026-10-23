# FryRank - Categorization Feature

## What We Built

Added review categorization (fry-type tags like Curly, Waffle, Regular) to the FryRankLambda backend. Reviews can now be posted with a `tags` field, and all three review-read endpoints — by restaurant, by account, and recent — accept an optional `tag` query param that filters results to reviews containing that tag. The feature is opt-in everywhere: queries without a tag behave identically to before, and legacy untagged reviews continue to be returned normally except when a tag filter is active (in which case they're silently excluded).

## How It Works

**Data model.** A new `tags: List<String>` field was added to `Review`. It's optional — a review can have zero or more tags. On write, when the list is non-empty, the DAL persists it as a DynamoDB `L` (list) attribute. On read, the DAL maps the `L` back into a `List<String>` on the Review object.

**Filtering.** Rather than building a reverse index / GSI (the more scalable but heavier option), we used a DynamoDB **filter expression**: `contains(#tags, :tag)`. This clause is AND-ed onto the existing filter expression in `queryReviews` (which already had `attribute_exists(isReview)` for an unrelated reason) and is used as the sole filter expression in `getRecentReviews` (which had no filter before).

A single helper consolidates the logic:

```java
private String buildFilterExpression(Optional<String> baseFilter, ReviewFilter filter,
                                     Map<String, String> exprAttrNames,
                                     Map<String, AttributeValue> exprAttrValues) {
    final String tag = filter.tag();
    if (tag == null || tag.isEmpty()) return baseFilter.orElse(null);
    exprAttrNames.put("#tags", TAGS_KEY);
    exprAttrValues.put(":tag", AttributeValue.builder().s(tag).build());
    String tagFilter = "contains(#tags, :tag)";
    return baseFilter.map(base -> base + " AND " + tagFilter).orElse(tagFilter);
}
```

`queryReviews` calls this with `baseFilter = Optional.of("attribute_exists(isReview)")`; `getRecentReviews` calls it with `baseFilter = Optional.empty()`. One function handles both shapes. The `baseFilter` parameter is typed as `Optional<String>` (rather than a nullable `String`) to make the optionality visible at the call site and prevent the kind of accidental NPE that comes from forgetting to null-check.

**Filter object.** The tag isn't passed around as a bare `String` — it's wrapped in a `ReviewFilter` record (`public record ReviewFilter(String tag) {}`). The DAL and domain methods take `@NonNull ReviewFilter filter` so callers must construct one, even if it carries a `null` tag. The record is single-field today but exists so future filters (score range, date range, media presence, etc.) can be added as new record components without changing any method signatures.

**Plumbing.** The `tag` query param flows: `APIGatewayV2HTTPEvent` → `GetAllReviewsHandler` / `GetRecentReviewsHandler` (wrap into `new ReviewFilter(tag)`) → `ReviewDomain` (forwarding only) → `ReviewDAL`. No validator class was added — tag validation is deliberately the frontend's job.

**Legacy untagged reviews.** Reviews persisted before this feature have no `tags` attribute at all. DynamoDB's `contains()` function returns false when the attribute is absent, so a tag filter naturally excludes them — no special-casing, no backfill, no migration. Whenever a query has no tag filter, those reviews come back as before.

## Key Design Decisions & Trade-offs

- **List (`L`) over String Set (`SS`) for tags.** Lists preserve insertion order, allow duplicates, and tolerate emptiness. String Sets are unordered and DynamoDB rejects empty ones. Order doesn't strictly matter today, but `L` is the more forgiving default and the JSON round-trip with the frontend is identical (`["Curly", "Waffle"]`).

- **Filter expression instead of a reverse index.** Filter expressions run server-side but *after* DynamoDB has paginated, so they can cause a page to return fewer items than `limit`. For a small-scale MVP this is acceptable. The Figma design doc explicitly notes the team can migrate to a GSI / reverse index later. Picking the lighter option now avoids over-engineering.

- **No backend tag validation.** The implementation guideline ("backend should not be doing the bulk of tag validation handling — the frontend forces users to pick a valid tag") was followed. There is no `Tag` enum or shared config. This means changing the tag list doesn't require a backend redeploy. The trade-off: a malicious or buggy client could POST a review with any string in `tags`. That's acceptable risk for an MVP and pushed to the frontend.

- **Bad queries silently return empty.** A `?tag=NotARealTag` request just returns zero reviews. No validator throws. No 4xx is returned. This matches the existing philosophy for GET review queries — bad input degrades to empty results, not errors.

- **One helper, two callers (no pass-through duplication).** The first draft had two helpers (`buildFilterExpression` and `buildTagFilterExpression`). The reviewer pushed back on this — one function with an optional `baseFilter` parameter is cleaner and avoids duplicated branching logic on tag null/empty.

- **`ReviewFilter` record instead of a raw `String tag` parameter.** The DAL and domain originally took `String tag` directly. A reviewer pointed out this would force a new parameter on every method whenever a future filter was added (score range, dietary, etc.). Switching to a single-field `record ReviewFilter(String tag) {}` means new filters slot in as additional record components — call-site signatures are stable. The trade-off is a slight construction cost everywhere (`new ReviewFilter(tag)`) for a much cheaper extension path later. Records' value-based `equals`/`hashCode` mean Mockito `eq(new ReviewFilter(...))` matching just works.

- **`Optional<String>` for `baseFilter`, not a nullable `String`.** Reviewer feedback: marking the base filter as `Optional<String>` instead of `String` makes the optionality visible in the type and prevents accidental NPEs. The internal helper uses `.orElse(null)` and `.map(...).orElse(...)` rather than null checks. We did NOT propagate `Optional` to public method signatures — that would be a style violation in Java — only to this one internal helper where the parameter is genuinely sometimes-absent.

- **Generic `tag` name, not `fryTypeTag`.** Considered making the field more specific (e.g., `fryTypeTag`) to signal what the value represents. Decided against it: the rest of the stack uses generic "tag" everywhere (`Review.tags`, `TAGS_KEY`, `QueryParam.TAG("tag")`, the URL param), and renaming only inside `ReviewFilter` would create a confusing seam where `filter.fryTypeTag()` maps to `params.get("tag")` and `review.getTags()`. The schema itself is generic — tags are stored as one undifferentiated `List<String>` — so "tag" is the most honest name for "does the list contain this value?" The principled rename would be schema-driven: when DynamoDB splits tags into typed categories (the future plan is fry shapes / flavors / toppings as separate attributes), the filter, the DDB attributes, and the query param names all rename together. Until then, the generic name leaves room for whatever non-fry tags (dietary, quality, etc.) might show up first.

- **Tag param added to all three read paths, not just the two the design doc called out.** The original backend prompt only mentioned `getAllReviewsByRestaurantId` / `getAllReviewsByAccountId`, but the frontend prompt mentions filtering recent reviews too. We extended `getRecentReviews` for symmetry — frontend can wire it up whenever it's ready.

- **No migration for existing reviews.** Existing untagged reviews stay as-is. When users edit a review the frontend will force them to pick at least one tag, so reviews will naturally acquire tags over time.

## Concepts to Remember

- **DynamoDB List (`L`).** A native attribute type — an ordered, possibly-mixed-type collection. Built via `AttributeValue.builder().l(av1, av2, ...).build()`. Compare to **String Set (`SS`)** which is unordered, deduplicated, non-empty.

- **`contains()` in a filter expression.** A built-in DynamoDB function. `contains(listAttr, scalar)` returns true if the scalar is one of the list elements. `contains(stringAttr, substring)` does substring search. Critically, it returns **false** when the attribute is missing — that's what makes legacy untagged reviews silently filter out.

- **Filter expression vs key condition expression.** A *key condition* runs at the index level — DynamoDB only reads matching rows. A *filter expression* runs after the read — DynamoDB still consumes capacity for non-matching rows and *then* drops them. Filter expressions can make a `limit`-sized request return fewer items.

- **Pass-through methods.** A method that just forwards arguments to another layer with no transformation. The codebase's style says to avoid them unless they cross a layer boundary (handler → domain → DAL). `ReviewDomain.getAllReviews(..., filter)` is borderline — it adds a logging line and routes between two DAL methods, so it earns its keep.

- **Expression attribute names / values in DynamoDB.** Placeholders that let you reference attribute names (`#tags`) and values (`:tag`) safely in expression strings, avoiding clashes with reserved words and enabling parameter binding. The two maps (`expressionAttributeNames` / `expressionAttributeValues`) are passed alongside the expression string.

- **Java records as parameter objects.** `public record ReviewFilter(String tag) {}` — the component list in the header is the entire definition. The compiler generates a private final field, an accessor (`tag()`), a canonical constructor, and value-based `equals`/`hashCode`/`toString`. The empty `{}` body is required syntax but means "no custom additions." Value-based equality is what makes records ideal for parameter-object patterns: Mockito's `eq(new ReviewFilter("Curly"))` matches structurally, so tests don't need a custom matcher.

- **`Optional<T>` for sometimes-absent values.** Use `Optional` in a parameter or return type when the absence is a normal part of the contract — it forces the reader to handle both cases via `.orElse(...)`, `.map(...)`, or `.ifPresent(...)`. The codebase convention is to use `Optional` only on *internal* helpers (like `buildFilterExpression`'s `baseFilter`), not on public DAL/domain method signatures, where a plain nullable parameter with `@NonNull` annotations is the prevailing style.

## Files Changed

| File | What Changed |
|------|--------------|
| `src/main/java/com/fryrank/model/Review.java` | Added `tags: List<String>` field |
| `src/main/java/com/fryrank/Constants.java` | Added `TAGS_KEY = "tags"` |
| `src/main/java/com/fryrank/model/enums/QueryParam.java` | Added `TAG("tag")` |
| `src/main/java/com/fryrank/model/GetAllReviewsRequest.java` | Added `tag` to record |
| `src/main/java/com/fryrank/model/ReviewFilter.java` | **New.** `public record ReviewFilter(String tag) {}` — the parameter object for review filter criteria; designed to grow as more filters are added |
| `src/main/java/com/fryrank/dal/ReviewDAL.java` | Added `@NonNull ReviewFilter filter` param to all three read methods (replaced earlier `String tag`) |
| `src/main/java/com/fryrank/dal/ReviewDALImpl.java` | Filter expression injection, tag persistence on write, tag round-trip on read, `buildFilterExpression` helper now takes `Optional<String> baseFilter` and `ReviewFilter filter`, new `getStringListAttribute` helper |
| `src/main/java/com/fryrank/domain/ReviewDomain.java` | Forward `@NonNull ReviewFilter` through `getAllReviews` and `getRecentReviews` |
| `src/main/java/com/fryrank/handler/GetAllReviewsHandler.java` | Read `tag` query param and wrap in `new ReviewFilter(...)` before forwarding to the domain |
| `src/main/java/com/fryrank/handler/GetRecentReviewsHandler.java` | Read `tag` query param and wrap in `new ReviewFilter(...)` before forwarding to the domain |
| `src/test/java/com/fryrank/TestConstants.java` | Added `TEST_TAG_1 = "Curly"`, `TEST_TAG_2 = "Waffle"`, `TEST_TAGS = List.of(TEST_TAG_1, TEST_TAG_2)` so test files don't inline tag literals |
| `src/test/java/com/fryrank/dal/ReviewDALTests.java` | Updated existing tests for new arity (pass `new ReviewFilter(null)`) + 3 tag tests (filter injection, write persistence, read round-trip); inline tag literals replaced with `TEST_TAG_*` constants |
| `src/test/java/com/fryrank/domain/ReviewDomainTests.java` | Updated existing tests for new arity + tag-forwarding tests for each path, using `TEST_TAG_*` constants |
| `src/test/java/com/fryrank/handler/GetAllReviewsHandlerTests.java` | Updated existing tests for new arity + 2 new tag-forwarding tests (restaurant + account paths) that verify the handler wraps the `tag` query param into a `ReviewFilter` and passes it to the domain — tripwires for any future custom tag-handling logic |

## Testing Approach

**Three essential DAL tests** were added — one per new behavior:

1. `testGetAllReviewsByRestaurantId_withTag_injectsContainsTagsFilter` — captures the `QueryRequest` sent to DynamoDB and asserts `filterExpression` contains `contains(#tags, :tag)` and `:tag` is bound to the right value.

2. `testAddNewReview_withTags_persistsTagsAsList` — captures the `TransactWriteItemsRequest` and asserts the persisted item has a `TAGS_KEY` attribute of type `L` containing the expected strings in order.

3. `testMapItemToReview_withTags_populatesTagsList` — feeds the DAL a DynamoDB item with a `tags` `L` attribute and asserts the resulting `Review.getTags()` matches.

**Two handler-level tag tests** (`GetAllReviewsHandlerTests`) verify the handler wraps the `tag` query param into a `ReviewFilter` and passes it through — one test per code path (restaurantId + accountId). The reviewer specifically asked for these as **tripwires** for future custom tag-handling: if an engineer later adds tag normalization / validation / category routing in the handler, these tests fail and force them to engage with the existing pass-through contract instead of silently breaking it.

**Tests deliberately *not* added:**

- No "valid tag value" validation tests — backend has no validation.
- No bad-query tests — bad queries silently return empty, which is the existing system behavior for review GETs.
- No "tag is null" tests at the DAL/domain layer for behavior — covered transitively by all the existing pagination / limit tests, which now pass `new ReviewFilter(null)` for the filter argument.
- No new gateway-validation tests — `APIGatewayRequestValidatorTest` already covers gateway-level cases.
- No `GetRecentReviewsHandlerTests` tag tests — the file doesn't exist yet, and adding it just for this feature was out of scope.

Three forwarding tests were added to `ReviewDomainTests` (one for `getAllReviews`-with-restaurant, one for `getAllReviews`-with-account, one for `getRecentReviews`) just to confirm the filter plumbs through the domain layer.

**Test-constant hygiene.** Repeated tag literals (`"Curly"`, `"Waffle"`, `List.of("Curly", "Waffle")`) were extracted into `TestConstants.TEST_TAG_1`, `TEST_TAG_2`, and `TEST_TAGS`. This matches the convention already used for `TEST_RESTAURANT_ID`, `TEST_REVIEW_*`, etc. — surfacing tag values in one place so a future rename or schema split (e.g., separating fry shapes from flavors from toppings) only touches the constants file.

All existing tests were updated to pass `new ReviewFilter(null)` for the new `filter` argument, preserving original behavior. Records' value-based `equals` means Mockito `eq(new ReviewFilter(null))` works without a custom argument matcher. Full test suite passes.
