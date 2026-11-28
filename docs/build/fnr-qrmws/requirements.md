---
schema: gc.build.requirements.v1
workflow:
  id: fnr-qrmws
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
    - path: /home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/brainstorming/design-candidate.md
      hash: sha256:37074054bd4a36ebc79b7d5ca4287d6ac36a887d95f67aa6954c4fe888f2a66c
      ids: [REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009, REQ-010, REQ-011, REQ-012, REQ-013, REQ-014, REQ-015, REQ-016, REQ-017, REQ-018, REQ-019, REQ-020, REQ-021, REQ-022]
    - path: beads/fnr-urmil
      hash: bead:fnr-urmil
    - path: beads/fnr-y3xhu
      hash: bead:fnr-y3xhu
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
    - id: REQ-021
      status: covered
    - id: REQ-022
      status: covered
---

# Settings in Compose (stage 5, build A): requirements

| Field | Value |
|-------|-------|
| Source bead | fnr-urmil (stage 5, the last stage of the Compose migration) |
| Workflow root | fnr-qrmws (`superpowers-lite-build`) |
| Target branch | `fnr/settings-dialogs` |
| Approved design | `/home/jsz/Devel/jacekszymanski/gcexp/.gc/artifacts/freenetradio/settings-dialogs/brainstorming/design-candidate.md` |
| Design approval | gate fnr-y3xhu, 2026-11-27 21:14Z: "Q0 = no (no visual companion). Approve: all recommended answers Q1-Q16 as written, agree to D1-D9. Verdict: approve." |
| Approach | A: a `ScreenStack` of `Destination`s beside `MainScreenModel`; no navigation library, no bottom sheets |
| Scope | Build A of four (A → B → C → D). B, C and D are already filed as fnr-hkpbe, fnr-uo578 and fnr-baige; this build files no beads (REQ-021) |
| Repository mirror | none; see "Spec location" below |

### Spec location

This artifact is the spec. It is not mirrored under `docs/superpowers/specs/` in the repository:
the only checkout this lane can reach is `master`, which nothing but the owner's merge may move,
and the `commit-docs` step of this build commits lasting documents to `fnr/settings-dialogs`.

### Coverage

| ID | Requirement | Status |
|----|-------------|--------|
| REQ-001 | `ScreenStack` and `Destination`: the screen host | covered |
| REQ-002 | Back order and leaving the screens | covered |
| REQ-003 | Main overflow after build A | covered |
| REQ-004 | Settings screen layout and row values | covered |
| REQ-005 | `SettingsModel` and the stores behind it | covered |
| REQ-006 | Choice sub-screen | covered |
| REQ-007 | Start playback | covered |
| REQ-008 | Source, with restart and revert | covered |
| REQ-009 | Default country | covered |
| REQ-010 | Master volume stepper | covered |
| REQ-011 | Bluetooth auto play | covered |
| REQ-012 | Use mobile data | covered |
| REQ-013 | User agent sub-screen | covered |
| REQ-014 | Clear cache | covered |
| REQ-015 | Stream buffering edit sub-screen | covered |
| REQ-016 | Field error line | covered |
| REQ-017 | Export and import stations | covered |
| REQ-018 | Diagnostics sub-screen | covered |
| REQ-019 | About sub-screen | covered |
| REQ-020 | Battery prompt and deleted View code | covered |
| REQ-021 | Builds B, C and D are already filed; this build files no beads | covered |
| REQ-022 | Tests, visual review and verification commands | covered |

## Problem Statement

After stage 4 (fnr-qdkv7, merged into `master` at 90782c3), every screen in FreeNetRadio is
Compose in the Console design except the dialogs. `MainActivity` still opens fourteen View
dialogs through `showDialog`, `UiUtils.clearDialogs` and `BaseDialogFragment.newInstance` by
reflection. Six of them are settings reached from eight separate overflow entries; one holds
eight unrelated settings. They break `console-design/DESIGN.md`, which wants settings as
`SettingRow`/`SettingSwitchRow` screens with mono values, edit screens with ✕ and Save, and
dialogs only to confirm a destructive action. `BatteryOptimizationDialog` is wired in at start
but has shown nothing since upstream commit 0c25ff9.

