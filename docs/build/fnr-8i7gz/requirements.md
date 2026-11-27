---
schema: gc.build.requirements.v1
workflow:
  id: fnr-8i7gz
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
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/brainstorming/design-candidate.md
      hash: sha256:7fece371060ea41fe195b912ae380cde2f5e37373e20ecb38941e61bfc2a7869
      ids: [REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011, REQ-012, REQ-013, REQ-014, REQ-015, REQ-016, REQ-017, REQ-018, REQ-019, REQ-020]
    - path: beads/fnr-qdkv7
      hash: bead:fnr-qdkv7
    - path: beads/fnr-2ewr1
      hash: bead:fnr-2ewr1
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
    - id: REQ-018
      status: covered
    - id: REQ-019
      status: covered
    - id: REQ-020
      status: covered
---

# Now playing screen in Compose (Console design): requirements

| Field | Value |
|-------|-------|
| Source bead | fnr-qdkv7 (input convoy fnr-cv3dn) |
| Workflow root | fnr-8i7gz (`superpowers-lite-build`) |
| Target branch | `fnr/now-playing` |
| Approved design | `/home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/now-playing/brainstorming/design-candidate.md` |
| Design approval | gate fnr-2ewr1, 2026-11-18 19:39Z: "approve, all recommended" (Q0 no; Q1-Q8 a; D1-D7 accepted) |
| Spec approval | gate fnr-mixmt, 2026-11-18 20:11Z: "approve" (O1-O5 as written) |
| Approach | A: a second screen in `MainActivity`'s Compose tree, sharing `MainScreenModel`, with playback split into `NowPlayingTracker` |
| Repository mirror | none; see "Spec location" below |

### Spec location

This artifact is the spec. It is not mirrored under `docs/superpowers/specs/` in the repository:
the only checkout this lane can reach is `master`, which nothing but the owner's merge may move,
and the `commit-docs` step of this build commits lasting documents to `fnr/now-playing`.

### Coverage

| ID | Requirement | Status |
|----|-------------|--------|
| REQ-001 | The mini player opens Now playing | covered |
| REQ-002 | Now playing is a screen inside `MainActivity`'s Compose tree | covered |
| REQ-003 | Leaving Now playing | covered |
| REQ-004 | Screen layout | covered |
| REQ-005 | `NowPlayingTracker`: playback state split out of `MainScreenModel` | covered |
| REQ-006 | Pause/play on Now playing | covered |
| REQ-007 | Favourite on Now playing, in agreement with every list | covered |
| REQ-008 | Overflow menu | covered |
| REQ-009 | Copy stream URL | covered |
| REQ-010 | Share stream URL | covered |
| REQ-011 | Sleep timer and Equalizer from Now playing | covered |
| REQ-012 | Station details sub-screen | covered |
| REQ-013 | Home page carried on the station's media item | covered |
| REQ-014 | Screen states | covered |
| REQ-015 | Closing when the current station goes away | covered |
| REQ-016 | Replaced mini player behaviour and nothing left behind | covered |
| REQ-017 | Visual conformance and the agreed differences D1-D7 | covered |
| REQ-018 | Android Auto and the service contract | covered |
| REQ-019 | Tests: JVM, Roborazzi and instrumented journeys | covered |
| REQ-020 | Build and verification commands pass | covered |

## Problem Statement

Since stage 3 of the Compose migration (fnr-bfxtd, merged into `master` at 612caf5), the main
screen has a mini player whose only active part is its pause/play button (REQ-008 of
`docs/build/fnr-pi6ap/requirements.md`). Nothing leads to a player screen, and the only way to
favourite the station that is playing is to find it again in a list and tap its heart. The
station's details (country, genre, stream format, stream URL, home page) are not shown anywhere,
and there is no way to copy or share the stream URL.

This build, stage 4 of the Compose migration, adds a Compose Now playing screen in the Console
design, opened by tapping the mini player outside its button. It has a pause/play button and a
favourite button; every other action is in a three-dot overflow menu, which more actions will
join later. The playback state moves out of `MainScreenModel` into a `NowPlayingTracker` that the
mini player, the station lists and Now playing all read, so they agree without syncing code.

## W6H

- **Who:** phone and tablet users of FreeNetRadio. Android Auto users are unaffected.
- **What:** a Now playing screen (top bar with close chevron and overflow, artwork, station name,
  track line, Pause/Play and favourite buttons), a Station details sub-screen, overflow actions
  (Station details, Copy stream URL, Share stream URL, Sleep timer, Equalizer), a
  `NowPlayingTracker` split out of `MainScreenModel`, and one added home page extra on the
  station's media item.
