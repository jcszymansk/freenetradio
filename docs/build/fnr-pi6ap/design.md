# Design candidate: main screen in Compose (Console design)

- Source bead: fnr-bfxtd (input convoy fnr-d4two, target branch `fnr/compose-shell`)
- Workflow root: fnr-pi6ap; lane beads: fnr-locp8 (iteration 1), fnr-r5r6e (iteration 2)
- Approval mode: interactive
- Approval status: **approved** at gate fnr-ermlu on 2026-11-10 (the human's answer applied; see approval-summary.md).
  Previously: ready for re-approval. The human answered on 2026-11-10 (comment on gate
  bead fnr-q5qys): "approve with Q8 = b, Q0 = no, everything else as recommended". Q8 differs from
  the recommendation, so the gate iterated. Iteration 2 checked the gate's draft of those answers
  against the code (see "Iteration 2 verification") and changed nothing else. Every decision in
  this design is now a human answer; no question remains open.

## Checklist state

1. Project context inspected: done (see "Context found").
2. Visual companion: decided worthwhile (drill-down header, mini player idle state, MINE tab with
   FAB, row actions are layout questions the two browse PNGs do not answer). Offered in sessions 2
   and 3 (2026-11-09 22:15, 22:17 CET) and through a blocking question dialog in session 4 (22:18).
   Answered at the gate: Q0 = no.
3. Clarifying questions: Q1-Q8 answered at the gate (see "Answered questions").
4. Approaches: done; the human confirmed approach A.
5. Design sections: revised to the answers and verified in iteration 2; awaiting re-approval at
   the gate.

### Iteration 2 verification (2026-11-10)

- Q8 = b rests on code as described: `MediaItemRoot.kt:239-242` adds
  `MediaItemBuilder.buildCountryMenuItem(context, countryCode)` (id `MEDIA_ID_COUNTRY_STATIONS`,
  title the country name, flag drawable) only when `countryCode` is non-empty and
  `LocationService.isDefaultLocationEnabled` is false; `MediaItemRootTest` covers both absences.
  `MediaItemRootCar` is untouched, so Android Auto is unaffected.
- The user's country therefore appears twice on COUNTRY's root: once under YOUR COUNTRY and once
  in its place under ALL COUNTRIES. That is intended; the headers separate the two.
- Sections 2-7, section 9 and the acceptance criteria name the answered behaviour; no "proposed"
  or "per Qn" wording remains in them.

### Iteration 1 history: why that pass stopped without answers (session 5, 22:21 CET)

The controller kills this session about 30 s after the step goes quiet: events show
`execution.step_stalled` for fnr-locp8 at 22:20:46, then `worker.operation kill` at 22:21:17 and
a restart, the same cycle every ~3 minutes (drain_at 21:18:17Z, 21:21:17Z). No human was
attached to the tmux session (`session_attached=0`). A session that waits for an answer, with or
without a dialog, is killed before one can arrive. This pass therefore records the questions and
a full draft for the approval lane to put in front of the human, instead of waiting in-session.
To answer in place, edit "Answered questions" below, or attach to
`superpowers__brainstorming-gc-wisp-gj9wc4` while a later iteration is asking.

## Requested outcome

Replace the View main screen (drawer, toolbar, RecyclerView list, current-station strip, add FAB)
with one Compose screen in the Console design: `ConsoleTopBar` (title, search, overflow),
`MonoTabs` FAV · MINE · POPULAR · NEW · COUNTRY · GENRE, the station list, and a persistent mini
player. Stage 3 of the Compose migration.

## Decided before this build (from fnr-bfxtd)

- Tab order FAV · MINE · POPULAR · NEW · COUNTRY · GENRE; MINE is the local radio stations list.
- The drawer goes; General, Source, Network, Buffering, Sleep timer, File storage, About move to
  the overflow menu and keep opening their existing dialogs.
- Search keeps its existing behaviour behind the top bar's search icon.
- targetSdk stays 34; the screen draws edge to edge itself.
- Android Auto and the media service contract do not change.

## Context found

- `MainActivity` (459 lines) wires `MediaPresenterImpl` (common-ui, 870 lines) to Views:
  `init(activity, mainLayout, bundle, RecyclerView, currentRadioStationView, MediaItemsAdapter, ...)`.
  The presenter owns the browse stack (`mMediaItemsStack`), `ShownNodeSubscription` (drops answers
  for nodes no longer on top), paging on scroll end (`ScrollListener.onScrolledToEnd`), list
  position memory (`mPositions`), the mini-player click (`CMD_TOGGLE_LAST_PLAYED_ITEM`),
  `RSSettingsDialog` for item settings, receivers, location, sleep timer and the artwork
  `ContentObserver` (which finds an `ImageView` by tag in the RecyclerView).
- `MediaPresenter` exposes View types (`View`, `TextView`, `RecyclerView`, `MediaItemsAdapter`) in
  its interface. It is used by MainActivity, `SearchDialog`, `RSSettingsDialog`,
  `GeneralSettingsDialog`, `AppCloseReceiver`, `MobileMediaItemsAdapter` and several journey
  tests. JVM tests: `MediaPresenterStackTest`, `...ShownNodeTest`, `...ChildrenLoadedTest`
  (512 lines with support).
- Root node (`MediaItemRoot`) lists Favorites (only if non-empty), New and Popular (Radio Browser
  only), Categories, Countries, "stations in my country" (if location known), Local stations (only
  if non-empty). The tabs map to `MEDIA_ID_FAVORITES_LIST`, `MEDIA_ID_LOCAL_RADIO_STATIONS_LIST`,
  `MEDIA_ID_POPULAR_STATIONS`, `MEDIA_ID_NEW_STATIONS`, `MEDIA_ID_COUNTRIES_LIST`,
  `MEDIA_ID_ALL_CATEGORIES`.
- WebRadioDB has no Popular or New node.
- Row today: swipe reveals a settings button (`RSSettingsDialog`: edit/remove for MINE, a sort
  position NumberPicker for FAV and MINE) and a favourite checkbox; tap plays or drills down.
- Mini player today: hidden until metadata arrives; tap toggles play/pause; tapping its image
  opens `RSSettingsDialog` for the current item; favourite checkbox.
- EQ is a top bar action today (`action_eq` → `EqualizerDialog`); it is not in the decided
  overflow list.
- Search: `SearchDialog` pushes `MEDIA_ID_SEARCH_FROM_APP` with the query onto the browse stack.
- No Now playing screen exists yet.
- console-design: `ConsoleTopBar`, `ConsoleIconButton`, `MonoTabs` (already scrolls horizontally,
  so six tabs fit a 360 dp screen), `SectionHeader`, `ConsoleFab`, `Tag`, `InitialsTile`,
  `GhostIconButton`, `PrimaryButton`. No station row or mini player component; those are app
  composables built from `InitialsTile`, `Tag`, `Console.type`.
- Reference `freenetradio-browse-{dark,light}.png`: title "Radio", search and overflow icons,
  five tabs, rows (tile, name, `CC · CODEC` mono, bitrate right in mono, heart; playing row has
  the accent tile and a tinted background), mini player (accent tile, bold name, `LIVE` tag +
  track in mono, square accent pause button). No FAB, no drill-down, no empty or loading state.
- DESIGN.md rules that bite here: dialogs only to confirm a destructive action (settings, sleep
  timer, EQ, source must become screens or sheets eventually); empty state is one line plus the
  FAB; one primary action per screen; root screens have no back arrow, sub-screens do.
- 17 instrumented journey files (4632 lines) drive the View UI by `R.id` (list_view,
  current_radio_station_view, crs_*, add_station_btn, nav_*, dialog_rs_settings_*).

## Constraints

- console-design rules (DESIGN.md); no edits under `console-design/`.
- No DI framework; `*Dependency` / `configureWith` pattern.
- Tests: hand-written fakes, no network, Roborazzi baselines in both themes.
- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass.

## Non-goals

- Now playing screen, settings screens, restyling the existing dialogs (later builds).
- Any change to `OpenRadioService`, the browse tree, `MediaId`s or Android Auto.
- New features in search (only its entry point moves).
- Changes under `console-design/`.

## Approaches

**A. Extract a UI-free browse state holder (recommended).** Move the browse stack, shown-node
filtering, paging, position memory and now-playing tracking out of `MediaPresenterImpl` into a
plain Kotlin class (working name `MainScreenModel`) in `:common-ui` that exposes one
`StateFlow<MainScreenState>` and plain action methods (`selectTab`, `open`, `back`, `loadMore`,
`toggleFavorite`, `togglePlayback`). `MainActivity` becomes `setContent { ConsoleTheme {
MainScreen(state, actions) } }`. View-only parts (RecyclerView, adapter, `findViewWithTag`,
`updateDescription(TextView)`) are deleted with their callers; the dialogs that took a
`MediaPresenter` get the narrow interface they actually use.
- For: the screen is a pure function of state, so Roborazzi can render every state from fixed
  data and the model is JVM-testable with the existing fakes; the existing presenter tests port
  to the model almost line for line; no View type survives in the main path.
- Against: the largest diff (presenter split, dialogs re-pointed, journey tests rewritten to
  Compose test tags); must keep `ShownNodeSubscription` semantics exactly.

**B. Keep `MediaPresenterImpl`, swap its View sinks for a callback interface.** Replace the
`RecyclerView`/adapter/`View` parameters with a `MediaItemsSink` the Compose layer implements and
turns into state.
- For: smaller diff; presenter and its tests stay mostly as they are.
- Against: the presenter keeps its View-era shape (adapter positions, scroll listener semantics,
  activity reference) behind a thinner seam; state lives in two places (presenter fields and
  Compose state); the interface still leaks UI concerns into `:common-ui`.

**C. Compose chrome around the existing RecyclerView (`AndroidView`).** Top bar, tabs and mini
player in Compose; the list stays a RecyclerView restyled with XML.
- For: fastest; journey tests that use list `R.id`s keep working.
- Against: the rows cannot use `InitialsTile`/`Console.type`, so they would not match the
  reference; leaves the View list behind, which fnr-bfxtd's "nothing replaced is left behind"
  rules out. Listed only for completeness.

Recommendation: **A**. B is the fallback if the human wants this build's diff kept small and the
presenter split deferred to the Now playing build.

## Design (answers applied; awaiting re-approval)

### 1. Structure (approach A)
- `MainScreenModel` (`:common-ui`, plain class, configured through a `MainScreenModelDependency`
  in `DependencyRegistryCommonUi`). Talks to the service through the existing
  `MediaResourcesManager`; keeps one browse stack per tab, so switching tabs keeps each tab's
  drill-down and scroll position.
- `MainScreenState`: selected tab, visible tabs, per-tab stack top (title, back allowed), items
  (`StationRowState` or `CategoryRowState`), load state (`Loading`, `Loaded`, `Empty`, `Error`),
  paging in progress, playing item id, `MiniPlayerState?`.
- `:app` composables: `MainScreen`, `StationRow`, `CategoryRow`, `MiniPlayer`, `OverflowMenu`,
  each previewed and screenshot-tested in both themes.
- `MainActivity` keeps lifecycle, permissions, the service connection and the dialog launches;
  it loses the drawer, toolbar, list and strip wiring.

### 2. Tabs and drill-down (Q1, Q6, Q8)
- Visible tabs follow the active provider: POPULAR and NEW are hidden for WebRadioDB (Q6 = a).
  FAV and MINE are always shown even when empty (the root node hides them; tabs do not).
- Tapping a COUNTRY or GENRE row pushes it on that tab's stack (Q1 = a): the top bar shows a back
  arrow and the country or genre name, tabs stay visible, system back pops; reselecting the
  current tab pops to its root.
- "Stations in my country" is kept as a section of COUNTRY (Q8 = b). When the root node offers
  the user's country item (`MEDIA_ID_COUNTRY_STATIONS`, present only when the country code is
  known and is not the default location, as `MediaItemRoot` decides today), the COUNTRY tab's
  root shows a `SectionHeader` "YOUR COUNTRY" with that one country row, then a `SectionHeader`
  "ALL COUNTRIES" above the full `MEDIA_ID_COUNTRIES_LIST` children. Without that item the tab
  shows the countries list alone, with no headers. Tapping the YOUR COUNTRY row drills down like
  any country row (back arrow, country name, tabs stay) by pushing the existing
  `MEDIA_ID_COUNTRY_STATIONS` node, so neither the browse tree nor the `MediaId`s change. The
  model reads the item from the root node's children; it does not compute the country itself.