This build, build A of stage 5, replaces the settings dialogs (General, Source, Network,
Stream buffering, File storage, About) with one Compose Settings screen and its sub-screens,
hosted by a small destination stack beside `MainScreenModel`. It deletes the battery dialog and
the replaced dialogs, layouts and adapter. Stored settings keep their keys and meanings. The
remaining dialogs move in builds B (sleep timer, equaliser), C (station actions and form, with
fnr-fml) and D (search, then the shared dialog machinery). The owner has already filed those as
fnr-hkpbe, fnr-uo578 and fnr-baige.

## W6H

- **Who:** phone and tablet users of FreeNetRadio. Android Auto users are unaffected.
- **What:** a `ScreenStack` host; a Settings screen with PLAYBACK, STATIONS, NETWORK,
  DIAGNOSTICS and ABOUT sections; sub-screens for start playback, master volume, stream
  buffering, source, default country, user agent, diagnostics and About; export and import
  through activity result launchers; deletion of seven dialogs and their resources. No new
  beads: B, C and D are already filed.
- **When:** this build, on the integration branch `fnr/settings-dialogs`. Builds B, C and D
  follow, in that order, as their own builds.
- **Where:** `ScreenStack`, `Destination`, `SettingsModel`, the buffering validation function
  and the About rows in `:common-ui`; the composables and `MainActivity` in `:app`; screenshot
  tests in `app/src/test`; journeys in `app/src/androidTest`; deletions in `:common-ui`
  `shared/view/dialog/` and `res/layout/`.
- **Why:** fnr-urmil: the last stage of the Compose migration, so the app follows DESIGN.md
  everywhere and the View dialog code can go.
- **Which:** approach A of the approved design, with the recommended answer (a) to each of
  Q1-Q16 and the differences D1-D9 as agreed at gate fnr-y3xhu.
- **How:** `MainActivity`'s `setContent` shows the top `Destination` when the stack is not
  empty, otherwise Now playing when it is open, otherwise the main screen. Each destination
  renders from its own state holder over narrow interfaces to the existing stores, wired with
  `configureWith(...)`.

## User Stories

- **US-1.** As a listener, I open Settings from the overflow and find every setting that used to
  be spread across five menu entries on one screen, each with its current value shown.
- **US-2.** As a listener, I change start playback, the source, the default country, the master
  volume, Bluetooth auto play, mobile data use and the user agent, and the app behaves as it did
  when I changed them in the old dialogs.
- **US-3.** As a listener, I edit the stream buffering values, save them or discard them with ✕,
  and see what is wrong next to the field I got wrong.
- **US-4.** As a listener, I export my stations to a file and import them back.
- **US-5.** As a listener, I read the warning about what a diagnostic report contains before I
  create one, then share it through the system chooser.
- **US-6.** As a listener, I see the app version and the project links in About, from Settings
  or straight from the overflow.
- **US-7.** As a listener, I go back from any sub-screen to Settings and from Settings to the
  main screen exactly as I left it, and rotating the phone keeps me where I am.
- **US-8.** As an existing user, I update the app and find all my settings as I left them.

## Technical Stories

- **TS-1.** As a maintainer, I test the screen stack, every settings row's value and write, the
  source revert, the user agent rule and every buffering rule on the JVM with hand-written fakes.
- **TS-2.** As a maintainer, I render each settings screen in Roborazzi from fixed state, in
  dark and light.
- **TS-3.** As a maintainer, I add a later screen (sleep timer, station form, search) by adding a
  `Destination` and a composable, without touching browse code or `MainScreenModel`.
- **TS-4.** As a maintainer, I find no dialog, layout, adapter or `clearDialogs` line left for a
  screen this build replaced, and no deprecated `onActivityResult` in the file storage path.
- **TS-5.** As a maintainer, I check export, import and the diagnostic share in instrumented
  tests through a recording launcher seam, without a real document UI or target app.
- **TS-6.** As a maintainer, I find the remaining stage 5 work in the beads the owner already
  filed (fnr-hkpbe, fnr-uo578, fnr-baige), not duplicated by this build.

