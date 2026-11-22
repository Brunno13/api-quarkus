#!/bin/sh
# AQK-17: blocking HIGH/CRITICAL Gradle runtime vulnerabilities. Fail closed on
# scan errors, malformed/missing inventories and count mismatch. No raw reports.
set -eu

if [ ! -s gradle.lockfile ]; then
  echo 'AQK_17_TRIVY_GATE=FAIL (gradle.lockfile missing)' >&2
  exit 1
fi

if [ ! -x .aqk17-bin/trivy ]; then
  echo 'AQK_17_TRIVY_GATE=FAIL (verified Trivy binary missing)' >&2
  exit 1
fi

python3 scripts/ci/aqk17_trivy_gate.py --self-test

umask 077
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

./.aqk17-bin/trivy fs \
  --scanners vuln \
  --list-all-pkgs \
  --format json \
  --output "$tmp_dir/vulnerability-report.json" \
  --skip-dirs build \
  --skip-dirs .git \
  --skip-dirs .aqk17-bin \
  --exit-code 0 \
  .

echo 'AQK_17_TRIVY_SCAN_EXECUTION=PASS'
python3 scripts/ci/aqk17_trivy_gate.py "$tmp_dir/vulnerability-report.json"
