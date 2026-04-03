# DAL Pagination Planning

## Context

Use this skill when implementing cursor-based pagination in the DAL layer, particularly for DynamoDB-backed queries using GSIs.

## Cursor Format

**Format:** URL-encoded `isoDateTime` string — e.g. `2026-03-17T03%3A58%3A02Z`.

The cursor is the `isoDateTime` of the last item returned, URL-encoded so it's safe to use as a query param value. Since `restaurantId` and `accountId` are already known from the request's query params, they don't need to be in the cursor.

The cursor must be **URL-decoded in the handler** before use. The API Gateway test console passes query param values as-is (no decoding), so a cursor pasted from the JSON response (`2026-03-17T10%3A00%3A00Z`) arrives at the Lambda still encoded. If it's used as-is in a DynamoDB key condition, `%` (ASCII 37) compares differently than `:` (ASCII 58), silently corrupting the comparison. Always call `URLDecoder.decode(cursor, StandardCharsets.UTF_8)` in the handler before passing the cursor downstream.

## DynamoDB Pagination Pattern

The canonical implementation is `ReviewDALImpl.queryReviews(...)` in `src/main/java/com/fryrank/dal/ReviewDALImpl.java`. Read that method for the authoritative query construction, cursor injection, and `nextCursor` derivation logic — do not rely on any snapshot here.

## Key Design Decisions

- **Seek method over ExclusiveStartKey** — `ExclusiveStartKey` requires the full item primary key, which callers don't have. The seek approach (`isoDateTime < :cursor`) only needs `isoDateTime`, which the client already has from `next_cursor` in the previous response.
- **No cursor encoding** — The cursor is a plain `isoDateTime` string. Earlier designs used `Base64url(isoDateTime|restaurantId|identifier)`, but `restaurantId`/`accountId` are redundant (already in query params). Encoding also caused Jackson HTML-escaping issues (`=` → `\u003d`).
- **No cursor format validation** — Do not validate the cursor string in the validator. Wrong values produce bad query results, not a security issue. Strict validation (e.g. `OffsetDateTime.parse()`) would reject valid cursors if the datetime format varies slightly.
- **`limit` always has a value** — The handler parses `limit` as a primitive `int`: if absent or invalid format it defaults to `DEFAULT_PAGE_LIMIT` (10); if provided it is clamped to `[1, MAX_PAGE_LIMIT]` via `Math.min(Math.max(parsed, 1), MAX_PAGE_LIMIT)`. There is no limit validator — bad input silently falls back to default rather than returning a 400. This is a deliberate design choice: `limit` is optional and has a sensible default, so being forgiving is preferable to erroring on malformed input.
- **`limit` applies before `filterExpression`** — DynamoDB counts items read, not items returned, toward the limit. The `attribute_exists(isReview)` filter (TODO FRY-114) means pages may return fewer items than requested. This resolves when FRY-114 is addressed.
- **`getRecentReviews` unchanged** — passes `null` as `nextCursor` since it fetches a fixed snapshot, not a paginated list.
- **Use mutable maps for expression attributes** — `Map.of()` is immutable. When conditionally adding cursor attributes, use `new HashMap<>()` so you can `put()` into it.

## Limit Enforcement

- **`limit` absent or invalid format → `DEFAULT_PAGE_LIMIT` (10)** — `NumberFormatException` is caught and logged as a warning; no error is returned to the caller.
- **`limit` present and valid → clamped to `[1, MAX_PAGE_LIMIT]`** — `Math.min(Math.max(Integer.parseInt(limitParam), 1), MAX_PAGE_LIMIT)`. Zero and negatives clamp to 1; values above 100 clamp to 100.
- **No `GetAllReviewsRequestValidator`** — it was deleted. Limit validation is entirely the handler's responsibility via silent fallback.
- **`DEFAULT_PAGE_LIMIT` and `MAX_PAGE_LIMIT` are both in `Constants.java`.**

## Testing Checklist

**DAL pagination tests** (`ReviewDALTests`) — for each paginated query method:

1. **Cursor provided → returns output** — mock empty items, pass a cursor; assert output is not null, `getNextCursor()` is null, reviews is empty. Do **not** assert query shape (no `ArgumentCaptor` on `KeyConditionExpression` or attribute maps).
2. **nextCursor returned** — mock non-empty `lastEvaluatedKey` + non-empty items; assert `getNextCursor()` is not null. Do **not** assert the exact encoded value — that belongs in a dedicated encoding test.
3. **No nextCursor when LEK empty** — mock empty/absent `lastEvaluatedKey`; assert `getNextCursor()` is null
4. **No nextCursor when items empty** — mock non-empty LEK but empty items list; assert `getNextCursor()` is null
5. **No nextCursor when last item has no isoDateTime** — mock LEK present, item present but without `ISO_DATE_TIME` key; assert `getNextCursor()` is null

> Note: There is no separate "no cursor → base key condition" test — that scenario is already covered by the existing non-pagination DAL tests which all pass `null` as the cursor.

**Limit tests** — none needed. Invalid or missing limit silently falls back to `DEFAULT_PAGE_LIMIT` in the handler. Limit behavior is covered implicitly by the existing `APIGatewayRequestValidatorTest` cases that include a `limit` query param.

**Handler tests** (`GetAllReviewsHandlerTests`) — add tests for these pagination-specific cases:

- **Cursor decoding** — verify the handler URL-decodes the cursor before passing it to the domain/DAL. A cursor pasted from a JSON response arrives still encoded; using it as-is silently corrupts the DynamoDB comparison.
- **Limit fallback** — missing or non-numeric `limit` → `DEFAULT_PAGE_LIMIT` (10); no error returned.
- **Limit clamping** — valid `limit` outside `[1, MAX_PAGE_LIMIT]` is clamped, not rejected.

## Manual Testing

The `next_cursor` returned by the API is already URL-encoded and can be pasted directly as the `cursor` query param in the API Gateway test console — no transformation needed.

For the **first** page, just omit the `cursor` param entirely. The default limit of 10 applies unless you specify `limit`.
