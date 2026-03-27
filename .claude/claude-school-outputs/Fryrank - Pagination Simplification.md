# Fryrank - Pagination Simplification

## What We Built

Implemented cursor-based pagination for the `GetAllReviews` API, then simplified it by reverting an overly-complex composite cursor design. The final approach uses a plain `isoDateTime` string as the cursor — no Base64 encoding, no composite key — because the DynamoDB GSI + known query parameters already handle the necessary filtering. We also fixed two bugs left from a reverted state: a missing DynamoDB SDK dependency and a missing `DynamoDbUtils` utility class.

## How It Works

**Cursor as a seek value (not an ExclusiveStartKey):**

Instead of using DynamoDB's native `ExclusiveStartKey` pagination (which requires the full item key: `restaurantId` + `identifier` + `isoDateTime`), the implementation uses a key condition approach:

```
keyConditionExpression: "pk = :pk AND isoDateTime < :cursor"
```

For `restaurantId-time-index`: `restaurantId = :rid AND isoDateTime < :cursor`
For `accountId-time-index`: `accountId = :aid AND isoDateTime < :cursor`

Since `restaurantId` / `accountId` are already known from the request's query params, the cursor only needs to carry `isoDateTime` — the one piece of information the caller doesn't already have.

**Returning `nextCursor`:**

After each query, if `response.lastEvaluatedKey()` is non-empty (DynamoDB hit the `limit` and stopped early), the `isoDateTime` of the last item in the result is returned as `nextCursor`. The client passes this back as the `cursor` query param on the next request.

**Default limit:**

If the caller omits the `limit` param, the handler defaults to `DEFAULT_PAGE_LIMIT = 10`. The validator only validates format if `limit` is present — it never requires it.

## Key Design Decisions & Trade-offs

- **Seek method vs ExclusiveStartKey** — The seek approach (`isoDateTime < :cursor`) avoids needing `identifier` (the table SK) in the cursor. `ExclusiveStartKey` would require the full primary key of the last item, which callers don't have. The downside: if two reviews share the exact same `isoDateTime`, one could be skipped. In practice, timestamps are granular enough to make this negligible.

- **Cursor = URL-encoded isoDateTime** — Earlier designs encoded the cursor as `Base64url(isoDateTime|restaurantId|identifier)`. That was unnecessary since `restaurantId`/`accountId` are already in the query params. The cursor is now just the `isoDateTime` URL-encoded (e.g. `2026-03-17T03%3A58%3A02Z`) so it's safe to use as a query param value. API Gateway V2 automatically decodes query params before they reach the Lambda, so no explicit decode step is needed on the way in.

- **No cursor format validation in the validator** — The validator doesn't parse or validate the cursor string. Wrong cursor values produce bad query results (no items or wrong page), but not a security issue. Strict validation would break the API's own `nextCursor` output if the datetime format ever varies slightly.

- **limit is optional, defaulted in the handler** — Validators only check format; defaults belong in the handler where logging context exists. A `DEBUG` log fires when the default is applied.

- **DynamoDbUtils is a simple factory** — Just calls `DynamoDbClient.create()`, which picks up region from `AWS_REGION` (set automatically in Lambda) and credentials from the IAM role. No SSM needed for DynamoDB configuration.

## Concepts to Remember

**Cursor-based pagination** — Instead of page numbers (which break when data changes), a cursor is a bookmark pointing to the last item seen. The next request asks for items "after this cursor". Stateless, stable, and efficient for append-heavy data.

**Seek method (keyset pagination)** — A cursor-based strategy where the cursor is a value used directly in a `WHERE` / key condition clause (`isoDateTime < :cursor`), as opposed to DynamoDB's `ExclusiveStartKey` which tells the DB "start scanning after this exact item". The seek method is more portable and avoids needing the full primary key.

**DynamoDB GSI (Global Secondary Index)** — An alternate index on a DynamoDB table that lets you query by a different partition key + sort key. `restaurantId-time-index` has PK=`restaurantId`, SK=`isoDateTime`. This is what makes `isoDateTime < :cursor` efficient — it's a range scan on the index's sort key.

