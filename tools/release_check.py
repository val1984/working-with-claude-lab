#!/usr/bin/env python3
"""
Run both test suites and check them against the baseline before a PR.

Run it from the repository root:

  python3 tools/release_check.py

A suite passes only if it ran, nothing failed or errored, nothing was skipped, and
at least BASELINE tests ran. Counts come from the machine-readable reports (Surefire
XML, Jest JSON), not from console output. The README's "Test it" section must quote
the same baseline, so the two cannot drift apart.

Exits 0 when the verdict is READY, 1 otherwise. When a change adds tests, raise
BASELINE here and the counts in the README in the same change.
"""
from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

BASELINE = {"java": 25, "jest": 45}

ROOT = Path(__file__).resolve().parent.parent
SUREFIRE_REPORTS = ROOT / "target" / "surefire-reports"
README = ROOT / "README.md"
README_COUNTS = {
    "java": re.compile(r"^\./mvnw test\s+# Java: (\d+) tests", re.M),
    "jest": re.compile(r"^npm test\s+# Frontend: (\d+) tests", re.M),
}


@dataclass
class Suite:
    name: str
    baseline: int
    ran: bool = False
    total: int = 0
    passed: int = 0
    failed: int = 0
    skipped: int = 0
    problems: list[str] = field(default_factory=list)

    def judge(self) -> None:
        if not self.ran:
            self.problems.append(f"{self.name}: the suite did not run (build or setup failure)")
            return
        if self.failed:
            self.problems.append(f"{self.name}: {self.failed} failed or errored")
        if self.skipped:
            self.problems.append(f"{self.name}: {self.skipped} skipped; find out why")
        if self.total < self.baseline:
            self.problems.append(
                f"{self.name}: {self.total} tests, {self.baseline - self.total} below the "
                "baseline; find out what was deleted"
            )

    @property
    def status(self) -> str:
        return "FAIL" if self.problems else "PASS"


def run_java() -> Suite:
    suite = Suite("Java", BASELINE["java"])
    # Stale reports from deleted test classes would inflate the count.
    shutil.rmtree(SUREFIRE_REPORTS, ignore_errors=True)
    subprocess.run(["./mvnw", "-q", "test"], cwd=ROOT)
    reports = list(SUREFIRE_REPORTS.glob("TEST-*.xml"))
    if not reports:
        return suite
    suite.ran = True
    for report in reports:
        attrs = ET.parse(report).getroot().attrib
        tests, skipped = int(attrs["tests"]), int(attrs.get("skipped", 0))
        failed = int(attrs.get("failures", 0)) + int(attrs.get("errors", 0))
        suite.total += tests
        suite.skipped += skipped
        suite.failed += failed
        suite.passed += tests - skipped - failed
    return suite


def run_jest() -> Suite:
    suite = Suite("Jest", BASELINE["jest"])
    with tempfile.TemporaryDirectory() as tmp:
        output = Path(tmp) / "jest.json"
        subprocess.run(["npm", "test", "--silent", "--", "--json", f"--outputFile={output}"], cwd=ROOT)
        if not output.exists():
            return suite
        result = json.loads(output.read_text())
    suite.ran = True
    suite.total = result["numTotalTests"]
    suite.passed = result["numPassedTests"]
    # A test file that fails to load contributes no tests, so count it as an error.
    suite.failed = result["numFailedTests"] + result["numRuntimeErrorTestSuites"]
    suite.skipped = result["numPendingTests"] + result["numTodoTests"]
    return suite


def readme_problems() -> list[str]:
    text = README.read_text()
    problems = []
    for key, pattern in README_COUNTS.items():
        match = pattern.search(text)
        if not match:
            problems.append(f"README: no test count found for {key}")
        elif int(match.group(1)) != BASELINE[key]:
            problems.append(f"README: says {match.group(1)} {key} tests, baseline is {BASELINE[key]}")
    return problems


def main() -> int:
    java, jest = run_java(), run_jest()
    java.judge()
    jest.judge()
    problems = java.problems + jest.problems + readme_problems()
    print("Release check")
    print(f"  Java:  {java.passed} passed, {java.failed} failed, {java.skipped} skipped, "
          f"{java.total} total   (baseline {java.baseline})  {java.status}")
    print(f"  Jest:  {jest.passed} passed, {jest.failed} failed, {jest.skipped} skipped, "
          f"{jest.total} total   (baseline {jest.baseline})  {jest.status}")
    print(f"  Verdict: {'NOT READY' if problems else 'READY'}")
    print("  Notes: " + ("none" if not problems else problems[0]))
    for problem in problems[1:]:
        print("         " + problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
