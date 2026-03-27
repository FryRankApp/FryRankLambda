# DAL Pagination Planning

## Context

Use this skill when implementing cursor-based pagination in the DAL layer, particularly for DynamoDB-backed queries using GSIs.

## Cursor Format

**Format:** URL-encoded `isoDateTime` string — e.g. `2026-03-17T03%3A58%3A02Z`.

The cursor is the `isoDateTime` of the last item returned, URL-encoded so it's safe to use as a query param value. Since `restaurantId` and `accountId` are already known from the request's query params, they don't need to be in the cursor.

The cursor must be **URL-decoded in the handler** before use. The API Gateway test console passes query param values as-is (no decoding), so a cursor pasted from the JSON response (`2026-03-17T10%3A00%3A00Z`) arrives at the Lambda still encoded. If it's used as-is in a DynamoDB key condition, `%` (ASCII 37) compares differently than `:` (ASCII 58), silently corrupting the comparison. Always call `URLDecoder.decode(cursor, StandardCharsets.UTF_8)` in the handler before passing the cursor downstream.

## DynamoDB Pagination Pattern

Use the **seek method**: add `isoDateTime < :cursor` to the key condition expression. Do **not** use `ExclusiveStartKey` — that would require the full item primary key (`restaurantId` + `identifier` + `isoDateTime`), which the caller doesn't have.

```java
// Build expression attribute maps (must be mutable HashMap, not Map.of())
final Map<String, String> exprAttrNames = new HashMap<>();
exprAttrNames.put("#key", keyAttribute);

final Map<String, AttributeValue> exprAttrValues = new HashMap<>();
exprAttrValues.put(":value", AttributeValue.builder().s(keyValue).build());

final String keyCondition;
if (cursor != null && !cursor.isEmpty()) {
    exprAttrNames.put("#dt", ISO_DATE_TIME);
    exprAttrValues.put(":cursor", AttributeValue.builder().s(cursor).build());
    keyCondition = "#key = :value AND #dt < :cursor";
} else {
    keyCondition = "#key = :value";
}

final QueryRequest.Builder requestBuilder = QueryRequest.builder()
        .tableName(RANKINGS_TABLE_NAME)
        .indexName(indexName)
        .keyConditionExpression(keyCondition)
        .filterExpression("attribute_exists(isReview)")
        .expressionAttributeNames(exprAttrNames)
        .expressionAttributeValues(exprAttrValues)
        .scanIndexForward(false);  // Most recent first

requestBuilder.limit(limit);  // always set; handler clamps to MAX_PAGE_LIMIT (100) and defaults to DEFAULT_PAGE_LIMIT (10)

final QueryResponse response = dynamoDb.query(requestBuilder.build());

// Derive nextCursor from the last item in results
final List<Map<String, AttributeValue>> items = response.items();
final Map<String, AttributeValue> lek = response.lastEvaluatedKey();
String nextCursor = null;
if (lek != null && !lek.isEmpty() && !items.isEmpty()) {
    final AttributeValue lastDateTime = items.getLast().get(ISO_DATE_TIME);
    if (lastDateTime != null) {
        nextCursor = URLEncoder.encode(lastDateTime.s(), StandardCharsets.UTF_8);
    }
}

return mapItemsToReviewsWithUserMetadata(items, nextCursor);
```

## Key Design Decisions

- **Seek method over ExclusiveStartKey** — `ExclusiveStartKey` requires the full item primary key, which callers don't have. The seek approach (`isoDateTime < :cursor`) only needs `isoDateTime`, which the client already has from `next_cursor` in the previous response.
- **No cursor encoding** — The cursor is a plain `isoDateTime` string. Earlier designs used `Base64url(isoDateTime|restaurantId|identifier)`, but `restaurantId`/`accountId` are redundant (already in query params). Encoding also caused Jackson HTML-escaping issues (`=` → `\u003d`).
- **No cursor format validation** — Do not validate the cursor string in the validator. Wrong values produce bad query results, not a security issue. Strict validation (e.g. `OffsetDateTime.parse()`) would reject valid cursors if the datetime format varies slightly.
- **`limit` always has a value** — The handler parses `limit` as a primitive `int`: if absent it defaults to `DEFAULT_PAGE_LIMIT` (10); if provided it is clamped to `MAX_PAGE_LIMIT` (100) via `Math.min()`. The validator only checks format (positive integer); enforcing the cap is the handler's job, not the validator's.
- **`limit` applies before `filterExpression`** — DynamoDB counts items read, not items returned, toward the limit. The `attribute_exists(isReview)` filter (TODO FRY-114) means pages may return fewer items than requested. This resolves when FRY-114 is addressed.
- **`getRecentReviews` unchanged** — passes `null` as `nextCursor` since it fetches a fixed snapshot, not a paginated list.
- **Use mutable maps for expression attributes** — `Map.of()` is immutable. When conditionally adding cursor attributes, use `new HashMap<>()` so you can `put()` into it.

## Limit Enforcement

- **`limit` absent → `DEFAULT_PAGE_LIMIT` (10)** — the handler always produces an `int`; no null path.
- **`limit` present → clamped to `MAX_PAGE_LIMIT` (100)** — `Math.min(Integer.parseInt(limitParam), MAX_PAGE_LIMIT)`.
- **Validator does not enforce `MAX_PAGE_LIMIT`** — a value of 200 passes validation; the handler silently clamps it. This keeps validator scope narrow (format only).
- **`DEFAULT_PAGE_LIMIT` and `MAX_PAGE_LIMIT` are both in `Constants.java`.**

## Testing Checklist

**DAL pagination tests** (`ReviewDALTests`) — for each paginated query method:

1. **Cursor sets key condition** — assert `KeyConditionExpression` includes `AND #dt < :cursor`; `:cursor` value equals the decoded cursor string
2. **No cursor → base key condition only** — assert `KeyConditionExpression` is `#key = :value`; `#dt` and `:cursor` not present
3. **nextCursor returned** — mock non-empty `lastEvaluatedKey` + non-empty items; assert `getNextCursor()` is the last item's URL-encoded `isoDateTime`
4. **No nextCursor when LEK empty** — mock empty/absent `lastEvaluatedKey`; assert `getNextCursor()` is null
5. **No nextCursor when items empty** — mock non-empty LEK but empty items list; assert `getNextCursor()` is null
6. **No nextCursor when last item has no isoDateTime** — mock LEK present, item present but without `ISO_DATE_TIME` key; assert `getNextCursor()` is null

**Validator tests** (`GetAllReviewsRequestValidatorTest`):

1. Null limit → no errors
2. Empty string limit → no errors
3. Valid positive integer → no errors
4. `"1"` (boundary) → no errors
5. `"0"` → error (field = `limit`)
6. Negative → error
7. Non-numeric string → error
8. Decimal string → error
9. Above `MAX_PAGE_LIMIT` (e.g. `"200"`) → **no errors** (validator does not enforce the cap)
10. `supports(GetAllReviewsRequest.class)` → true; `supports(String.class)` → false

## Manual Testing

The `next_cursor` returned by the API is already URL-encoded and can be pasted directly as the `cursor` query param in the API Gateway test console — no transformation needed.

For the **first** page, just omit the `cursor` param entirely. The default limit of 10 applies unless you specify `limit`.
