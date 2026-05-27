# FryRank - Categorization Feature

## What We Built

Added review categorization (fry-type tags like Curly, Waffle, Regular) to the FryRankLambda backend. Reviews can now be posted with a `tags` field, and all three review-read endpoints — by restaurant, by account, and recent — accept an optional `tag` query param that filters results to reviews containing that tag. The feature is opt-in everywhere: queries without a tag behave identically to before, and legacy untagged reviews continue to be returned normally except when a tag filter is active (in which case they're silently excluded).

## How It Works

**Data model.** A new `tags: List<String>` field was added to `Review`. It's optional — a review can have zero or more tags. On write, when the list is non-empty, the DAL persists it as a DynamoDB `L` (list) attribute. On read, the DAL maps the `L` back into a `List<String>` on the Review object.

**Filtering.** Rather than building a reverse index / GSI (the more scalable but heavier option), we used a DynamoDB **filter expression**: `contains(#tags, :tag)`. This clause is AND-ed onto the existing filter expression in `queryReviews` (which already had `attribute_exists(isReview)` for an unrelated reason) and is used as the sole filter expression in `getRecentReviews` (which had no filter before).

A single helper consolidates the logic:

```java
private String buildFilterExpression(String baseFilter, String tag,
                                     Map<String, String> exprAttrNames,
                                     Map<String, AttributeValue> exprAttrValues) {
    if (tag == null || tag.isEmpty()) return baseFilter;
    exprAttrNames.put("#tags", TAGS_KEY);
    exprAttrValues.put(":tag", AttributeValue.builder().s(tag).build());
    String tagFilter = "contains(#tags, :tag)";
    return baseFilter == null ? tagFilter : baseFilter + " AND " + tagFilter;
}
```

`queryReviews` calls this with `baseFilter = "attribute_exists(isReview)"`; `getRecentReviews` calls it with `baseFilter = null`. One function handles both shapes.

**Plumbing.** The `tag` query param flows: `APIGatewayV2HTTPEvent` → `GetAllReviewsHandler` / `GetRecentReviewsHandler` → `ReviewDomain` (forwarding only) → `ReviewDAL`. No validator class was added — tag validation is deliberately the frontend's job.

**Legacy untagged reviews.** Reviews persisted before this feature have no `tags` attribute at all. DynamoDB's `contains()` function returns false when the attribute is absent, so a tag filter naturally excludes them — no special-casing, no backfill, no migration. Whenever a query has no tag filter, those reviews come back as before.

## Key Design Decisions & Trade-offs

- **List (`L`) over String Set (`SS`) for tags.** Lists preserve insertion order, allow duplicates, and tolerate emptiness. String Sets are unordered and DynamoDB rejects empty ones. Order doesn't strictly matter today, but `L` is the more forgiving default and the JSON round-trip with the frontend is identical (`["Curly", "Waffle"]`).

- **Filter expression instead of a reverse index.** Filter expressions run server-side but *after* DynamoDB has paginated, so they can cause a page to return fewer items than `limit`. For a small-scale MVP this is acceptable. The Figma design doc explicitly notes the team can migrate to a GSI / reverse index later. Picking the lighter option now avoids over-engineering.

- **No backend tag validation.** The implementation guideline ("backend should not be doing the bulk of tag validation handling — the frontend forces users to pick a valid tag") was followed. There is no `Tag` enum or shared config. This means changing the tag list doesn't require a backend redeploy. The trade-off: a malicious or buggy client could POST a review with any string in `tags`. That's acceptable risk for an MVP and pushed to the frontend.

- **Bad queries silently return empty.** A `?tag=NotARealTag` request just returns zero reviews. No validator throws. No 4xx is returned. This matches the existing philosophy for GET review queries — bad input degrades to empty results, not errors.

- **One helper, two callers (no pass-through duplication).** The first draft had two helpers (`buildFilterExpression` and `buildTagFilterExpression`). The reviewer pushed back on this — one function with an optional `baseFilter` parameter is cleaner and avoids duplicated branching logic on tag null/empty.

- **Tag param added to all three read paths, not just the two the design doc called out.** The original backend prompt only mentioned `getAllReviewsByRestaurantId` / `getAllReviewsByAccountId`, but the frontend prompt mentions filtering recent reviews too. We extended `getRecentReviews` for symmetry — frontend can wire it up whenever it's ready.

- **No migration for existing reviews.** Existing untagged reviews stay as-is. When users edit a review the frontend will force them to pick at least one tag, so reviews will naturally acquire tags over time.

## Concepts to Remember

- **DynamoDB List (`L`).** A native attribute type — an ordered, possibly-mixed-type collection. Built via `AttributeValue.builder().l(av1, av2, ...).build()`. Compare to **String Set (`SS`)** which is unordered, deduplicated, non-empty.

- **`contains()` in a filter expression.** A built-in DynamoDB function. `contains(listAttr, scalar)` returns true if the scalar is one of the list elements. `contains(stringAttr, substring)` does substring search. Critically, it returns **false** when the attribute is missing — that's what makes legacy untagged reviews silently filter out.

- **Filter expression vs key condition expression.** A *key condition* runs at the index level — DynamoDB only reads matching rows. A *filter expression* runs after the read — DynamoDB still consumes capacity for non-matching rows and *then* drops them. Filter expressions can make a `limit`-sized request return fewer items.

- **Pass-through methods.** A method that just forwards arguments to another layer with no transformation. The codebase's style says to avoid them unless they cross a layer boundary (handler → domain → DAL). The new `ReviewDomain.getAllReviews(..., tag)` is borderline — it adds a logging line and routes between two DAL methods, so it earns its keep.

- **Expression attribute names / values in DynamoDB.** Placeholders that let you reference attribute names (`#tags`) and values (`:tag`) safely in expression strings, avoiding clashes with reserved words and enabling parameter binding. The two maps (`expressionAttributeNames` / `expressionAttributeValues`) are passed alongside the expression string.

## Files Changed

| File | What Changed |
|------|--------------|
| `src/main/java/com/fryrank/model/Review.java` | Added `tags: List<String>` field |
| `src/main/java/com/fryrank/Constants.java` | Added `TAGS_KEY = "tags"` |
| `src/main/java/com/fryrank/model/enums/QueryParam.java` | Added `TAG("tag")` |
| `src/main/java/com/fryrank/model/GetAllReviewsRequest.java` | Added `tag` to record |
| `src/main/java/com/fryrank/dal/ReviewDAL.java` | Added `tag` param to all three read methods |
| `src/main/java/com/fryrank/dal/ReviewDALImpl.java` | Filter expression injection, tag persistence on write, tag round-trip on read, new `buildFilterExpression` helper, new `getStringListAttribute` helper |
| `src/main/java/com/fryrank/domain/ReviewDomain.java` | Forward `tag` through `getAllReviews` and `getRecentReviews` |
| `src/main/java/com/fryrank/handler/GetAllReviewsHandler.java` | Read `tag` query param and pass it through |
| `src/main/java/com/fryrank/handler/GetRecentReviewsHandler.java` | Read `tag` query param and pass it through |
| `src/test/java/com/fryrank/dal/ReviewDALTests.java` | Updated existing tests for new arity + 3 new tag tests (filter injection, write persistence, read round-trip) |
| `src/test/java/com/fryrank/domain/ReviewDomainTests.java` | Updated existing tests for new arity + tag-forwarding tests for each path |
| `src/test/java/com/fryrank/handler/GetAllReviewsHandlerTests.java` | Updated existing tests for new arity |

## Testing Approach

**Three essential DAL tests** were added — one per new behavior:

1. `testGetAllReviewsByRestaurantId_withTag_injectsContainsTagsFilter` — captures the `QueryRequest` sent to DynamoDB and asserts `filterExpression` contains `contains(#tags, :tag)` and `:tag` is bound to the right value.

2. `testAddNewReview_withTags_persistsTagsAsList` — captures the `TransactWriteItemsRequest` and asserts the persisted item has a `TAGS_KEY` attribute of type `L` containing the expected strings in order.

3. `testMapItemToReview_withTags_populatesTagsList` — feeds the DAL a DynamoDB item with a `tags` `L` attribute and asserts the resulting `Review.getTags()` matches.

**Tests deliberately *not* added:**

- No "valid tag value" validation tests — backend has no validation.
- No bad-query tests — bad queries silently return empty, which is the existing system behavior for review GETs.
- No "tag is null" tests — the system is designed so tag is optional everywhere; if no tag is supplied, the query behaves exactly as before, which is already covered by all the existing pagination / limit tests.
- No new gateway-validation tests — `APIGatewayRequestValidatorTest` already covers gateway-level cases.

Two forwarding tests were added to `ReviewDomainTests` (one for `getAllReviews`-with-restaurant, one for `getAllReviews`-with-account, one for `getRecentReviews`) just to confirm the tag plumbs through the domain layer.

All existing tests were updated to pass `null` for the new `tag` argument, preserving original behavior. Full test suite passes.
