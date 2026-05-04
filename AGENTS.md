<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **yukta** (6356 symbols, 18459 relationships, 300 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> If any GitNexus tool warns the index is stale, run `npx gitnexus analyze` in terminal first.

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `gitnexus_impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `gitnexus_detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `gitnexus_query({query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `gitnexus_context({name: "symbolName"})`.

## Never Do

- NEVER edit a function, class, or method without first running `gitnexus_impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `gitnexus_rename` which understands the call graph.
- NEVER commit changes without running `gitnexus_detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/yukta/context` | Codebase overview, check index freshness |
| `gitnexus://repo/yukta/clusters` | All functional areas |
| `gitnexus://repo/yukta/processes` | All execution flows |
| `gitnexus://repo/yukta/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |
| Work in the Service area (392 symbols) | `.claude/skills/generated/service/SKILL.md` |
| Work in the Orchestrator area (230 symbols) | `.claude/skills/generated/orchestrator/SKILL.md` |
| Work in the Router area (122 symbols) | `.claude/skills/generated/router/SKILL.md` |
| Work in the Transformer area (114 symbols) | `.claude/skills/generated/transformer/SKILL.md` |
| Work in the Util area (105 symbols) | `.claude/skills/generated/util/SKILL.md` |
| Work in the Resequence area (91 symbols) | `.claude/skills/generated/resequence/SKILL.md` |
| Work in the Api area (89 symbols) | `.claude/skills/generated/api/SKILL.md` |
| Work in the Control area (89 symbols) | `.claude/skills/generated/control/SKILL.md` |
| Work in the Directive area (86 symbols) | `.claude/skills/generated/directive/SKILL.md` |
| Work in the Session area (81 symbols) | `.claude/skills/generated/session/SKILL.md` |
| Work in the Provider area (79 symbols) | `.claude/skills/generated/provider/SKILL.md` |
| Work in the Aggregate area (58 symbols) | `.claude/skills/generated/aggregate/SKILL.md` |
| Work in the Flow area (50 symbols) | `.claude/skills/generated/flow/SKILL.md` |
| Work in the Message area (45 symbols) | `.claude/skills/generated/message/SKILL.md` |
| Work in the Join area (44 symbols) | `.claude/skills/generated/join/SKILL.md` |
| Work in the Store area (40 symbols) | `.claude/skills/generated/store/SKILL.md` |
| Work in the Process area (37 symbols) | `.claude/skills/generated/process/SKILL.md` |
| Work in the Valve area (35 symbols) | `.claude/skills/generated/valve/SKILL.md` |
| Work in the Ui area (22 symbols) | `.claude/skills/generated/ui/SKILL.md` |
| Work in the Controller area (20 symbols) | `.claude/skills/generated/controller/SKILL.md` |

<!-- gitnexus:end -->

<skills_system priority="1">

## Available Skills

<!-- SKILLS_TABLE_START -->
<usage>
When users ask you to perform tasks, check if any of the available skills below can help complete the task more effectively. Skills provide specialized capabilities and domain knowledge.

How to use skills:
- Invoke: `npx openskills read <skill-name>` (run in your shell)
  - For multiple: `npx openskills read skill-one,skill-two`
- The skill content will load with detailed instructions on how to complete the task
- Base directory provided in output for resolving bundled resources (references/, scripts/, assets/)

Usage notes:
- Only use skills listed in <available_skills> below
- Do not invoke a skill that is already loaded in your context
- Each skill invocation is stateless
</usage>

<available_skills>

<skill>
<name>generate-test-cases</name>
<description>"Use when the user asks to analyze code for test coverage, list what test cases are needed, or review testing strategy — WITHOUT generating actual test code."</description>
<location>project</location>
</skill>

<skill>
<name>generate-tests</name>
<description>"Use when the user asks to generate, create, or write unit tests for code. Analyzes the target code, produces a structured test case list for review, then generates test code. Supports Java (JUnit 5, Mockito, AssertJ)."</description>
<location>project</location>
</skill>

</available_skills>
<!-- SKILLS_TABLE_END -->

</skills_system>
