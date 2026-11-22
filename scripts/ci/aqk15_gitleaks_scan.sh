#!/bin/sh
# AQK-15: discovery scans. Report data is private to this ephemeral workspace.
set -eu

if [ "$(git rev-parse --is-shallow-repository)" != "false" ]; then
  echo 'GITLEAKS_FULL_HISTORY=FAIL (shallow repository)' >&2
  exit 1
fi
if [ ! -d .git ]; then
  echo 'GITLEAKS_GIT_REPOSITORY=FAIL' >&2
  exit 1
fi

echo "GITLEAKS_FULL_HISTORY=PASS COMMITS=$(git rev-list --count HEAD)"
mkdir -p .aqk15-gitleaks-ci
# Never print findings to CI stdout/stderr. A report can contain sensitive
# metadata; do not upload or commit it.
gitleaks dir --no-banner --redact=100 --log-level error \
  --exit-code=0 --report-format=json \
  --report-path=.aqk15-gitleaks-ci/current.json . \
  > .aqk15-gitleaks-ci/current.log 2>&1 || {
    echo 'GITLEAKS_CURRENT_SCAN_EXECUTION=FAIL' >&2
    exit 1
  }
echo 'GITLEAKS_CURRENT_SCAN_EXECUTION=PASS'

gitleaks git --no-banner --redact=100 --log-level error \
  --exit-code=0 --log-opts=HEAD --report-format=json \
  --report-path=.aqk15-gitleaks-ci/history.json . \
  > .aqk15-gitleaks-ci/history.log 2>&1 || {
    echo 'GITLEAKS_HISTORY_SCAN_EXECUTION=FAIL' >&2
    exit 1
  }
echo 'GITLEAKS_HISTORY_SCAN_EXECUTION=PASS'
