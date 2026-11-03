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

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context layout: one `GLOSSARY.md` and `docs/adr/` at the repo root (both created lazily). See `docs/agents/domain.md`.