**`LastEvaluatedKey`** — DynamoDB's signal that a query stopped early (because it hit the `limit`). Non-empty means there are more results. Used here to decide whether to set `nextCursor` in the response.

**`filterExpression` vs `keyConditionExpression`** — Key conditions run before data is read (efficient). Filter expressions run after data is read (reads consumed, then items discarded). The `attribute_exists(isReview)` filter is a temporary workaround (tracked as FRY-114) that means pages may return fewer items than the `limit`.

**Immutable vs mutable maps in Java** — `Map.of()` creates immutable maps. When building DynamoDB expression attribute maps conditionally (adding cursor attributes only when a cursor is present), you need a `HashMap` so you can `put()` into it.

**URL encoding** — Special characters like `:` and `+` in a query param value must be percent-encoded (e.g. `%3A`, `%2B`) so they aren't misinterpreted by HTTP. In Java: `URLEncoder.encode(value, StandardCharsets.UTF_8)`. The cursor should be URL-encoded in the JSON response so it's safe to use as a query param.

**URL decode on the way in (API Gateway test console gotcha)** — The API Gateway test console passes query param values to the Lambda **as-is**, without URL-decoding them. So if a user copies `2026-03-17T10%3A00%3A00Z` from a JSON response and pastes it into the test console, the Lambda receives the literal string with `%3A` still in it. If that encoded cursor is used directly in a DynamoDB key condition, it silently corrupts the comparison: `%` is ASCII 37 and `:` is ASCII 58, so `"2026-03-17T10%3A..."` compares as *less than* `"2026-03-17T10:..."` — which means all of today's reviews (with `:`) are excluded, producing an empty result or wrong page. Fix: always call `URLDecoder.decode(cursor, StandardCharsets.UTF_8)` in the handler before passing the cursor downstream. This is safe either way — if a real HTTP client already caused API Gateway to decode it, `URLDecoder` on a clean string is a no-op.

## Files Changed

| File | What Changed |
|------|--------------|
| `build.gradle.kts` | Added `software.amazon.awssdk:dynamodb:2.24.0` dependency |
| `util/DynamoDbUtils.java` | Created — simple factory that returns `DynamoDbClient.create()` |
| `Constants.java` | Added `DEFAULT_PAGE_LIMIT = 10` and `GET_ALL_REVIEWS_REQUEST_VALIDATOR_ERRORS_OBJECT_NAME` |
| `dal/ReviewDAL.java` | Renamed `cursorIsoDateTime` → `cursor` in interface signatures |
| `dal/ReviewDALImpl.java` | Updated `getAllReviewsByRestaurantId/ByAccountId` to 3-arg; `queryReviews` now applies limit + seek-cursor; returns `nextCursor` as URL-encoded `isoDateTime` of last item |
| `domain/ReviewDomain.java` | Renamed `cursorIsoDateTime` → `cursor` |
| `validator/GetAllReviewsRequestValidator.java` | Made `limit` optional; removed `OffsetDateTime` cursor validation entirely |
| `handler/GetAllReviewsHandler.java` | Added `DEFAULT_PAGE_LIMIT` fallback with DEBUG log; URL-decodes incoming cursor before passing to domain |
| `dal/ReviewDALTests.java` | Updated all 1-arg `getAllReviewsByRestaurantId/ByAccountId` call sites to 3-arg |

## Testing Approach

- **Unit tests (`ReviewDALTests`)** — All existing tests updated to pass `(id, TEST_LIMIT, null)` to the new 3-arg signatures. Tests mock `dynamoDb.query()` with `any(QueryRequest.class)` so they don't care about the specific key condition — they validate the response mapping logic.
- **Build verification** — `./gradlew build` runs all tests. All passed after the changes.
- **Manual API testing** — Use the API Gateway test console. First call with no `cursor` param returns `next_cursor` in the response. The value is already URL-encoded (e.g. `2026-03-17T03%3A58%3A02Z`) — paste it directly as the `cursor` query param on the next call to get the next page.
