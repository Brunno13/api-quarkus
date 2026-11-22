#!/usr/bin/env python3
"""AQK-15 discovery: count findings without displaying sensitive values."""

from __future__ import annotations

import json
from pathlib import Path
import shutil
import sys

REPORT_DIR = Path('.aqk15-gitleaks-ci')


def count_report(name: str) -> int:
    path = REPORT_DIR / f'{name}.json'
    if not path.is_file():
        raise RuntimeError(f'{name} report missing')
    value = json.loads(path.read_text(encoding='utf-8'))
    if value is None:
        return 0
    if not isinstance(value, list):
        raise RuntimeError(f'{name} report is not a JSON array')
    if any(not isinstance(item, dict) for item in value):
        raise RuntimeError(f'{name} report has an unexpected schema')
    return len(value)


def main() -> int:
    try:
        current = count_report('current')
        history = count_report('history')
        print('===== AQK-15 GITLEAKS DISCOVERY =====')
        print('GITLEAKS_VERSION=8.30.1')
        print(f'CURRENT_FINDINGS={current}')
        print(f'HISTORY_FINDINGS={history}')
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
