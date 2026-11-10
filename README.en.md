# fuckPrivacyGuard

![license](https://img.shields.io/badge/license-GPL--3.0-blue)
![language](https://img.shields.io/badge/language-Java-orange)
![release](https://img.shields.io/github/v/release/zeroedx/fuckPrivacyGuard?label=release&color=green)
![android](https://img.shields.io/badge/Android-10--16-green)

[简体中文](README.md) · English

An LSPosed module that blocks telemetry from preinstalled HyperOS components at four stages: where data is collected, where it is stored locally, the inter-process interfaces, and the network egress.

This project is based on static analysis and runtime observation of the components shipped on the author's own device. Components differ between models and ROM versions; treat everything here as observed behaviour, not as a universal description.

> **Maintenance**: this is a personal project. Development and testing rely on AI assistance and self-funded device testing, so the cost is borne by the author alone. Releases happen only for defects that affect use, newly discovered reporting channels, or adaptation to a new major system version. UI and wording changes are folded into the next substantive release instead of shipping on their own. Issues are read, but no response time or delivery schedule is promised.

## Independent app and package

As of 2.0.0 this is a fully **independent** app and LSPosed module; it no longer shares the upstream HyperPGuard package name or signature:

- applicationId / Java package: `io.github.zeroedx.fuckprivacyguard`
- App name: `fuckPrivacyGuard`
- Signature: an independent release certificate (not upstream's `debug.keystore`)

So it **cannot be installed over** upstream HyperPGuard, and upstream's APK cannot be installed over this one: they are treated as different apps. Migrating from upstream requires **uninstalling it first**; uninstalling wipes the old scope configuration, and LSPosed scope is **not migrated automatically** — re-tick the target components under LSPosed → Modules → fuckPrivacyGuard → Scope. A different signature also means future updates of this project can only be installed over builds signed by the same new key.

## Requirements

- Android 10 – 16 on HyperOS / MIUI, LSPosed 2.2.x (libxposed API 100+)
- Root for the in-app "restart" and "view log" actions (KernelSU, Magisk, APatch)

> Android 17 / SDK 37 / HyperOS 4 has source-level adaptation but is **not yet verified on a
> real device**: hook signatures were checked against the real android-37.0 platform jar and a
> signature bug that silently disabled the SMS hooks was fixed. The declared support range still
> ends at Android 16. See [compatibility and coverage](docs/COMPATIBILITY.md).

## Install

1. Install `fuckPrivacyGuard.apk` from [Releases](https://github.com/zeroedx/fuckPrivacyGuard/releases)
2. Enable the module in LSPosed
3. Open the app once; it requests the full scope through the official libxposed API, or pick packages manually in LSPosed
4. Press "restart" on a card, or "restart all", so the target process reloads with the module

Packages outside the scope are never injected.

After an in-place upgrade, LSPosed does **not** automatically add newly declared components to the scope. Re-check LSPosed → Modules → fuckPrivacyGuard → Scope; the components added in 1.6.x are "Ads (MSA)" and "Diagnostics (MiSight)". When one is missing, its card shows "Not in scope — tap to add".

## Usage

- **Group switch**: the toggle in the card header mirrors that package's LSPosed scope state. Turning it on requests scope through the framework API; if the framework refuses, a dialog explains what to tick manually.
- **Sub-switches**: "on" means the behaviour is blocked. Rules that can affect system features default to off, and the subtitle states the cost.
- **Restart**: applies `am force-stop` to the target package so it reloads with the new rules. System components are usually restarted by the system within seconds.
- **Custom rules**: append extra blocking by domain, `domain/path`, or `class#method`. See [docs/CUSTOM-RULES.md](docs/CUSTOM-RULES.md) (Chinese).
- **Log unblocked hosts**: records hosts that matched no rule, for finding missing rules; keep it off normally.

The UI follows the system language: English on English devices, Chinese otherwise. Log text and diagnostics remain Chinese.

## What is intercepted

| Stage | Implementation |
|---|---|
| Network egress | Hooks `URL#openConnection`, `InetAddress#getByName`, `Socket` construction/`connect`, and custom okhttp `Dns` implementations; rules are "host suffix + optional path prefix" |
| Privacy APIs | Installed app list, usage stats, device identifiers, SMS sending, XMSF broadcast relay |
| Behaviour database | Blocks `SQLiteDatabase` writes to the eight tables `screenshot`, `screen_share`, `call_forwarding`, `airplane_mode`, `fraud_phone`, `voip_record`, `fraud_url`, `install_app` |
| Silent screen recording | `MediaProjection#createVirtualDisplay` is never created |
| Method layer | Resolves methods by signature and returns constants: SystemHelper upload/write, analytics usage aggregation |
| XSOF | Provider / AIDL / database writes / scoring, four layers; third parties can no longer read screenshots, calls or screen-sharing records |
| Ads (MSA) | Usage profile answers the "fewer ads" side; OneTrack `track*` / `adTrack*` return immediately |

Network and method layers are independent: when obfuscated class names change, domain rules keep working, and when a domain changes, the method layer keeps working. The log shows which layer matched.

## Covered components

| Component | Package |
|---|---|
| SystemHelper (carrier DM) | `com.mobiletools.systemhelper` |
| System security | `com.miui.guardprovider` |
| Security Center | `com.miui.securitycenter` |
| Xiaomi Analytics | `com.miui.analytics` |
| Package installer | `com.miui.packageinstaller` |
| App store | `com.xiaomi.market` |
| Mi services framework | `com.xiaomi.xmsf` |
| Carrier services | `com.xiaomi.registration` |
| Mobile DM | `com.miui.dmregservice` |
| Diagnostics (MiSight) | `com.miui.misightservice` |
| Xiaomi Trust Service | `com.xiaomi.trustservice` |
| Translation | `com.xiaomi.aiasst.vision` |
| Ads (MSA) | `com.miui.systemAdSolution` |

## Logs

| Prefix | Meaning |
|---|---|
| `attach <package>` | Module injected into a process |
| `X(host)` / `X(host+path)` | A rule matched; path rules log `X(host+path)`, so grep with `grep -F 'X(host'` |
| `X(strict)` | Whole-process blocking active |
| `? <tag> <host>` | "Log unblocked hosts" record: request was not blocked |
| `hook ok` / `hook miss` | Method hook installed / target not found |
| `xsof provider hooked`, `xsof aidl hooked=N`, `xsof db write hooked=N`, `xsof score hooked=N` | XSOF layers |
| `msa usage opened=… lastUsed=…`, `msa track hooked=N` | MSA hooks |
| `hookBySignature skipped classes=N` | Classes whose signatures could not be resolved were skipped, scanning continued |

### Verifying it works

1. Enable the module and restart the target app
2. Trigger its reporting (open Security Center, install an app)
3. "View log" should show `attach <package>` and `X(...)` lines
4. XSOF lines should all say `hooked`, with no `miss`
5. If only `attach` appears, enable "log unblocked hosts" for a while and add the `?` hosts to the rules

## UI

| Brand icon | Group cards |
|---|---|
| ![brand](docs/images/brand-icon.png) | ![cards](docs/images/ui-cards.png) |

Since 2.0 the UI is six pages behind a bottom navigation bar: Home (status / master switch / bulk actions), Categories (13 component groups), Search (filter by name, description, package or config key), Diagnostics (scope / runtime records / self-check), Settings (general / appearance / rules / logs) and About (links / credits / license).

Dark mode follows the system; the night palette is pure black with `#1C1C1E` cards. Appearance can also be pinned to Light or Dark in Settings.

## Build

JDK 17 and build-tools 35+ are required. There is no Gradle; `build.sh` runs `javac --release 11` → `d8` → `aapt2 link` → `zipalign` → `apksigner`.

```sh
export ANDROID_JAR=/path/to/android.jar D8=/path/to/d8 AAPT2=/path/to/aapt2
export ZIPALIGN=/path/to/zipalign APKSIGNER=/path/to/apksigner
sh build.sh
```

Signing material is **never committed**; it is read from outside the repo — environment variables first, then `~/.config/fuckprivacyguard/signing.env` (override the path with `SIGNING_ENV`). If a required value is missing the build fails; it never falls back to a bundled or upstream key.

```sh
# ~/.config/fuckprivacyguard/signing.env
KEYSTORE=/path/to/release.jks   # independent release key
KEY_ALIAS=fuckprivacyguard
STORE_PASS=...
KEY_PASS=...                    # defaults to STORE_PASS
EXPECT_SHA256=<64-hex cert fingerprint>  # guard against the wrong key
```

`EXPECT_SHA256` is checked before signing: if the keystore's real fingerprint does not match, the build aborts. CI injects the same values from repository Secrets.

## Source layout

```
src/io/github/zeroedx/fuckprivacyguard/
├── HookEntry.java       entry point, dispatch per package
├── Config.java          switches and config channel
├── I18n.java            UI strings (Chinese source, English lookup)
├── NetGuard.java        URL / InetAddress / Socket + rule matching
├── PrivacyApis.java     app list, usage stats, identifiers, SMS, XMSF
├── CaptureHooks.java    behaviour database writes, silent recording
├── XsofHooks.java       XSOF provider / AIDL / writes / scoring
├── MsaHooks.java        MSA usage profile and OneTrack
├── AnalyticsHooks.java  analytics method layer
├── Hooks.java           shared hook helpers
├── Logs.java            unified log tag
├── PgSwitch.java        MIUI-style switch used by the settings UI
└── MainActivity.java    settings UI
```

## Limitations

- Domain lists can be incomplete: addresses can be delivered from server config
- Switches left at their default value are not written into `fuckprivacyguard_conf`; a missing key does not mean the switch is off
- Obfuscated class names change between versions; `hook miss` in the log is expected and does not affect the network layer
- Requests made through native stacks (Cronet and friends) are not covered by Java-level hooks
- `IMrm` is a HAL-level service; only the `ServiceManager` side is disconnected
- The module blocks reporting; it does not revoke permissions already granted during device setup

## Contributing

Run "self-check" before opening an issue, redact identifiers in logs, and read [CONTRIBUTING.en.md](CONTRIBUTING.en.md) for what not to upload (vendor APKs, decompiled output, device databases).

## License and disclaimer

GPL-3.0, see [LICENSE](LICENSE). Usage terms, legal notes and risk statements are in [DISCLAIMER.txt](DISCLAIMER.txt); the English summary there is for convenience only and the Chinese text prevails. Please read it before use. The project is not affiliated with Xiaomi or China Unicom, and all trademarks belong to their respective owners.

## Credits

HyperCeiler, XSOF Privacy, HyperOS4-XSOF-Analysis, OSAKOo, RooyCo, frauddb, misight-viewer, libxposed, LSPosed and others. The full list with links is in the [Chinese README](README.md#参考来源).
