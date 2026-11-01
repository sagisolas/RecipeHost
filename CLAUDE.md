# recipehost

Kotlin Multiplatform project (Android + iOS + shared).

## Rules
- Implementation must be flexible when working with mod loader, to easily support other modloaders later.
- Use at least dependencies as possible. Do not use farbic-api if there are builtin methods.
- Commit messages follow Conventional Commits (`<type>(<scope>): <description>`).

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context layout: one `GLOSSARY.md` and `docs/adr/` at the repo root (both created lazily). See `docs/agents/domain.md`.
