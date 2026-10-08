---
name: release-check
description: Run before opening a PR. Verifies both test suites against the baseline.
---

# Release check

Invoke it with `/release-check`.

## Preconditions

- You are at the root of the ops-dashboard repository (`pom.xml` and `package.json`
  are both present).
- `node_modules/` exists. If not, run `npm install` first and say so.
- The working tree has the change you intend to ship. Do not stash or discard
  anything.

## Steps

1. Run `python3 tools/release_check.py`. It runs both suites, reads the counts from
   the Surefire XML and Jest JSON reports, checks them against the baseline, and
   prints the report. It exits 0 for READY and 1 for NOT READY.
2. For every problem in its Notes, find the cause before reporting: which test was
   deleted, skipped or failing, and in which commit or uncommitted change.
3. Report the script's output as it is, then one line per cause you found.

## Constraints

- The baseline lives in `BASELINE` in `tools/release_check.py`. The script is the
  rule; do not override its verdict. A suite FAILs when it did not run, when any
  test failed, errored or was skipped, or when fewer tests ran than the baseline.
- A count above the baseline is fine when the change adds tests. In that case,
  raise `BASELINE` and the counts in the README's "Test it" section in the same
  change; the script fails if they disagree.
- Never edit, skip or delete a test to make the check pass.
- Do not open the PR from this skill; it only reports.
