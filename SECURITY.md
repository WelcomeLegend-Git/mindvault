# Security Policy

## Supported Versions

MindVault actively maintains security patches for the following versions:

| Version | Supported          |
| ------- | ------------------ |
| 3.3.x   | :white_check_mark: |
| 3.2.x   | :white_check_mark: |
| < 3.2   | :x:                |

## Reporting a Vulnerability

We take the security and privacy of MindVault very seriously. If you discover a vulnerability or security flaw, please report it responsibly.

### How to Report:
1. **GitHub Security Advisory (Recommended)**: Submit a private report via the [Security Advisories](https://github.com/WelcomeLegend-Git/mindvault/security/advisories) tab.
2. **Private-channel fallback**: If private reporting is unavailable, open only a sanitized contact request at [Issues](https://github.com/WelcomeLegend-Git/mindvault/issues), asking a maintainer to provide a private disclosure channel. Do not post exploit details, reproduction steps, proof-of-concept code, credentials, personal data, or sensitive logs publicly. Wait for a private channel before sending the report.

### What to Include Privately:
* Description of the vulnerability and its potential impact.
* Step-by-step instructions or proof-of-concept to reproduce the issue.
* Any potential mitigations or suggested fixes.

### Response Expectations

Maintainers aim to acknowledge private reports promptly and coordinate assessment, fixes, and disclosure with the reporter. Response and release dates depend on maintainer availability and issue complexity; no fixed turnaround or OTA delivery is guaranteed.

## Known Boundaries and Remaining Verification

- Focus blocking and call-handling policy are intentionally unchanged. Related availability and call-access risks remain; this audit work does not guarantee uninterruptibility, emergency access, or tamper resistance.
- Local persistence uses SharedPreferences (including Gson JSON), not an encrypted Room database. App password protection does not encrypt all app data.
- Cloud backups are not end-to-end encrypted by MindVault. Deployed Firestore rules have not been verified; authentication and user-specific document paths alone do not prove account isolation. Maintainers must validate deployed access rules separately.
- CI uses dummy Firebase configuration for build-only checks. It does not test live sign-in, production authorization, or device-specific focus behavior.
- Release APKs previously signed with a debug key require signing-identity continuity to update in place. Switching to a different key is not a transparent upgrade; see [README.md](README.md) before distributing replacements.
- New backups use typed v1 data; the new reader also accepts the old format. Older app readers cannot safely read new v1 backups. Plan multi-device upgrades and downgrades accordingly.
