# Design candidate: Now playing screen (Compose, Console design)

- Source bead: fnr-qdkv7 (stage 4 of the Compose migration), build target `fnr/now-playing`
- Workflow root: fnr-8i7gz; lane bead: fnr-1c63b (iteration 1)
- Approval mode: interactive
- Approval status: **approved** by the owner on 2026-11-18 ("approve, all recommended"). Every
  question in "Question queue" (Q0-Q8) takes its recommended answer, and the differences D1-D7 in
  the approval summary are accepted. Q0 = no visual companion.

## Checklist state

1. Project context inspected: done (see "Context found").
2. Visual companion: there are layout questions ahead (details placement, button row, top bar
   icon), but the two reference PNGs answer most of them. The owner declined the companion in the
   previous build (fnr-pi6ap, Q0 = no). The offer is asked again as Q0, in its own message, because
   that answer was for another build.
3. Clarifying questions: queued as Q0-Q8, one question each, in the order they should be asked.
   This headless session cannot wait for answers (in the last build the controller killed a
   waiting session within about 30 s), so the approval lane asks them.
4. Approaches: three, with a recommendation (see "Approaches"); confirmation is the last queued
   question.
5. Design sections: drafted with the recommended answers; each section names the question it rests
   on.

## Requested outcome

A Compose Now playing screen for the current station, opened by tapping the mini player anywhere
except its pause/play button. It has a pause/play button and a favourite button, and puts every
other action in a three-dot overflow menu. It follows `console-design/DESIGN.md` and the reference
images `freenetradio-now-playing-{dark,light}.png`, except where the owner's decisions in
fnr-qdkv7 differ. Each difference is listed in "Differences from DESIGN.md and the reference" for
the owner to agree to.

## Decided by the owner (fnr-qdkv7)

- Tapping the mini player anywhere except its pause/play button opens Now playing. The button keeps
  toggling playback and does not open the screen.
- Now playing shows the current station's information.
- Pause/play and favourite (toggling both ways) are buttons on the screen. Every other action goes
  in a three-dot overflow menu, because more actions will come later.
- The overflow menu has at least: show the station's details, and copy the stream URL to the
  clipboard, or share it through an Android share intent, or both (to settle).
- No favourite button is added to the mini player.
- The owner's decisions win over DESIGN.md where they conflict; DESIGN.md changes go to the
  console-design repository, never to files under `console-design/` here.
- Android Auto and the `OpenRadioService` media contract do not change.

## Context found

- `MainActivity` (214 lines) renders `MainScreen(state, actions)` from the registry's singleton
  `MainScreenModel` (`:common-ui`, 564 lines) and launches the existing dialogs from the overflow.
  There is no navigation library in the build. The activity handles system back itself
  (`mModel.back()`, otherwise `leave()` and `finish()`).
- `MainScreenModel` owns the mini player: `mMiniPlayer: MiniPlayerState?` (name, initials, track,
  isPlaying) and `mPlayingId`, updated from `MediaResourceManagerListener.onPlaybackStateChanged`
  and `onMetadataChanged`, plus `showWhatTheServiceHolds()` for a cold start (the last played
  station, paused, from `LastPlayedStation`). `togglePlayback()` sends
  `CMD_TOGGLE_LAST_PLAYED_ITEM`, or calls `play()` when the player is empty.
- `toggleFavorite(rowId)` only finds stations in the shown list (`itemOf`): it cannot favourite a
  station that is not on screen. Hearts tapped are kept in `mFavoriteOverrides` until the service
  answers again, so one override map keeps every view of a station in agreement.
- `MiniPlayer.kt` (114 lines): the row has no click of its own, only the 44 dp button does. KDoc
  says "the rest does nothing until a Now playing screen exists" (REQ-008 of fnr-pi6ap).
- What a playable `MediaItem` carries (`MediaItemBuilder.buildPlayable`): title (name), subtitle
  (country name), description (genre), artwork URI, the stream URI (`getStreamUrlFixed`) and extras
  for bitrate, country code, codec, favourite and sort id. It does **not** carry the station's
  home page (`RadioStation.homePage`), which the browse tree holds only in the service.
