---
schema: gc.build.requirements.v1
workflow:
  id: fnr-pi6ap
  formula: superpowers-lite-build
methodology:
  pack: superpowers
  name: superpowers-brainstorming
producer:
  formula: superpowers-brainstorming
  stage: write-requirements-spec
  attempt: 1
status: approved
trace:
  upstream:
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/brainstorming/design-candidate.md
      hash: sha256:1cfbc453bbe3cf1a9016fa426a05e2687bb6f1e4325a52043a71d5807613dc89
      ids: [REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011, REQ-012, REQ-013, REQ-014, REQ-015, REQ-016, REQ-017]
    - path: beads/fnr-bfxtd
      hash: bead:fnr-bfxtd
    - path: beads/fnr-ermlu
      hash: bead:fnr-ermlu
  coverage:
    - id: REQ-001
      status: covered
    - id: REQ-002
      status: covered
    - id: REQ-003
      status: covered
    - id: REQ-004
      status: covered
    - id: REQ-005
      status: covered
    - id: REQ-006
      status: covered
    - id: REQ-007
      status: covered
    - id: REQ-008
      status: covered
    - id: REQ-009
      status: covered
    - id: REQ-010
      status: covered
    - id: REQ-011
      status: covered
    - id: REQ-012
      status: covered
    - id: REQ-013
      status: covered
    - id: REQ-014
      status: covered
    - id: REQ-015
      status: covered
    - id: REQ-016
      status: covered
    - id: REQ-017
      status: covered
---

# Main screen in Compose (Console design): requirements

| Field | Value |
|-------|-------|
| Source bead | fnr-bfxtd (input convoy fnr-d4two) |
| Workflow root | fnr-pi6ap (`superpowers-lite-build`) |
| Target branch | `fnr/compose-shell` |
| Approved design | `/home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/compose-shell/brainstorming/design-candidate.md` |
| Design approval | gate fnr-ermlu, 2026-11-10: "approve with Q8 = b, Q0 = no, everything else as recommended" |
| Approach | A: extract a UI-free state holder (`MainScreenModel`) from `MediaPresenterImpl` |
| Repository mirror | none; see "Spec location" below |

### Spec location

This artifact is the spec. It is not mirrored under `docs/superpowers/specs/` in the repository:
the only checkout this lane can reach is `master`, which nothing but the owner's merge may move,
and the `commit-docs` step of this build commits lasting documents to `fnr/compose-shell`.

### Coverage

| ID | Requirement | Status |
|----|-------------|--------|
| REQ-001 | The Compose main screen replaces the View main screen | covered |
| REQ-002 | `MainScreenModel`: UI-free state holder in `:common-ui` | covered |
| REQ-003 | Tabs, order and provider-dependent visibility | covered |
| REQ-004 | Per-tab drill-down, back and position memory | covered |
| REQ-005 | COUNTRY: YOUR COUNTRY section | covered |
| REQ-006 | Station row content, tap and favourite | covered |
| REQ-007 | Long-press on FAV and MINE rows | covered |
| REQ-008 | Mini player | covered |
| REQ-009 | Loading, paging, empty and error states | covered |
| REQ-010 | MINE "+ Add" FAB | covered |
| REQ-011 | Top bar, search and overflow menu | covered |
| REQ-012 | Search results inside the tab stack | covered |
| REQ-013 | Dead View code removed | covered |
| REQ-014 | Visual conformance in dark and light | covered |
| REQ-015 | Android Auto and the service contract unchanged | covered |
| REQ-016 | Tests: JVM, Roborazzi and instrumented journeys | covered |
| REQ-017 | Build and verification commands pass | covered |

## Problem Statement

FreeNetRadio's main screen is built with Views: a navigation drawer, a toolbar, a `RecyclerView`
station list, a current-station strip and an add FAB, wired to Views by `MediaPresenterImpl`
(`:common-ui`), whose interface exposes `View`, `TextView`, `RecyclerView` and
`MediaItemsAdapter`. It does not follow the Console design that the app is moving to, and the
presenter's View-typed shape keeps browse state untestable without a device.

