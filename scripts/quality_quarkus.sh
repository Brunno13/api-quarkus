#!/usr/bin/env bash
# AQK-5: reuse the exact Gradle check already tested on Windows.
# Gradle `check` includes Detekt baseline, tests and JaCoCo thresholds.
set -euo pipefail

cd "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ ! -f gradle/wrapper/gradle-wrapper.jar || ! -f gradlew ]]; then
  echo 'AQK_5_CI_QUALITY_GATE=FAIL (Gradle wrapper not available)'
  exit 1
fi
if [[ ! -s config/detekt/baseline.xml ]]; then
  echo 'AQK_5_CI_QUALITY_GATE=FAIL (versioned Detekt baseline missing)'
  exit 1
fi

java -version
bash ./gradlew clean check --no-daemon --console=plain

if [[ ! -s build/jacoco-report/jacoco.xml ]]; then
  echo 'AQK_5_CI_QUALITY_GATE=FAIL (JaCoCo XML report missing)'
  exit 1
fi

echo 'AQK_5_CI_QUALITY_GATE=PASS'
