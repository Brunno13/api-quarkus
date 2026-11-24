#!/usr/bin/env python3
"""AQK-15: fail-closed Gitleaks policy for current files and Git history.

Log only non-secret finding metadata. The reviewed synthetic PEM fixtures have
exact fingerprints checked against their test method and literal source. Full
Gitleaks JSON reports stay in the CI workspace for troubleshooting; no global
rule exclusions.
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
SPOTLESS_COMMIT = '040c6079ffaf54fe29ec30464d0bf3ffdcb24ce3'
# Exact commit that introduced the gate self-scan literals, reviewed separately.
GATE_SELF_SCAN_COMMIT = '7d125ce274a885606579a2cad1c005e134daab93'
GATE_SELF_SCAN_PATH = 'scripts/ci/aqk15_gitleaks_gate.py'
EXPECTED_TEST_LINE = 'fun `rejects invalid PEM base64 without exposing key material`('
EXPECTED_MARKER_LINE = 'val marker = "NOT_A_PRIVATE_KEY_12345"'
EXPECTED_PATH_LINE = 'val path = directory.resolve("malformed.pem")'
# Build the source-code fixture comparison without embedding full PEM blocks
# directly in the Python source; otherwise Gitleaks scans its own gate tests.
PEM_BEGIN = '-----BEGIN ' + 'PRIVATE KEY-----'
PEM_END = '-----END ' + 'PRIVATE KEY-----'
EXPECTED_PEM_LINE = f'"{PEM_BEGIN}\\n$marker\\n{PEM_END}\\n",'

# Synthetic invalid-input fixtures added in 040c607, not usable private keys.
EXPECTED_BASE64_TEST = 'fun `rejects invalid base64 inside a structurally valid PKCS8 PEM`('
EXPECTED_BASE64_PATH = 'val path = directory.resolve("invalid-base64.pem")'
EXPECTED_BASE64_PEM = f'"{PEM_BEGIN}\\nA\\n{PEM_END}\\n",'
EXPECTED_DER_TEST = 'fun `rejects invalid DER after decoding a structurally valid PKCS8 PEM`('
EXPECTED_DER_PATH = 'val path = directory.resolve("invalid-der.pem")'
EXPECTED_DER_PEM = f'"{PEM_BEGIN}\\nAA==\\n{PEM_END}\\n",'
INVALID_PEM_LINE = f'"{PEM_BEGIN}\\nUNEXPECTED\\n{PEM_END}\\n",'



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


def reviewed_synthetic_line(
    source: list[str], test_line: str, path_line: str, pem_line: str,
) -> int:
    """Pin one synthetic PEM to its unique negative JUnit test and file."""
    def unique_index(value: str) -> int:
        matches = [i for i, line in enumerate(source) if line.strip() == value]
        if len(matches) != 1:
            raise ValueError('synthetic PEM fixture changed; review the exception')
        return matches[0]

    test = unique_index(test_line)
    path = unique_index(path_line)
    pem = unique_index(pem_line)
    next_test = next((i for i in range(test + 1, len(source))
                      if source[i].strip() == '@Test'), len(source))
    if not test < path < pem < next_test:
        raise ValueError('synthetic PEM fixture changed; review the exception')
    return pem + 1


def expected_ignore_for_source(source: list[str]) -> list[str]:
    marker = reviewed_fingerprint_line(source)
    base64 = reviewed_synthetic_line(
        source, EXPECTED_BASE64_TEST, EXPECTED_BASE64_PATH, EXPECTED_BASE64_PEM,
    )
    der = reviewed_synthetic_line(
        source, EXPECTED_DER_TEST, EXPECTED_DER_PATH, EXPECTED_DER_PEM,
    )
    return [
        f'{FIXTURE_PATH}:private-key:{marker}',
        f'{HISTORICAL_COMMIT}:{FIXTURE_PATH}:private-key:64',
        f'{FIXTURE_PATH}:private-key:{der}',
        f'{SPOTLESS_COMMIT}:{FIXTURE_PATH}:private-key:{base64}',
        f'{GATE_SELF_SCAN_COMMIT}:{GATE_SELF_SCAN_PATH}:private-key:40',
        f'{GATE_SELF_SCAN_COMMIT}:{GATE_SELF_SCAN_PATH}:private-key:306',
    ]


def validate_reviewed_exception(root: Path) -> None:
    """Fail closed unless all fingerprints match the exact synthetic fixtures."""
    source = (root / FIXTURE_PATH).read_text(encoding='utf-8').splitlines()
    expected = expected_ignore_for_source(source)
    lines = (root / '.gitleaksignore').read_text(encoding='ascii').splitlines()
    actual = [line.strip() for line in lines
              if line.strip() and not line.lstrip().startswith('#')]
    if actual != expected:
        raise ValueError('unexpected Gitleaks ignore fingerprints')


def reviewed_finding_label(name: str, finding: dict, source: list[str]) -> str:
    """Classify only known synthetic fixtures without revealing report fields."""
    if (finding.get('File') != FIXTURE_PATH or
            finding.get('RuleID') != 'private-key'):
        return '-'
    line = finding.get('StartLine')
    commit = finding.get('Commit')
    if name == 'current' and not commit:
        if line == reviewed_fingerprint_line(source):
            return 'invalid-marker'
        if line == reviewed_synthetic_line(
                source, EXPECTED_DER_TEST, EXPECTED_DER_PATH, EXPECTED_DER_PEM):
            return 'invalid-der'
        if line == reviewed_synthetic_line(
                source, EXPECTED_BASE64_TEST, EXPECTED_BASE64_PATH,
                EXPECTED_BASE64_PEM):
            return 'invalid-base64'
    if name == 'history' and commit == SPOTLESS_COMMIT and line == 93:
        return 'invalid-base64'
    if name == 'history' and commit == HISTORICAL_COMMIT and line == 64:
        return 'invalid-marker'
    return '-'


def print_safe_finding_metadata(
    name: str, findings: list[dict], source: list[str],
) -> None:
    """Only allowlisted, non-secret metadata; never echo report text or values."""
    for index, finding in enumerate(findings[:5], start=1):
        rule = 'private-key' if finding.get('RuleID') == 'private-key' else 'other'
        raw_path = finding.get('File')
        path = raw_path if (isinstance(raw_path, str) and
                            0 < len(raw_path) <= 240 and raw_path.isascii() and
                            all(char.isalnum() or char in './_-' for char in raw_path)) else '-'
        reviewed = 'yes' if raw_path == FIXTURE_PATH else 'no'
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
        label = reviewed_finding_label(name, finding, source)
        print(f'GITLEAKS_SAFE_FINDING scan={name} index={index} '
              f'rule={rule} file={path} reviewed_file={reviewed} '
              f'fixture={label} start_line={start_line} '
              f'fingerprint_line={fp_line} commit={commit_short}')
    if len(findings) > 5:
        print(f'GITLEAKS_SAFE_FINDINGS_OMITTED={len(findings) - 5}')


def count_findings(report_dir: Path, name: str, source: list[str]) -> int:
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
        print_safe_finding_metadata(name, data, source)
    return len(data)


def audit(root: Path, *, cleanup: bool = False) -> bool:
    reports = root / REPORT_DIR_NAME
    try:
        validate_reviewed_exception(root)
        print('GITLEAKS_EXCEPTION_POLICY=PASS')
        source = (root / FIXTURE_PATH).read_text(encoding='utf-8').splitlines()
        current = count_findings(reports, 'current', source)
        history = count_findings(reports, 'history', source)
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
        elif reports.is_dir():
            print(f'GITLEAKS_REPORT_DIR_RETAINED={REPORT_DIR_NAME}')


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
                '@Test', EXPECTED_BASE64_TEST,
                EXPECTED_BASE64_PATH, 'Files.writeString(', 'path,',
                EXPECTED_BASE64_PEM, 'StandardCharsets.US_ASCII,', ')', '}',
                '@Test', EXPECTED_DER_TEST,
                EXPECTED_DER_PATH, 'Files.writeString(', 'path,',
                EXPECTED_DER_PEM, 'StandardCharsets.US_ASCII,', ')', '}',
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
            if (f'file={FIXTURE_PATH}' not in output or
                    'reviewed_file=yes' not in output or
                    'fixture=-' not in output or
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
            lines[pem] = INVALID_PEM_LINE
            fixture.write_text('\n'.join(lines) + '\n', encoding='utf-8')
            try:
                validate_reviewed_exception(root)
                raise AssertionError('altered-fixture test did not fail')
            except ValueError:
                pass
            # Both newly reviewed fixtures remain byte-for-byte pinned.
            for original, invalid in (
                (EXPECTED_BASE64_PEM, INVALID_PEM_LINE),
                (EXPECTED_DER_PEM, INVALID_PEM_LINE),
            ):
                clean = [EXPECTED_PEM_LINE if line == lines[pem] else line
                         for line in lines]
                mutated = [invalid if line == original else line for line in clean]
                fixture.write_text('\n'.join(mutated) + '\n', encoding='utf-8')
                try:
                    validate_reviewed_exception(root)
                    raise AssertionError('modified synthetic fixture accepted')
                except ValueError:
                    pass
            # Restore and check classification of the two actual regressions.
            clean = [EXPECTED_PEM_LINE if line == lines[pem] else line
                     for line in lines]
            fixture.write_text('\n'.join(clean) + '\n', encoding='utf-8')
            validate_reviewed_exception(root)
            for scan, line, commit, expected_label in (
                ('current', reviewed_synthetic_line(clean, EXPECTED_DER_TEST,
                     EXPECTED_DER_PATH, EXPECTED_DER_PEM), '', 'invalid-der'),
                ('history', 93, SPOTLESS_COMMIT, 'invalid-base64'),
            ):
                finding = {'File': FIXTURE_PATH, 'RuleID': 'private-key',
                           'StartLine': line, 'Commit': commit}
                if reviewed_finding_label(scan, finding, clean) != expected_label:
                    raise AssertionError('fixture classification failed')
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
