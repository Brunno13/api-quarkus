#!/usr/bin/env bash
# AQK-5: reproducible Gradle checks, one result per Woodpecker workflow.
# Usage: bash scripts/quality_quarkus.sh detekt|tests|coverage|all
set -Eeuo pipefail

MODE="${1:-all}"
case "$MODE" in
  detekt|tests|coverage|all) ;;
  *) echo 'AQK_5_GATE=FAIL (unknown mode)' >&2; exit 2 ;;
esac

finish() {
  local rc=$?
  local result=PASS
  if (( rc != 0 )); then result=FAIL; fi
  echo "AQK_5_${MODE^^}_SUMMARY=${result} EXIT_CODE=${rc}"
}
trap finish EXIT

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -f gradle/wrapper/gradle-wrapper.jar || ! -f gradlew ]]; then
  echo 'GRADLE_WRAPPER=FAIL (files missing)' >&2
  exit 1
fi
if [[ ! -s config/detekt/baseline.xml ]]; then
  echo 'DETEKT_BASELINE=FAIL (versioned file missing)' >&2
  exit 1
fi

java -version
gradle_args=(--no-daemon --console=plain --max-workers=2)

test_summary() {
  local files=0 total=0 failures=0 errors=0 xml header
  local -a xmls=()
  shopt -s nullglob
  xmls=(build/test-results/test/TEST-*.xml)
  shopt -u nullglob
  files=${#xmls[@]}
  if (( files == 0 )); then
    echo 'TEST_REPORT=FAIL (no JUnit XML files)' >&2
    return 1
  fi
  for xml in "${xmls[@]}"; do
    header="$(head -c 2048 "$xml")"
    if [[ $header =~ tests=\"([0-9]+)\" ]]; then
      (( total += BASH_REMATCH[1] )) || true
    else
      echo "TEST_REPORT=FAIL (tests count missing: $xml)" >&2
      return 1
    fi
    if [[ $header =~ failures=\"([0-9]+)\" ]]; then
      (( failures += BASH_REMATCH[1] )) || true
    fi
    if [[ $header =~ errors=\"([0-9]+)\" ]]; then
      (( errors += BASH_REMATCH[1] )) || true
    fi
  done
  echo "JUNIT_XML_FILES=$files"
  echo "JUNIT_TESTS=$total FAILURES=$failures ERRORS=$errors"
  if (( total == 0 || failures > 0 || errors > 0 )); then
    echo 'TEST_REPORT=FAIL' >&2
    return 1
  fi
  echo 'TEST_REPORT=PASS'
}

coverage_summary() {
  local report=build/jacoco-report/jacoco.xml
  if [[ ! -s $report ]]; then
    echo 'JACOCO_XML=FAIL (missing report)' >&2
    return 1
  fi
  local kind counter missed covered total basis
  for kind in LINE BRANCH; do
    counter="$(grep -oE "<counter type=\"$kind\" missed=\"[0-9]+\" covered=\"[0-9]+\"/>" "$report" | tail -n 1 || true)"
    if [[ -z $counter ]]; then
      echo "JACOCO_${kind}=FAIL (root counter missing)" >&2
      return 1
    fi
    if [[ $counter =~ missed=\"([0-9]+)\" ]]; then
      missed=${BASH_REMATCH[1]}
    else
      return 1
    fi
    if [[ $counter =~ covered=\"([0-9]+)\" ]]; then
      covered=${BASH_REMATCH[1]}
    else
      return 1
    fi
    total=$((missed + covered))
    if ((total == 0)); then
      echo "JACOCO_${kind}=FAIL (zero denominator)" >&2
      return 1
    fi
    basis=$((10000 * covered / total))
    printf 'JACOCO_%s=%d.%02d%% (%d/%d)\n' "$kind" "$((basis / 100))" "$((basis % 100))" "$covered" "$total"
  done
  echo 'JACOCO_THRESHOLDS=PASS (Gradle verified LINE>=75%, BRANCH>=68%)'
}

case "$MODE" in
  detekt)
    echo '===== AQK-5 DETEKT ====='
    bash ./gradlew detekt "${gradle_args[@]}"
    echo 'DETEKT_BASELINE=PASS'
    ;;
  tests)
    echo '===== AQK-5 TESTS ====='
    bash ./gradlew clean test "${gradle_args[@]}"
    test_summary
    ;;
  coverage)
    # Woodpecker workflows do not share test artifacts: running the test
    # task here is intentional, so coverage is independently verifiable.
    echo '===== AQK-5 JACOCO COVERAGE ====='
    bash ./gradlew clean jacocoTestCoverageVerification "${gradle_args[@]}"
    test_summary
    coverage_summary
    ;;
  all)
    echo '===== AQK-5 FULL LOCAL CHECK ====='
    bash ./gradlew clean check "${gradle_args[@]}"
    test_summary
    coverage_summary
    echo 'AQK_5_CI_QUALITY_GATE=PASS'
    ;;
esac
