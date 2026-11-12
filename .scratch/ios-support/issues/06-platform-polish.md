# Platform polish

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

## What

- `Info.plist`: `UISupportedInterfaceOrientations` = portrait only; `UIStatusBarStyle` = `UIStatusBarStyleLightContent` and `UIViewControllerBasedStatusBarAppearance` = `NO`.
- `createAppGraph`: after the driver opens, set `NSURLIsExcludedFromBackupKey` on the `catalog.db` file (and its `-wal`/`-shm` files if present).
- `IPHONEOS_DEPLOYMENT_TARGET` in `project.pbxproj`: lower from 18.2 to 16.0 (spec question 3). Check that Compose Multiplatform 1.12.2 supports 16 before changing it.
- `MARKETING_VERSION` (hard-coded `1.0` in `iosApp/Configuration/Config.xcconfig`): read `app_version` from `gradle.properties`, as Android's `versionName` and the Settings footer already do, for example through a Gradle-generated `Version.xcconfig` that `Config.xcconfig` `#include`s.
- Delete `Greeting.kt`, `GreetingUtil.kt`, `Platform.kt`, `Platform.android.kt` and `Platform.ios.kt`.
- Check that the Items search field stays visible above the keyboard and that `McBottomBar` clears the home indicator; fix with insets in common code if not.

## Acceptance

- Both apps compile; `spotlessCheck` passes.
- The iOS app's version (`CFBundleShortVersionString`) equals `app_version`.
- On a device (user): portrait only, light status bar on every screen, keyboard and home indicator don't cover content.
