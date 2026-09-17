# CI and supply-chain checks

## Execution and speed

`.github/workflows/android-ci.yml` runs on main pushes, PRs targeting main,
and Mondays at 06:23 UTC (scheduled workflows run from the default branch).
It never deploys, uploads a distribution, or accesses signing credentials.

Four independent matrix jobs run debug unit tests, Android debug lint, the
minified/resource-shrunk unsigned release build, and dependency inventory/OSV
checking. `fail-fast: false` keeps sibling checks running after a failure.
There are no path filters or test/lint/release exclusions. Workflow concurrency
cancels superseded runs for the same event and PR/ref, not unrelated PRs.
Reports have unique artifact names and seven-day retention, including on failure.

`setup-gradle` is the sole Gradle cache owner; setup-java's duplicate cache is
removed. Gradle task-output caching is enabled with `--build-cache`. Only main
pushes write caches; PRs, forks, and schedules read them. No secrets, project
configuration-cache encryption key, or permission increases are introduced.
Configuration-cache state is not persisted by setup-gradle without an encryption
key. The inventory job explicitly disables configuration cache because its task
resolves project configurations during execution; the other jobs retain it.

Expected impact: shorter wall-clock feedback when runners are available, but
more total runner minutes and duplicated cold-start configuration/compilation.
Cache hits and actual speedup have not been measured. The weekly run adds cost
but catches newly published advisories without needing a dependency change.
The SDK/JDK versions, R8/resource shrinking, tests, and lint policy are unchanged.

Branch protection must use the new check names: `Unit tests`, `Android lint`,
`Minified unsigned release`, and `Resolved dependency vulnerabilities`, replacing
any requirement for the old `Build, Lint & Test` check. Repository settings were
not changed here.

## What the security checks actually do

### Resolved Maven vulnerability check

`:app:exportDependencyInventory` exports selected versions from these eight
configurations, including transitives and BOM constraints:

- debug and release CompileClasspath / RuntimeClasspath
- debugUnitTest CompileClasspath / RuntimeClasspath
- debugAndroidTest CompileClasspath / RuntimeClasspath

Unresolved dependencies or an empty inventory fail the task. This does not run
instrumentation tests. It is not an SBOM or a lockfile: dependency paths and
configuration attribution are not retained in the deduplicated coordinate list.

`.github/scripts/osv-scan.mjs` queries OSV's Maven ecosystem for each exact
resolved version. It batches requests, follows pagination, retries network
failures three times with 30-second request timeouts, and writes `osv.json`.
Every returned advisory fails the job, including existing advisories; the batch
API does not provide severity, so no severity threshold is claimed. API or
malformed/incomplete response failures are failures, not clean scan results.
An outer 30-minute job timeout bounds the entire operation. Reports contain
advisory IDs for lookup at `https://osv.dev/vulnerability/ID`.

OSV receives Maven package names and versions, not source code, credentials,
or app/user data. No API key or paid service is configured; public service
availability/rate limits can fail CI. This implementation uses runner-provided
Node (20+), built-in APIs only, and no npm packages.

Limitations: this is known-advisory matching, not exploitability/reachability
analysis, malware detection, artifact integrity verification, or proof of safety.
It excludes build-plugin/KSP processor classpaths, the Gradle/JDK/SDK toolchains,
embedded/shaded/native libraries without separate Maven coordinates, and
configurations not listed above. Existing dependencies may make the first scan
fail. Triage the IDs and propose narrowly scoped fixes; no suppressions or
unreviewed dependency upgrades are included here.

### GitHub dependency review

A separate PR job uses dependency-review-action and fails on newly introduced
high/critical vulnerabilities visible to GitHub's dependency graph. License
checking and PR comments are disabled. It uses only `contents: read`.

Public repositories run this check by default. Private repositories need the
appropriate GitHub dependency-review entitlement and dependency graph enabled;
a maintainer can then set the non-secret repository variable
`DEPENDENCY_REVIEW_ENABLED=true`. No settings or entitlements were verified or
changed. Once enabled, API/entitlement errors fail rather than being ignored.

**This is not full Gradle dependency review.** GitHub graph coverage may omit
Gradle dependencies unless resolved snapshots have been submitted. This workflow
does not submit snapshots, since that would require write permissions. A green
review can mean no graph-visible changes, not a clean Android dependency graph.
The separate OSV check provides resolved app/test coverage on pushes, PRs, and
weekly runs without graph submission or elevated follow-up workflows.

## Verified pins and wrapper

Verified via upstream HTTPS endpoints during this change (2026-09-17):