## Behavior Requirements

### REQ-001: `ScreenStack` and `Destination`

- A plain Kotlin class in `:common-ui` (working name `ScreenStack`) holds a stack of
  `Destination`s and exposes the top one as observable state. Its public API has no Android
  View, `Activity` or Compose type.
- `Destination` is a sealed type. Build A has `Settings`, `SettingsChoice(kind)` (kinds: start
  playback, source, default country), `MasterVolume`, `StreamBuffering`, `UserAgent`,
  `Diagnostics` and `About`. Later builds add their own; planning may rename these.
- `ScreenStack` is constructed and held by the registry like `MainScreenModel`, without a DI
  framework, so it survives activity recreation (rotation, dark/light switch). Surviving process
  death is not required, as for `MainScreenModel` today.
- `MainActivity`'s `setContent` shows, in this order of precedence: the top destination's
  screen; otherwise Now playing when it is open; otherwise the main screen. All in
  `ConsoleTheme`. No navigation library, activity or fragment is added.
- While a destination is shown, the main screen's selected tab, tab stacks and scroll positions
  and Now playing's state are not touched.
- Clearing the app data (the path that empties `BrowseTree`) also empties the stack.
- `MainScreenModel` does not grow: no settings state or logic goes into it.

### REQ-002: back order and leaving the screens

- System back pops the stack while it is not empty, and only then reaches `MainScreenModel`'s
  `back()`. The order is decided in one place in `MainActivity`.
- Every sub-screen's back arrow or ✕ pops one level, the same as system back, except where a
  requirement below says what leaving also does (REQ-008, REQ-013, REQ-015).
- Back from a sub-screen opened from Settings returns to Settings; back from Settings returns to
  the screen that was shown before it (the main screen, as it was).
- About opened from the overflow (REQ-003) is pushed on its own, so back from it returns to the
  main screen.

### REQ-003: main overflow after build A

- The main screen's overflow becomes, in this order: **Settings, Sleep timer, Equalizer, About**
  (Q5 = a; difference D6). `OverflowItem` loses GENERAL, SOURCE, NETWORK, BUFFERING and
  FILE_STORAGE and gains SETTINGS.
- Settings pushes `Destination.Settings`; About pushes `Destination.About`. Sleep timer and
  Equalizer still open their View dialogs until build B.
- The Now playing overflow does not change.

### REQ-004: Settings screen layout and row values

- `ConsoleTopBar` with a back arrow and the title "Settings", then `SectionHeader`s and rows as
  `SettingRow`/`SettingSwitchRow` from console-design. Every value is in mono. No two-line rows
  with explanations.
- Sections, rows and the values they show (Q4 = a, Q6 = a):

| Section | Row | Kind | Value shown | Tapping it |
|---|---|---|---|---|
| PLAYBACK | Start playback | `SettingRow` | `ALWAYS` / `IF PLAYING` / `NEVER` | Choice sub-screen (REQ-007) |
| | Master volume | `SettingRow` | the stored volume, e.g. `100%` | Volume sub-screen (REQ-010) |
| | Bluetooth auto play | `SettingSwitchRow` | switch | REQ-011 |
| | Stream buffering | `SettingRow` | `DEFAULT` or `CUSTOM` | Edit sub-screen (REQ-015) |
| STATIONS | Source | `SettingRow` | the active source's name in capitals, e.g. `RADIO BROWSER` | Choice sub-screen (REQ-008) |
| | Default country | `SettingRow` | the country code, e.g. `PL` | Choice sub-screen (REQ-009) |
| | Export stations | `SettingRow` | none | REQ-017 |
| | Import stations | `SettingRow` | none | REQ-017 |
| NETWORK | Use mobile data | `SettingSwitchRow` | switch | REQ-012 |
| | User agent | `SettingRow` | `DEFAULT` or `CUSTOM` | Sub-screen (REQ-013) |
| | Clear cache | `SettingRow` | none | REQ-014 |
| DIAGNOSTICS | Send diagnostic report | `SettingRow` | none | Sub-screen (REQ-018) |
| ABOUT | About FreeNetRadio | `SettingRow` | the version as `name.code`, as About shows it (REQ-019) | Sub-screen (REQ-019) |