This build, stage 3 of the Compose migration, replaces that screen with one Compose screen in the
Console design: `ConsoleTopBar` with search and an overflow menu, `MonoTabs`
FAV · MINE · POPULAR · NEW · COUNTRY · GENRE, a station list and a persistent mini player. Browse
state moves into a UI-free `MainScreenModel` that exposes one `StateFlow`, so the screen is a
function of state that Roborazzi and JVM tests can drive with fixed data. Nothing the new screen
replaces is left in the code.

## W6H

- **Who:** phone and tablet users of FreeNetRadio. Android Auto users are unaffected.
- **What:** a Compose main screen (top bar, six tabs, station list, mini player) backed by a
  UI-free `MainScreenModel`; the drawer, toolbar, View list, current-station strip and their
  adapters, layouts and View-typed presenter methods are removed.
- **When:** this build, on the integration branch `fnr/compose-shell`. Now playing, the settings
  screens and restyling the remaining dialogs are later builds.
- **Where:** `MainScreenModel` and its state types in `:common-ui`; the composables and
  `MainActivity` in `:app`; screenshot tests in `app/src/test`; journeys in `app/src/androidTest`.
- **Why:** the app is moving to the Console design (`console-design/DESIGN.md`), and a state
  holder without View types makes the main screen testable on the JVM.
- **Which:** approach A of the approved design; the design questions Q1-Q8 answered as recorded
  at the gate (Q1-Q7 a, Q8 b).
- **How:** `MainActivity` calls `setContent { ConsoleTheme { MainScreen(state, actions) } }`; the
  model talks to `OpenRadioService` through the existing `MediaResourcesManager`, keeps one browse
  stack per tab, and is configured through a `MainScreenModelDependency` in
  `DependencyRegistryCommonUi`; the existing dialogs are launched as today.

## User Stories

- **US-1.** As a listener, I switch between FAV, MINE, POPULAR, NEW, COUNTRY and GENRE with one
  tap, and each tab is where I left it.
- **US-2.** As a listener, I open a country or a genre, see its stations on the same screen with
  the tabs still there, and go back with the top bar arrow, system back, or by tapping the tab
  again.
- **US-3.** As a listener whose country is known, I find my country first in COUNTRY under
  "YOUR COUNTRY".
- **US-4.** As a listener, I start a station by tapping its row, mark it favourite with the
  heart, and pause or resume it from the mini player at the bottom.
- **US-5.** As a listener who adds stations by hand, I add one with "+ Add" on MINE and edit,
  remove or reorder one by long-pressing its row.
- **US-6.** As a listener, I reach search, the settings dialogs, the sleep timer, the equalizer
  and About from the top bar, without a drawer.
- **US-7.** As a listener, I can tell when a list is loading, empty or failed, and retry a
  failed load.

## Technical Stories

- **TS-1.** As a maintainer, I test browse behaviour (stack, shown-node filtering, paging,
  per-tab state, provider-dependent tabs, error and retry) on the JVM with hand-written fakes,
  without a device and without View types.
- **TS-2.** As a maintainer, I render every main screen state in Roborazzi from fixed
  `MainScreenState` values, in dark and light.
- **TS-3.** As a maintainer, I find no View-era main screen code left: no drawer, toolbar, list
  adapter, swipe helper or View-typed presenter method without a remaining caller.
- **TS-4.** As a maintainer, I drive the instrumented journeys through Compose test tags instead
  of `R.id` lookups.
- **TS-5.** As a maintainer, I find the browse tree, the `MediaId`s, `OpenRadioService` and
  Android Auto unchanged.

## Behavior Requirements

### REQ-001: the Compose main screen replaces the View main screen

- `MainActivity` shows one Compose screen inside `ConsoleTheme`: `ConsoleTopBar`, `MonoTabs`,
  the list of the selected tab, and the mini player.
- `MainActivity` keeps lifecycle, permissions, the service connection, receivers and the dialog
  launches; it no longer wires a drawer, toolbar, `RecyclerView` or current-station strip.
- The screen draws edge to edge itself; `targetSdk` stays 34.
- Recreating the activity (rotation, switching between dark and light) keeps the selected tab
  and that tab's drill-down stack, as today's presenter keeps the current parent id. Scroll
  positions and the other tabs' stacks are not required to survive recreation.