- Whether a `MediaController` in the app's own process receives the `MediaItem`'s
  `localConfiguration` (and so its stream URI) depends on the Media3 version; the service runs in
  the app's process (no `android:process`). This is checked in planning (see "Risks").
- Playback states that reach the client: `PlaybackState(playbackState, playWhenReady)`;
  `isPlaying` counts `STATE_BUFFERING` as playing. Playback errors do not reach the client as
  errors: `OpenRadioPlayer.onPlayerError` writes a message ("network failed", "stream rejected",
  "stream error") into the stream metadata, which arrives as the track text through
  `onMetadataChanged`.
- Sleep timer: `SleepTimerModel` (enabled, time). EQ: `EqualizerLayer` in the service, chosen
  through the existing `EqualizerDialog`. Buffering: settings in `StreamBufferingDialog`.
- console-design offers `ConsoleTopBar` (navigation slot, actions), `ConsoleIconButton`,
  `PrimaryButton` (text + icon), `GhostIconButton` (56 dp square), `KeyValueTable` (link rows in
  accent), `InitialsTile` (38 dp, small text). There is **no bottom sheet component** and no large
  artwork component.
- Reference images: top bar with a **down chevron** ("Close player" in the HTML), dimmed title
  "Now playing" and a three-dot overflow; a 320 x 200 dp artwork box with large accent initials;
  station name; track line ("**Candy** — Iggy Pop & Kate Pierson", title bold); a button row:
  `PrimaryButton` Pause and three ghost buttons (heart filled in accent, moon, EQ sliders); a
  `KeyValueTable` with COUNTRY, STREAM, BUFFER, SLEEP, EQ, WEB (link). **No mini player** on the
  screen.

## Constraints

- console-design rules (DESIGN.md); no edits under `console-design/`.
- No DI framework (`*Dependency` / `configureWith`); no navigation library is added (Q8).
- Tests: hand-written fakes, no network, Roborazzi baselines in both themes reviewed by the owner
  against the now-playing PNGs.
- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass.
- No telemetry; the share action is a user-initiated `ACTION_SEND` chooser and carries only the
  station's name and stream URL.

## Non-goals

- Android Auto, the browse tree, `MediaId`s and service commands (beyond the optional additive
  extra in Q2).
- Sleep timer and EQ as Compose screens (they keep their existing dialogs, as in stage 3).
- A favourite button on the mini player.
- A live buffer readout, splitting the track into title and artist, or animations beyond what
  Compose does by default.
- Changes under `console-design/`.

## Approaches

**A. One model, a playback part split out, a screen switch in the activity (recommended).**
Move the now-playing state out of `MainScreenModel` into a small plain class (working name
`NowPlayingTracker`, `:common-ui`) that owns the current item, `mPlayingId`, the mini player state,
the current station's favourite state and `togglePlayback`. `MainScreenModel` keeps the browse
stacks and delegates to it, and builds both `MiniPlayerState` and a new `NowPlayingState` from it
into `MainScreenState`. Whether Now playing is open is part of the same state (`showNowPlaying`),
so `MainActivity`'s `setContent` shows `MainScreen` or `NowPlayingScreen` from one value, and
system back closes Now playing first.
- For: one service connection and one override map, so pause/play and the heart agree in the mini
  player, the lists and Now playing without any syncing code; the screen is a function of state,
  so Roborazzi renders each state from fixed data; the split keeps `MainScreenModel` from growing
  past 600 lines and gives the playback part its own JVM tests.
- Against: touches `MainScreenModel` and its playback tests (they move to the tracker).

**B. A separate `NowPlayingModel` with its own client, in the same activity.**
- For: leaves `MainScreenModel` alone.
- Against: two subscribers to the same player state, and favourite state in two places, which must
  then be kept in sync by hand; that is exactly the "stays in sync" acceptance criterion at risk.

