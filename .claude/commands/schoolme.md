---
description: Export a learning summary of the current coding session to C:\Users\Matt\Documents\Claude-school\
---

## Your task

The user wants a "school me" export — a well-formatted learning summary of the current coding session saved as a Markdown file.

**Step 1: Determine the session name**
- If the user provided a name after `/schoolme` (e.g. `/schoolme Fryrank - Pagination Feature`), use that.
- Otherwise, infer an appropriate filename from the conversation context (e.g. the feature, bug, or topic worked on).
- Filename format: `<Project> - <Topic>` (e.g. `Fryrank - Pagination Feature`)

**Step 2: Write the file**
- Output path: `C:\Users\Matt\Documents\Claude-school\<filename>.md`
- Use the Markdown format defined in the SKILL.md at `.claude/claude-school/SKILL.md`

**Step 3: Confirm**
- Tell the user the file was saved and where.