- **Stream buffering** shows `DEFAULT` when each of the four stored values equals its default
  (or nothing is stored), and `CUSTOM` otherwise.
- **User agent** shows `CUSTOM` when `AppPreferencesManager.isCustomUserAgent` is true, and
  `DEFAULT` otherwise.
- Values are refreshed whenever Settings is shown again, so a change made on a sub-screen shows
  on return.

### REQ-005: `SettingsModel` and the stores behind it

- A state holder in `:common-ui` (working name `SettingsModel`; planning may split it per
  sub-screen) produces the state each settings screen renders and handles each action.
- It reaches the stores only through narrow interfaces passed in `configureWith(...)`, backed in
  production by the same `AppPreferencesManager`, `NetworkSettingsStorage`, `CountrySetting`,
  `LocationService` and `SourcesLayer` calls and the same service commands
  (`CMD_MASTER_VOLUME_CHANGED`, `CMD_NET_CHANGED`, `CMD_CLEAR_CACHE`) the dialogs use today.
- Every setting is stored under the same key with the same meaning and value range as today.
  Nothing new is stored, except what the requirements below name.

### REQ-006: choice sub-screen

- One app-local composable serves every choice: a `ConsoleTopBar` with a back arrow and the
  setting's name, then one row per option separated by hairlines. The selected option's label
  is in accent (difference D2); the others in the normal text colour.
- Tapping an option stores it and pops back to Settings, except for Source (REQ-008).
- Build B reuses the same composable for equaliser presets.

### REQ-007: start playback

- Options, in this order: Always, If it was playing, Never, read from and written to
  `AppPreferencesManager.startPlayback` with today's values. The default stays If it was playing
  (fnr-lka81).

### REQ-008: source, with restart and revert

- The options are the sources `SourcesLayer` offers, labelled as in `SourceDialog`.
- Choosing a source other than the one active when the sub-screen opened calls
  `setActiveSource` at once, as `SourceDialog` does, and shows one `PrimaryButton`
  "Restart to apply" below the list. The sub-screen stays open.
- Choosing the original source again hides the button.
- "Restart to apply" restarts the app as `SourceDialog` does today (`Runtime.exit(0)` after the
  same steps). In tests it goes through a recording restart seam.
- Leaving the sub-screen without restarting (back arrow or system back) puts the original source
  back, as `SourceDialog.onDestroy` does now. Activity recreation while the sub-screen is open
  does not count as leaving: it neither reverts nor loses the pending choice.

### REQ-009: default country

- The options come from `LocationService.getCountriesWithLocation`, in its order, each showing
  the country name with its code in mono. The choice is stored through `CountrySetting` as the
  spinner stores it today.
- The current country is shown as selected, and the list opens scrolled to it.

### REQ-010: master volume stepper

- A sub-screen with a back arrow and "Master volume", showing the value in mono as a percentage
  between a − and a + `GhostIconButton` (Q7 = a; difference D3).
- Each tap changes the value by 5, clamped to 0..100, stores it through
  `AppPreferencesManager` and sends `CMD_MASTER_VOLUME_CHANGED` with it, as releasing the
  SeekBar does today. A stored value that is not a multiple of 5 is shown as stored and stepped
  from (73 → 78 or 68); it is not rounded.
- − is disabled at 0 and + at 100. The default stays `OpenRadioService.MASTER_VOLUME_DEFAULT`.

### REQ-011: Bluetooth auto play

- The switch shows the stored value. Turning it off stores false.
- Turning it on below Android 12 stores true. On Android 12 and later it first asks for
  `BLUETOOTH_CONNECT` and stores true only once that is granted; when it is refused, nothing is
  stored and the switch shows off. The request runs at activity level, without a View
  (`PermissionChecker.requestBluetoothPermission` gets or is replaced by a View-free path).

### REQ-012: use mobile data

- The switch reads and writes `NetworkSettingsStorage`'s "use mobile network" value and sends
  `CMD_NET_CHANGED` on every change, as `NetworkDialog` does.

