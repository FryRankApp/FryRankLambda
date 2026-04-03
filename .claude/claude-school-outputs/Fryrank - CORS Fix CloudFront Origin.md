# Fryrank - CORS Fix CloudFront Origin

## What We Built
Added a missing CloudFront distribution URL to the Lambda's `ALLOWED_ORIGINS` set so that the production frontend could successfully call the API. The missing origin was causing a silent CORS failure that crashed the Redux root saga, taking down all data fetching including the Google Map.

## How It Works
When a browser makes a cross-origin request, the Lambda reads the `Origin` request header and checks it against `ALLOWED_ORIGINS` in `Constants.java`. If the origin is present, the Lambda includes `Access-Control-Allow-Origin: <origin>` in the response headers via `HeaderUtils.createCorsHeaders()`. If it's absent, the header is omitted entirely — the Lambda still returns its normal HTTP response (200 or 500), but the browser blocks the response client-side with a CORS error.

The specific origin `https://d3mznj1yywvp2h.cloudfront.net` was not in the allowed list, so every Lambda call from the production frontend was being blocked. The catch block in the frontend Redux saga tried to access `err.response.data.error.message` — but since `err.response` is `undefined` when the browser blocks a response (no axios response object is created), the catch itself threw a `TypeError`, killing **all** `takeEvery` saga watchers including `GET_RESTAURANTS_FOR_QUERY_REQUEST` and `GET_RESTAURANTS_FOR_IDS_REQUEST`. This is why the Google Map never loaded.

## Key Design Decisions & Trade-offs

- **Added a second CloudFront constant** (`FRYRANK_PROD_CLOUDFRONT_2`) rather than replacing the existing one — the original `d3h6a05rzfj3y8` distribution still exists and must remain allowed.
- **Lambda deploys take 1-3 minutes** — the 500 errors seen immediately after deploying were the old Lambda version still serving traffic. Once the deployment propagated, the fix took effect.
- **CORS headers are attached even on error responses** — `APIGatewayResponseBuilder.handleRequest()` computes `corsHeaders` before entering the try block, so 400/404/500 responses all include the CORS header as long as the origin is in the allowed list. A 500 with no CORS header means the origin wasn't recognized, not that the error path is broken.

## Concepts to Remember

- **CORS (Cross-Origin Resource Sharing)**: A browser security mechanism. The server must opt-in to cross-origin access by returning `Access-Control-Allow-Origin`. If the header is missing, the browser blocks the response regardless of HTTP status code.
- **CORS is enforced client-side**: The Lambda still returns a full response. The browser is what blocks it — so server logs show success (200/500) while the browser shows a CORS error.
- **Saga crash from unsafe error access**: If a Redux saga catch block accesses a property that doesn't exist on the error object (e.g. `err.response.data` when `err.response` is undefined), the catch itself throws. In redux-saga, an unhandled throw inside a `takeEvery` worker kills that watcher permanently for the session.
- **`err.response` is undefined for network/CORS errors**: Axios only populates `err.response` when the server sends a response that the browser accepts. A CORS-blocked response means `err.response` is never set.

## Files Changed

| File | What Changed |
|------|--------------|
| `src/main/java/com/fryrank/Constants.java` | Added `FRYRANK_PROD_CLOUDFRONT_2 = "https://d3mznj1yywvp2h.cloudfront.net"` constant and added it to `ALLOWED_ORIGINS` set |

## Testing Approach

- Deployed via `deploy.bat` and waited ~2 minutes for propagation
- Verified in browser console — CORS error resolved, map loaded, reviews populated
- No unit test changes needed (CORS is an integration-level concern tested end-to-end)
