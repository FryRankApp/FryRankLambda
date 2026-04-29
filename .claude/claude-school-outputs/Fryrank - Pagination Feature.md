# FryRank - Cursor-Based Pagination (Final)

## What We Built

Added cursor-based pagination to the `GetAllReviews` endpoint in FryRankLambda. Previously the endpoint returned all reviews for a restaurant at once. Now it returns a page of results plus a cursor token the client can pass back to fetch the next page.

## How It Works

The cursor is just a URL-encoded `isoDateTime` string — no encoding, no composite key. Because `restaurantId` / `accountId` are already in the query params, the cursor only needs to carry the one thing the caller doesn't have: the timestamp of the last item seen.

Each query uses a **seek condition** on the DynamoDB GSI's sort key:

```
restaurantId-time-index:  restaurantId = :rid AND isoDateTime < :cursor
accountId-time-index:     accountId = :aid AND isoDateTime < :cursor
```

After a query, if DynamoDB's `LastEvaluatedKey` is non-empty (it hit the `limit` and stopped early), the `isoDateTime` of the last item in the result is returned as `nextCursor`. The client passes it back as the `cursor` query param on the next request.

**Client flow:**
1. `GET /reviews?restaurantId=XYZ` → page of reviews + `nextCursor` string
2. `GET /reviews?restaurantId=XYZ&cursor=<token>` → next page
3. When `nextCursor` is `null`, there are no more results

**Limit handling:**
- `limit` param is optional
- If omitted or not a valid integer → defaults to `DEFAULT_PAGE_LIMIT = 10`
- If in range → used as-is; if out of range → silently clamped via `Math.min(Math.max(parsed, 1), MAX_PAGE_LIMIT)` where `MAX_PAGE_LIMIT = 100`
- No validator class — limit logic lives entirely in the handler

## Key Design Decisions & Trade-offs

**Seek method vs. DynamoDB ExclusiveStartKey**

DynamoDB's native `ExclusiveStartKey` approach requires the full primary key of the last item (partition key + sort key + GSI keys). That information isn't exposed to callers. The seek method (`isoDateTime < :cursor`) only needs the sort key value, which is already the `nextCursor` value the server returns. Tradeoff: if two reviews share the exact same `isoDateTime`, one could be skipped. In practice this is negligible.

**Cursor = URL-encoded isoDateTime (not Base64 composite)**