### REQ-013: user agent sub-screen

- A sub-screen with a back arrow and "User agent": a `SettingSwitchRow` "Custom user agent"
  (`AppPreferencesManager.isCustomUserAgent`, stored when toggled, as today), then a
  `FieldLabel` and a `ConsoleTextField` holding `AppUtils.getUserAgent`, editable only while the
  switch is on.
- The text is trimmed and stored with `setCustomUserAgent` when the sub-screen is left, as the
  dialog stores it in `onPause`. An empty value is not stored; while the switch is on and the
  field is empty, the error line (REQ-016) under the field says the value will not be saved
  (today's `user_agent_empty_warning`). Leaving is never blocked.
- Activity recreation keeps the typed text and does not count as leaving.

### REQ-014: clear cache

- Tapping the row sends `CMD_CLEAR_CACHE`, as today, then shows a short message "Cache cleared"
  (a new string) the way the app shows its other short messages (`SafeToast`).

### REQ-015: stream buffering edit sub-screen

- An edit screen (Q8 = a; difference D4): `ConsoleTopBar` with ✕, the title "Stream buffering"
  and a `SmallPrimaryButton` "Save". Four `FieldLabel`/`ConsoleTextField` fields in mono with a
  numeric keyboard and their units: min buffer, max buffer, play buffer, play buffer after
  rebuffer, filled from the stored values. A "Restore defaults" text action fills the fields
  with the defaults without storing them. One short line under the fields gives the allowed
  range (`stream_buffering_descr`, shortened if needed).
- Validation is one pure function taken out of `StreamBufferingDialog.handleOnPause`, with the
  bounds passed in. It applies exactly today's rules: a value that is empty, not digits or out
  of range (`invalid_buffer_desc`); max buffer below min buffer (`min_buffer_greater_max_buffer`);
  play buffer above min buffer (`play_buffer_greater_min_buffer`); play buffer after rebuffer
  above min buffer (`play_re_buffer_greater_min_buffer`).
- Unlike the dialog, which stops at the first failure, the function returns every failed rule:
  each field that is empty, not digits or out of range, and each cross-field rule whose two
  values are themselves valid.
- Save runs the function. When every value is valid it stores all four, as the dialog does, and
  pops back to Settings. Otherwise it stores nothing and shows each error under the field it
  concerns (REQ-016); a rule between two fields shows under the second-named field (max buffer
  for "max below min", play buffer for "play buffer above min", play buffer after rebuffer for
  "play buffer after rebuffer above min").
- ✕ and system back pop without storing, whatever the fields hold.
- Activity recreation keeps the typed values and any shown errors.

### REQ-016: field error line

- An error is one line under its field: `Console.type.small` in `textSecondary`, after an
  outlined error icon (Q9 = b; difference D9). No toast is shown for a field error.
- It is one app-local composable, so the station form in build C uses the same one and a later
  console-design error state can replace it in one place.

### REQ-017: export and import stations

- Export starts `ACTION_CREATE_DOCUMENT` and import starts `ACTION_OPEN_DOCUMENT` through
  activity result launchers registered in `MainActivity` (`rememberLauncherForActivityResult` or
  `registerForActivityResult`), with today's `FILE_TYPE` and `FILE_NAME`.
- The chosen document is written or read through `FileStoreManager` in today's file format, with
  today's merge rules on import. `FileStoreManager`'s `DialogFragment` overloads and its
  `onActivityResult` request-code path are deleted; it takes the launcher's result (the URI, or
  none) instead.
- While a file is written or read, a thin accent progress line shows under Settings' top bar.
  The result is today's `success` or `failure` message. A result that is not OK or carries no
  URI, including a cancelled document UI, is reported as a failure, as today.
- `FileStoreManagerTest` keeps every case it has, moved to the new entry points; the
  unknown-request-code case goes with the request codes.

### REQ-018: diagnostics sub-screen

- A sub-screen "Diagnostic report" (Q10 = a; difference D5): the existing
  `diagnostic_report_warning` text, then one `PrimaryButton` "Create report".
