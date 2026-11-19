"""Watch upstream Loop Habit Tracker releases and open a tracking issue on the fork.

Scheduled run: reads the latest release of the upstream repo, checks whether a
tracking issue already exists on the fork (dedupe by tag in the title), and
opens one if not. The GitHub credential is read from the worker environment
inside the activities (workspace secret bound at deployment time).
"""

import mistralai.workflows as workflows
from pydantic import BaseModel, Field

GITHUB_API = "https://api.github.com"


class UpstreamWatchInput(BaseModel):
    upstream_repo: str = Field(default="iSoron/uhabits")
    fork_repo: str = Field(default="prout800/uhabits")
    feature_branch: str = Field(default="feature/dashboard")
    dry_run: bool = Field(default=False)


class ReleaseInfo(BaseModel):
    tag: str
    name: str
    url: str
    published_at: str


class UpstreamWatchOutput(BaseModel):
    latest_tag: str
    latest_url: str
    issue_already_exists: bool
    issue_url: str | None
    dry_run: bool


def _github_headers() -> dict:
    import os

    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    token = os.environ.get("GITHUB_PAT", "").strip()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    return headers


@workflows.activity()
async def fetch_latest_release(repo: str) -> ReleaseInfo:
    import httpx

    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.get(
            f"{GITHUB_API}/repos/{repo}/releases/latest",
            headers=_github_headers(),
        )
        resp.raise_for_status()
        data = resp.json()
    return ReleaseInfo(
        tag=data["tag_name"],
        name=data.get("name") or data["tag_name"],
        url=data["html_url"],
        published_at=data.get("published_at", ""),
    )


@workflows.activity(retry_policy_max_attempts=3)
async def find_existing_issue(repo: str, tag: str) -> str | None:
    import httpx

    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.get(
            f"{GITHUB_API}/search/issues",
            headers=_github_headers(),
            params={"q": f'repo:{repo} "{tag}" in:title', "per_page": 10},
        )
        resp.raise_for_status()
        items = resp.json().get("items", [])
    for item in items:
        if "pull_request" not in item:
            return item["html_url"]
    return None


@workflows.activity(retry_policy_max_attempts=1)
async def create_tracking_issue(repo: str, release: ReleaseInfo, feature_branch: str) -> str:
    import httpx

    title = f"[upstream] Nouvelle version {release.tag} à intégrer dans {feature_branch}"
    body = (
        f"## Nouvelle version upstream détectée\n\n"
        f"- Dépôt upstream : `{release.url}`\n"
        f"- Tag : `{release.tag}` ({release.name})\n"
        f"- Publiée le : {release.published_at}\n\n"
        f"## À faire pour mettre à jour la branche custom `{feature_branch}`\n\n"
        f"1. `git fetch upstream && git checkout {feature_branch}`\n"
        f"2. `git rebase upstream/dev` (résoudre les conflits si upstream a touché les mêmes fichiers)\n"
        f"3. `git push --force-with-lease origin {feature_branch}`\n"
        f"4. Recréer la branche `ci/apk` au-dessus de `{feature_branch}` et relancer le workflow Actions « Build debug APK »\n"
        f"5. La PR #1 (`{feature_branch}` → `dev`) se met à jour automatiquement ; vérifier le build CI\n"
    )
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.post(
            f"{GITHUB_API}/repos/{repo}/issues",
            headers={**_github_headers(), "Content-Type": "application/json"},
            json={"title": title, "body": body},
        )
        resp.raise_for_status()
        return resp.json()["html_url"]


@workflows.workflow.define(name="upstream_watch")
class UpstreamWatchWorkflow:
    @workflows.workflow.entrypoint
    async def run(self, inp: UpstreamWatchInput) -> UpstreamWatchOutput:
        release = await fetch_latest_release(inp.upstream_repo)
        existing = await find_existing_issue(inp.fork_repo, release.tag)
        issue_url = existing
        if existing is None and not inp.dry_run:
            issue_url = await create_tracking_issue(inp.fork_repo, release, inp.feature_branch)
        return UpstreamWatchOutput(
            latest_tag=release.tag,
            latest_url=release.url,
            issue_already_exists=existing is not None,
            issue_url=issue_url,
            dry_run=inp.dry_run,
        )
