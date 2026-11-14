# Release plan

What has to happen before the app goes on Google Play and the App Store, and the mod goes on Modrinth and CurseForge. Surveyed 2026-11-12.

## Blockers: app (Google Play)

- [ ] **Release signing.** `androidApp/build.gradle.kts` signs `release` with the debug key. Add an upload keystore kept out of git (read through `local.properties` or environment variables) and turn on Play App Signing.
- [x] **Launcher icon.** The Android launcher icons and the iOS `AppIcon` are still the project template's.
- [x] **App name.** `Recipehost` in `androidApp/src/main/res/values/strings.xml` and `iosApp/Configuration/Config.xcconfig`, `RecipeHost` everywhere else.
- [ ] **Mod page links.** `mod_modrinth_url` and `mod_curseforge_url` in `gradle.properties` are placeholders. Create the mod pages, then set the real URLs.
- [x] **Network permissions.** `INTERNET` and `ACCESS_NETWORK_STATE` reach the merged manifest only through libraries. Declare them in `androidApp/src/main/AndroidManifest.xml`.
- [ ] **Play Console.** A privacy policy URL, the Data safety form (the app only talks to the user's own game on the local network, with no analytics), a content rating, and the store listing with screenshots.
- [x] **Versioning.** Both apps take their version and build number from `app_version`: `versionName` and `MARKETING_VERSION` are the version, and `versionCode` and `CURRENT_PROJECT_VERSION` are `major * 1_000_000 + minor * 1_000 + patch` (`buildSrc` `buildNumber`), so every upload needs a version bump. `app_version` is still set apart from the mod's `mod_version`. Decide how the two advance together, since the app shows "update both" on a protocol mismatch.

## Blockers: app (App Store)

The iOS wrapper is in place (`.scratch/ios-support`): QR scanner, `recipehost://pair` link, Local Network prompt, and `MARKETING_VERSION` read from `app_version`. What's left for a release:

- [ ] **Signing and team ID.** `DEVELOPMENT_TEAM` is set in `project.pbxproj` while `TEAM_ID` in `Config.xcconfig` is empty. Keep one source, join the Apple Developer Program, and archive with distribution signing (automatic signing is enough for App Store Connect uploads).
- [ ] **Bundle ID.** `org.sagisolas.recipehost.Recipehost` is the template's. Pick the final one (likely matching Android's `applicationId`) before the first upload: it can't change after.
- [x] **App icon.** `AppIcon` has only the template's 1024 image, and the dark and tinted variants are empty (see Launcher icon above).
- [ ] **Privacy manifest.** Add `PrivacyInfo.xcprivacy` to the app target: no tracking, no data collected, and any required-reason APIs the app or its static `Shared` framework uses.
- [ ] **App Store Connect.** The listing with screenshots, the privacy policy URL, the App Privacy answers (no data collected), the age rating, and review notes explaining that pairing needs the mod running in Minecraft on the same network.

## Blockers: mod (Modrinth, CurseForge)

- [x] **License.** MPL-2.0: root `LICENSE`, `license` set in `fabric.mod.json` and `neoforge.mods.toml`. Mojang GUI textures (`mc_*.png`) still need replacing or loading at runtime, since they can't be relicensed.
- [x] **Metadata.**
  - `fabric.mod.json`: add `authors`, `contact` (homepage, sources, issues), `icon` and `license`.
  - `neoforge.mods.toml`: add `authors`, `displayURL` and `logoFile`.
  - Declare EMI as optional: `suggests` on Fabric and `type = "optional"` on NeoForge.
- [x] **Supported versions.** Store pages list only Minecraft 1.21.1, matching the declared range (see `.scratch/version-range/research.md`).

## Should do before release

- [ ] **Large-pack performance.** `.scratch/app-performance` 01 (page the item grid) and 02 (load recipe bodies only for the recipes shown). A pack with about 40k items is the normal case.
- [x] **Scan memory.** Measure memory and commit time on a large modpack (`ScanIngest.kt` TODO). Move to staging tables if needed.
- [ ] **Instance names.** Check them on real CurseForge, Modrinth App, Prism and vanilla installs (`InstanceName.java` TODO).
- [x] **Release build check.** Pair and scan end to end on a minified `release` build (R8 has broken the code scanner before; see `androidApp/proguard-rules.pro`).
- [x] **README.** Replace the KMP template with what the mod and app do, how to pair, and how to build. It doubles as the sources page.

## Decisions to make

- [ ] **Potion variants and EMI brewing** (`.scratch/item-variants/issues/01`, needs-triage). Decide whether it's in v1: it changes the protocol, which is cheaper before anything is published.
- [x] **Design system textures** (`.scratch/mobile-app/issues/11`, needs-info). The real textures look to be in place already, so it may only need closing.
- [ ] **Pairing without Play services.** The `recipehost://` link is kept as is. Some camera apps (e.g. the ColorOS camera) send it as a web search, so phones without Google Play services may not be able to pair. State on the store page that it needs Play services.

## Housekeeping

- [ ] Update stale issue statuses: several say `ready-for-agent` even though the work looks shipped (e.g. `mobile-app/05`, `07`, and the `qr-pairing` and `recipe-layouts` specs).
- [ ] Add CI (e.g. GitHub Actions) running `spotlessCheck`, the tests, and both mod builds.