An earlier design encoded the cursor as `Base64url(isoDateTime|restaurantId|identifier)`. This was unnecessary — `restaurantId`/`accountId` are already known from query params, and `identifier` (the table SK) isn't needed by the seek method. The final cursor is just the `isoDateTime` value URL-encoded (e.g. `2026-03-17T03%3A58%3A02Z`) so colons don't break query string parsing. API Gateway V2 automatically decodes query params before they reach Lambda, but to be safe the handler calls `URLDecoder.decode()` explicitly (it's a no-op on already-decoded strings).

**URL decode on the way in (API Gateway test console gotcha)**

The API Gateway test console passes query param values to the Lambda **as-is**, without URL-decoding them. If a user pastes `2026-03-17T10%3A00%3A00Z` into the test console, the Lambda receives it with `%3A` still in it. Without explicit decoding, the DynamoDB key condition silently corrupts: `%` (ASCII 37) compares as less than `:` (ASCII 58), so all reviews with `:` in their timestamp are excluded. Fix: always call `URLDecoder.decode(cursor, StandardCharsets.UTF_8)` in the handler.

**No validator for limit — silent fallback instead**

The original `GetAllReviewsRequestValidator` class was deleted. Limit validation turned out to not need a structured error response — invalid inputs (bad format, out of range) just fall back to a sane default or get clamped. This keeps the handler simpler and avoids a validator-vs-handler disagreement about what counts as "valid."

**`nextCursor` in camelCase**

`GetAllReviewsOutput.nextCursor` originally had `@SerializedName("next_cursor")` forcing snake_case. This was removed so Gson serializes it as `nextCursor` (camelCase), consistent with the rest of the API. Since pagination wasn't yet wired to the frontend when the change was made, the migration cost was zero.

**`limit` applies before filter (known gap)**

DynamoDB counts *items read* toward the limit, not *items returned after filtering*. The temporary `attribute_exists(isReview)` filter expression means pages can come back shorter than `limit`. This resolves when FRY-114 cleans up the data model.

## Concepts to Remember

| Concept | Plain English |
|---------|--------------|
| **Cursor-based pagination** | Instead of "give me page 2", you say "give me results after this specific item." More reliable for live data — inserts between pages don't cause duplicates or skips. |
| **Seek method (keyset pagination)** | The cursor value is used directly in a key condition clause (`isoDateTime < :cursor`). More portable than `ExclusiveStartKey`; doesn't require the full primary key. |
| **DynamoDB LastEvaluatedKey** | Non-empty means the query hit the `limit` and stopped early. Used here to decide whether to return `nextCursor`. |
| **DynamoDB GSI** | An alternate index that lets you query by a different PK + SK. `restaurantId-time-index` has PK=`restaurantId`, SK=`isoDateTime` — this is what makes the range scan efficient. |
| **`filterExpression` vs `keyConditionExpression`** | Key conditions run before data is read (efficient, counts toward `limit`). Filter expressions run after (data is read and consumed, then discarded). |
| **`Math.min(Math.max(x, lo), hi)`** | One-liner to enforce both a floor and a ceiling on a user-supplied integer. No if-statements needed. |
| **Primitive vs. boxed types** | `int limit` (primitive) signals "this always has a value." `Integer limit` (boxed) signals "this might be absent." Switching from boxed to primitive forces a default to be chosen at the parse site. |
| **`List.getLast()` (Java 21+)** | Equivalent to `list.get(list.size() - 1)` but more readable. Throws `NoSuchElementException` if empty (vs. `IndexOutOfBoundsException`). |
| **`Map.of()` vs `HashMap`** | `Map.of()` creates immutable maps. When building DynamoDB expression attribute maps conditionally (adding cursor attributes only when a cursor is present), you need a `HashMap` so you can `put()` into it. |
| **Opaque cursor** | The client treats the cursor as a black box and just passes it back. Only the server needs to understand its structure. |

## Final File State

| File | What It Does |
|------|--------------|
| `dal/ReviewDALImpl.java` | `queryReviews()` applies `limit`, injects `isoDateTime < :cursor` condition when cursor is present, URL-encodes the `isoDateTime` of the last item inline via `URLEncoder.encode()`, returns it as `nextCursor` (or `null` if `LastEvaluatedKey` is empty or items is empty) |
| `model/GetAllReviewsOutput.java` | `nextCursor` field — no `@SerializedName`, serializes as camelCase |
| `handler/GetAllReviewsHandler.java` | Parses `limit` with fallback to `DEFAULT_PAGE_LIMIT`; clamps via `Math.min/max`; URL-decodes incoming cursor via `decodeCursor()` helper before passing downstream |
| `Constants.java` | `DEFAULT_PAGE_LIMIT = 10`, `MAX_PAGE_LIMIT = 100` |
| `dal/ReviewDALTests.java` | Pagination tests: cursor generates correctly, no cursor when LEK empty or items empty or last item lacks `isoDateTime`; cursor condition injected into `KeyConditionExpression` for both query paths |

## Testing Approach

- **Unit tests (Mockito):** Mocked `DynamoDbClient`, verified `QueryRequest` had correct `limit` and key condition, and that `nextCursor` was encoded/null based on `LastEvaluatedKey` state.
- **Manual API testing:** Fetched the first page from the API Gateway console, copied the `nextCursor` value from the JSON response, and passed it directly as the `cursor` query param — no transformation needed since the returned value is already URL-encoded.