- While the logs are collected a progress line shows and the button is disabled. When they are
  ready, the same `ACTION_SEND` chooser opens with the same `FileProvider` zip; a failure shows
  today's failure message and enables the button again.
- No AlertDialog. The report still leaves the app only through this per-report chooser.

### REQ-019: about sub-screen

- A sub-screen with a back arrow and "About", then a `KeyValueTable`: VERSION (`name.code`, as
  `AboutDialog` shows it), MEDIA3 (the Media3 version), and link rows PROJECT, ISSUES,
  RADIO BROWSER, WEBRADIODB, PLAYLIST PARSER, SWIPE, FONTS with the URLs `AboutDialog` uses.
- MEDIA3 is a link row too: it keeps `AboutDialog`'s link to `EXO_PLAYER_URL`, so all eight of
  today's links stay.
- Tapping a link row opens its URL with `ACTION_VIEW`.
- The rows are built by a plain function in `:common-ui` from the version values and the link
  list, so they are JVM-tested.

### REQ-020: battery prompt and deleted View code

- `BatteryOptimizationDialog` is deleted with its `handle` call in `MainActivity` and its
  `clearDialogs` line; nothing replaces it (Q11 = a).
- Deleted: `GeneralSettingsDialog`, `SourceDialog`, `NetworkDialog`, `StreamBufferingDialog`,
  `FileStorageDialog`, `AboutDialog`, `BatteryOptimizationDialog`; the layouts
  `dialog_general_settings`, `dialog_source`, `source_view`, `dialog_network`,
  `settings_stream_buffering`, `dialog_file_storage`, `dialog_about` and
  `countries_spinner_item`; `CountriesArrayAdapter`; their lines in `UiUtils.clearDialogs`; and
  their uses in `JourneyDialogs`.
- Any other class, function, string or drawable that nothing references after these deletions is
  deleted too, string resources in every locale.
- `BaseDialogFragment`, `UiUtils.clearDialogs`, `MainActivity.showDialog` and `JourneyDialogs`
  stay: the dialogs of builds B, C and D still use them (Q16 = a).
- `OpenRadioService`, the browse tree, the `MediaId`s, the service commands, Android Auto and
  Now playing do not change.

### REQ-021: builds B, C and D are already filed

The owner filed the later stage 5 builds by hand after approving the design (fnr-urmil notes,
2026-11-27). This build files, edits and closes no bead for them; planning adds no bead-filing
task:

- **B** = fnr-hkpbe: Sleep timer and Equalizer screens (design section 7 B).
- **C** = fnr-uo578: station actions, the Add/Edit station screen, the Remove confirmation and
  the fnr-fml fix (design section 7 C).
- **D** = fnr-baige: search in the top bar, then the deletion of the shared dialog machinery
  (design section 7 D).
- The edges are in place: fnr-urmil blocks fnr-hkpbe, fnr-hkpbe blocks fnr-uo578, fnr-uo578 and
  fnr-hkpbe block fnr-baige, and fnr-fml has a `related` edge to fnr-uo578.
- Build A still leaves what those builds need: the choice sub-screen (REQ-006) and the field
  error line (REQ-016) are reusable, and `BaseDialogFragment`, `UiUtils.clearDialogs`,
  `MainActivity.showDialog` and `JourneyDialogs` stay (REQ-020).

### REQ-022: tests, visual review and verification

- **JVM** (`:common-ui`, hand-written fakes, no network):
  - `ScreenStack`: push, pop, top, empty; back popping it before the browse stacks; emptied when
    the app data is cleared; About pushed alone returns to the main screen.
  - `SettingsModel`: each row's value from fakes (including `DEFAULT`/`CUSTOM` for buffering and
    user agent); each write going to the right store with the same value; the master volume steps,
    clamps and unrounded values; the Bluetooth permission path granted and refused, and below
    Android 12; source choice, choosing back the original, restart, and revert on leave but not
    on recreation; user agent empty and non-empty, switch on and off; clear cache.
  - The buffering validation function: every rule, every bound, and which field each error
    belongs to.
  - The About rows.
  - Every new class that the coverage gate checks is listed in `gradle/pure-core-coverage.tsv`
    or excluded there with a rule, and `verifyPureCoreCoverage` passes.
