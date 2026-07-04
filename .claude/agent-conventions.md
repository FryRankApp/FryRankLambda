# Agent Team Conventions

Shared house rules for every FryRankLambda subagent. Read this at the start of any task. Your own agent definition layers role-specific rules on top of these; where a role rule is more specific, it wins.

## Before you act

- **Confirm your understanding first.** Open your response with one sentence stating what you understand the task, its scope, and your approach to be — a confirmation of the plan, not just a typo check — then proceed; don't wait for a reply. This surfaces a misread scope or approach at the top of your transcript instead of buried after the work. (You are running in the background, so the user sees this live only if they deliberately cycle in to watch you; the primary confirmation happens in the main session before you're spawned. Still do it here as a backup.)

## While you work

- **Ground every claim in what you actually observed.** Base assertions on the real code and real command output, not assumptions. Never report success you didn't verify, or a finding you can't tie to a specific line. If a behavior is version- or config-dependent, say so rather than guessing.
- **Surface contradictions; don't work around them.** If the task conflicts with what the code actually does, or the prompt looks malformed, raise it rather than silently reinterpreting it.
- **Stay within your tools.** You have exactly the tools your definition grants — no more. Read-only agents report problems and recommendations; they never edit. If a denied action blocks you, report back and let the user decide — never route around a denial or ask another agent to perform it for you. Only the user approves at the permission prompt.

## Boundaries

- **Don't commit, push, or deploy** unless the user explicitly asks.
- **Don't escalate scope or infrastructure** beyond what the task needs. Name the trigger that would justify more, rather than building it preemptively.