### REQ-002: `MainScreenModel`

- A plain Kotlin class in `:common-ui`, with no Android View, `Activity` or Compose type in its
  public API. It exposes one `StateFlow<MainScreenState>` and plain action methods; the design's
  working set is `selectTab`, `open`, `back`, `loadMore`, `toggleFavorite`, `togglePlayback`,
  plus whatever retry needs. Names may change in planning; the shape may not.
- It is configured through a `MainScreenModelDependency` (`configureWith(...)`) in
  `DependencyRegistryCommonUi`, with no DI framework.
- It owns what `MediaPresenterImpl` owns today for the main screen: the browse stack (one per
  tab), shown-node filtering (`ShownNodeSubscription` semantics: an answer for a node that is no
  longer on top of its stack is dropped), paging on reaching the list end, position memory, and
  now-playing tracking.
- `MainScreenState` holds at least: the selected tab, the visible tabs, the top of the selected
  tab's stack (title, whether back is allowed), the items (station rows and category rows), the
  load state (`Loading`, `Loaded`, `Empty`, `Error`), whether a page is loading, the playing
  item's id, and the mini player state or its absence.
- Dialogs and receivers that took a `MediaPresenter` (`SearchDialog`, `RSSettingsDialog`,
  `GeneralSettingsDialog`, `AppCloseReceiver`, and any other caller found in planning) depend on
  the narrow interface they use instead.

### REQ-003: tabs

- Tabs in this order: FAV · MINE · POPULAR · NEW · COUNTRY · GENRE, mapped to
  `MEDIA_ID_FAVORITES_LIST`, `MEDIA_ID_LOCAL_RADIO_STATIONS_LIST`, `MEDIA_ID_POPULAR_STATIONS`,
  `MEDIA_ID_NEW_STATIONS`, `MEDIA_ID_COUNTRIES_LIST` and `MEDIA_ID_ALL_CATEGORIES`.
- With WebRadioDB as the active provider, POPULAR and NEW are hidden (the provider has no such
  nodes). With Radio Browser, all six are shown.
- FAV and MINE are always shown, even when empty, although the root node omits them when empty.
- The tabs scroll horizontally where they do not fit, as `MonoTabs` already does.

### REQ-004: drill-down, back and position memory

- Tapping a country row in COUNTRY or a genre row in GENRE pushes that node onto the selected
  tab's stack. The top bar then shows a back arrow and the country or genre name; the tabs stay
  visible.
- The top bar back arrow and system back pop one level. At a tab's root the top bar has no back
  arrow, and system back leaves the screen as it does today.
- Tapping the already selected tab pops its stack to the root.
- Each tab keeps its own stack. Each stack level remembers its scroll position, restored when
  the user comes back to it by back or by switching tabs.
- A category row shows the category's name (and, for countries, its flag where the item has
  one), carries no heart, plays nothing, and has no long-press action. Its styling follows
  DESIGN.md's list rules and is fixed by the Roborazzi baselines the human reviews.

### REQ-005: COUNTRY, YOUR COUNTRY section

- When the root node's children include the user's country item (`MEDIA_ID_COUNTRY_STATIONS`,
  which `MediaItemRoot` adds only when the country code is non-empty and
  `LocationService.isDefaultLocationEnabled` is false), COUNTRY's root shows a `SectionHeader`
  "YOUR COUNTRY" with that one country row, then a `SectionHeader` "ALL COUNTRIES" above the
  children of `MEDIA_ID_COUNTRIES_LIST`.
- Without that item, COUNTRY's root shows the countries list alone, with no headers.
- The model takes the item from the root node's children; it does not determine the country
  itself.
- Tapping the YOUR COUNTRY row pushes `MEDIA_ID_COUNTRY_STATIONS` and drills down like any
  country row (REQ-004).
- The user's country also keeps its own row in ALL COUNTRIES; the duplication is intended.
- Neither the browse tree nor the `MediaId`s change.

### REQ-006: station row

- As in `freenetradio-browse-*.png`: an `InitialsTile` (the station's artwork in the same 38 dp
  box when it has one), the name, `CC · CODEC` in mono, the bitrate on the right in mono, and a
  heart, outlined when not favourite and filled when favourite.
