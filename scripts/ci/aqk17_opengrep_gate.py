#!/usr/bin/env python3
"""AQK-17: blocking Kotlin Opengrep SAST gate; sanitized diagnostics only.

Reject any finding in the four explicitly maintained SAST rules. Fail closed on
invalid reports, scanner errors/skipped rules, incomplete tracked-Kotlin coverage,
and a missing positive-control finding. Never print source snippets or secrets.
"""
import json
import subprocess
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



def normalize_source_path(path: str) -> str:
    """Compare report paths using the same repo-relative spelling as Git."""
    normalized = path.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def verify_coverage(report: object, tracked: set[str]) -> tuple[int, int]:
    """Fail closed when the scanner silently skips any tracked Kotlin file."""
    if not isinstance(report, dict):
        raise ValueError("invalid report")
    paths = report.get("paths")
    if not isinstance(paths, dict) or not isinstance(paths.get("scanned"), list):
        raise ValueError("missing scanned paths")
    scanned = paths["scanned"]
    if not all(isinstance(p, str) for p in scanned):
        raise ValueError("invalid scanned path")
    normalized = [normalize_source_path(p) for p in scanned]
    actual = set(normalized)
    if len(actual) != len(normalized):
        raise ValueError("duplicate scanned targets")
    tracked_main = {p for p in tracked if p.startswith("src/main/kotlin/") and p.endswith(".kt")}
    tracked_test = {p for p in tracked if p.startswith("src/test/kotlin/") and p.endswith(".kt")}
    if not tracked_main or not tracked_test:
        raise ValueError("missing tracked main or test Kotlin sources")
    if tracked_main | tracked_test != actual:
        raise ValueError("incomplete or unexpected Kotlin scan coverage")
    return len(tracked_main), len(tracked_test)


def tracked_kotlin_sources() -> set[str]:
    """Get the authoritative tracked-file inventory without exposing paths."""
    output = subprocess.check_output(
        ["git", "ls-files", "-z", "--", "src/main/kotlin", "src/test/kotlin"],
        stderr=subprocess.DEVNULL,
    )
    return {
        normalize_source_path(name)
        for name in output.decode("utf-8", errors="surrogateescape").split("\0")
        if name.endswith(".kt")
    }

def enforce_findings(findings: int) -> bool:
    """All configured AQK-17 SAST findings are blocking (no baseline)."""
    if not isinstance(findings, int) or isinstance(findings, bool) or findings < 0:
        raise ValueError("invalid findings count")
    return findings == 0


def self_test() -> None:
    ok = {"results": [], "errors": [], "paths": {"scanned": ["src/main/kotlin/Example.kt"]}, "skipped_rules": []}
    assert summarize(ok)[:2] == (1, 0)
    with_finding = {**ok, "results": [{"check_id": PROBE_RULE}]}
    assert summarize(with_finding, probe=True)[1] == 1
    assert enforce_findings(0)
    assert not enforce_findings(1)
    assert not enforce_findings(3)
    for bad_count in (-1, None, "0", True):
        try:
            enforce_findings(bad_count)
        except ValueError:
            pass
        else:
            raise AssertionError("invalid findings count was accepted")
    tracked = {"src/main/kotlin/A.kt", "src/test/kotlin/B.kt"}
    both = {**ok, "paths": {"scanned": ["./src/main/kotlin/A.kt", "src/test/kotlin/B.kt"]}}
    assert verify_coverage(both, tracked) == (1, 1)
    incomplete = {**ok, "paths": {"scanned": ["src/main/kotlin/A.kt"]}}
    duplicate = {**ok, "paths": {"scanned": ["src/main/kotlin/A.kt", "src/main/kotlin/A.kt", "src/test/kotlin/B.kt"]}}
    substitute = {**ok, "paths": {"scanned": ["src/main/kotlin/A.kt", "src/test/kotlin/C.kt"]}}
    for report in (incomplete, duplicate, substitute):
        try:
            verify_coverage(report, tracked)
        except ValueError:
            continue
        raise AssertionError("incomplete or substituted scan unexpectedly passed")
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
    print("AQK_17_OPENGREP_GATE_SELFTEST=PASS")


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
        print("AQK_17_OPENGREP_GATE=FAIL (invalid arguments)", file=sys.stderr)
        return 2
    try:
        report = json.loads(Path(path).read_text(encoding="utf-8"))
        scanned, findings, rule_counts = summarize(report, probe=probe)
        if not probe:
            main_count, test_count = verify_coverage(report, tracked_kotlin_sources())
    except (OSError, UnicodeError, ValueError, subprocess.CalledProcessError) as exc:
        print(f"AQK_17_OPENGREP_GATE=FAIL ({type(exc).__name__})", file=sys.stderr)
        return 1
    if probe:
        print("AQK_17_OPENGREP_PROBE=PASS")
        return 0
    print("===== AQK-17 OPENGREP KOTLIN BLOCKING GATE =====")
    print(f"OPENGREP_TRACKED_KOTLIN_FILES={main_count + test_count}")
    print(f"OPENGREP_SCANNED_MAIN_KOTLIN_FILES={main_count}")
    print(f"OPENGREP_SCANNED_TEST_KOTLIN_FILES={test_count}")
    print(f"OPENGREP_SCANNED_KOTLIN_FILES={scanned}")
    print("OPENGREP_KOTLIN_COVERAGE=PASS")
    print(f"OPENGREP_FINDINGS_TOTAL={findings}")
    for rule_id in sorted(rule_counts):
        print(f"OPENGREP_RULE_{rule_id.upper().replace('-', '_')}={rule_counts[rule_id]}")
    print("OPENGREP_POLICY=BLOCK_ANY_FINDING")
    allowed = enforce_findings(findings)
    print("AQK_17_OPENGREP_GATE=" + ("PASS" if allowed else "FAIL"))
    return 0 if allowed else 1


if __name__ == "__main__":
    sys.exit(main())