- **When:** this build, on the integration branch `fnr/now-playing`. Compose screens for the
  sleep timer and the equalizer are a later build.
- **Where:** `NowPlayingTracker`, the state types and `MainScreenModel` in `:common-ui`; the home
  page extra in `MediaItemBuilder` and `MediaItemHelper` in `:common`; the composables and
  `MainActivity` in `:app`; screenshot tests in `app/src/test`; journeys in `app/src/androidTest`.
- **Why:** the owner's decisions in fnr-qdkv7: the mini player should lead somewhere, the playing
  station should be favouritable where it plays, and its details and stream URL should be
  reachable.
- **Which:** approach A of the approved design, with the recommended answers to Q1-Q8 and the
  differences D1-D7 as accepted at gate fnr-2ewr1.
- **How:** `MainScreenState` gains a Now playing part and the flag that it is open;
  `MainActivity`'s `setContent` shows `MainScreen` or `NowPlayingScreen` from that state; no
  navigation library is added; the existing dialogs are launched as the main screen's overflow
  launches them.

## User Stories

- **US-1.** As a listener, I tap the mini player and see what is playing on a full screen; I
  tap its button and playback toggles without leaving the list.
- **US-2.** As a listener, I pause, resume and favourite the playing station from Now playing,
  and the mini player and the lists show the same state when I go back.
- **US-3.** As a listener, I open Station details to see the station's country, genre, stream
  format, stream URL and home page, and open the home page in my browser.
- **US-4.** As a listener, I copy the stream URL to the clipboard or share it to another app.
- **US-5.** As a listener, I set the sleep timer or the equalizer from Now playing without going
  back to the main screen.
- **US-6.** As a listener, I leave Now playing with the chevron or system back and find the main
  screen exactly where I left it.
- **US-7.** As a listener, I can tell when the stream is buffering or has failed, and the screen
  still works for a station without artwork or a track title.

## Technical Stories

- **TS-1.** As a maintainer, I test playback state (metadata, playback state, cold start,
  toggle, the current station's favourite) in `NowPlayingTracker` on the JVM with hand-written
  fakes.
- **TS-2.** As a maintainer, I render every Now playing and Station details state in Roborazzi
  from fixed state values, in dark and light.
- **TS-3.** As a maintainer, I find one source of playback and favourite state shared by the mini
  player, the lists and Now playing, and no playback fields left in `MainScreenModel`.
- **TS-4.** As a maintainer, I find the browse tree, the `MediaId`s, the service commands and
  Android Auto unchanged; the only service-side change is one additive media item extra.
- **TS-5.** As a maintainer, I check the share and browser launches in instrumented tests through
  a recording seam, without a real target app.

## Behavior Requirements

### REQ-001: the mini player opens Now playing

- Tapping the mini player anywhere outside its pause/play button opens Now playing for the
  current station (`MainScreenActions.onOpenNowPlaying`, or the name planning gives it).
- The pause/play button keeps toggling playback and does not open Now playing; its click does
  not reach the row.
- The mini player gains no favourite button.

### REQ-002: a screen inside `MainActivity`'s Compose tree

- Now playing is shown inside `MainActivity`'s existing Compose tree, in `ConsoleTheme`, in place
  of the main screen's content. No activity, fragment or navigation library is added.
- Whether Now playing is open is part of the model's state (`showNowPlaying` or the name
  planning gives it). `MainActivity` chooses between `MainScreen` and `NowPlayingScreen` from
  that one value.
- Station details (REQ-012) is a level inside Now playing, held in the same state.
- Recreating the activity (rotation, switching between dark and light) keeps Now playing open,
  and Station details open when it was.
- The main screen's selected tab, tab stacks and scroll positions are not touched while Now
  playing is open.

### REQ-003: leaving Now playing

- The top bar's navigation icon is a down chevron with the content description "Close player",
  as in the reference (Q5 = a; difference D4).
- The chevron and system back both close Now playing and show the main screen with its tab,
  stack and scroll position as they were.
- System back on Station details returns to Now playing, not to the main screen.
- The mini player is not shown while Now playing or Station details is open (Q6 = a; difference
  D7). It is shown again on return to the main screen.

### REQ-004: screen layout

From top to bottom:

- `ConsoleTopBar`: the chevron (REQ-003), the dimmed title "Now playing", the overflow icon.
- An artwork box at the full content width, 200 dp high, on `surface1`, with small corners. It
  shows the station's artwork through the existing `ArtworkCache` when the station has artwork,
  and otherwise the station's initials in large accent mono text.
