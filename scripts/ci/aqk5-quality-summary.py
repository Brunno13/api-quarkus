#!/usr/bin/env python3
"""AQK-5: read real Gitea statuses and fail closed on missing/failed gates.

No status is created or modified. No token is embedded in this script.
The repo's Gitea API must be readable from the runner without credentials;
otherwise this check deliberately fails instead of reporting a false PASS.
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

BASE_URL = "https://gitea.brunnoserver.duckdns.org"
REPOSITORY = "brunno/api-quarkus"
GATES = (
    "security-wake-x86",
    "quality-detekt",
    "quality-tests",
    "quality-coverage",
    "security-gitleaks",
    "security-trivy",
    "security-opengrep",
)
PAGE_SIZE = 50
MAX_PAGES = 10
MAX_ATTEMPTS = 8


def newest_by_context(items: list[dict]) -> dict[str, dict]:
    """Preserve first item per context (Gitea queried newest first)."""
    out: dict[str, dict] = {}
    for item in items:
        name = item.get("context")
        if isinstance(name, str) and name not in out:
            out[name] = item
    return out


def context_for(event: str, gate: str) -> str:
    kind = {"pull_request": "pr", "push": "push"}[event]
    return f"ci/woodpecker/{kind}/{gate}"


def summarize(statuses: dict[str, dict], event: str) -> tuple[bool, bool, str]:
    good = True
    terminal = True
    passed = 0
    rows = ["===== AQK-5 - CONSOLIDATED QUALITY SUMMARY =====",
            "GATE | STATUS | CONTEXT"]
    for gate in GATES:
        context = context_for(event, gate)
        state = (statuses.get(context) or {}).get("status", "missing")
        if not isinstance(state, str):
            state = "invalid"
        state = state.lower()
        passed += state == "success"
        if state != "success":
            good = False
        if state in ("missing", "pending"):
            terminal = False
        rows.append(f"{gate} | {state.upper()} | {context}")
    rows += [f"CHECKS={len(GATES)}",
             f"PASSED={passed}",
             f"FAILED_OR_NOT_SUCCESSFUL={len(GATES) - passed}",
             "AQK_5_CONSOLIDATED_GATE=" + ("PASS" if good else "FAIL")]
    return good, terminal, "\n".join(rows)


def fetch_statuses(sha: str) -> list[dict]:
    records: list[dict] = []
    for page in range(1, MAX_PAGES + 1):
        url = (f"{BASE_URL}/api/v1/repos/{REPOSITORY}/statuses/{sha}"
               f"?limit={PAGE_SIZE}&page={page}&sort=recentupdate")
        request = urllib.request.Request(
            url, headers={"Accept": "application/json", "User-Agent": "AQK5-quality-summary/1"}
        )
        with urllib.request.urlopen(request, timeout=8) as response:
            part = json.load(response)
        if not isinstance(part, list):
            raise ValueError("unexpected Gitea status response")
        records.extend(x for x in part if isinstance(x, dict))
        if len(part) < PAGE_SIZE:
            return records
    raise ValueError("statuses pagination exceeded")


def main() -> int:
    event = os.getenv("CI_PIPELINE_EVENT", "")
    sha = os.getenv("CI_COMMIT_SHA", "")
    if event not in ("pull_request", "push"):
        print("AQK_5_CONSOLIDATED_GATE=FAIL unsupported CI event", file=sys.stderr)
        return 1
    if not re.fullmatch(r"[0-9a-fA-F]{40}", sha):
        print("AQK_5_CONSOLIDATED_GATE=FAIL invalid commit SHA", file=sys.stderr)
        return 1

    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            states = newest_by_context(fetch_statuses(sha))
            good, terminal, summary = summarize(states, event)
            if terminal or attempt == MAX_ATTEMPTS:
                print(summary)
                return 0 if good else 1
        except (urllib.error.HTTPError, urllib.error.URLError,
                TimeoutError, ValueError, json.JSONDecodeError) as exc:
            # Never log response contents or credentials. HTTP 401/403/404
            # is expected when an unauthenticated request targets a private repo.
            code = exc.code if isinstance(exc, urllib.error.HTTPError) else "N/A"
            print(f"AQK_5_STATUS_API_ATTEMPT={attempt} ERROR={type(exc).__name__} HTTP={code}",
                  file=sys.stderr)
            if code in (401, 403, 404):
                print("AQK_5_CONSOLIDATED_GATE=FAIL repository statuses not anonymously readable",
                      file=sys.stderr)
                return 1
            if attempt == MAX_ATTEMPTS:
                print("AQK_5_CONSOLIDATED_GATE=FAIL status API unavailable", file=sys.stderr)
                return 1
        if attempt < MAX_ATTEMPTS:
            time.sleep(3)
    return 1


if __name__ == "__main__":
    sys.exit(main())
