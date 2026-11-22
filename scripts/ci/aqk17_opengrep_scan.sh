#!/bin/sh
# AQK-17: discovery scan with a synthetic positive control and safe summaries.
# Fail on scanner errors, invalid rules, missing targets, or missing probe match.
set -eu

if [ ! -s .opengrep.yml ] || [ ! -x .aqk17-bin/opengrep ]; then
  echo 'AQK_17_OPENGREP_DISCOVERY=FAIL (rules or verified binary missing)' >&2
  exit 1
fi

if [ ! -d src/main/kotlin ] || [ ! -d src/test/kotlin ]; then
  echo 'AQK_17_OPENGREP_DISCOVERY=FAIL (Kotlin targets missing)' >&2
  exit 1
fi

python3 scripts/ci/aqk17_opengrep_discovery.py --self-test
umask 077
report_dir="$(mktemp -d)"
trap 'rm -rf "$report_dir"' EXIT HUP INT TERM

# Positive control must trigger a real Kotlin AST rule before we trust a zero-findings scan.
cat > "$report_dir/Aqk17Probe.kt" <<'EOF'
import java.security.MessageDigest
fun aqk17Probe() {
    MessageDigest.getInstance("MD5")
}
EOF

if ! ./.aqk17-bin/opengrep scan --config .opengrep.yml --json \
  --output "$report_dir/probe.json" "$report_dir/Aqk17Probe.kt" \
  > "$report_dir/probe.out" 2> "$report_dir/probe.err"; then
  echo 'AQK_17_OPENGREP_PROBE=FAIL (scanner execution)' >&2
  exit 1
fi
python3 scripts/ci/aqk17_opengrep_discovery.py --probe "$report_dir/probe.json"

if ! ./.aqk17-bin/opengrep scan --config .opengrep.yml --json \
  --output "$report_dir/source.json" src/main/kotlin src/test/kotlin \
  > "$report_dir/source.out" 2> "$report_dir/source.err"; then
  echo 'AQK_17_OPENGREP_SCAN_EXECUTION=FAIL' >&2
  exit 1
fi

echo 'AQK_17_OPENGREP_SCAN_EXECUTION=PASS'
python3 scripts/ci/aqk17_opengrep_discovery.py "$report_dir/source.json"
