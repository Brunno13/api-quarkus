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

# Preserve the actual exit code: findings alone should not cause scan failure
# without --error. The original scanner stderr/JSON remain private and ephemeral.
scan_rc=0
./.aqk17-bin/opengrep scan --config .opengrep.yml --json \
  --output "$report_dir/source.json" src/main/kotlin src/test/kotlin \
  > "$report_dir/source.out" 2> "$report_dir/source.err" || scan_rc=$?
echo "OPENGREP_SOURCE_EXIT_CODE=$scan_rc"
if [ "$scan_rc" -ne 0 ]; then
  # Emit only classified diagnostics, never code excerpts or report bodies.
  python3 - "$report_dir/source.json" "$report_dir/source.err" <<'PY_DIAG'
import json
import re
import sys
from collections import Counter
from pathlib import Path

report_path, stderr_path = (Path(arg) for arg in sys.argv[1:])
print(f"OPENGREP_SOURCE_JSON_PRESENT={int(report_path.is_file() and report_path.stat().st_size > 0)}")
if report_path.is_file() and report_path.stat().st_size:
    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
        if not isinstance(report, dict):
            raise ValueError("invalid report root")
        for key in ("results", "errors", "skipped_rules"):
            value = report.get(key)
            print(f"OPENGREP_JSON_{key.upper()}_COUNT={len(value) if isinstance(value, list) else 'UNAVAILABLE'}")
        scanned = report.get("paths", {}).get("scanned", []) if isinstance(report.get("paths"), dict) else []
        print(f"OPENGREP_JSON_SCANNED_COUNT={len(scanned) if isinstance(scanned, list) else 'UNAVAILABLE'}")
        counts = Counter()
        for item in report.get("errors", []) if isinstance(report.get("errors"), list) else []:
            if isinstance(item, dict):
                kind = item.get("type")
                if isinstance(kind, str):
                    counts[re.sub(r"[^A-Za-z0-9_-]", "_", kind)[:40]] += 1
                else:
                    counts["UNKNOWN"] += 1
        for kind, count in sorted(counts.items()):
            print(f"OPENGREP_JSON_ERROR_TYPE_{kind}={count}")
    except (OSError, UnicodeError, ValueError):
        print("OPENGREP_SOURCE_JSON_FORMAT=INVALID")
else:
    print("OPENGREP_SOURCE_JSON_FORMAT=MISSING")
try:
    diagnostic_text = stderr_path.read_text(encoding="utf-8", errors="replace")[:131072].lower()
except OSError:
    diagnostic_text = ""
patterns = {
    "PARSING": r"parse error|parsing error|syntax error|cannot parse",
    "MEMORY": r"out of memory|oom|memoryerror|killed|sigkill",
    "TIMEOUT": r"timed out|timeout",
    "PERMISSION": r"permission denied|access denied",
    "INTERNAL": r"internal error|fatal error|exception|traceback|panic",
}
for kind, pattern in patterns.items():
    if re.search(pattern, diagnostic_text):
        print(f"OPENGREP_STDERR_CATEGORY_{kind}=DETECTED")
PY_DIAG
  echo 'AQK_17_OPENGREP_SCAN_EXECUTION=FAIL' >&2
  exit "$scan_rc"
fi

echo 'AQK_17_OPENGREP_SCAN_EXECUTION=PASS'
python3 scripts/ci/aqk17_opengrep_discovery.py "$report_dir/source.json"
