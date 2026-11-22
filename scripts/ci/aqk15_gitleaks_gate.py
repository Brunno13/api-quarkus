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
EXPECTED_TEST_LINE = 'fun `rejects invalid PEM base64 without exposing key material`('
EXPECTED_MARKER_LINE = 'val marker = "NOT_A_PRIVATE_KEY_12345"'
EXPECTED_PATH_LINE = 'val path = directory.resolve("malformed.pem")'
EXPECTED_PEM_LINE = (
    '"-----BEGIN PRIVATE KEY-----\\n$marker\\n'
    '-----END PRIVATE KEY-----\\n",'
)


def reviewed_fingerprint_line(source: list[str]) -> int:
    """Locate only the known synthetic PEM inside its original negative test."""
    def unique_index(expected: str) -> int:
        matches = [i for i, line in enumerate(source) if line.strip() == expected]
        if len(matches) != 1:
            raise ValueError('reviewed PEM fixture changed; review the exception')
        return matches[0]

    test = unique_index(EXPECTED_TEST_LINE)
    marker = unique_index(EXPECTED_MARKER_LINE)
    path = unique_index(EXPECTED_PATH_LINE)
    pem = unique_index(EXPECTED_PEM_LINE)
    next_test = next((i for i in range(test + 1, len(source))
                      if source[i].strip() == '@Test'), len(source))
    if not (test < marker < path < pem < next_test):
        raise ValueError('reviewed PEM fixture changed; review the exception')
    return pem + 1  # GitLeaks fingerprint line numbers are 1-based.


def expected_ignore_for_source(source: list[str]) -> list[str]:
    line = reviewed_fingerprint_line(source)
    return [
        f'{FIXTURE_PATH}:private-key:{line}',
        f'{HISTORICAL_COMMIT}:{FIXTURE_PATH}:private-key:64',
    ]


def validate_reviewed_exception(root: Path) -> None:
    """Fail closed unless both fingerprints match the exact reviewed fixture."""
    source = (root / FIXTURE_PATH).read_text(encoding='utf-8').splitlines()
    expected = expected_ignore_for_source(source)
    lines = (root / '.gitleaksignore').read_text(encoding='ascii').splitlines()
    actual = [line.strip() for line in lines
              if line.strip() and not line.lstrip().startswith('#')]
    if actual != expected:
        raise ValueError('unexpected Gitleaks ignore fingerprints')


def print_safe_finding_metadata(name: str, findings: list[dict]) -> None:
    """Only allowlisted, non-secret metadata; never echo report text or values."""
    for index, finding in enumerate(findings[:5], start=1):
        rule = 'private-key' if finding.get('RuleID') == 'private-key' else 'other'
        reviewed = 'yes' if finding.get('File') == FIXTURE_PATH else 'no'
        start = finding.get('StartLine')
        start_line = start if type(start) is int and 0 < start < 1000000 else '-'
        fingerprint = finding.get('Fingerprint')
        fp_line = '-'
        if isinstance(fingerprint, str):
            suffix = fingerprint.rsplit(':', 1)[-1]
            if suffix.isascii() and suffix.isdecimal() and len(suffix) <= 6:
                fp_line = int(suffix)
        commit = finding.get('Commit')
        commit_short = '-'
        if (isinstance(commit, str) and len(commit) == 40
                and all(c in '0123456789abcdefABCDEF' for c in commit)):
            commit_short = commit[:12]
        print(f'GITLEAKS_SAFE_FINDING scan={name} index={index} '
              f'rule={rule} reviewed_file={reviewed} '
              f'start_line={start_line} fingerprint_line={fp_line} '
              f'commit={commit_short}')
    if len(findings) > 5:
        print(f'GITLEAKS_SAFE_FINDINGS_OMITTED={len(findings) - 5}')


def count_findings(report_dir: Path, name: str) -> int:
    """Validate report shape; print only non-secret finding classifications."""
    path = report_dir / f'{name}.json'
    if not path.is_file():
        raise ValueError(f'missing {name} report')
    data = json.loads(path.read_text(encoding='utf-8'))
    if data is None:
        return 0
    if not isinstance(data, list) or any(not isinstance(x, dict) for x in data):
        raise ValueError(f'invalid {name} report schema')
    if data:
        print_safe_finding_metadata(name, data)
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
            lines = [
                '@Test', EXPECTED_TEST_LINE, '    @TempDir directory: Path,',
                ') {', EXPECTED_MARKER_LINE, EXPECTED_PATH_LINE,
                'Files.writeString(', 'path,', EXPECTED_PEM_LINE,
                'StandardCharsets.US_ASCII,', ')', '}',
            ]
            fixture.write_text('\n'.join(lines) + '\n', encoding='utf-8')
            ignore = root / '.gitleaksignore'
            expected = expected_ignore_for_source(lines)
            ignore.write_text('\n'.join(expected) + '\n', encoding='ascii')
            validate_reviewed_exception(root)

            # Whitespace/formatting shifts must not require rewriting gate code.
            lines.insert(0, '// formatter inserted a line')
            fixture.write_text('\n'.join(lines) + '\n', encoding='utf-8')
            try:
                validate_reviewed_exception(root)
                raise AssertionError('stale fingerprint did not fail closed')
            except ValueError:
                pass
            expected = expected_ignore_for_source(lines)
            ignore.write_text('\n'.join(expected) + '\n', encoding='ascii')
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

            current_report.write_text(json.dumps([{
                'RuleID': 'private-key', 'File': FIXTURE_PATH,
                'StartLine': 123, 'Fingerprint': f'{FIXTURE_PATH}:private-key:123',
                'Commit': 'a' * 40, 'Secret': 'do-not-print',
                'Match': 'secret-match-do-not-print',
                'Message': 'secret-message-do-not-print',
            }]), encoding='utf-8')
            captured = io.StringIO()
            with contextlib.redirect_stdout(captured):
                if audit(root, cleanup=False):
                    raise AssertionError('current finding did not block')
            output = captured.getvalue()
            if 'do-not-print' in output:
                raise AssertionError('secret leaked to gate output')
            if ('reviewed_file=yes' not in output or
                    'fingerprint_line=123' not in output or
                    'commit=aaaaaaaaaaaa' not in output):
                raise AssertionError('safe finding metadata missing')

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
            ignore.write_text('\n'.join(expected + ['*']) + '\n',
                              encoding='ascii')
            try:
                validate_reviewed_exception(root)
                raise AssertionError('broad-ignore test did not fail')
            except ValueError:
                pass
            ignore.write_text('\n'.join(expected) + '\n',
                              encoding='ascii')
            pem = reviewed_fingerprint_line(lines) - 1
            lines[pem] = '"-----BEGIN PRIVATE KEY-----\\nUNEXPECTED\\n-----END PRIVATE KEY-----\\n",'
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
