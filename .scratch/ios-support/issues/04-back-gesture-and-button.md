# Screen header with back button, and the iOS back gesture

Status: ready-for-agent
Blocked by: 01
Spec: ../spec.md

Decided (spec question 2): both the swipe and a visible button, on both platforms. As a general rule, every screen header has its title centred and an icon back button on its left, built from one shared component so the screens stay consistent.

Only the gesture step needs 01; the header work is common code and can go first.

## What

### `McHeader` (design system, `ui/designsystem/McHeader.kt`)

- `McHeader(onBack: (() -> Unit)?, backDescription: String, modifier, title: @Composable () -> Unit)`, plus a `McHeader(title: String, onBack, backDescription, modifier)` overload that draws the title as a single-line, ellipsized `McText` in `McTheme.typography.title`.
- Layout: a fixed-height row (`McDimens.SquareButtonSize` plus padding). The back `McIconButton` sits at the start; the title is centred on the **whole width**, not on the space left of the button. To get that, it reserves the button's width on both sides, so a long title ellipsizes before it reaches the button.
- `onBack == null` → no button, but the title stays centred (the same reserved space), so a screen without back looks the same.
- The back icon is a new `pixel_arrow_left.xml`, the mirror of `pixel_arrow.xml` in the same 9×7 pixel style, owned by the design system. The design system still imports no app code: callers pass `backDescription` (a new `back` string).
- Add it to `McGallery` with and without back, and with a long title.

### Screens

- `ModListScreen`: the `mod_list_title` `McText` becomes `McHeader(..., onBack = navigator::pop)`.
- `ModVersionsScreen`: drop the `OK` `McButton` on the right; the header's back button on the left replaces it (it did `navigator.pop()` too). The `ok` string stays if anything else uses it, otherwise it goes.
- `ItemScreen`: the private `Header` becomes the slot form, `McHeader(onBack = navigator::pop) { ... }`, with the Item's `McSlot` icon and its name over its id, centred as one group. The name keeps `maxLines = 2`.
- Tab roots (Grid, Bookmarks, Settings) and the full-screen routes (Welcome, Scan progress) have no header today and get none here. If one is added later, it uses `McHeader` with `onBack = null`.
- Back always shows on a pushed screen. Every pushed screen sits on top of a tab root, so `navigator.pop()` always has somewhere to go.

### iOS back gesture

- Turn on Compose Multiplatform's iOS edge-swipe back in `MainViewController`'s `ComposeUIViewController` configuration (check the flag in CMP 1.12.2), so it reaches `RootScaffold`'s `NavDisplay` `onBack = navigator::pop`.
- The outer `NavDisplay` keeps `onBack = {}`: full-screen routes have no back, as on Android, where back there exits.
- Check that the edge swipe doesn't fight horizontal gestures that start near the edge (`McPager` on the Item screen).

## Acceptance

- No new unit tests: `McHeader` is layout only, and `NavigatorTest` already covers `pop`. `NavigatorTest` and the other tests still pass, and `spotlessCheck` passes.
- `McGallery` shows the header variants.
- On a device (user, Android and iOS):
  - ModList, ModVersions and Item show a centred title with the back button on the left; ModVersions has no `OK`.
  - The button pops `Item` → `Item` → `Grid`, `ModList` → `Grid`, and `ModVersions` → `Settings`.
  - On iOS the edge swipe does the same; on Android system back still does.
  - A long Item name ellipsizes or wraps without overlapping the button.

## Comments

- 2026-11-12: `McHeader` done (`ui/designsystem/McHeader.kt`, `mc_back_arrow.xml`, `back` string replacing `ok`). ModList, ModVersions and Item use it, and `McGallery` shows it. The iOS gesture step is left, waiting on 01.
