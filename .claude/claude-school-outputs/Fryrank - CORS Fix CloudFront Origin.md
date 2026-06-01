# Fryrank - CORS Fix CloudFront Origin

## Context

This document describes a common class of bug that can surface whenever a new deployment environment is added to this project — a CORS failure caused by a frontend origin not being listed in the Lambda's allowed origins. It's written as a concrete example of what went wrong and how to diagnose it, so that future contributors recognize the pattern quickly.

## How CORS Works in This Project

Every response from FryRankLambda goes through `APIGatewayResponseBuilder`, which attaches an `Access-Control-Allow-Origin` header if and only if the request's `Origin` header matches an entry in `ALLOWED_ORIGINS` (defined in `Constants.java`). If the origin is present, the browser accepts the response. If it's absent, the header is omitted and the browser blocks the response entirely — even if the HTTP status code is 200.

This means: **server logs will show a successful response while the browser shows a CORS error.** The Lambda did its job; the browser is what blocks it.

`ALLOWED_ORIGINS` currently contains the known CloudFront distribution URLs for each deployed environment (staging, production, etc.). Any new environment — a new CloudFront distribution, a new dev sandbox, a preview deploy — must have its origin added here before the frontend can call the API.

## What Goes Wrong (and Why It's Hard to Spot)

When an origin is missing from `ALLOWED_ORIGINS`:

1. The Lambda returns a normal HTTP response (200 or 500), but without the `Access-Control-Allow-Origin` header.
2. The browser silently drops the response. No response body, no status code visible to JavaScript.
3. In Axios, this means `err.response` is `undefined` — Axios only sets `err.response` when the browser actually delivers a response.
4. If the frontend catch block assumes `err.response` exists and tries to access a nested property (e.g. `err.response.data.error.message`), the catch itself throws a `TypeError`.
5. In Redux-Saga, an unhandled throw inside a `takeEvery` worker permanently kills that watcher for the session — meaning *all* saga-driven data fetching stops, not just the failing request.

This is why a single missing CORS origin can cause seemingly unrelated UI failures (like the Google Map not loading) with no obvious error in the network tab.

## How to Fix It

Add the missing origin to `ALLOWED_ORIGINS` in `Constants.java`:

```java
// Constants.java
public static final Set<String> ALLOWED_ORIGINS = Set.of(
    "https://your-existing-origin.cloudfront.net",
    "https://your-new-origin.cloudfront.net"   // <-- add the new environment here
);
```

After deploying, wait 1-3 minutes for Lambda propagation before testing. The old Lambda version continues serving traffic during the rollout window, so errors immediately after deploy do not indicate the fix failed.

## Key Design Notes

- **CORS headers are attached even on error responses.** `APIGatewayResponseBuilder.handleRequest()` computes `corsHeaders` before entering the try block, so 400/404/500 responses all carry the CORS header as long as the origin is recognized. A 500 with no CORS header almost always means the origin wasn't in `ALLOWED_ORIGINS`, not that an error path is broken.
- **Never use a wildcard origin (`*`)** — write operations require Google OAuth, and `Access-Control-Allow-Origin: *` is incompatible with credentialed requests.
- **No unit test changes are needed** for origin additions. CORS is an integration-level concern; correctness is verified end-to-end in the browser.

## Concepts to Remember

- **CORS is enforced client-side.** The server returns a full response regardless. It is the browser that blocks it when the expected header is missing.
- **`err.response` is `undefined` for CORS/network errors in Axios.** Always guard against this in catch blocks (`err.response?.data`).
- **A saga worker that throws kills its own `takeEvery` watcher.** Unsafe property access in a catch block can silently take down multiple unrelated data-fetching flows.

## Files to Change

| File | What to Change |
|------|----------------|
| `src/main/java/com/fryrank/Constants.java` | Add the new CloudFront origin to `ALLOWED_ORIGINS` |
