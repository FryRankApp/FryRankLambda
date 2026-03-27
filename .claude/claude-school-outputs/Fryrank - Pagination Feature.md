# Fryrank - Pagination Feature

## What We Built

Added cursor-based pagination to the `GetAllReviews` endpoint in FryRankLambda. Previously the endpoint returned all reviews for a restaurant at once, which doesn't scale. Now it returns a page of results plus a cursor token the client can pass back to fetch the next page.

## How It Works

DynamoDB has native pagination built in. When a query hits the `limit`, it returns a `LastEvaluatedKey` — essentially a bookmark to where it stopped. On the next request, you pass that back as `ExclusiveStartKey` and DynamoDB resumes from there.

We wrapped this in an opaque cursor token so the client never needs to know DynamoDB internals:

1. Client calls `GET /reviews?restaurantId=XYZ` — gets a page of reviews + a `next_cursor` string
2. Client calls `GET /reviews?restaurantId=XYZ&cursor=<token>` — gets the next page
3. When `next_cursor` is `null`, there are no more results

## Key Design Decisions & Trade-offs

- **Cursor-based vs offset pagination** — We used cursors (`next_cursor` token) rather than `?page=2`. Cursors handle real-time inserts better: if a new review is added between page 1 and page 2, offset pagination would show a duplicate or skip a result. Cursors don't have this problem.

- **Composite cursor format** — The cursor encodes three fields pipe-delimited, then Base64url encoded: `isoDateTime|restaurantId|identifier`. This mirrors exactly what DynamoDB needs for `ExclusiveStartKey` on the two GSIs used.

- **No JSON in the cursor** — We considered `{"ts": "...", "id": "..."}` but went with the simpler pipe-delimited string. Avoids pulling in Gson/Jackson just for a cursor, and stays consistent with the existing `CursorUtils` class.

- **`accountId` not stored in cursor** — The `accountId-time-index` GSI needs `accountId` in its `ExclusiveStartKey`, but we don't encode it redundantly. Since `identifier` is always `"REVIEW:<accountId>"`, we just strip the prefix at query time.

- **`limit` applies before filter** — DynamoDB counts *items read* toward the limit, not *items returned after filtering*. A temporary `attribute_exists(isReview)` filter means pages can come back short. This resolves when TODO FRY-114 cleans up the data model.

- **`getRecentReviews` left unchanged** — It fetches a fixed snapshot, not a paginated list, so no cursor needed there.

## Concepts to Remember

| Concept | Plain English |
|---------|--------------|
| **Cursor-based pagination** | Instead of "give me page 2", you say "give me results after this specific item". More reliable for live data. |
| **DynamoDB ExclusiveStartKey** | DynamoDB's built-in way to resume a query from where you left off. |
| **GSI (Global Secondary Index)** | A way to query a DynamoDB table by attributes other than the primary key. We used `restaurantId-time-index` and `accountId-time-index`. |
| **Base64url encoding** | URL-safe variant of Base64. Used here to safely pass a structured cursor as a query parameter. |
| **Opaque cursor** | The client treats the cursor as a black box — it just passes it back. The server is the only one who needs to understand it. |
| **DAL pattern** | Data Access Layer — keeps all database logic in one place, separate from business logic. Handlers → Domain → DAL → DB. |

## Files Changed

| File | What Changed |
|------|--------------|
| `src/main/java/com/fryrank/util/CursorUtils.java` | `encode()` and `decode()` methods for the composite cursor |
| `src/main/java/com/fryrank/dal/ReviewDALImpl.java` | `queryReviews()` helper applies `limit`, sets `ExclusiveStartKey` from cursor, encodes `nextCursor` from `LastEvaluatedKey` |
| `src/test/java/com/fryrank/dal/ReviewDALTests.java` | Tests for: limit applied, cursor sets correct ESK fields, `nextCursor` returned, no `nextCursor` on last page |
| `src/test/java/com/fryrank/TestConstants.java` | Test constants updated for new cursor format |
| `python/scripts/generate_cursor.py` | New helper script to manually craft cursors for API Gateway testing |

## Testing Approach

- **Unit tests** (Mockito): Mocked `DynamoDbClient`, verified `QueryRequest` had correct `limit`, `exclusiveStartKey` fields, and that `nextCursor` was encoded/null correctly based on `lastEvaluatedKey`.
- **Manual API testing**: Used `generate_cursor.py` to craft a cursor for a known review, then passed it as a `cursor` query param in the API Gateway console to verify pagination resumed correctly.
