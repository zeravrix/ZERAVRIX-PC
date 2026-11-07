---
name: implement-ticket
description: Use when asked to implement, continue or finish a ZERAVRIX ticket (Z-xxx or T-xxx) or the next task from docs/EXECUTION_PLAN.md.
---
# Implement a ticket
1. Open `docs/PROGRESS.md`; pick the first task not done (or the one the user names).
2. Read the task card in `docs/EXECUTION_PLAN.md` and ONLY the blueprint sections it cites.
3. State a 5-line plan: files to touch, test to write first, risks, `[CHECK]` items.
4. Write the failing test, implement, make it pass.
5. Run all gates in `.agents/rules/20-quality-gates.md`.
6. Append to `docs/PROGRESS.md`: ticket, date, summary, evidence (test names, numbers, screenshot paths), unverified items.
7. Stop. Report in 8 lines or fewer. Do not start the next ticket unless asked.