- **Roborazzi**, dark and light: Settings with defaults; Settings with changed values; each
  choice sub-screen (start playback, source, default country); Source with "Restart to apply";
  master volume at 0, in between and at 100; stream buffering valid and with errors; user agent
  with a custom value and with an empty custom value; Diagnostics idle and collecting; About;
  Settings with the export progress line; the existing `main_overflow_open` baselines re-recorded
  with the four-entry overflow. The owner reviews them
  against DESIGN.md and the `ntrecv-settings-*` and `msgsched-edit-*` reference images, since
  none exist for these screens.
- **Instrumented** (offline, cleared emulator):
  - `SettingsPersistenceJourneyTest`, `CountryChangeJourneyTest` and
    `DiagnosticReportJourneyTest` drive the Compose screens by test tag instead of View ids.
  - The buffering journey
    `recreatingTheActivityWithTheBufferingDialogUpStoresWhatTheUserHadTyped` is replaced by:
    recreating the activity keeps the typed values; Save stores them; ✕ does not.
  - Back from every sub-screen returns to Settings, and back from Settings returns to the main
    screen with its tab and stack as they were; rotation keeps the open screen.
  - The source journey stops at "Restart to apply" or uses the recorded restart seam; the test
    process is never killed.
  - Export and import run against a recorded launcher seam with a document the test owns, and
    check the stations round-trip.
