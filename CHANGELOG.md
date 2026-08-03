# Changelog

All notable changes to **4G 5G Toggle Async** (`com.shams.srk.nrtoggle`) are documented here.

## [34.52.04-async] — 2026-08-03

**versionCode:** `345204`

### Added
- First public release: Shizuku-powered Quick Settings tile for **Prefer LTE ↔ Prefer 5G**
- Live **4G / 5G** tile glyphs (autofit bold text for HyperOS circular tiles)
- Setup screen: Shizuku status, grant, add tile, in-app toggle
- Dual-SIM: apply allowed-network-types to slots 0 and 1 in one shell
- HyperOS-calibrated bitmasks matching Settings UI (mode **9** Prefer LTE, mode **27** Prefer 5G)

### Changed
- Fast path: read preferred mode from `Settings.Global` (no shell on QS panel open)
- QS tap uses `setMode` with optimistic UI update
- Shorter verify poll and 3s Shizuku shell timeout

### Verified
- Device: Redmi Note 13 Pro+ 5G / HyperOS OS3 / Android 16
- Prefer LTE → mode 9, no NR in allowed types: PASS
- Prefer 5G → mode 27, NR present: PASS
- Settings Preferred network type selection stays in sync: PASS

## [34.52.03-async] — 2026-08-03

**versionCode:** `345203`

- State-reflecting 4G/5G tile icons and labels
- Settings.Global fast read + dual-slot apply optimizations

## [34.52.02-async] — 2026-08-03

**versionCode:** `345202`

- Fixed Prefer LTE UI sync (mask without CDMA → mode 9 instead of 10)
- QA against HyperOS Preferred network type panel

## [34.52.01-async] — 2026-08-03

**versionCode:** `345201`

- Initial greenfield scaffold (Compose + Shizuku + QS tile)
