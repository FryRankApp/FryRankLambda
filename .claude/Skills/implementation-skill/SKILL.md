---
name: implementation-skill
description: Use when implementing new features or making changes in FryRankLambda. Follow DRY, avoid pass-through methods, avoid unnecessarily splitting methods. Consult the engineer before refactoring existing code or adding new layers.
version: 1.0.0
---

# Implementation Skill

Principles for writing implementation code in FryRankLambda. Built up over real implementation sessions — every rule here came from a moment where pushback corrected the approach.

## Core Principles

### 1. Follow DRY

Avoid repeating logic that can be handled in a single method.

If two callers need slightly different versions of the same operation, consolidate into one helper that takes the variant as a parameter — don't write two near-identical functions.

### 2. Avoid Pass-Through Methods

Don't add methods that just forward arguments to another layer without adding any value (logging, mapping, validation, branching).

Especially avoid pass-throughs **when existing code needs to be refactored to support a new feature**. In that situation: **stop and consult the engineer first**. Explain what needs to change and why before writing code. The engineer may have a different shape in mind, or may want you to extend the existing method rather than add a new one.

The layer boundaries (handler → domain → DAL) earn their keep when each layer adds something. A new method that just calls one method on the layer below is a smell.

### 3. Avoid Unnecessarily Splitting Methods

If you find yourself splitting a small helper into two functions because the callers differ slightly, first ask whether one function with an optional parameter would work.

**Real session example:** During the categorization feature, an early draft split a filter-expression builder into `buildFilterExpression` (for base + tag) and `buildTagFilterExpression` (for tag only). The engineer pushed back — a single function with an optional `baseFilter` argument handled both cases cleanly. The split was solving a problem that didn't exist.

Rule of thumb: if the second function's body is "do half of what the first function does", you don't need a second function.

### 4. Minimum Surface Change

Scope changes to what the feature requires. Don't:
- Refactor surrounding code that already works.
- Add error handling for impossible cases.
- "Future-proof" for hypothetical needs that haven't been requested.
- Add abstractions for a single concrete use case.

For internal code, trust framework guarantees. Validate only at true system boundaries (user input, external APIs, third-party SDKs).

### 5. Backend Validation Is Light By Default

For user-driven inputs (review tags, search filters, etc.), the frontend is the primary validator. The backend should not redundantly re-validate unless there's a security or data-integrity reason.

**Bad GET queries are fine** — they should silently return empty results rather than 4xx errors. The frontend is responsible for never sending malformed queries; if it does, returning `[]` is an acceptable degradation.

**Prefer silent fallback over hard error for optional inputs.** When an optional input is malformed (e.g. an unparseable `limit`, an unknown `tag`, a corrupted `cursor`), prefer a safe default or empty result over a 4xx. Examples in the codebase:
- `limit`: invalid format → `DEFAULT_PAGE_LIMIT`; out-of-range → clamped to `[1, MAX_PAGE_LIMIT]`. No validator class.
- `cursor`: bad value produces bad query results, not a thrown exception. Strict format validation would reject valid cursors if the datetime format varies slightly.
- `tag`: unknown value silently returns empty results.

The rule: if a bad value can only cause "empty or unexpected results" (not a security or data-integrity issue), don't validate.

### 6. Don't Build A Reverse Index / GSI / Cache Until It's Needed

For MVP features, prefer the lightweight approach (filter expressions, in-memory filtering) over the scalable one (GSIs, reverse indexes, cache layers). Note the migration path in a comment or design doc — but don't ship the heavier solution preemptively.

### 7. Wrap Likely-To-Grow Param Lists In A Record

When a method takes filter / option / config parameters that are likely to grow, prefer a Java record over individual parameters from the start.

```java
// Instead of:
GetAllReviewsOutput getAllReviewsByRestaurantId(String restaurantId, Integer limit, String cursor, String tag);

// Prefer:
GetAllReviewsOutput getAllReviewsByRestaurantId(String restaurantId, Integer limit, String cursor, ReviewFilter filter);

public record ReviewFilter(String tag) {}
```

Adding new filters becomes a no-op for the method signature — append a new record component, callers and existing tests don't shift. Records' value-based `equals`/`hashCode` are free and Mockito-compatible (`eq(new ReviewFilter("Curly"))` matches structurally without a custom matcher).

