#!/usr/bin/env python3
"""AQK-17 blocking Trivy gate. Only sanitized counts are written to CI logs."""

from __future__ import annotations

import argparse
import json
import sys
import tempfile
from collections import Counter
from pathlib import Path

SEVERITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN")


def runtime_modules(lockfile: Path) -> int:
    """Count exactly the production modules covered by the Gradle lockfile."""
    total = 0
    for line in lockfile.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("empty="):
            continue
        if "=" not in line:
            raise ValueError("invalid Gradle lockfile line")
        module, configurations = line.rsplit("=", 1)
        if not module or not configurations:
            raise ValueError("invalid Gradle lockfile entry")
        if "runtimeClasspath" in configurations.split(","):
            total += 1
    if not total:
        raise ValueError("missing runtimeClasspath dependency inventory")
    return total


def audit(report: object, expected_packages: int) -> dict[str, int]:
    if not isinstance(report, dict):
        raise ValueError("Trivy JSON root must be an object")
    results = report.get("Results")
    if not isinstance(results, list):
        raise ValueError("Trivy Results list missing")
    gradle = [
        result for result in results
        if isinstance(result, dict)
        and isinstance(result.get("Target"), str)
        and result["Target"].replace("\\", "/").split("/")[-1] == "gradle.lockfile"
    ]
    if len(gradle) != 1:
        raise ValueError("expected exactly one Gradle lockfile result")
    packages = gradle[0].get("Packages")
    if not isinstance(packages, list) or not packages:
        raise ValueError("missing/empty Trivy Gradle package inventory")
    if not all(isinstance(package, dict) and package.get("Name") for package in packages):
        raise ValueError("invalid Trivy Gradle package entries")
    if len(packages) != expected_packages:
        raise ValueError("Trivy inventory does not match runtime Gradle lock count")

    vulnerabilities = gradle[0].get("Vulnerabilities", [])
    if vulnerabilities is None:
        vulnerabilities = []
    if not isinstance(vulnerabilities, list):
        raise ValueError("invalid Trivy vulnerability report")
    if not all(isinstance(v, dict) for v in vulnerabilities):
        raise ValueError("invalid Trivy vulnerability entries")
    counts = Counter()
    for vuln in vulnerabilities:
        raw_severity = vuln.get("Severity", "UNKNOWN")
        if not isinstance(raw_severity, str):
            raise ValueError("invalid Trivy vulnerability severity")
        severity = raw_severity.upper()
        if severity not in SEVERITIES:
            raise ValueError("unrecognized Trivy vulnerability severity")
        counts[severity] += 1
    return {"packages": len(packages), "total": len(vulnerabilities),
            **{name.lower(): counts[name] for name in SEVERITIES}}


def blocking(result: dict[str, int]) -> bool:
    return result["critical"] > 0 or result["high"] > 0


def self_test() -> None:
    fixture = {"Results": [{"Target": "gradle.lockfile",
                            "Packages": [{"Name": "example:lib", "Version": "1.0"}],
                            "Vulnerabilities": []}]}
    assert audit(fixture, 1)["packages"] == 1
    assert not blocking(audit(fixture, 1))
    for severity in ("HIGH", "CRITICAL"):
        fixture["Results"][0]["Vulnerabilities"] = [{"Severity": severity}]
        assert blocking(audit(fixture, 1))
    fixture["Results"][0]["Vulnerabilities"] = [{"Severity": "MEDIUM"}]
    assert not blocking(audit(fixture, 1))
    fixture["Results"][0]["Vulnerabilities"] = []
    for malformed, expected in (({}, 1), ({"Results": []}, 1), (fixture, 2)):
        try:
            audit(malformed, expected)
        except ValueError:
            pass
        else:
            raise AssertionError("Trivy gate accepted missing/mismatched inventory")
    with tempfile.TemporaryDirectory(prefix="aqk17-trivy-gate-") as directory:
        lockfile = Path(directory) / "gradle.lockfile"
        lockfile.write_text(
            "# synthetic fixture\n"
            "example:lib:1.0=runtimeClasspath,testRuntimeClasspath\n"
            "example:test-only:1.0=testRuntimeClasspath\n",
            encoding="utf-8",
        )
        assert runtime_modules(lockfile) == 1
        lockfile.write_text("example:test-only:1.0=testRuntimeClasspath\n", encoding="utf-8")
        try:
            runtime_modules(lockfile)
        except ValueError:
            pass
        else:
            raise AssertionError("gate accepted missing runtime dependencies")
    print("AQK_17_TRIVY_GATE_SELFTEST=PASS")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    if args.report is None:
        parser.error("Trivy JSON report required")
    try:
        expected = runtime_modules(Path("gradle.lockfile"))
        data = json.loads(args.report.read_text(encoding="utf-8"))
        result = audit(data, expected)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as exc:
        # No raw package, CVE, source, or secret material in CI logs.
        print(f"AQK_17_TRIVY_GATE=FAIL ERROR={type(exc).__name__}", file=sys.stderr)
        return 1

    print("===== AQK-17 TRIVY BLOCKING GATE =====")
    print("TRIVY_GRADLE_INVENTORY=PASS")
    print(f"TRIVY_GRADLE_RUNTIME_LOCKED={expected}")
    print(f"TRIVY_GRADLE_PACKAGES={result['packages']}")
    print(f"TRIVY_VULNERABILITIES_TOTAL={result['total']}")
    for name in SEVERITIES:
        print(f"TRIVY_VULNERABILITIES_{name}={result[name.lower()]}")
    print("TRIVY_POLICY=BLOCK_HIGH_CRITICAL")
    failed = blocking(result)
    print("AQK_17_TRIVY_GATE=" + ("FAIL" if failed else "PASS"))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
