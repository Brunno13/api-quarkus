#!/usr/bin/env python3
"""AQK-15 discovery: summarize Gitleaks findings without leaking secret values."""

from __future__ import annotations

import json
from pathlib import Path
import shutil
import sys

REPORT_DIR = Path('.aqk15-gitleaks-ci')


def read_report(name: str) -> list[dict]:
    path = REPORT_DIR / f'{name}.json'
    if not path.is_file():
        raise RuntimeError(f'{name} report missing')
    value = json.loads(path.read_text(encoding='utf-8'))
    if value is None:
        return []
    if not isinstance(value, list):
        raise RuntimeError(f'{name} report is not a JSON array')
    if any(not isinstance(item, dict) for item in value):
        raise RuntimeError(f'{name} report has an unexpected schema')
    return value


def print_safe_metadata(name: str, findings: list[dict]) -> None:
    """Allowlist fields only. NEVER print Match, Secret, Message, or source."""
    for i, finding in enumerate(findings[:20], start=1):
        rule = finding.get('RuleID')
        path = finding.get('File')
        commit = finding.get('Commit', '')
        line = finding.get('StartLine')
        if not isinstance(rule, str) or not rule:
            raise RuntimeError('invalid rule id in report')
        if not isinstance(path, str) or not path:
            raise RuntimeError('invalid file path in report')
        if not isinstance(commit, str) or not (commit == '' or
                                                all(c in '0123456789abcdefABCDEF' for c in commit)):
            raise RuntimeError('invalid commit hash in report')
        if type(line) is not int or line < 0:
            raise RuntimeError('invalid line number in report')
        metadata = {
            'scan': name,
            'index': i,
            'rule': rule[:120],
            'file': path[:240],
            'line': line,
            'commit': commit[:12] if commit else '-',
        }
        print('GITLEAKS_FINDING_METADATA=' + json.dumps(metadata, ensure_ascii=True))
    if len(findings) > 20:
        print(f'GITLEAKS_{name.upper()}_METADATA_OMITTED={len(findings) - 20}')


def main() -> int:
    try:
        current = read_report('current')
        history = read_report('history')
        # Validate/prepare everything before reporting PASS; no raw report dumps.
        print('===== AQK-15 GITLEAKS DISCOVERY =====')
        print('GITLEAKS_VERSION=8.30.1')
        print(f'CURRENT_FINDINGS={len(current)}')
        print(f'HISTORY_FINDINGS={len(history)}')
        print_safe_metadata('current', current)
        print_safe_metadata('history', history)
        if current or history:
            print('FINDINGS_TRIAGE=REQUIRED')
        else:
            print('FINDINGS_TRIAGE=NOT_REQUIRED')
        print('GITLEAKS_POLICY=DISCOVERY_ONLY')
        print('AQK_15_DISCOVERY=PASS')
        return 0
    except (OSError, ValueError, RuntimeError) as error:
        print(f'AQK_15_DISCOVERY=FAIL ({type(error).__name__})', file=sys.stderr)
        return 1
    finally:
        shutil.rmtree(REPORT_DIR, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