- The station name in the `Console.type` screen-title style.
- The track line: the stream's track text exactly as the stream sends it, on one line in one
  weight. It is not split into a bold title and an artist (difference D6).
- A button row: a `PrimaryButton` Pause or Play with its icon, at the reference width, then a
  `GhostIconButton` heart, filled in accent when the station is a favourite and outlined
  otherwise, with the content description "Remove from favourites" or "Add to favourites". The
  row holds nothing else; the space where the reference has moon and EQ buttons stays empty,
  and the row is left-aligned (difference D1).
- Nothing below the button row: no `KeyValueTable` on this screen (difference D2).

### REQ-005: `NowPlayingTracker`

- A plain Kotlin class in `:common-ui` (working name `NowPlayingTracker`), with no Android View,
  `Activity` or Compose type in its public API.
- It owns what `MainScreenModel` owns for playback today: the current item, `mPlayingId`, the
  mini player state, the metadata and playback state handling from
  `MediaResourceManagerListener.onPlaybackStateChanged` and `onMetadataChanged`, the cold-start
  restore from `LastPlayedStation` (today's `showWhatTheServiceHolds()`), and `togglePlayback()`
  (`CMD_TOGGLE_LAST_PLAYED_ITEM`, or `play()` when the player is empty).
- It also provides what Now playing and Station details show: name, initials, artwork, track,
  playing and buffering flags, the current station's favourite state, and the station details
  fields (REQ-012) with the stream URL.
- `MainScreenModel` keeps the browse stacks, delegates playback to the tracker, and builds
  `MiniPlayerState` and the new Now playing state into `MainScreenState` from it.
- The tracker is constructed and wired without a DI framework, following the registry and
  `*Dependency` / `configureWith(...)` pattern of the surrounding code.
- `MainScreenModel`'s playback fields and their tests move to the tracker; nothing duplicates them
  in the old place (REQ-016).

### REQ-006: pause/play on Now playing

- The Pause/Play button calls the same `togglePlayback()` as the mini player's button.
- Its label and icon follow the player: Pause while the player plays or buffers with
  play-when-ready, Play otherwise.
- After a toggle on Now playing, the mini player shows the same state on return, and vice versa.

### REQ-007: favourite on Now playing

- The heart toggles the current station's favourite both ways, sending `CMD_MARK_FAVORITE` or
  `CMD_UNMARK_FAVORITE` as the list hearts do.
- It works whether or not the current station is in the list shown on the main screen:
  `toggleFavorite` gains a path for the current item.
- It uses the same favourite override map as the list hearts, keyed by the normalized media id
  (`MediaId.normalizeFromSearchId`), so the heart on Now playing, the station's row in every list
  showing it, and the FAV tab agree at once, and the service's next answer wins, as today.
- A heart tapped in a list for the current station shows on Now playing too.

### REQ-008: overflow menu

- The overflow icon opens a menu with, in this order: **Station details**, **Copy stream URL**,
  **Share stream URL**, **Sleep timer**, **Equalizer** (difference D3).
- Copy stream URL and Share stream URL are not offered when the current station has no stream
  URL. The other three are always offered.

### REQ-009: copy stream URL

- Copy stream URL puts the station's listed stream URL on the clipboard as plain text: the URL
  the station was listed or added with, not a URL resolved from its playlist.
- On Android 12L (API 32) and lower, where the system shows no clipboard confirmation, the app
  shows a short `Toast` "Stream URL copied". On Android 13 and later it shows nothing of its own.

### REQ-010: share stream URL

- Share stream URL opens an `ACTION_SEND` chooser with the stream URL (the same one as REQ-009)
  as `EXTRA_TEXT` and the station name as `EXTRA_SUBJECT`, type `text/plain`.
- The intent carries nothing else. It is sent only when the user picks the item.

### REQ-011: sleep timer and Equalizer from Now playing

- Sleep timer and Equalizer open the existing `SleepTimerDialog` and `EqualizerDialog`, as the
  main screen's overflow does (difference D5).
- Both stay in the main screen's overflow menu, unchanged.
- Closing either dialog leaves Now playing open.

### REQ-012: Station details sub-screen

- Opened from the overflow's Station details item (Q1 = c, a sub-screen). It shows a `ConsoleTopBar`
  with a back arrow and the station name, then a `KeyValueTable`. The back arrow and system back
  return to Now playing.
- Rows, in this order, each shown only when the station has a non-empty value for it (Q2 = a):
  - **COUNTRY**: the country name.
  - **GENRE**: the genre.
  - **STREAM**: codec and bitrate as `MP3 · 192 kbps`; the codec alone or the bitrate alone when
    only one is known.
  - **URL**: the stream URL (the same one as REQ-009), in mono.
  - **WEB**: the home page's host, as a link row in accent. Tapping it opens the full home page
    URL with `ACTION_VIEW`.
- There are no BUFFER, SLEEP or EQ rows (difference D2).
- A station with none of these values shows the top bar and an empty table area, with no error.

### REQ-013: home page on the station's media item

- `MediaItemBuilder.buildPlayable` writes the station's `homePage` into a new `KEY_HOME_PAGE`
  extra when it is non-empty, and `MediaItemHelper` reads it back, as it does the bitrate extra.
- If planning finds that `MediaController.currentMediaItem` in the app's process does not carry
  the stream URI (`localConfiguration`), the stream URL travels in a second added extra in the
  same way. Either extra is additive: the ids, the browse tree, the commands and what Android
  Auto shows do not change.
- On a cold start, when the service holds no current item and the screen shows the station
  restored from `LastPlayedStation` (REQ-014), the details and the stream URL come from that
  restored `RadioStation`.

### REQ-014: screen states

- **Playing:** Pause, as in the reference.
- **Paused or stopped:** Play.
- **Buffering** (the player buffering with play-when-ready): a thin accent progress line under
  the top bar, the same component the main screen uses for loading, and the button shows Pause.
  The line goes when the player leaves buffering.
- **Playback error:** the service already writes its message (`media_stream_*` strings such as
  "Network Failed", "Forbidden", "Unplayable Stream") into the track text; Now playing shows it in the track line, and the
  button follows the player (Play once it stops). No other error UI and no service change.
- **No artwork:** the initials in the artwork box (REQ-004), as in the reference.
- **No track text:** the track line is left out and the button row moves up.
- **Cold start** (the last station restored, paused, the start-playback setting not resuming
  it): Now playing shows that station paused; Play resumes it, as the mini player's button does.

### REQ-015: closing when the current station goes away

- When the mini player would go down (no current station and nothing to restore), when the app
  data is cleared, or when the model's `close()` runs, Now playing and Station details close and
  the main screen is shown.
- Switching to another station while Now playing is open updates the screen to that station and
  keeps it open; Station details, if open, shows the new station's rows.

### REQ-016: replaced behaviour, nothing left behind

- REQ-008 of the stage 3 requirements ("Only the button acts ... Tapping anywhere else on the mini
  player does nothing until a Now playing screen exists") is replaced by REQ-001.
- The `MiniPlayer` KDoc that says the rest of the mini player does nothing is updated, and the
  instrumented journey that asserts a tap outside the button does nothing is changed to assert
  that it opens Now playing.
- No playback field, method or test is left in `MainScreenModel` alongside its copy in
  `NowPlayingTracker`.

### REQ-017: visual conformance

- Every Now playing state with a reference image matches it in dark and light: playing, a
  station without artwork, favourite, track text present
  (`console-design/design-reference/png/freenetradio-now-playing-dark.png` and `-light.png`),
  apart from the agreed differences from the reference: D1 (no moon or EQ button), D2 (no table)
  and D6 (one-weight track line). The owner reviews those Roborazzi baselines against the reference images.
- Every other state and the Station details sub-screen follow `console-design/DESIGN.md`, as
  agreed in D1-D7.
- Nothing under `console-design/` is edited. The DESIGN.md line naming the chevron for the player
  screen (Q5 = a) is a change for the console-design repository; the build files it as a bead
  labelled `needs-human` with a `discovered-from` edge to fnr-qdkv7, so the owner carries it to
  that repository.

### REQ-018: Android Auto and the service contract

- `OpenRadioService`, the browse tree and its `MediaItemCommand`s, the `MediaId`s, the service
  commands and the Android Auto experience do not change. The only service-side change is the
  additive media item extra (or extras) of REQ-013.

### REQ-019: tests

- JVM tests in `:common-ui`, with hand-written fakes and no network:
  - `NowPlayingTracker`: metadata updates, playback state (playing, paused, buffering), the error
    text arriving as track text, cold start with and without a restorable station, toggle with
    and without a current item, the current station's favourite toggled when it is and is not in
    the shown list, the override shared with list rows, and normalized ids.
  - `MainScreenModel`: open and close of Now playing and Station details, back from each, the
    main screen's tab and stack unchanged by a visit, and Now playing closing when the current
    station goes (REQ-015).
  - The Station details rows built from a station with every field and with each field missing.
  - The playback tests that move from `MainScreenModel` keep their cases in the tracker's tests.
- JVM tests in `:common`: the home page extra (and the stream URL extra, if added) written by
  `buildPlayable` and read by `MediaItemHelper`, present and absent.
- Roborazzi screenshots in dark and light: playing without artwork (the reference state), with
  artwork, paused, buffering, error text, no track text, not favourite, the overflow menu open,
  Station details with every row and with missing rows. The mini player screenshots change only
  if the mini player's look changes.
- Instrumented journeys under `app/src/androidTest`, on an offline, cleared emulator:
  - Tapping the mini player outside its button opens Now playing; tapping its button does not.
  - Pause/play on Now playing and on the mini player agree.
  - The heart on Now playing shows in the FAV tab and on the station's list row, and back again.
  - The chevron and system back return to the main screen with its tab and stack as they were.
  - Copy stream URL puts the URL on the clipboard.
  - Share stream URL fires an `ACTION_SEND` chooser, checked through a recording launcher seam.
  - Station details shows its rows, WEB fires `ACTION_VIEW` through the same seam, and back
    returns to Now playing.
- Fixture URLs follow AGENTS.md: `UNREACHABLE_ORIGIN` or a `LoopbackHttpFixture`, never a host
  name.

### REQ-020: build and verification

- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass
  (including `verifyRoborazziDebug` and `verifyPureCoreCoverage`; a new class that the coverage
  gate checks is listed in `gradle/pure-core-coverage.tsv` or excluded by a rule).
- `./gradlew :app:connectedDebugAndroidTest` passes on an emulator with networking disabled and
  the app's data cleared, as AGENTS.md describes.

## Example Mapping

| Rule | Example | Expected |
|------|---------|----------|
| REQ-001 open | Station playing, tap the mini player's station name | Now playing for that station; mini player hidden |
| REQ-001 button | Station playing, tap the mini player's button | Playback pauses; main screen stays |
| REQ-003 chevron | In COUNTRY › "Poland", scrolled down, open Now playing, tap the chevron | COUNTRY › "Poland" at the same scroll position; mini player shown |
| REQ-003 system back | On Station details, system back | Now playing |
| REQ-002 rotation | Now playing open, rotate | Now playing still open |
| REQ-006 toggle | Now playing, playing, tap Pause | Button shows Play; back on the main screen the mini player shows paused |
| REQ-007 heart, off list | Station from POPULAR playing, FAV tab shown underneath, tap the heart on Now playing | Heart filled; on return the station is in FAV and its POPULAR row heart is filled |
| REQ-007 heart, from list | Tap the heart of the playing station's row, then open Now playing | Now playing's heart matches the row |
| REQ-008 menu | Tap the overflow | Station details, Copy stream URL, Share stream URL, Sleep timer, Equalizer |
| REQ-008 no URL | Current station has no stream URL | Menu without Copy and Share |
| REQ-009 copy, API 30 | Tap Copy stream URL | Clipboard holds the listed URL; "Stream URL copied" toast |
| REQ-009 copy, API 34 | Tap Copy stream URL | Clipboard holds the listed URL; no toast from the app |
| REQ-010 share | Tap Share stream URL | Chooser with the URL as text and the station name as subject |
| REQ-011 dialogs | Tap Equalizer | `EqualizerDialog` opens; closing it leaves Now playing |
| REQ-012 all rows | Station with country, genre, MP3, 192 kbps, URL, home page | COUNTRY, GENRE, STREAM `MP3 · 192 kbps`, URL, WEB with the home page host |
| REQ-012 missing rows | Station with no genre, no bitrate, no home page | COUNTRY, STREAM `MP3`, URL |
| REQ-012 WEB | Tap the WEB row | `ACTION_VIEW` with the full home page URL |
| REQ-014 buffering | Player buffering with play-when-ready | Progress line under the top bar; Pause |
| REQ-014 error | Stream fails; service writes "Network Failed" | Track line "Network Failed"; Play once the player stops |
| REQ-014 no artwork | Station without artwork | Large accent initials in the artwork box |
| REQ-014 no track | Stream sends no track text | No track line; button row moves up |
| REQ-014 cold start | App killed after playing, reopened, start playback "never", open Now playing | That station shown paused; Play resumes it; Station details shows its rows |
| REQ-015 station goes | App data cleared while Now playing is open | Main screen; no mini player |
| REQ-015 next station | Another station starts while Now playing is open | Now playing shows the new station |

## Acceptance Criteria

1. Tapping the mini player outside its button opens Now playing for the current station; the
   button toggles playback and does not open it; the mini player has no favourite button
   (REQ-001).
2. Now playing lives in `MainActivity`'s Compose tree, chosen from model state, survives
   recreation, and adds no activity or navigation library (REQ-002).
3. The chevron ("Close player") and system back return to the main screen as it was; back on
   Station details returns to Now playing; the mini player is hidden while Now playing is open
   (REQ-003).
4. The layout is top bar, artwork box (artwork or initials), station name, one-line track,
   Pause/Play and heart, with no table below (REQ-004).
5. Playback state lives in `NowPlayingTracker` in `:common-ui`; `MainScreenModel` delegates to it
   and keeps no copy of it (REQ-005, REQ-016).
6. Pause/play and the heart on Now playing work and agree with the mini player and every list
   showing the station, both ways, including a current station that is not in the shown list
   (REQ-006, REQ-007).
7. The overflow holds Station details, Copy stream URL, Share stream URL, Sleep timer and
   Equalizer in that order; Copy and Share are absent without a stream URL (REQ-008).
8. Copy puts the listed stream URL on the clipboard, with a toast only below Android 13; Share
   opens an `ACTION_SEND` chooser with the URL and the station name; Sleep timer and Equalizer
   open their existing dialogs and stay in the main screen's overflow (REQ-009, REQ-010,
   REQ-011).
9. Station details shows COUNTRY, GENRE, STREAM, URL and WEB, each only when known, and WEB opens
   the home page with `ACTION_VIEW` (REQ-012, REQ-013).
10. Buffering, paused, error, no-artwork, no-track and cold-start states look and behave as
    REQ-014 describes, and the screen closes or follows the station as REQ-015 describes.
11. REQ-008's "does nothing until a Now playing screen exists" is replaced, the `MiniPlayer` KDoc
    and its journey are updated, and nothing replaced is left behind (REQ-016).
12. Every state with a reference image matches it in dark and light, the owner having reviewed
    the Roborazzi baselines against `freenetradio-now-playing-*.png`; the rest follows DESIGN.md
    as agreed in D1-D7; nothing under `console-design/` changed; the chevron follow-up bead
    exists (REQ-017).
13. Android Auto, `OpenRadioService`, the browse tree, the `MediaId`s and the commands are
    unchanged apart from the additive extra(s) of REQ-013 (REQ-018).
14. The JVM, Roborazzi and instrumented tests listed in REQ-019 exist and pass.
15. `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass, and
    the instrumented journeys pass on an offline, cleared emulator (REQ-020).

## Out Of Scope

- Any change to Android Auto, the browse tree, the `MediaId`s or the service commands, beyond the
  additive extra(s) of REQ-013.
- Compose screens for the sleep timer and the equalizer; they keep their existing dialogs.
- A favourite button on the mini player.
- A live buffer readout, BUFFER/SLEEP/EQ rows, or splitting the track into title and artist.
- Animations beyond what Compose does by default.
- A bottom sheet component.
- Edits under `console-design/`, including the DESIGN.md line for the chevron (filed as a
  follow-up, REQ-017).
- A browser-based visual companion for mockups (declined, Q0 = no).

## Open Questions

None block planning. Points the approved design leaves implicit are settled here as the closest
reading of it; the owner can overrule any of them when approving this spec:

- **The "copied" message (REQ-009).** The design asks for "a short message" below Android 13, and
  DESIGN.md has no snackbar or toast rule. This spec uses a platform `Toast`.
- **Cold-start details (REQ-013).** With no current item in the service, the design does not say
  where Station details and the stream URL come from. This spec takes them from the
  `RadioStation` that `LastPlayedStation` restores, which holds every field.
- **Station change while open (REQ-015).** The design says the screen closes when the station
  goes away, and does not cover another station starting. This spec keeps the screen open and
  shows the new station.
- **Who carries the DESIGN.md chevron line (REQ-017).** The design calls it "a console-design
  follow-up". This spec files it as a `needs-human` bead here, since this repository never edits
  `console-design/`.
- **Stream URL source (REQ-013).** Planning checks whether `currentMediaItem.localConfiguration`
  carries the stream URI in the app's process. If it does not, an added extra carries it, under
  the owner's approval of Q2 = a.
