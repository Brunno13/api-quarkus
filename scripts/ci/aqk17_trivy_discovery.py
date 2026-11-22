#!/usr/bin/env python3
"""AQK-17 Trivy Gradle inventory audit; never log raw report or code content."""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path


def audit(report: object) -> dict[str, int]:
    if not isinstance(report, dict):
        raise ValueError("JSON report root is not an object")
    results = report.get("Results")
    if not isinstance(results, list):
        raise ValueError("Results list missing")

    gradle = [
        result
        for result in results
        if isinstance(result, dict)
        and isinstance(result.get("Target"), str)
        and result["Target"].replace("\\", "/").endswith("gradle.lockfile")
    ]
    if len(gradle) != 1:
        raise ValueError(f"expected exactly one Gradle lockfile result, got {len(gradle)}")

    packages = gradle[0].get("Packages")
    if not isinstance(packages, list) or not packages:
        raise ValueError("Gradle lockfile was not inventoried")
    if not all(isinstance(package, dict) and package.get("Name") for package in packages):
        raise ValueError("invalid Gradle package entries")

    vulnerabilities = gradle[0].get("Vulnerabilities") or []
    if not isinstance(vulnerabilities, list):
        raise ValueError("invalid Vulnerabilities list")
    counts = Counter(
        str(item.get("Severity", "UNKNOWN")).upper()
        for item in vulnerabilities
        if isinstance(item, dict)
    )
    return {
        "packages": len(packages),
        "critical": counts["CRITICAL"],
        "high": counts["HIGH"],
        "medium": counts["MEDIUM"],
        "low": counts["LOW"],
        "unknown": counts["UNKNOWN"],
        "total": len(vulnerabilities),
    }


def self_test() -> None:
    fixture = {
        "Results": [
            {
                "Target": "gradle.lockfile",
                "Packages": [{"Name": "example:library", "Version": "1.0"}],
                "Vulnerabilities": [{"Severity": "HIGH"}],
            }
        ]
    }
    assert audit(fixture)["packages"] == 1
    assert audit(fixture)["high"] == 1
    for invalid in ({"Results": []}, {"Results": [{"Target": "gradle.lockfile", "Packages": []}]}, {}):
        try:
            audit(invalid)
        except ValueError:
            pass
        else:
            raise AssertionError("audit accepted invalid or empty dependency inventory")
    print("AQK_17_TRIVY_DISCOVERY_SELFTEST=PASS")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("report", nargs="?", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    if args.report is None:
        parser.error("report JSON path required")
    try:
        data = json.loads(args.report.read_text(encoding="utf-8"))
        result = audit(data)
    except (OSError, UnicodeError, ValueError, json.JSONDecodeError) as error:
        # Never print report contents or vulnerabilities here.
        print(f"TRIVY_INVENTORY=FAIL ({type(error).__name__})")
        return 1
    print("===== AQK-17 TRIVY GRADLE DISCOVERY =====")
    print("TRIVY_GRADLE_INVENTORY=PASS")
    print(f"TRIVY_GRADLE_PACKAGES={result['packages']}")
    print(f"TRIVY_VULNERABILITIES_TOTAL={result['total']}")
    for severity in ("critical", "high", "medium", "low", "unknown"):
        print(f"TRIVY_VULNERABILITIES_{severity.upper()}={result[severity]}")
    print("TRIVY_POLICY=DISCOVERY_ONLY")
    print("AQK_17_TRIVY_DISCOVERY=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
