#!/bin/sh
# AQK-17: non-blocking vulnerability discovery, but fail-closed on scan errors
# and missing/empty Gradle package inventories. Never publish raw reports.
set -eu

if [ ! -s gradle.lockfile ]; then
  echo 'AQK_17_TRIVY_DISCOVERY=FAIL (gradle.lockfile missing)' >&2
  exit 1
fi

if [ ! -x .aqk17-bin/trivy ]; then
  echo 'AQK_17_TRIVY_DISCOVERY=FAIL (verified Trivy binary missing)' >&2
  exit 1
fi

python3 scripts/ci/aqk17_trivy_discovery.py --self-test

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
python3 scripts/ci/aqk17_trivy_discovery.py "$tmp_dir/vulnerability-report.json"
