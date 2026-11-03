# recipehost

Kotlin Multiplatform project (Android + iOS + shared).

## Rules
- Implementation must be flexible when working with mod loader, to easily support other modloaders later.
- Keep dependencies minimal. On Fabric, fabric-api is the accepted base dependency: prefer its modules over custom Mixins (ADR 0009). Keep fabric-api out of `:mod:common`; common code reaches loader features only through `Platform`.
- Commit messages follow Conventional Commits (`<type>(<scope>): <description>`).
- No static-only classes for objects that another class owns or uses: widgets, controllers, factories, schedulers, services. Make them instances that receive their collaborators through the constructor. The owner creates them, and `RecipeHost.init` is the composition root that wires them together. (Example: `PairingButtonInstaller` holds the `PairingController` that `RecipeHost.init` passes in; `PairingButton` has no static `attach`/`onClick`.) Static is fine only for:
  - pure, stateless functions (`PairingPayload.toUri`, `HostAddresses.all`);
  - `@ExpectPlatform` methods on `Platform`;
  - loader entrypoints and the static sinks a mixin needs (`*Dispatcher`), because mixins can't receive injected instances;
  - constants.
- The same rule applies to the mobile app (`:shared`): services, repositories, use cases and ViewModels receive collaborators through the constructor, and `AppGraph` (built by the Android/iOS wrapper) is the composition root.

## Code style
- One component family per file: don't mix unrelated components (e.g. a button and a slot) in one file, but keep variants and the shared base of one component together (e.g. `McButton`, `McSquareButton` and their `McPressable` base in `McButton.kt`). Don't split finer than that. The file is named after its main declaration. Small helpers and data types used only by that component stay in its file.
- Use the narrowest access modifier that works: `private` for file-local helpers, `internal` for anything used only within its module (in `:shared`, the design system and screens are `internal`, so they stay out of the iOS framework's exported API). Make something `public` only when another module or the platform wrapper needs it.

## Working with the user
- Before implementing a task, post a short top-level overview of how you'll implement it (approach, main types and files, what gets tested) so the user can check it quickly. Keep it to a few bullets.

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context layout: one `GLOSSARY.md` and `docs/adr/` at the repo root (both created lazily). See `docs/agents/domain.md`.
