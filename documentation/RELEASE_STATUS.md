# Checking an OMS release

From the repository root:

```sh
node tools/check-release-status.mjs
```

This read-only command inspects the local HEAD, `origin/master`, GitHub push
workflows for that exact SHA and public VPS `/version`, `/health` and `/ready`.
It does not push, trigger workflows, deploy, authenticate users or modify data.
No access token is needed for the public repository; GitHub API rate limits apply.
Requests have 15-second timeouts. Failed diagnostic requests exit nonzero instead
of inventing a status from missing information.

An explicit **full 40-character SHA** can be supplied as the first argument.
`masterTipMatchesCommit: false` means the remote tip differs, not necessarily
that the inspected commit was never pushed: it may be an older ancestor.

## Distinguishing the stages

- `workflows` lists each exact-SHA push workflow separately. PR checks and
  workflows belonging to another commit cannot stand in for release checks.
  The latest run/attempt wins when a workflow is rerun. `missing`, pending,
  cancelled and failed runs are not successful checks.
- `requiredChecksPassed` requires successful backend regression, browser/form
  regression and container publication/deployment workflows. Pages is displayed
  separately: its success alone does not establish the VPS version.
- `productionMatchesCommit` compares the live revision with the requested SHA,
  accepting the server's 12-character revision or a full SHA, not short prefixes.
- `productionApiHealthy` checks both API liveness and database readiness.
- `releaseVerified` requires all the above and the requested commit at the remote
  master tip. This is an API/deployment diagnostic, not a browser acceptance test.
  `browserUiVerified` therefore always remains false in this tool's output.

GitHub queries are scoped by SHA and use no-cache headers and a per-invocation
nonce to avoid an old intermediary response being mistaken for current progress.

For an automated check that must fail until this release is deployed and healthy:

```sh
node tools/check-release-status.mjs --require-deployed
```

Without that flag, a valid diagnostic snapshot exits successfully even while a
release is still pending. To test classification without contacting production:

```sh
node --test tools/test-release-status.mjs
```
