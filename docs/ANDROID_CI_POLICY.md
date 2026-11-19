# Android CI verification policy

## Required behavior

- Every pull request revision must receive the Android verification workflow.
- Development branch pushes (`fix/**`, `feature/**`, and `release/**`) also run the same verification so a missed PR webhook cannot leave a commit without a check.
- Pull request validation must not use path filters. The workflow definition on the base branch controls pull-request event filtering; editing filters only in a pull request cannot repair that pull request's missing check.
- A running verification must not be cancelled merely because a newer commit is pushed. Keep active checks alive and let the newest revision receive its own result.
- PR runs are grouped by pull request number; branch push runs are grouped by ref.
- A missing workflow run is an infrastructure/triggering condition, not a passing test and not an application-code failure.
- A failed workflow must be classified by the failing step and logs. Emulator startup/runner allocation failures must be separated from build, unit-test, lint, APK-integrity, and app-runtime failures.

## Release gate

Do not merge a feature PR or publish a release APK until the exact PR head has a successful verification run that includes unit tests, lint, APK package/version/checksum checks, emulator install/launch, and the VPN runtime smoke test. A successful build without runtime verification is not a release pass.

Physical Samsung device validation remains a separate release checklist item; emulator success must not be represented as physical-device verification.