- The playing station's row has the active tile and the tinted background.
- Tapping the row plays the station. Tapping the heart toggles the favourite and does not play.
- Artwork that arrives after the row is shown updates the row through state, not by looking up
  an `ImageView` by tag.

### REQ-007: long-press

- Long-press on a FAV or MINE station row opens the existing station settings dialog
  (`RSSettingsDialog`: edit and remove for MINE, sort position for FAV and MINE). It replaces
  today's swipe.
- Long-press on any other row does nothing.

### REQ-008: mini player

- Hidden until a station is chosen. Afterwards it stays visible; when playback stops it shows
  the station paused.
- After an app restart, it shows whatever the playback service holds, which the "last known
  station" setting decides: with the setting on (the default), the service restores the last
  played station and resumes it, and the mini player shows that station playing; with it off,
  the mini player stays hidden. Amended by the owner on 2026-11-10 (decision 14, `fnr-yn3gp`):
  showing the station paused would need a change to the service, which REQ-015 rules out.
- Content as in the reference: the active tile, the station's name in bold, a `LIVE` tag
  followed by the track title in mono, and a square accent pause/play button.
- Only the button acts: it toggles playback (resuming the last played station when paused).
  Tapping anywhere else on the mini player does nothing until a Now playing screen exists.
- Today's mini player image tap (settings dialog) and favourite checkbox are not carried over;
  the favourite toggle stays on the row (REQ-006).

### REQ-009: loading, paging, empty and error

- First load of a list: a thin progress line under the tabs.
- Loading the next page: the progress line at the end of the list.
- Empty list: one line of text. FAV: "No favourites yet". MINE: "No stations added", with the
  "+ Add" FAB (REQ-010). Any other tab, drill-down or search result: "Nothing found".
- Failed load: one line of error text and a "Retry" text action that reloads the node on top of
  the selected tab's stack.

### REQ-010: MINE "+ Add" FAB

- A `ConsoleFab` "+ Add" is shown on MINE only, empty or not, and on no other tab.
- It opens the existing `AddStationDialog`.

### REQ-011: top bar and overflow menu

- At a tab's root the top bar title is "Radio", as in the reference. It carries a search icon
  and an overflow icon; there is no equalizer icon.
- The search icon opens the existing `SearchDialog`.
- The overflow menu lists, in this order: General, Source, Network, Buffering, Sleep timer,
  Equalizer, File storage, About. Each opens its existing dialog (`GeneralSettingsDialog`,
  `SourceDialog`, `NetworkDialog`, `StreamBufferingDialog`, `SleepTimerDialog`,
  `EqualizerDialog`, `FileStorageDialog`, `AboutDialog`).

### REQ-012: search results

- Search keeps its behaviour: submitting a query in `SearchDialog` opens
  `MEDIA_ID_SEARCH_FROM_APP` with that query, as today.
- The result is pushed onto the selected tab's stack and behaves like a drill-down (REQ-004):
  the top bar shows a back arrow and the query, the tabs stay visible, and back returns to where
  the user searched from.

### REQ-013: dead View code removed

- Removed: the drawer layout and its menu, the toolbar, the main list and current-station strip
  layouts, `MobileMediaItemsAdapter`, the swipe helper, the View-typed methods of
  `MediaPresenter`, and `MediaItemsAdapter` and its view holders unless something outside the
  main screen still uses them. Resources (layouts, menus, ids, strings, drawables) used only by
  the removed code go with it.
- Planning confirms the list by searching for remaining references; anything kept is kept
  because a named caller outside the main screen still uses it.

### REQ-014: visual conformance

- Every state that has a reference image (`console-design/design-reference/png/freenetradio-browse-dark.png`
  and `-light.png`: a loaded station list with a playing row and the mini player) matches it in
  dark and light. The human reviews those Roborazzi baselines against the reference images.
- Every other state follows `console-design/DESIGN.md`. Nothing under `console-design/` is
  edited.

### REQ-015: Android Auto and the service contract

