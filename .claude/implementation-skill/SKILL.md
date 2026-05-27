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
- `dal-pagination-planning/SKILL.md` for pagination-specific guidance (this skill borrows several principles from there).
- `claude-school/SKILL.md` for the post-session learning export format.