### 3. Station row
- Per reference: `InitialsTile` (artwork in the same 38 dp box when present), name, `CC · CODEC`
  in mono, bitrate right in mono, outlined heart / filled heart for favourite. Playing row: active
  tile and tinted background. Tap plays. Long-press on a FAV or MINE row opens the existing
  station settings dialog (Q5 = a); other rows have no long-press action.

### 4. Mini player (Q2, Q3)
- Per reference when a station is playing or paused: active tile, name, `LIVE` tag + track title,
  square pause/play button.
- Hidden until a station is chosen; afterwards it stays, paused when playback stops. After a
  restart it shows the last played station paused (Q2 = a).
- Only its button acts; tapping elsewhere on it does nothing until the Now playing screen exists
  (Q3 = a). The current mini player's image tap (settings dialog) and favourite checkbox go; the
  favourite toggle stays on the row.

### 5. Loading, empty, error (Q4)
- First load shows a thin progress line under the tabs; paging shows it at the list end.
  Empty: one line of text ("No favourites yet", "No stations added" with the FAB on MINE,
  "Nothing found"). Error: one line plus a "Retry" text action (Q4 = a).

### 6. MINE station management (Q5)
- `ConsoleFab` "+ Add" on MINE only; it opens the existing add-station dialog for now (Q5 = a).
- Edit, remove and sort position stay in the existing station settings dialog, reached by
  long-press on a FAV or MINE row (it replaces today's swipe).

### 7. Top bar
- Title "Radio" as in the reference; search icon opens the existing `SearchDialog`; overflow:
  General, Source, Network, Buffering, Sleep timer, Equalizer (Q7 = a), File storage, About, each
  opening its existing dialog. No EQ icon in the top bar.

### 8. Removed in this build
- Drawer layout and menu, toolbar, `activity_main` list and current-station-strip layouts,
  `MobileMediaItemsAdapter`, `MediaItemsAdapter` and its view holders if nothing else uses them,
  the swipe helper, the View-typed `MediaPresenter` methods. The exact list is confirmed during
  planning by searching for remaining references.

### 9. Testing
- JVM: `MainScreenModel` tests ported from the three presenter tests plus tab switching, per-tab
  stacks, provider-dependent tabs, paging, error/retry; fakes only.
- JVM also covers the COUNTRY sections: with and without the root node's country item.
- Roborazzi: each tab loaded, playing row, mini player states, drill-down, COUNTRY with the YOUR
  COUNTRY section, loading, empty (FAV, MINE), error, in dark and light; browse baselines reviewed by the human against
  `freenetradio-browse-*.png`.
- Instrumented journeys rewritten from `R.id` lookups to Compose test tags, same journeys.

## Question queue (iteration 1; all answered, kept for the record)

**Q0 (visual companion, its own message):** "Some of what we're working on might be easier to
explain if I can show it to you in a web browser. I can put together mockups, diagrams,
comparisons, and other visuals as we go. This feature is still new and can be token-intensive.
Want to try it? (Requires opening a local URL)"

**Q1 (drill-down):** "When you tap a country in COUNTRY or a genre in GENRE, how should its
station list appear? (a) Same screen, tabs stay, top bar shows a back arrow and the country name;
back or re-tapping the tab returns to the list (recommended). (b) A separate sub-screen without
tabs, back arrow and name in the top bar. (c) Tabs stay and a `SectionHeader` row with the name
and a back icon sits above the list."

**Q2 (mini player, nothing played):** "What should the mini player show before anything has been
played? (a) Nothing: it appears once a station is chosen and stays, paused, afterwards; after a
restart it shows the last played station paused (recommended). (b) Always visible, with a
'Nothing playing' line and a disabled button. (c) Hidden whenever playback is stopped."

**Q3 (mini player tap):** "Until the Now playing screen exists, what does tapping the mini player
(outside its pause button) do? (a) Nothing; only the button acts (recommended). (b) Toggle
play/pause, as today. (c) Scroll the list to the playing station."

**Q4 (states):** "Loading, empty and error per tab: is this right? Loading = thin progress line
under the tabs; empty = one line ('No favourites yet', 'No stations added' + FAB, 'Nothing
found'); error = one line + 'Retry' text action. (a) Yes (recommended). (b) Different (say how)."

**Q5 (MINE add/edit/remove and row actions):** "How are add, edit and remove reached for MINE,
and what other per-row actions remain? (a) FAB '+ Add' on MINE opens the existing add dialog;
long-press on a row opens the existing station settings dialog (edit, remove, sort position) for
FAV and MINE, as swipe does today (recommended, keeps 'existing dialogs for now'). (b) Same FAB,
but long-press opens a bottom sheet with Edit, Remove (confirm dialog) and Move up/down. (c) Build
add/edit as Compose screens in this build."

**Q6 (provider-dependent tabs):** "With WebRadioDB as the source there is no POPULAR or NEW.
(a) Hide those two tabs (recommended). (b) Keep them with an empty-state line 'Not available
with WebRadioDB'."

**Q7 (equalizer):** "The EQ action is in today's toolbar but not in the decided overflow list.
(a) Add 'Equalizer' to the overflow, opening the existing dialog (recommended). (b) Keep it as a
top bar icon. (c) Drop it from the main screen until Now playing."

**Q8 ('stations in my country'):** "The root list has 'stations in my country' when the location
is known. (a) Drop it; the user finds their country in COUNTRY (recommended). (b) Put the user's
country first in COUNTRY under a 'YOUR COUNTRY' header."

**Approach confirmation:** "Approach A (extract a UI-free state holder from MediaPresenterImpl),
B (keep the presenter, swap its View sinks) or C (Compose around the RecyclerView)? A is
recommended."

## Answered questions

Recorded at the design gate on 2026-11-10, relayed verbatim from the human: "approve with Q8 = b,
Q0 = no, everything else as recommended".

- Approach: A (extract a UI-free state holder).
- Q0: no visual companion.
- Q1: a. Q2: a. Q3: a. Q4: a. Q5: a. Q6: a. Q7: a.
- Q8: b, differing from the recommendation: the user's country comes first in COUNTRY under a
  "YOUR COUNTRY" header (design section 2).

## Unresolved questions

None.

## Acceptance criteria (from fnr-bfxtd, plus the draft)

- The Compose screen replaces the View main screen; no drawer, toolbar, RecyclerView list or
  current-station strip remains, and the code only they used is deleted.
- Tabs in the order FAV · MINE · POPULAR · NEW · COUNTRY · GENRE; POPULAR and NEW hidden for
  WebRadioDB; FAV and MINE shown even when empty.
- Tapping a country or genre opens its stations on the same screen with tabs visible, a back
  arrow and the name in the top bar; system back pops, reselecting the tab returns to its root;
  each tab keeps its own drill-down and scroll position.
- COUNTRY's root shows "YOUR COUNTRY" with the user's country row, then "ALL COUNTRIES" above the
  list, exactly when the root node offers `MEDIA_ID_COUNTRY_STATIONS`; otherwise the plain list
  with no headers. Tapping that row drills down like any country.
- The mini player is hidden until a station is chosen, then stays (paused when stopped; last
  played station shown paused after a restart); only its button acts.
- Loading shows a thin progress line under the tabs (at the list end while paging); empty is one
  line (with "+ Add" FAB on MINE); error is one line plus "Retry".
- "+ Add" on MINE opens the existing add dialog; long-press on a FAV or MINE row opens the
  existing station settings dialog; other rows have no long-press action.
- The overflow menu holds General, Source, Network, Buffering, Sleep timer, Equalizer, File
  storage and About, each opening its existing dialog; the top bar has no EQ icon.
- Every state with a reference image matches it in dark and light (Roborazzi baselines reviewed
  by the human against `freenetradio-browse-*.png`); the rest follows DESIGN.md.
- Android Auto and the service contract unchanged.
- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass; the
  instrumented journeys pass on an offline, cleared emulator.
