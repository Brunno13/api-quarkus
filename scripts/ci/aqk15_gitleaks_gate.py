#!/usr/bin/env python3
"""AQK-15: fail-closed Gitleaks policy for current files and Git history.

Never print Gitleaks JSON reports: they can contain secrets. The sole exception
is an intentionally malformed PEM fixture whose two fingerprints are checked
against an exact, reviewed test-source location. No global rule exclusions.
"""

from __future__ import annotations

import contextlib
import io
import json
from pathlib import Path
import shutil
import sys
import tempfile

REPORT_DIR_NAME = '.aqk15-gitleaks-ci'
FIXTURE_PATH = (
    'src/test/kotlin/br/com/brunno/features/auth/'
    'infrastructure/Pkcs8RsaPrivateKeyLoaderTest.kt'
)
HISTORICAL_COMMIT = '452f631eb0e00311971c947896f37159d48cfc72'
EXPECTED_IGNORE = [
    f'{FIXTURE_PATH}:private-key:64',
    f'{HISTORICAL_COMMIT}:{FIXTURE_PATH}:private-key:64',
]
EXPECTED_MARKER_LINE = 'val marker = "NOT_A_PRIVATE_KEY_12345"'
EXPECTED_PEM_LINE = (
    '"-----BEGIN PRIVATE KEY-----\\n$marker\\n'
    '-----END PRIVATE KEY-----\\n",'
)


def validate_reviewed_exception(root: Path) -> None:
    """Fail if an exception broadens or the ignored source changes."""
    lines = (root / '.gitleaksignore').read_text(encoding='ascii').splitlines()
    actual = [line.strip() for line in lines
              if line.strip() and not line.lstrip().startswith('#')]
    if actual != EXPECTED_IGNORE:
        raise ValueError('unexpected Gitleaks ignore fingerprints')

    source = (root / FIXTURE_PATH).read_text(encoding='utf-8').splitlines()
    if (len(source) < 64 or source[59].strip() != EXPECTED_MARKER_LINE
            or source[63].strip() != EXPECTED_PEM_LINE):
        raise ValueError('reviewed PEM fixture changed; review the exception')


def count_findings(report_dir: Path, name: str) -> int:
    """Validate report shape but never reproduce finding fields in logs."""
    path = report_dir / f'{name}.json'
    if not path.is_file():
        raise ValueError(f'missing {name} report')
    data = json.loads(path.read_text(encoding='utf-8'))
    if data is None:
        return 0
    if not isinstance(data, list) or any(not isinstance(x, dict) for x in data):
        raise ValueError(f'invalid {name} report schema')
    return len(data)


def audit(root: Path, *, cleanup: bool = True) -> bool:
    reports = root / REPORT_DIR_NAME
    try:
        validate_reviewed_exception(root)
        print('GITLEAKS_EXCEPTION_POLICY=PASS')
        current = count_findings(reports, 'current')
        history = count_findings(reports, 'history')
        print('===== AQK-15 GITLEAKS BLOCKING GATE =====')
        print('GITLEAKS_VERSION=8.30.1')
        print(f'CURRENT_FINDINGS={current}')
        print(f'HISTORY_FINDINGS={history}')
        print('GITLEAKS_POLICY=BLOCKING')
        passed = current == 0 and history == 0
        print('AQK_15_GITLEAKS_GATE=' + ('PASS' if passed else 'FAIL'))
        return passed
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError) as error:
        print(f'AQK_15_GITLEAKS_GATE=FAIL ERROR={type(error).__name__}',
              file=sys.stderr)
        return False
    finally:
        if cleanup:
            shutil.rmtree(reports, ignore_errors=True)


def self_test() -> bool:
    """Safe synthetic tests; never clone, fetch, or expose credentials."""
    try:
        with tempfile.TemporaryDirectory(prefix='aqk15-gitleaks-test-') as directory:
            root = Path(directory)
            fixture = root / FIXTURE_PATH
            fixture.parent.mkdir(parents=True)
            lines = ['// fixture test'] * 64
            lines[59] = EXPECTED_MARKER_LINE
            lines[63] = EXPECTED_PEM_LINE
            fixture.write_text('\n'.join(lines) + '\n', encoding='utf-8')
            ignore = root / '.gitleaksignore'
            ignore.write_text('\n'.join(EXPECTED_IGNORE) + '\n', encoding='ascii')
            validate_reviewed_exception(root)
            reports = root / REPORT_DIR_NAME
            reports.mkdir()
            current_report = reports / 'current.json'
            history_report = reports / 'history.json'
            current_report.write_text('[]', encoding='utf-8')
            history_report.write_text('null', encoding='utf-8')
            with contextlib.redirect_stdout(io.StringIO()):
                if not audit(root, cleanup=False):
                    raise AssertionError('empty-reports gate failed')

            current_report.write_text(
                '[{"RuleID":"synthetic-test", "Secret":"do-not-print"}]',
                encoding='utf-8')
            captured = io.StringIO()
            with contextlib.redirect_stdout(captured):
                if audit(root, cleanup=False):
                    raise AssertionError('current finding did not block')
            if 'do-not-print' in captured.getvalue():
                raise AssertionError('secret leaked to gate output')

            current_report.write_text('[]', encoding='utf-8')
            history_report.write_text('[{}]', encoding='utf-8')
            with contextlib.redirect_stdout(io.StringIO()):
                if audit(root, cleanup=False):
                    raise AssertionError('historical finding did not block')

            history_report.unlink()
            with contextlib.redirect_stdout(io.StringIO()), \
                    contextlib.redirect_stderr(io.StringIO()):
                if audit(root, cleanup=False):
                    raise AssertionError('missing report did not block')
            ignore.write_text('\n'.join(EXPECTED_IGNORE + ['*']) + '\n',
                              encoding='ascii')
            try:
                validate_reviewed_exception(root)
                raise AssertionError('broad-ignore test did not fail')
            except ValueError:
                pass
            ignore.write_text('\n'.join(EXPECTED_IGNORE) + '\n',
                              encoding='ascii')
            lines[63] = '"-----BEGIN PRIVATE KEY-----\\nUNEXPECTED\\n-----END PRIVATE KEY-----\\n",'
            fixture.write_text('\n'.join(lines) + '\n', encoding='utf-8')
            try:
                validate_reviewed_exception(root)
                raise AssertionError('altered-fixture test did not fail')
            except ValueError:
                pass
        print('AQK_15_GITLEAKS_SELFTEST=PASS')
        return True
    except (OSError, ValueError, AssertionError) as error:
        print(f'AQK_15_GITLEAKS_SELFTEST=FAIL ERROR={type(error).__name__}',
              file=sys.stderr)
        return False


if __name__ == '__main__':
    if len(sys.argv) == 2 and sys.argv[1] == '--self-test':
        sys.exit(0 if self_test() else 1)
    if len(sys.argv) != 1:
        print('Usage: aqk15_gitleaks_gate.py [--self-test]', file=sys.stderr)
        sys.exit(2)
    sys.exit(0 if audit(Path('.')) else 1)
