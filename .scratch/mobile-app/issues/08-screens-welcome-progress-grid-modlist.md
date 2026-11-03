# Screens: Welcome, Scan progress, Items tab (grid and mod list)

Status: ready-for-agent
Blocked by: 05, 06, 07
Spec: ../spec.md

## What

Each screen has a ViewModel in `presentation` exposing `StateFlow<UiState>` and taking its collaborators through the constructor. Screens are in `ui/screens`, built only from design-system components. Strings go through `stringResource` (English only for now).

- **Welcome** (full screen, no bottom bar): a "Scan QR" button (`QrScanner.scan()` then `PairingEntry`) and a short hint: "Open the pairing screen from your inventory in Minecraft".
- **Scan progress** (full screen, no bottom bar):
  - Steps: Connecting → Items n/N → Recipes m/M, from `ScanProgress`.
  - On Committed, it shows the bottom-navigation root with the Items tab selected (`Navigator.showRoot(Items)`).
  - A domain failure shows its message (unauthorized, busy, game error, version mismatch, invalid code) with a "Scan again" button.
  - A network failure that remains after the retry shows "Couldn't reach the game. Same Wi-Fi? Is the pairing screen open?"
- **Items tab: grid** (the tab's first screen):
  - A lazy grid of `McSlot` + `ItemIcon`, with the column count from the width and the GUI scale.
  - The top bar has a search field, a **Mods** button that pushes `ModList` onto the Items tab's stack, and a Scan button. Settings is a bottom-bar tab.
  - While `ScanProgress` is Icons k/N, a thin `McProgressBar` is visible.
  - Tapping an item pushes `Item(itemId)` onto the Items tab's stack. A long press shows the item id.
  - The active mod filter shows as a removable chip.
- **Mod list** (pushed onto the Items tab's stack):
  - Rows show the mod name and a `McDropdown` of releases ("6.0.1 · MC 1.21.1", newest Scan first).
  - Changing the dropdown calls `ModList.select`.
  - Tapping a row sets the grid's mod filter and pops back to the grid.

## Acceptance

- ViewModel tests with fakes cover:
  - the progress mapping and the transition to the Items tab on Committed;
  - the domain-error and network-error states;
  - search text reaching the query;
  - a dropdown change updating the grid's state;
  - the Mods button pushing the mod list, and tapping a mod setting the filter and popping back to the grid.
- Manual check on a device against a real game, including turning Wi-Fi off during Icons: the grid stays usable, and the missing Icons fill in on the next Scan.