**C. A separate `NowPlayingActivity`.**
- For: system back and process recreation come from the platform.
- Against: a second service connection and model, the same sync problem as B, a second activity in
  every instrumented journey, and the stage-3 lifecycle handling (`isClosed`, `leave()`) would have
  to be repeated.

Recommendation: **A**.

## Design (recommended answers; approved by the owner 2026-11-18)

### 1. Opening and leaving (Q5, Q6, Q8)
- The mini player row becomes clickable except its button: a tap opens Now playing
  (`MainScreenActions.onOpenNowPlaying`). The button keeps its own click and does not propagate.
- Now playing replaces the main screen's content inside `MainActivity`'s Compose tree (Q8 = a);
  the main screen's tabs, stacks and scroll positions are untouched underneath and are shown again
  as they were.
- Leaving: the top bar's down chevron (content description "Close player", as in the reference,
  Q5 = a) and system back both return to the main screen. Rotation keeps the screen open (the
  flag is model state).
- The mini player is not shown while Now playing is open (Q6 = a), as in the reference.
- If the current station goes away while the screen is open (the model's `close()`, app data
  cleared, the mini player going down), the screen closes with it.

### 2. Screen layout
- `ConsoleTopBar`: chevron, dimmed title "Now playing", overflow icon.
- Artwork box, full content width, 200 dp high, `surface1`, small corners: the station's artwork
  through the existing `ArtworkCache` when it has one, otherwise its initials in large accent mono
  text (the reference shows this no-artwork state).
- Station name (`Console.type` screen-title weight), then the track line as the stream sends it
  (one plain line; see difference D6).
- Button row: `PrimaryButton` Pause/Play with icon, at the reference width, then a
  `GhostIconButton` heart (filled accent when favourite, outlined otherwise; content description
  "Add to favourites" / "Remove from favourites"). The space where the moon and EQ buttons were
  stays empty (difference D1).
- Below the button row: what Q1 decides. Recommended: nothing more on this screen; the details
  are a sub-screen (section 4).

### 3. Overflow menu (Q3, Q4)
In this order: **Station details**, **Copy stream URL**, **Share stream URL**, **Sleep timer**,
**Equalizer**.
- Copy puts the station's listed stream URL (the one the station was added or listed with, not a
  URL resolved from its playlist) on the clipboard; below Android 13, where the system shows no
  confirmation, a short "Stream URL copied" message is shown.
- Share opens an `ACTION_SEND` chooser with the URL as text and the station name as subject.
- Sleep timer and Equalizer open the existing dialogs, as the main screen's overflow does; both
  stay in the main screen's overflow too.
- An item whose value is missing (no stream URL) is not offered.

### 4. Station details (Q1, Q2)
- A sub-screen opened from the overflow (Q1 = c): `ConsoleTopBar` with a back arrow and the
  station name, then a `KeyValueTable`. Back returns to Now playing.
- Rows (Q2 = a), each shown only when it has a value: COUNTRY (country name), GENRE, STREAM
  (`MP3 · 192 kbps`), URL (the stream URL, mono), WEB (home page host, a link that opens the
  browser with `ACTION_VIEW`).
- BUFFER, SLEEP and EQ rows of the reference are not shown: they are player settings, not station
  facts, and their dialogs are one tap away in the overflow (difference D2).
- WEB needs the home page in the `MediaItem`: an added `KEY_HOME_PAGE` extra in
  `MediaItemBuilder.buildPlayable`, read through `MediaItemHelper`. It is additive: ids, tree,
  commands and Android Auto do not change (Q2 decides whether that is acceptable).

### 5. Screen states (Q7)
- Playing: Pause, as in the reference.
- Paused or stopped: Play.
- Buffering (player buffering while it should play): a thin accent progress line under the top
  bar, the same one the main screen uses; the button shows Pause.