- **Commands:** `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and
  `./gradlew check` pass, and `./gradlew :app:connectedDebugAndroidTest` passes on an offline,
  cleared emulator. A release build is checked on a device if the build adds code that loads a
  class by name (it should not: the reflective path is `BaseDialogFragment`, which stays).

## Example Mapping

### Rule: back pops the screen stack before the browse stacks (REQ-001, REQ-002)

- Example: on the FAV tab two levels deep, overflow → Settings → Source; back shows Settings;
  back shows FAV two levels deep, scrolled as before; back goes up one FAV level.
- Example: Now playing is open; there is no Settings entry there, so the stack is empty and back
  closes Now playing as today.
- Example: overflow → About; back shows the main screen, not Settings.
- Example: Settings → Stream buffering open; rotate; Stream buffering is still shown with the
  typed values.

### Rule: source changes apply on restart and revert on leave (REQ-008)

- Example: Radio Browser active; choose WebRadioDB; "Restart to apply" shows; back; the active
  source is Radio Browser again, and Settings shows `RADIO BROWSER`.
- Example: choose WebRadioDB, then Radio Browser again; the button goes; back changes nothing.
- Example: choose WebRadioDB; tap "Restart to apply"; the restart seam records one restart with
  WebRadioDB active.

### Rule: buffering stores on Save only when every value is valid (REQ-015)

- Example: change min buffer to a valid value, tap Save; all four are stored, Settings shows
  `CUSTOM`.
- Example: set max buffer below min buffer, tap Save; nothing is stored; the error shows under
  max buffer; the screen stays.
- Example: type valid values, tap ✕; nothing is stored.
- Example: tap "Restore defaults", tap Save; the defaults are stored, Settings shows `DEFAULT`.

### Rule: user agent empty custom value is not stored (REQ-013)

- Example: switch on, field cleared, back; the previous custom value is kept, the custom flag is
  on, and while the field was empty the error line showed.
- Example: switch on, type `MyAgent/1.0 `, back; `MyAgent/1.0` is stored.

### Rule: master volume steps by 5 within 0..100 (REQ-010)

- Example: 100, tap −; 95 is stored and sent.
- Example: 73, tap +; 78. At 98, tap +; 100. At 0, − is disabled.

### Rule: Bluetooth auto play stores only with permission on 12+ (REQ-011)

- Example: Android 13, permission refused; the switch is off, nothing stored.
- Example: Android 13, permission granted; true is stored, the switch is on.
- Example: Android 11; true is stored with no request.

## Acceptance Criteria

- **AC-1.** Overflow → Settings opens the Compose Settings screen; every former General, Source,
  Network, Stream buffering, File storage and About function works from it as before, apart from
  D1-D9. (REQ-001, REQ-003-REQ-019)
- **AC-2.** Each row shows its current value as REQ-004 lists, and each change is stored under
  the same key with the same meaning; an existing user's settings appear unchanged after the
  update. (REQ-004, REQ-005)
- **AC-3.** Back from any sub-screen returns to Settings, back from Settings returns to the main
  screen as it was, About from the overflow returns to the main screen, and rotation keeps the
  open screen and its typed values. (REQ-001, REQ-002, REQ-013, REQ-015)
- **AC-4.** The main overflow holds exactly Settings, Sleep timer, Equalizer, About, and the Now
  playing overflow is unchanged. (REQ-003)
- **AC-5.** Source changes show "Restart to apply" and revert on leave; buffering stores on Save
  only, with errors under the fields; an empty custom user agent is not stored. (REQ-008,
  REQ-013, REQ-015, REQ-016)
- **AC-6.** Export and import work through activity result launchers in today's file format; the
  diagnostic report goes through the warning sub-screen and the same `ACTION_SEND` chooser with
  the `FileProvider` zip. (REQ-017, REQ-018)
- **AC-7.** The battery dialog and its call are gone and nothing replaces it; the seven dialogs,
  the eight layouts, `CountriesArrayAdapter`, their `clearDialogs` lines and their
  `JourneyDialogs` uses are deleted, with nothing left unreferenced. Android Auto, the service,
  the browse tree and the `MediaId`s are unchanged. (REQ-020)
- **AC-8.** This build creates no stage 5 beads: fnr-hkpbe, fnr-uo578 and fnr-baige are not
  duplicated, and their edges and fnr-fml's link to fnr-uo578 are as the owner left them.
  (REQ-021)
- **AC-9.** The JVM, Roborazzi and instrumented tests of REQ-022 exist and pass; the owner has
  reviewed the new Roborazzi baselines in both themes; `./gradlew test assembleDebug
  :app:assembleDebugAndroidTest` and `./gradlew check` pass. (REQ-022)
- **AC-10.** Nothing under `console-design/` is edited and no navigation library, DI framework or
  bottom sheet is added. (REQ-001, REQ-016)

## Out Of Scope

- Sleep timer and Equalizer screens (build B, fnr-hkpbe), station actions, form and remove
  confirmation and fnr-fml (build C, fnr-uo578), search and the deletion of `BaseDialogFragment`,
  `clearDialogs`, `showDialog` and `JourneyDialogs` (build D, fnr-baige).
- Filing, editing or closing any bead for builds B, C and D, or closing fnr-fml.
- Now playing's layout (fnr-dm6fz), Android Auto, `OpenRadioService`, the browse tree and the
  `MediaId`s.
- New settings, or changes to what a setting stores or means. Only how values are shown is new.
- Moving storage away from `AppPreferencesManager`, `NetworkSettingsStorage` or `CountrySetting`.
- Bottom sheets, a slider, an error colour or a confirmation dialog in console-design; any edit
  under `console-design/`.
- Any battery optimisation prompt or banner.
- Keeping a settings screen open across process death.

## Open Questions

None block planning. The design left these points open, and this spec settles them as follows.
The owner may change any of them at the spec gate:

- **O1.** Master volume steps from the stored value without rounding, clamped to 0..100, and each
  tap stores and sends the command (REQ-010).
- **O2.** Stream buffering shows `DEFAULT` only when all four values equal their defaults;
  user agent shows `CUSTOM` from the custom flag alone (REQ-004).
- **O3.** A cross-field buffering error shows under the second-named field (REQ-015).
- **O4.** About opened from the overflow is pushed alone, so back returns to the main screen
  (REQ-002).
- **O5.** "Cache cleared" is a `SafeToast`, like the app's other short messages (REQ-014).
- **O6.** Activity recreation neither reverts a pending source choice nor stores the user agent;
  only leaving does (REQ-008, REQ-013).
- **O7.** Strings and drawables left unreferenced by the deletions are removed in every locale
  (REQ-020).
- **O8.** A cancelled export or import still shows the failure message, as today; showing nothing
  would be a behaviour change the design did not agree (REQ-017).