This is **not** a violation of Principle 4 (Minimum Surface Change). The cost today is one extra line of construction at the call site (`new ReviewFilter(tag)`); no new abstraction layer, no hooks, no extension points. The record stays the right shape forever if the params never grow, and absorbs new ones with zero signature churn if they do.

**Real session example:** First pass passed `String tag` directly through the DAL, domain, and handler. A reviewer pointed out that every future filter (score range, dietary, etc.) would force a new parameter on every method in the chain. Switching to `ReviewFilter` stabilized the signatures.

### 8. Use `Optional<T>` On Internal Helpers For Sometimes-Absent Values

When an *internal helper* takes a parameter that's legitimately sometimes absent (not just nullable for convenience), declare it as `Optional<T>` in the helper's signature. This:

- Surfaces the optionality in the type — readers must handle both cases.
- Prevents accidental NPEs from unchecked use of the value.
- Enables fluent `.orElse(...)`, `.map(...)`, `.ifPresent(...)` instead of `if (x == null) ... else ...`.

Apply this **only to internal helpers**, not to public DAL / domain / handler method signatures — those follow the codebase convention of nullable parameters annotated with `@NonNull` where required. Propagating `Optional` to public APIs is a Java style violation and not what the rest of this codebase does.

**Real session example:** `buildFilterExpression` originally took `String baseFilter` with an internal `baseFilter == null` check. The reviewer pushed for `Optional<String> baseFilter` to make the absence part of the type contract.

### 9. Naming Tracks The Schema, Not The Use Case

When naming a field, parameter, or record component, match the specificity of the rest of the stack — model class field, DB attribute key, URL param, constants. Don't introduce a more specific name in just one layer.

The schema sets the right level of specificity. If a value is stored as one undifferentiated `List<String>`, then a generic name (`tag`) is honest. If the schema later splits into typed categories (e.g., separate DDB attributes for `fryShape`, `flavor`, `topping`), rename in lockstep across the stack — model, key constant, URL param, filter field, test constants — all together. A schema split is what justifies a name split.

**Real session example:** Considered renaming `ReviewFilter.tag` to `fryTypeTag` to signal the value's domain meaning. Decided against it — the rest of the stack (`Review.tags`, `TAGS_KEY`, `QueryParam.TAG("tag")`, URL param) all use generic "tag," and a localized rename would create a confusing seam where `filter.fryTypeTag()` maps to `params.get("tag")` and `review.getTags()`. The future plan (split tags into shape/flavor/topping) is what would drive the rename — schema-first.

## DynamoDB / Query Construction Notes

- **Use mutable maps for expression attributes when conditionally building.** `Map.of()` is immutable; if you might conditionally add cursor / tag / filter bindings, start with `new HashMap<>()` and `put()` into it. Trying to `put()` on a `Map.of()` throws.
- **URL-decode query-string values in the handler before passing them downstream.** API Gateway passes query params verbatim, so a value that's already URL-encoded (like a cursor from a previous response) arrives still encoded. If used as-is in a DynamoDB key condition, `%` (37) compares differently than `:` (58) and silently corrupts results.
- **Filter expressions run *after* DynamoDB's pagination.** A `limit` counts items *read*, not items *returned*. A page with a strict filter expression may return fewer items than the requested limit. This is acceptable for MVP; a GSI is the long-term fix.

## When To Pause And Ask

- About to refactor existing code to fit a new feature → ask first.
- About to add a new validator class, handler, or interface method that doesn't have a clearly distinct responsibility from existing ones → ask first.
- About to add a shared enum, config object, or constants file for something that's only used in one place → ask first.
- Unsure whether a piece of logic belongs in the handler, the domain, or the DAL → ask first.

## Code Style Reminders

- Don't write comments that just describe what the code does — well-named identifiers cover that.
- Don't reference the current PR or task in a comment ("added for the tags feature") — that belongs in the commit message.
- Don't add backwards-compatibility shims for code that's not yet shipped.
- When deleting code, delete it cleanly — no `// removed` comments or unused `_var` renames.

## See Also

- `tests-skill/SKILL.md` for testing principles that complement these.
- `claude-school/SKILL.md` for the post-session learning export format.
