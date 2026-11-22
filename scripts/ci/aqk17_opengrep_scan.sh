#!/bin/sh
# AQK-17: blocking scan with a synthetic positive control and safe summaries.
# Fail on findings, scanner errors, invalid rules, incomplete Kotlin coverage or missing probe.
set -eu

if [ ! -s .opengrep.yml ] || [ ! -s .aqk17-opengrepignore ] || [ ! -x .aqk17-bin/opengrep ]; then
  echo 'AQK_17_OPENGREP_GATE=FAIL (rules, ignore policy or verified binary missing)' >&2
  exit 1
fi

if [ ! -d src/main/kotlin ] || [ ! -d src/test/kotlin ]; then
  echo 'AQK_17_OPENGREP_GATE=FAIL (Kotlin targets missing)' >&2
  exit 1
fi

python3 scripts/ci/aqk17_opengrep_gate.py --self-test
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
python3 scripts/ci/aqk17_opengrep_gate.py --probe "$report_dir/probe.json"

# Preserve scanner exit codes. The Python gate separately blocks findings even
# without --error, so safe counts are available without printing source snippets.
# Original stderr/JSON remain private and ephemeral.
scan_rc=0
./.aqk17-bin/opengrep scan --config .opengrep.yml --json \
  --semgrepignore-filename .aqk17-opengrepignore \
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
  # A SemgrepError is too generic to identify a cause. Narrow the failure
  # without printing source snippets, raw error messages or private paths.
  python3 - "$report_dir/source.json" <<'PY_HINTS'
import json
import re
import sys
from collections import Counter
from pathlib import Path

for label, directory in (("MAIN", "src/main/kotlin"), ("TEST", "src/test/kotlin")):
    print(f"OPENGREP_DIAG_{label}_KOTLIN_CANDIDATES={sum(1 for _ in Path(directory).rglob('*.kt'))}")

try:
    report = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    errors = report.get("errors", [])
    if not isinstance(errors, list):
        errors = []
except (OSError, ValueError, UnicodeError):
    errors = []

codes = Counter(str(e["code"]) for e in errors if isinstance(e, dict) and isinstance(e.get("code"), int))
for code, count in sorted(codes.items()):
    print(f"OPENGREP_DIAG_ERROR_CODE_{code}={count}")

# Match only fixed diagnostic categories, never publish the underlying messages.
hints = {
    "CONFIG": r"config|yaml|rule.*invalid|invalid.*rule",
    "TARGETS": r"no files|no targets|not found|does not exist|path|directory|outside.*project|project root",
    "GIT": r"git|repository|vcs",
    "PARSING": r"parse|syntax|grammar|lexer",
    "ENGINE": r"semgrep-core|engine|failed to run|internal|exception|assertion|fatal",
    "RESOURCE": r"memory|timeout|too many|limit|killed|resource|oom",
    "NETWORK": r"http|https|network|connection|dns|download",
    "PERMISSION": r"permission|access denied",
}
messages = "\n".join(e.get("message", "") for e in errors if isinstance(e, dict) and isinstance(e.get("message"), str)).lower()
for name, pattern in hints.items():
    if re.search(pattern, messages):
        print(f"OPENGREP_DIAG_ERROR_HINT_{name}=DETECTED")
PY_HINTS

  # Run controlled, read-only isolation scans only after the original scan
  # failed; never treat an isolation PASS as a replacement for the full gate.
  diagnose_target() {
    label="$1"
    shift
    isolated_rc=0
    ./.aqk17-bin/opengrep scan --config .opengrep.yml --json \
      --output "$report_dir/isolation-$label.json" "$@" \
      > "$report_dir/isolation-$label.out" 2> "$report_dir/isolation-$label.err" || isolated_rc=$?
    echo "OPENGREP_DIAG_${label}_EXIT_CODE=$isolated_rc"
    python3 - "$report_dir/isolation-$label.json" "$label" <<'PY_ISO'
import json
import sys
from pathlib import Path

path, label = Path(sys.argv[1]), sys.argv[2]
try:
    report = json.loads(path.read_text(encoding="utf-8"))
    scanned = report.get("paths", {}).get("scanned", [])
    errors = report.get("errors", [])
    print(f"OPENGREP_DIAG_{label}_SCANNED_COUNT={len(scanned) if isinstance(scanned, list) else 'INVALID'}")
    print(f"OPENGREP_DIAG_{label}_ERRORS_COUNT={len(errors) if isinstance(errors, list) else 'INVALID'}")
except (OSError, ValueError, UnicodeError, AttributeError):
    print(f"OPENGREP_DIAG_{label}_JSON=INVALID_OR_MISSING")
PY_ISO
  }
  diagnose_target SRC src
  diagnose_target MAIN src/main/kotlin
  diagnose_target TEST src/test/kotlin

  echo 'AQK_17_OPENGREP_SCAN_EXECUTION=FAIL' >&2
  exit "$scan_rc"
fi

echo 'AQK_17_OPENGREP_SCAN_EXECUTION=PASS'
python3 scripts/ci/aqk17_opengrep_gate.py "$report_dir/source.json"
