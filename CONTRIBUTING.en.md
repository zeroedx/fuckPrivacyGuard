# Contributing

[简体中文](CONTRIBUTING.md) · English

## Before opening an issue

- Search [existing issues](https://github.com/zeroedx/fuckPrivacyGuard/issues?q=is%3Aissue) first; add details to the original thread instead of opening a duplicate
- Run "self-check" in the app first; it shows the master switch, the scope and which processes were injected
- Redact your logs: IMEI, IMSI, ICCID, phone number, account names, usernames inside file paths
- Paste only the lines related to the problem, not a whole logcat

## Do not upload

- Vendor APKs and their decompiled output (SystemHelper, GuardProvider, Security Center, Mobile DM and so on).
  Those are copies of someone else's software; uploading them here is a copyright problem. Reproduce locally with `pm path` instead.
- Device databases, backups, full installed-app lists, call or SMS content

## Code contributions

- After changing anything, confirm `sh build.sh` still reproduces the build
- Keep the diff to the problem; do not mix in reformatting
- For a new blocking target, state the criterion (host, method signature, table name). Class and method signatures are facts and can be shared, but do not paste method bodies, decompiled sources or unpacked resource files

## Commercial use

This repository does not accept paid distribution, bundleware or "donate to unlock" schemes under the project's name. Forks are free to do what GPL-3.0 allows, but must not use the project's name or icon.

## Release cadence

- Releases happen only for broken blocking, crashes, newly discovered reporting channels or adaptation to a new major system version
- Wording, UI and log-format changes are folded into the next substantive release instead of shipping alone
- Cadence is deliberately reduced: development and testing rely on AI assistance and self-funded device testing, borne by the author
- Issues are read, but no response time or delivery schedule is promised; unconfirmed feature requests may be postponed or merged into a related change