- `OpenRadioService`, the browse tree (`MediaItemRoot`, `MediaItemRootCar`, `MediaItemBrowseCar`
  and the other `MediaItemCommand`s), the `MediaId`s and the Android Auto experience do not
  change. The screen remains one more MediaBrowser client of the service.

### REQ-016: tests

- JVM tests for `MainScreenModel`, with hand-written fakes and no network: the cases of
  `MediaPresenterStackTest`, `MediaPresenterShownNodeTest` and `MediaPresenterChildrenLoadedTest`
  ported to the model, plus tab switching, per-tab stacks and positions, provider-dependent tabs,
  paging, empty, error and retry, COUNTRY with and without the root node's country item, and
  search pushed onto the selected tab.
- Roborazzi screenshots in dark and light of: each tab loaded, the playing row, the mini player
  playing and paused, a drill-down, COUNTRY with the YOUR COUNTRY section and without it,
  loading, empty FAV, empty MINE with the FAB, error, and the overflow menu open.
- The instrumented journeys under `app/src/androidTest` that drove the View main screen by
  `R.id` cover the same journeys through Compose test tags. A journey whose subject no longer
  exists (the drawer, swipe, the mini player's image tap and checkbox) is rewritten for its
  replacement (overflow menu, long-press, row heart) or removed when there is none.

### REQ-017: build and verification

- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass
  (including `verifyRoborazziDebug` and `verifyPureCoreCoverage`).
- `./gradlew :app:connectedDebugAndroidTest` passes on an emulator with networking disabled and
  the app's data cleared, as AGENTS.md describes.

## Example Mapping

| Rule | Example | Expected |
|------|---------|----------|
| REQ-003 tab visibility | Provider Radio Browser | Tabs FAV, MINE, POPULAR, NEW, COUNTRY, GENRE |
| REQ-003 tab visibility | Provider WebRadioDB | Tabs FAV, MINE, COUNTRY, GENRE |
| REQ-003 tab visibility | No favourites, no local stations | FAV and MINE still shown |
| REQ-004 drill-down | COUNTRY, tap "Poland" | Back arrow and "Poland" in the top bar, Poland's stations, tabs visible |
| REQ-004 back | In "Poland", system back | COUNTRY root at its previous scroll position |
| REQ-004 reselect | In GENRE › "Jazz", tap GENRE | GENRE root |
| REQ-004 per-tab stacks | In COUNTRY › "Poland", switch to FAV, then back to COUNTRY | Still in "Poland", same scroll position |
| REQ-005 YOUR COUNTRY | Country code "PL", default location off | "YOUR COUNTRY" with Poland, then "ALL COUNTRIES" with every country, Poland included |
| REQ-005 no section | Country code unknown, or default location on | Countries list alone, no headers |
| REQ-005 drill-down | Tap the YOUR COUNTRY row | Same drill-down as tapping Poland in ALL COUNTRIES |
| REQ-006 row tap | Tap a station row | Station plays; row gets the active tile and tint; mini player appears |
| REQ-006 heart | Tap the heart on a station that is not favourite | Heart filled, station in FAV, playback unchanged |
| REQ-007 long-press | Long-press a MINE row | Station settings dialog with edit, remove, sort position |
| REQ-007 long-press | Long-press a POPULAR row | Nothing |
| REQ-008 first start | Fresh install, nothing played | No mini player |
| REQ-008 restart | Station played, app killed, reopened, last known station on | Mini player shows that station playing |
| REQ-008 restart, setting off | As above, last known station off | No mini player |
| REQ-008 tap outside button | Tap the mini player's name | Nothing |
| REQ-009 empty | FAV with no favourites | "No favourites yet" |
| REQ-009 empty | MINE with no stations | "No stations added" and "+ Add" |
| REQ-009 error | POPULAR load fails | One error line and "Retry"; Retry reloads POPULAR |
| REQ-010 FAB | MINE with three stations | "+ Add" shown; on other tabs it is not |
| REQ-011 overflow | Tap overflow, then "Equalizer" | Existing `EqualizerDialog` opens |
| REQ-012 search | On GENRE, search "rock" | Results on GENRE's stack with back arrow and "rock"; back returns to GENRE |
| REQ-001 recreation | In COUNTRY › "Poland", rotate | Still COUNTRY › "Poland" |

## Acceptance Criteria

1. The Compose screen replaces the View main screen; no drawer, toolbar, `RecyclerView` list or
   current-station strip remains, and the code only they used is deleted (REQ-001, REQ-013).
2. Browse state lives in `MainScreenModel` in `:common-ui`, exposed as one `StateFlow`, with no
   View type in its API, configured through `DependencyRegistryCommonUi`, and keeps
   `ShownNodeSubscription` semantics (REQ-002).
3. Tabs are FAV · MINE · POPULAR · NEW · COUNTRY · GENRE; POPULAR and NEW are hidden for
   WebRadioDB; FAV and MINE are shown even when empty (REQ-003).
4. Tapping a country or genre opens its stations on the same screen with tabs visible, a back
   arrow and the name in the top bar; back pops one level, reselecting the tab returns to its
   root, and each tab keeps its own drill-down and scroll position (REQ-004).
5. COUNTRY's root shows "YOUR COUNTRY" with the user's country row, then "ALL COUNTRIES" above
   the list, exactly when the root node offers `MEDIA_ID_COUNTRY_STATIONS`; otherwise the plain
   list with no headers. Tapping that row drills down like any country (REQ-005).
6. Station rows match the reference (tile or artwork, name, `CC · CODEC`, bitrate, heart;
   playing row tinted); tap plays, the heart toggles the favourite (REQ-006).
7. Long-press on a FAV or MINE row opens the existing station settings dialog; other rows have
   no long-press action (REQ-007).
8. The mini player is hidden until a station is chosen, then stays (paused when stopped; after a
   restart it shows what the service restored under the last known station setting); only its button acts (REQ-008).
9. Loading shows a thin progress line under the tabs (at the list end while paging); empty is
   one line (with "+ Add" on MINE); error is one line plus "Retry" (REQ-009).
10. "+ Add" appears on MINE only and opens the existing add dialog (REQ-010).
11. The overflow menu holds General, Source, Network, Buffering, Sleep timer, Equalizer, File
    storage and About, each opening its existing dialog; search opens `SearchDialog`; the top bar
    has no equalizer icon (REQ-011).
12. Search results open on the selected tab's stack as a drill-down titled with the query
    (REQ-012).
13. Every state with a reference image matches it in dark and light, the human having reviewed
    the Roborazzi baselines against `freenetradio-browse-*.png`; the rest follows DESIGN.md;
    nothing under `console-design/` changed (REQ-014).
14. Android Auto, `OpenRadioService`, the browse tree and the `MediaId`s are unchanged (REQ-015).
15. The JVM, Roborazzi and instrumented tests listed in REQ-016 exist and pass.
16. `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass, and
    the instrumented journeys pass on an offline, cleared emulator (REQ-017).

## Out Of Scope

- A Now playing screen; what the mini player's body tap will open.
- Settings screens; restyling `RSSettingsDialog`, the add and edit station dialogs, or the
  drawer-era dialogs now reached from the overflow menu.
- New search features; only search's entry point and where its results appear change.
- Any change to `OpenRadioService`, the browse tree, the `MediaId`s or Android Auto.
- Edits under `console-design/`, including updating DESIGN.md's five-tab list.
- A browser-based visual companion for mockups (declined, Q0 = no).
- Raising `targetSdk` above 34.

## Open Questions

None block planning. Two points the approved design leaves implicit are settled here as the
closest reading of it; the owner can overrule either when approving this spec:

- **Search results with per-tab stacks (REQ-012).** The design keeps search's existing
  behaviour, which pushes the search node onto the single browse stack. With one stack per tab,
  this spec pushes it onto the selected tab's stack.
- **State kept across activity recreation (REQ-001).** Today the presenter restores only the
  current parent id. This spec keeps the selected tab and its stack, and does not require scroll
  positions or other tabs' stacks to survive recreation.

The design candidate cites `MediaItemRoot.kt:239-242` for the country item; the code is at
`MediaItemRoot`'s `buildCountryMenuItem` call (lines 54-55 on `master` 2eb80f3). This spec and
planning cite it by name.