- Playback error: the service already writes its message into the track line ("Network
  failed", ...); the screen shows it there and the button follows the player (Play once it stops).
  No new error UI and no service change.
- No artwork: initials in the artwork box (reference). No track title: the track line is left out,
  and the button row moves up.
- Cold start (last station restored, paused): the screen shows that station paused; Play resumes
  it, as the mini player does.

### 6. Favourite and playback sync
- The heart on Now playing toggles the current station through the same override map the list
  hearts use, keyed by the normalized media id (`MediaId.normalizeFromSearchId`), so the list row,
  any other list showing the station, and Now playing agree immediately and the service's next
  answer wins, as today.
- `toggleFavorite` gains a path for the current item, which need not be in the shown list.
- Pause/play calls the same `togglePlayback()` as the mini player.

### 7. Removed or replaced
- REQ-008's "only the button acts; tapping elsewhere does nothing until a Now playing screen
  exists" is replaced by section 1; the `MiniPlayer` KDoc and the instrumented journey that asserts
  "tap does nothing" change with it.
- `MainScreenModel`'s playback fields and their tests move to the tracker; nothing stays behind in
  the old place.

### 8. Testing
- JVM (`:common-ui`): the tracker (metadata, playback state, cold start, toggle, favourite of the
  current item, override shared with list rows, normalized ids), and the model's open/close of Now
  playing, back, and closing when the station goes; the details rows built from a `MediaItem` with
  and without each field; fakes only.
- JVM (`:common`): the home page extra written and read (if Q2 = a).
- Roborazzi, dark and light: playing without artwork (the reference state), with artwork, paused,
  buffering, error text, no track title, not favourite, overflow open, station details with all
  rows and with missing rows. The owner reviews the playing baseline against the reference PNGs.
- Instrumented journeys (offline emulator, cleared app): tap the mini player opens Now playing and
  its button does not; pause/play on the screen and in the mini player agree; heart on Now playing
  shows in the FAV tab and in the station's list row, and back; chevron and system back return to
  the main screen where it was; Copy puts the URL on the clipboard; Share fires a chooser (checked
  through a recorded launcher seam, no real target app); Station details shows the rows and back
  returns.

## Differences from DESIGN.md and the reference (each needs the owner's agreement)

- **D1. Button row.** DESIGN.md and the reference: Pause plus ghost buttons for favourite, sleep
  timer and EQ. Here: Pause and favourite only, at their reference sizes and left-aligned; sleep
  timer and EQ go to the overflow (owner's decision). The row is shorter than in the reference.
- **D2. Key-value table.** DESIGN.md and the reference: a `KeyValueTable` on the screen with
  country, stream, buffer, sleep, EQ, website. Here (recommended Q1 = c, Q2 = a): no table on
  Now playing; a Station details sub-screen with COUNTRY, GENRE, STREAM, URL, WEB; no BUFFER,
  SLEEP or EQ rows.
- **D3. Overflow content.** DESIGN.md names no Now playing overflow items; the reference shows only
  the icon. Here: Station details, Copy stream URL, Share stream URL, Sleep timer, Equalizer.
- **D4. Top bar icon.** DESIGN.md: sub-screens have a back arrow (or ✕ for edit screens). The
  reference: a down chevron "Close player". Recommended (Q5 = a): the chevron, and a follow-up in
  the console-design repository to name it in DESIGN.md for the player screen.
- **D5. Dialogs.** DESIGN.md: no dialogs for sleep timer or EQ. Here they keep their existing
  dialogs, as the main screen's overflow already does since stage 3; their Compose screens are a
  later build.
- **D6. Track line.** The reference bolds the song title and puts the artist after a dash. Streams
  send one string with no reliable split, so the line is shown as sent, in one weight.
- **D7. Mini player** hidden on Now playing: matches the reference; DESIGN.md does not say.

## Question queue (iteration 1; for the owner, one at a time, in this order)

**Q0 (visual companion, its own message):** "Some of what we're working on might be easier to
explain if I can show it to you in a web browser. I can put together mockups, diagrams,
comparisons, and other visuals as we go. This feature is still new and can be token-intensive.
Want to try it? (Requires opening a local URL)"

**Q1 (station details):** "Where do the station's details go? (a) A bottom sheet over Now playing,
opened from the overflow; console-design has no sheet component, so it would be built in the app
from the Material sheet, or added to console-design first. (b) On the screen itself, as the
reference's table below the buttons, and the overflow's 'Station details' item is dropped (this
changes your decision that details are in the overflow). (c) A sub-screen opened from the overflow,
with a back arrow and a `KeyValueTable`, built only from existing components (recommended)."

**Q2 (which details):** "Which rows does Station details show? (a) COUNTRY, GENRE, STREAM
(codec · bitrate), URL (the stream URL) and WEB (home page link), each only when known; WEB needs
one added extra on the station's media item, which Android Auto ignores (recommended). (b) The same
without WEB, so nothing in the service changes. (c) The reference's rows: COUNTRY, STREAM, BUFFER,
SLEEP, EQ, WEB."

**Q3 (copy or share):** "For the stream URL: (a) both 'Copy stream URL' and 'Share stream URL'
(recommended: copy gives the URL straight to the clipboard, share hands it to another app; the
chooser's own copy action is not on every Android version the app supports). (b) Copy only.
(c) Share only. In each case the URL is the station's listed address, not the one resolved from
its playlist."

**Q4 (sleep timer and EQ):** "With no buttons for them, where are sleep timer and EQ reached from
Now playing? (a) In Now playing's overflow, opening their existing dialogs, and still in the main
screen's overflow (recommended). (b) Only in the main screen's overflow."

**Q5 (leaving the screen):** "Which icon closes Now playing? (a) The reference's down chevron
('Close player'); DESIGN.md gets a line for it through the console-design repository
(recommended). (b) DESIGN.md's back arrow for sub-screens. System back closes it in both cases."

**Q6 (mini player while open):** "Is the mini player hidden while Now playing is open? (a) Yes, as
in the reference; the screen has the same controls (recommended). (b) No, it stays at the bottom."

**Q7 (states):** "Buffering shows a thin progress line under the top bar with the Pause button;
a playback error shows the service's message in the track line, as the mini player does now, with
the button following the player; no artwork shows the initials; no track title leaves the track
line out. (a) Yes (recommended). (b) Different (say how)."

**Q8 (structure and approach):** "Is Now playing (a) a second screen inside the main activity's
Compose tree, sharing the main screen's model, with the playback part split into its own class
(approach A, recommended); (b) a separate model in the same activity (approach B); or (c) a
separate activity (approach C)?"

## Answered questions

None yet.

## Unresolved questions

Q0-Q8 above, and agreement to differences D1-D7.

## Risks to check in planning

- Whether `client.currentMediaItem` in the app's process carries the stream URI
  (`localConfiguration`). If it does not, the stream URL travels in an added extra next to the home
  page one, under the same Q2 decision.
- Whether the service updates the current item's favourite flag after `CMD_MARK_FAVORITE`; the
  override map covers the gap either way, but the tracker must read the flag from the item the
  service last sent.

## Acceptance criteria (from fnr-qdkv7, plus the draft)

- Tapping the mini player outside its button opens Now playing for the current station; the button
  toggles playback and does not open it.
- Pause/play and favourite work on Now playing and agree with the mini player and every station
  list showing the station, both ways.
- The overflow holds the items Q3 and Q4 decide, each working as described (clipboard, chooser,
  existing dialogs, Station details).
- Station details shows the rows Q2 decides, each only when known; WEB opens the browser.
- Leaving by the top bar icon or system back returns to the main screen as it was; the mini player
  is hidden while Now playing is open (as Q6 decides).
- Buffering, paused, error, no-artwork and no-track states look as section 5 describes.
- Every state with a reference image matches it in dark and light (Roborazzi baselines, reviewed by
  the owner against `freenetradio-now-playing-*.png`); the rest follows DESIGN.md as agreed in
  D1-D7.
- REQ-008's "does nothing until a Now playing screen exists" is replaced by the new behaviour, and
  nothing replaced is left behind.
- Android Auto and the service's ids, tree and commands unchanged.
- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass; the
  instrumented journeys pass on an offline, cleared emulator.
