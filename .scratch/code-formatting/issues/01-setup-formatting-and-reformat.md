# Set up code formatting and reformat the codebase

Status: needs-triage

## Problem

The repo has no formatter or `.editorconfig`. Code style is kept by hand across about 100 Kotlin files (`:shared`, `androidApp`, Gradle `.kts`) and 60 Java files (`:mod:*`, `:protocol`). Small differences build up (import order, wrapping, blank lines), and review time goes to layout instead of content.

## What

- Add one Gradle formatting setup that covers every module:
  - Kotlin, including `*.gradle.kts`;
  - Java.
  
  Spotless is the likely choice, with ktlint or ktfmt for Kotlin and palantir-java-format or google-java-format for Java. Pick the formatters during triage; keep build dependencies minimal (CLAUDE.md).
- Add a root `.editorconfig` that the IDE and the formatter both read:
  - match the current style: 4-space indent, 120-column lines, trailing commas in Kotlin;
  - turn off rules that fight the project's style, for example Compose `@Composable` function naming.
- Provide two tasks: one that checks formatting (for CI later) and one that applies it.
- Reformat all code once, in its own `style:` commit with no other changes, and list that commit in `.git-blame-ignore-revs`.
- Note the format command in `README.md` or `CLAUDE.md`, so agents run it before committing.

## Out of scope

- Swift in `iosApp`, `.sq`, `.proto`, and resource XML. Add them later if a formatter fits.
- Lint rules beyond formatting (detekt, Android lint configuration).

## Open questions

- ktlint (configurable through `.editorconfig`) or ktfmt (opinionated, few options)?
- palantir-java-format (120 columns) or google-java-format (100 columns, 2-space indent)? The latter would change most Java lines.
- Should the check run as part of `./gradlew check`, or only on demand until CI exists?

## Acceptance

- The check task passes on a clean checkout after the reformat commit.
- The reformat commit builds, and the full test suite passes (`./gradlew test :shared:testAndroidHostTest`).
- `git blame` skips the reformat commit when `.git-blame-ignore-revs` is configured.
