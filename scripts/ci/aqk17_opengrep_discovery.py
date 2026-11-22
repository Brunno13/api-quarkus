#!/usr/bin/env python3
"""AQK-17: validate Opengrep v2 JSON; safe counts only, no source excerpts.

Initial discovery does not reject findings. It rejects errors, skipped rules,
missing Kotlin targets, invalid JSON, and a failed synthetic positive control.
"""
import json
import sys
from collections import Counter
from pathlib import Path

PROBE_RULE = "aqk17-kotlin-weak-message-digest"


def summarize(report: object, *, probe: bool = False) -> tuple[int, int, Counter[str]]:
    if not isinstance(report, dict):
        raise ValueError("report must be an object")
    results = report.get("results")
    errors = report.get("errors")
    paths = report.get("paths")
    skipped_rules = report.get("skipped_rules", [])
    if not isinstance(results, list) or not isinstance(errors, list):
        raise ValueError("results/errors missing or malformed")
    if not isinstance(paths, dict) or not isinstance(paths.get("scanned"), list):
        raise ValueError("scanned paths missing or malformed")
    if not isinstance(skipped_rules, list):
        raise ValueError("skipped_rules malformed")
    if errors or skipped_rules:
        raise ValueError("scanner reported errors or skipped rules")
    scanned = paths["scanned"]
    if not scanned or not all(isinstance(p, str) for p in scanned):
        raise ValueError("empty/invalid scanned paths")
    if not any(p.lower().endswith(".kt") for p in scanned):
        raise ValueError("no Kotlin sources scanned")
    rule_counts: Counter[str] = Counter()
    for item in results:
        if not isinstance(item, dict) or not isinstance(item.get("check_id"), str):
            raise ValueError("malformed finding")
        rule_counts[item["check_id"].rsplit(".", 1)[-1]] += 1
    if probe and rule_counts[PROBE_RULE] < 1:
        raise ValueError("Kotlin positive control did not match")
    return len(scanned), len(results), rule_counts


def self_test() -> None:
    ok = {"results": [], "errors": [], "paths": {"scanned": ["src/main/kotlin/Example.kt"]}, "skipped_rules": []}
    assert summarize(ok)[:2] == (1, 0)
    with_finding = {**ok, "results": [{"check_id": PROBE_RULE}]}
    assert summarize(with_finding, probe=True)[1] == 1
    bad = [
        {**ok, "paths": {"scanned": []}},
        {**ok, "paths": {"scanned": ["README.md"]}},
        {**ok, "errors": [{"message": "failed"}]},
        {**ok, "skipped_rules": ["invalid"]},
        {**ok, "results": [{}]},
        {**ok, "results": []},  # this case is invalid only for probe
    ]
    for index, report in enumerate(bad):
        try:
            summarize(report, probe=index == len(bad) - 1)
        except ValueError:
            continue
        raise AssertionError(f"negative selftest {index} unexpectedly passed")
    print("AQK_17_OPENGREP_DISCOVERY_SELFTEST=PASS")


def main() -> int:
    if len(sys.argv) == 2 and sys.argv[1] == "--self-test":
        self_test()
        return 0
    probe = len(sys.argv) == 3 and sys.argv[1] == "--probe"
    if probe:
        path = sys.argv[2]
    elif len(sys.argv) == 2:
        path = sys.argv[1]
    else:
        print("AQK_17_OPENGREP_DISCOVERY=FAIL (invalid arguments)", file=sys.stderr)
        return 2
    try:
        scanned, findings, rule_counts = summarize(json.loads(Path(path).read_text(encoding="utf-8")), probe=probe)
    except (OSError, UnicodeError, ValueError) as exc:
        print(f"AQK_17_OPENGREP_DISCOVERY=FAIL ({type(exc).__name__})", file=sys.stderr)
        return 1
    if probe:
        print("AQK_17_OPENGREP_PROBE=PASS")
        return 0
    print("===== AQK-17 OPENGREP KOTLIN DISCOVERY =====")
    print(f"OPENGREP_SCANNED_KOTLIN_FILES={scanned}")
    print(f"OPENGREP_FINDINGS_TOTAL={findings}")
    for rule_id in sorted(rule_counts):
        print(f"OPENGREP_RULE_{rule_id.upper().replace('-', '_')}={rule_counts[rule_id]}")
    print("OPENGREP_POLICY=DISCOVERY_ONLY")
    print("AQK_17_OPENGREP_DISCOVERY=PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
