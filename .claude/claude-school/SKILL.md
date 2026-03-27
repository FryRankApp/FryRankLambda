---
name: claude-school
description: Use this skill when the user invokes /schoolme, asks to "summarize what we learned", "export our session", or "school me". Also use at the end of any implementation session to prompt the user about exporting a learning summary.
version: 1.0.0
---

# Claude School

Export well-formatted learning summaries of coding sessions to `.claude/claude-school-outputs/` (relative to the project root, i.e. the `FryRankLambda` directory).

## End-of-Session Habit

At the natural end of any implementation session (feature complete, PR created, bug fixed, refactor done), prompt the user:

> "Want me to do a `/schoolme` for this session? You can give it a custom name like `/schoolme Fryrank - Pagination Feature`, or I'll pick one for you."

Do this once, at a natural stopping point — not mid-task.

## Export Format

File path: `.claude/claude-school-outputs/<filename>.md` (relative to project root)

Use the following Markdown structure:

```markdown
# <Project> - <Topic>

## What We Built
One paragraph describing the feature or fix and why it was needed.

## How It Works
The core implementation explained plainly — data flow, key decisions, architecture.

## Key Design Decisions & Trade-offs
Bullet points for each meaningful decision made (and why alternatives were rejected).

## Concepts to Remember
A short glossary of SWE concepts touched in this session, with plain-English explanations.

## Files Changed
| File | What Changed |
|------|--------------|
| ...  | ...          |

## Testing Approach
How the feature was tested — unit tests, manual API testing, edge cases covered.
```

## Filename Convention

- Format: `<Project> - <Topic>`
- Examples:
  - `Fryrank - Pagination Feature`
  - `Fryrank - DynamoDB Migration`
  - `Fryrank - Delete Review Fix`
- If the user provides a name via `/schoolme <name>`, use that exactly.
- Otherwise infer from the session context.

## File Format

Always save as `.md`. Markdown renders well on phones via apps like Obsidian Mobile, GitHub Mobile, or any notes app that supports markdown.
