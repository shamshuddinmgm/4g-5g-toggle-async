# 4G 5G Toggle Async

One Quick Settings tap to flip **Prefer LTE** ↔ **Prefer 5G** on HyperOS — powered by [Shizuku](https://shizuku.rikka.app/). No root.

**Package:** `com.shams.srk.nrtoggle`  
**Version:** `34.52.04-async` (versionCode `345204`)  
**License:** [GNU General Public License v3.0](LICENSE)

## What it does

Add the **4G/5G** tile to Quick Settings. Tap once:

| Current preference | After tap |
|---|---|
| Prefer **5G** | Switches to Prefer **LTE** (4G) |
| Prefer **LTE** | Switches to Prefer **5G** |

The tile glyph reads **4G** or **5G** for the live preferred mode, with a short subtitle hint for the next tap.

Under the hood it drives telephony **allowed network types** via Shizuku shell (`cmd phone set-allowed-network-types-for-users`) — the same path HyperOS Settings uses — so the Preferred network type panel stays in sync (mode **9** / **27** on the author’s device).

Writing only `settings put global preferred_network_mode*` is **not** enough on modern HyperOS; this app does not rely on that alone.

## Requirements

- Android **12+** (API 31+)
- [Shizuku](https://shizuku.rikka.app/) installed, started, and permission granted
- Ability to add a custom Quick Settings tile

## Setup

1. Install **Shizuku** and start it (Wireless debugging pairing is fine).
2. Install **4G 5G Toggle Async**.
3. Open the app → **Grant Shizuku permission**.
4. **Add Quick Settings tile** (or edit the QS panel and add **4G/5G**).
5. Tap the tile to flip Prefer LTE ↔ Prefer 5G.

## Tested & working

Verified on the author’s device:

| Item | Value |
|---|---|
| Device | Redmi Note 13 Pro+ 5G |
| Android | **16** |
| HyperOS | **OS3** |
| Prefer LTE | `preferred_network_mode` **9** |
| Prefer 5G | `preferred_network_mode` **27** |

Other OEMs may use different mode integers or bitmasks. Treat anything outside this matrix as unverified.

## Disclaimer

**This is a personal power-user tool, not a Play Store product.**

- It uses **Shizuku** (ADB shell identity) to change preferred radio network types. That is privileged behaviour.
- You use it **at your own risk**. The authors are **not responsible** for data loss, battery impact, broken connectivity, carrier issues, warranty problems, or any damage to your device or data.
- Do **not** use this to bypass employer / school / parental controls or any policy that forbids modifying radio settings.
- Keep Shizuku updated. After a reboot you must start Shizuku again before the tile works.
- This software is provided **“as is”** with **no warranty** of any kind — to the maximum extent permitted by law.

If you are not comfortable granting Shizuku-level access, do not install this app.

## Privacy

- No ads, no analytics, no internet permission.
- No backup of app data (`allowBackup=false`).
- Only talks to the system via Settings reads and Shizuku telephony shell commands.

## Build

```bash
./gradlew assembleRelease
```

Release signing uses a local `keystore.properties` (gitignored). Place your keystore outside the repo or under a gitignored `keystore/` path.

## Related Async tools

- [GPS Toggle Async](https://github.com/shamshuddinmgm/gps-toggle-async) — location helpers QS tile
- [Private DNS Async](https://github.com/shamshuddinmgm/PrivateDNSAndroid) — Private DNS QS tile
- [Hail Async](https://github.com/shamshuddinmgm/Hail) — app freezer
- [Shappky Async](https://github.com/shamshuddinmgm/shappky) — force-stop utilities
