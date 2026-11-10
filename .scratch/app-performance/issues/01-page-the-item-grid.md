# App: page the item grid

Status: ready-for-agent

## Why

`ItemSearch.items` loads every matching `Item` of the `Version selection` as one list: the whole `selected` join, sorted, mapped and diffed by the `LazyVerticalGrid`. A large modpack has about 40k Items. The query re-runs on every keystroke, and while a Scan runs it re-runs at most once per `CATALOG_REFRESH_PERIOD`. `throttleLatest` (commit cab503c) only limits how often it runs, not how much each run costs.

## What

- Load the grid a page at a time: a `LIMIT`/`OFFSET` (or keyset on the ORDER BY columns) variant of `Item.sq`'s `selected`, plus a `COUNT` of the same filter.
- `ItemSearch` exposes the total count and the Items of a requested range. Pages near the visible rows load, and pages far off-screen are dropped.
- The grid knows the total from the start, so the draggable `McScrollbar` keeps its size and position before the pages around it have loaded. Cells not loaded yet show the empty-slot placeholder.
- Keep the current order: minecraft first, then the mods' scan order, then display name. Keep the `@modid` and name filters.
- Keep Compose out of `presentation` (CLAUDE.md MVVM rules). A paging library only fits if its types stay out of the ViewModel's state. Otherwise, a plain "window of pages" StateFlow is fine.
- Debounce search text before querying, without delaying the text field's own value (it must follow each edit within the frame).

## Acceptance

- Tests at `ItemSearch` against the in-memory Catalog:
  - the count matches the filter;
  - a page returns the right Items in order;
  - a page boundary neither skips nor repeats an Item;
  - an arriving Icon updates a loaded page.
- An `ItemGridViewModel` test: typing quickly runs one query for the final text.
- Manual (user): scroll and drag the scrollbar through a 40k-Item Catalog, and search while a Scan is running.