| Action | Upstream ref | Full commit SHA |
| --- | --- | --- |
| actions/checkout | v4 | `11d5960a326750d5838078e36cf38b85af677262` |
| actions/setup-java | v4 | `cf277c60eb25467037889841efdb72551f06f6c3` |
| actions/upload-artifact | v4 | `ea165f8d65b6e75b540449e92b4886f43607fa02` |
| android-actions/setup-android | v3 | `9fc6c4e9069bf8d3d10b2204b1fb8f6ef7065407` |
| gradle/actions/setup-gradle | v4.4.3 (v4) | `ed408507eac070d1f99cc633dbcf757c94c7933a` |
| actions/dependency-review-action | v4.9.0 | `2031cfc080254a8a887f58cffee85186f0e49e48` |

Sources: `https://api.github.com/repos/OWNER/REPO/git/ref/tags/REF`.
Gradle's v4 ref was peeled through two annotated tags to the commit, not pinned
to a tag object. Dependency review's v4 endpoint returned 404; its v4.9.0 commit
was obtained from `https://api.github.com/repos/actions/dependency-review-action/tags?per_page=5`
and its action metadata fetched successfully at that SHA. Gradle setup metadata
was also inspected at its pinned SHA. Existing actions retain their major
versions; setup-gradle and dependency review are newly added CI dependencies.

This verifies upstream ref-to-commit identity, not an independent source audit
or signatures for every action. SHA pins do not freeze runner images, tools
that actions download, action-internal dependencies, or live advisory databases.
Refresh pins by checking upstream again; never substitute remembered SHAs.

The Gradle distribution remains **8.13-bin**, with the SHA-256 from
`https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256`:

`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`

This was also corroborated at `https://gradle.org/release-checksums/` and added
as `distributionSha256Sum`. No distribution ZIP was downloaded or executed
for validation. Wrapper checksum verification applies to a fresh download;
it does not retroactively rehash an already-unpacked cached distribution.

The existing, unchanged `gradle-wrapper.jar` hashes to:

`e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637`

That matches the official checksum table for older wrappers (including Gradle
6.6–6.9.4 and 7.0–7.0.2). It does **not** match the Gradle 8.13 wrapper JAR:
`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`
(source: `https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256`).
The bootstrap JAR version and selected distribution version are distinct.
The older authentic JAR is preserved, not represented as an 8.13 JAR.
Setup-gradle validates wrapper JARs before executing Gradle; replacing the JAR
or regenerating wrapper launchers was not required for the distribution checksum.

## Exact dependency declaration changes

No application/library/plugin versions were upgraded, removed, or added relative
to the initial working tree. These existing declarations moved into the catalog:

| Coordinate / plugin ID | Preserved version |
| --- | --- |
| com.google.firebase:firebase-firestore-ktx | 24.10.3 |
| com.google.firebase:firebase-auth-ktx | 22.3.1 |
| androidx.fragment:fragment-ktx | 1.8.1 |
| androidx.compose.material:material-icons-extended | 1.5.4 |
| androidx.lifecycle:lifecycle-service | 2.7.0 |
| androidx.lifecycle:lifecycle-viewmodel-compose | 2.7.0 |
| androidx.lifecycle:lifecycle-runtime-compose | 2.7.0 |
| com.google.code.gson:gson | 2.10.1 |
| io.coil-kt:coil-compose | 2.4.0 |
| com.google.accompanist:accompanist-drawablepainter | 0.32.0 |
| com.google.android.gms:play-services-auth | 20.7.0 |
| androidx.credentials:credentials | 1.2.2 |
| androidx.credentials:credentials-play-services-auth | 1.2.2 |
| com.google.android.libraries.identity.googleid:googleid | 1.1.0 |
| com.google.devtools.ksp (plugin) | 2.0.0-1.0.21 |

KSP's default version moved from settings pluginManagement to the catalog/root
plugin alias. Lifecycle runtime KTX remains separately at 2.6.1; the 2.7.0
lifecycle declarations and explicit icons version were deliberately not aligned
or converted to BOM-only versions. Existing uncommitted WorkManager 2.9.0,
coroutines 1.7.3 (including test), and Room 2.6.1 catalog work is preserved.
No Firebase BOM migration or KTX replacement was attempted.

Signed releases still require all four existing `MINDVAULT_RELEASE_*` variables.
Absent credentials mean unsigned; partial credentials fail; there is no debug
signing fallback. CI keeps the existing dummy Firebase config and supplies none
of those signing variables. Release artifacts here are build checks, not usable
production authentication/distribution packages.

## Validation and remaining verification

- Passed four offline Node tests (inventory validation, batching, pagination,
  malformed/unavailable responses), and `node --check` on the scanner.
- Passed scoped `git diff --check`; local JAR SHA-256 checked as above.
- No local Gradle, Android tests/lint/build, dependency resolution, or live OSV
  inventory scan ran. The new Kotlin task and catalog accessors need validation
  by the main build owner / CI. No claim of clean vulnerabilities or build success.
- YAML/actionlint validation was not available locally; action inputs were
  checked against fetched pinned metadata where newly introduced.
- No workflow was triggered, no push/deploy/commit occurred, no repository
  permissions/settings were changed, and unrelated uncommitted work was left alone.
