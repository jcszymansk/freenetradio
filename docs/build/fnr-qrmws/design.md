# Design candidate: settings and the remaining dialogs in Compose (stage 5)

- Source bead: fnr-urmil (stage 5, the last stage of the Compose migration), build target
  `fnr/settings-dialogs`
- Workflow root: fnr-qrmws; lane bead: fnr-igl1f (iteration 1)
- Approval mode: interactive
- Approval status: **approved** by the owner on 2026-11-27 (gate bead fnr-y3xhu). Q0 = no
  visual companion; Q1-Q16 take the recommended answers as written; D1-D9 are agreed.

## Checklist state

1. Project context inspected: done (see "Context found").
2. Visual companion: layout questions are coming (settings organisation, sub-screens versus
   sheets, the station form). The owner declined the companion in builds fnr-pi6ap and fnr-8i7gz.
   Those answers covered other builds, so the offer is queued again as Q0, in its own message.
3. Clarifying questions: queued as Q0-Q16, one question each, in the order they should be asked.
   Scope (Q1) comes first, because the other answers depend on it. This headless lane cannot
   wait for answers, so the approval lane asks them.
4. Approaches: three, with a recommendation (see "Approaches"). Q2 asks the owner to confirm it.
5. Design sections: drafted from the recommended answers, at two depths. The whole stage gets one
   placement table, so the later builds are filed with agreed decisions. Build A (this build,
   if Q1 = a) is designed in detail.

## Requested outcome

Every View dialog still opened by `MainActivity` opens in Compose with the Console design, from
the same entry point or from the one this design names. Each works as before, follows
`console-design/DESIGN.md` except where the owner agrees to a difference, and the replaced
dialogs, fragments and layouts are deleted. Stored settings keep their keys and meanings.

## Decided by the owner (fnr-urmil)

- Start-up playback stays a three-way choice in General settings: Always / If it was playing
  (default) / Never (fnr-lka81).
- Now playing stays as it is; its redesign is the deferred bead fnr-dm6fz.
- DESIGN.md changes and new components go to the console-design repository, followed by a
  submodule bump. Files under `console-design/` are never edited here.
- Android Auto, `OpenRadioService`, the browse tree, the `MediaId`s and the stored settings' keys
  and meanings do not change.
- `BaseDialogFragment`, `UiUtils.clearDialogs` and the dialog layouts are deleted once nothing
  uses them. The bead says "delete them"; Q16 asks only which build does it.

## Context found

- **Entry points** (`MainActivity`, 254 lines): `onOverflow(OverflowItem)` opens GENERAL, SOURCE,
  NETWORK, BUFFERING, SLEEP_TIMER, EQUALIZER, FILE_STORAGE and ABOUT. `onPlayerMenu` opens
  SLEEP_TIMER and EQUALIZER from Now playing. `onSearch` opens SearchDialog. `onRowLongClick`
  opens RSSettingsDialog with the item, its parent id and the list's item count. `onAddStation`
  (the FAB) opens AddStationDialog. All of them go through `showDialog`, which calls
  `UiUtils.clearDialogs` and `BaseDialogFragment.newInstance(className)` by reflection.
- **Screen switching today:** `setContent` shows `NowPlayingScreen` when
  `state.playerScreen != NONE` and `MainScreen` otherwise. `PlayerScreen` (NONE, NOW_PLAYING,
  STATION_DETAILS) lives in `MainScreenModel` (558 lines), and `back()` pops it before the tab
  stack. No navigation library is in the build; stage 4 decided against adding one (its Q8).
- **GeneralSettingsDialog** (272 lines) holds eight unrelated things: start playback (radio group,
  `AppPreferencesManager.startPlayback`), a custom user agent (checkbox plus text field, saved in
  `onPause`; an empty value shows a toast and is not stored), master volume (SeekBar 0-100,
  default 100, sent as `CMD_MASTER_VOLUME_CHANGED` when the thumb is released), Bluetooth auto
  play (checkbox; on Android 12+ it asks for BLUETOOTH_CONNECT and stores nothing until that is
  granted), clear cache (`CMD_CLEAR_CACHE`, with no feedback), the default country (a spinner over
  `LocationService.getCountriesWithLocation`, through `CountrySetting`), and the diagnostic report
  (an AlertDialog warning, then a progress bar, then an `ACTION_SEND` chooser with a
  `FileProvider` zip).
- **SourceDialog:** one radio button per source. Choosing one calls `setActiveSource` at once and
  shows a restart button, which restarts the app with `Runtime.exit(0)`. Closing the dialog
  without restarting puts the old source back (`onDestroy`).
- **NetworkDialog:** one checkbox, "use mobile network" (`NetworkSettingsStorage`), which sends
  `CMD_NET_CHANGED`.
- **StreamBufferingDialog:** four numeric fields (min buffer, max buffer, play buffer, play buffer
  after rebuffer) and a "restore defaults" button. The fields are validated and stored in
  `onPause`, with a toast for the first error: a value that is empty, not digits or out of range,
  max below min, or a play buffer above min. The journey
  `recreatingTheActivityWithTheBufferingDialogUpStoresWhatTheUserHadTyped` relies on this
  save-on-leave behaviour.
- **FileStorageDialog:** two buttons, upload (`ACTION_CREATE_DOCUMENT`, which writes the stations
  to a file) and download (`ACTION_OPEN_DOCUMENT`, which reads them back), through
  `FileStoreManager`. It uses the deprecated `Fragment.onActivityResult` and reports success or
  failure in a toast.
- **AboutDialog:** the version (`name.code`), the Media3 version, and eight links opened with
  `ACTION_VIEW`.
- **BatteryOptimizationDialog:** `handle()` only logs whether the app ignores battery
  optimisations; it **never shows anything** (upstream commit 0c25ff9). The dialog itself
  inflates `dialog_about`. Users see no battery prompt today.
- **SleepTimerDialog:** an on/off toggle, plus date and time buttons that open
  `DatePickerFragment` and `TimePickerFragment`, through `SleepTimerModel`. The time is formatted
  with a hard-coded `hh:mm`, which breaks DESIGN.md's system 12/24-hour rule.
- **EqualizerDialog:** a preset spinner plus per-band SeekBars that are **disabled**, so they only
  display the levels. It shows "not available" when there is no equaliser state.
- **SearchDialog:** a text field and a button. An empty query shows a toast; otherwise it calls
  `BrowseSearch.search(query)`.
- **RSSettingsDialog:** Edit and Remove are shown only for local stations (parent
  `MEDIA_ID_LOCAL_RADIO_STATIONS_LIST`). Sorting is shown only for sortable parents, as a
  NumberPicker 0..itemsCount that sends `CMD_UPDATE_SORT_IDS` in `onPause`, then
  `BrowseReload.reload()`.
- **Add/EditStationDialog** (`BaseAddEditStationDialog`, 277 lines): name, stream URL, home page,
  image (a local path from `ACTION_GET_CONTENT` through `ImageFilePath`), country and genre
  spinners, and an "add to favourites" checkbox. It asks for storage permission in `onResume`.
  Results come back as toast messages from `RadioStationManagerLayer`. fnr-fml: a bad home page
  reports a failure and then a success for the same add.
- **RemoveStationDialog:** "Remove <name>?" with Remove and Cancel, then `updateBrowseTree()`.
- **console-design** provides `SettingRow(title, value, onClick)`, `SettingSwitchRow`,
  `SectionHeader`, `FieldLabel`, `ConsoleTextField` (with no error state), `PickerField`,
  `SegmentedPicker`, `WarningBanner`, `KeyValueTable`, `ConsoleTopBar`, `SmallPrimaryButton`,
  `PrimaryButton`, `GhostIconButton` and `ConsoleSwitch`. It has **no bottom sheet, no slider,
  no confirmation dialog and no list-choice row**, and no colour token for a field error (`danger`
  is for destructive text actions, `warn*` only for the battery banner). `ConsoleTheme` wraps
  `MaterialTheme` with a Console colour scheme, so M3 `DropdownMenu`, as used by the overflow
  menus, picks up Console colours.
- **design-reference/png** has no FreeNetRadio settings, form or sheet images. The nearest are
  `ntrecv-settings-*` (a `WarningBanner`, then sections of `SettingRow`/`SettingSwitchRow`) and
  `msgsched-edit-*` (✕, title, Save; `FieldLabel` plus fields).
- **Tests that drive the dialogs:** `JourneyDialogs` (View ids) is used by
  `SettingsPersistenceJourneyTest`, `CountryChangeJourneyTest`, `DiagnosticReportJourneyTest` and
  `LocalStationLifecycleJourneyTest`. Roborazzi baselines exist for main and Now playing only.
- **Coverage gate:** a new class with a branch or 20 lines in a package that already holds a
  listed pure-core class, for example `presenter/main`, fails `verifyPureCoreCoverage` until
  `gradle/pure-core-coverage.tsv` lists it.

## Constraints

- console-design rules; no edits under `console-design/`. A new component (sheet, slider, error
  state, confirmation dialog) means a console-design change and a submodule bump **before** the
  build that uses it.
- No DI framework (`*Dependency` / `configureWith`); no navigation library (stage 4 Q8), unless
  the owner chooses approach C.
- Tests: hand-written fakes, no network, and Roborazzi baselines in both themes that the owner
  reviews. There are no reference PNGs for these screens, so the owner reviews them against
  DESIGN.md and the ntrecv-settings and msgsched-edit images.
- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass, and
  the instrumented journeys pass on an offline, cleared emulator.
- The diagnostic report still leaves the app only through a per-report `ACTION_SEND` chooser
  with a `FileProvider` attachment.

## Non-goals

- Now playing's layout (fnr-dm6fz), Android Auto, the service, the browse tree and the `MediaId`s.
- New settings, or changes to what a setting means. The values a setting shows are new; what it
  stores is not.
- Moving storage away from `AppPreferencesManager`, `NetworkSettingsStorage` or `CountrySetting`.
- Making the equaliser bands editable. They are display-only today and stay so.

## Approaches (the screen host; Q2)

**A. A small destination stack beside `MainScreenModel` (recommended).** Add a plain class in
`:common-ui` (working name `ScreenStack`) that holds a stack of `Destination`s. `Destination` is
a sealed type: `Settings`, `SettingsChoice(kind)`, `StreamBuffering`, `UserAgent`, `Diagnostics`,
`About`, and in later builds `SleepTimer`, `Equalizer`, `StationForm(mediaId?)` and `Search`. It
is a registry singleton like `MainScreenModel`, so it survives rotation. `MainActivity`'s
`setContent` shows the top destination when there is one, then Now playing, then the main
screen. System back pops the stack before `mModel.back()`. Each destination has its own small
state holder (`SettingsModel`, ...) over narrow interfaces to the existing stores, so its logic
runs in JVM tests with fakes.
- For: `MainScreenModel` stays at its size and responsibility; each screen is a function of its
  own state, so Roborazzi renders it from fixed data; later builds add a `Destination` and a
  screen without touching browse code.
- Against: two back-handling sources (`ScreenStack`, then `MainScreenModel`), ordered in one
  place in the activity.

**B. Grow `PlayerScreen` into a general screen enum inside `MainScreenModel`.**
- For: a single back handler; it extends the pattern stage 4 started.
- Against: the model passes 600 lines, and browse, player and settings logic end up in one class
  and one test file. Every later build edits it.

**C. Add Navigation Compose.**
- For: back stack, arguments and process recreation come from the library.
- Against: it reverses stage 4's decision, adds a dependency, and makes the player screens
  (state-driven today) and the settings (route-driven) work in two different ways.

Recommendation: **A**.

## Design (approved)

### 1. Split into builds (Q1, Q16)

| Build | Contents | Deletes |
|---|---|---|
| **A** (this one, fnr-urmil) | `ScreenStack` host; Settings screen and its sub-screens (General, Source, Network, Buffering, File storage, Diagnostics, About); the battery prompt decision (Q11) | GeneralSettingsDialog, SourceDialog, NetworkDialog, StreamBufferingDialog, FileStorageDialog, AboutDialog, BatteryOptimizationDialog; their layouts (`dialog_general_settings`, `dialog_source`, `source_view`, `dialog_network`, `settings_stream_buffering`, `dialog_file_storage`, `dialog_about`, `countries_spinner_item`) and `CountriesArrayAdapter`, which only GeneralSettingsDialog uses; their `clearDialogs` lines; their `JourneyDialogs` uses |
| **B** (new bead) | Sleep timer and Equalizer screens, from both overflows | SleepTimerDialog, DatePickerFragment, TimePickerFragment, EqualizerDialog, EqualizerPresenter* if unused, `dialog_sleep_timer`, `dialog_eq` |
| **C** (new bead) | Station actions on long press, the Add/Edit station screen, the Remove confirmation; fnr-fml folded in (Q15) | RSSettingsDialog, BaseAddEditStationDialog, Add/EditStationDialog, RemoveStationDialog (replaced by its Compose form), their presenters if unused, `dialog_rs_settings*`, `dialog_add_edit_station`, `dialog_remove_station` |
| **D** (new bead, blocked by B and C) | Search in the top bar; final cleanup | SearchDialog, `dialog_search`, `BaseDialogFragment`, `UiUtils.clearDialogs`, `MainActivity.showDialog`, `JourneyDialogs` |

Each build deletes what it replaces. D runs last and removes the shared dialog machinery.
The later beads get `blocks` edges in landing order (A → B → C → D) and carry their placement
rows below as decided.

### 2. Where each dialog goes (Q3, Q5, Q11-Q14)

| Today | Becomes | Reached from |
|---|---|---|
| GeneralSettingsDialog | Rows in Settings' sections (section 3) | Overflow → Settings |
| SourceDialog | Settings › Source, a choice sub-screen | Settings |
| NetworkDialog | A switch row in Settings | Settings |
| StreamBufferingDialog | Settings › Stream buffering, an edit sub-screen | Settings |
| FileStorageDialog | Two rows in Settings: Export stations, Import stations | Settings |
| AboutDialog | Settings › About, a sub-screen with a `KeyValueTable` | Settings, and the overflow (Q5) |
| BatteryOptimizationDialog | Deleted; it shows nothing today (Q11) | none |
| SleepTimerDialog (+ pickers) | Sleep timer screen: switch row, date and time `PickerField`s opening the platform pickers | Main overflow and Now playing overflow (as now) |
| EqualizerDialog | Equalizer screen: preset choice rows and a mono band table | Both overflows (as now) |
| SearchDialog | A search field that replaces the top bar's title | The top bar search icon (as now) |
| RSSettingsDialog | A menu anchored at the long-pressed row: Edit, Remove (local stations), Move up, Move down (sortable lists) (Q13) | Long press (as now) |
| AddStationDialog | Station form edit screen (✕, title, Save) | The FAB (as now) |
| EditStationDialog | The same form, filled in | Row menu → Edit |
| RemoveStationDialog | Stays a dialog, the one destructive confirmation, restyled (Q12) | Row menu → Remove |

There are no bottom sheets in stage 5 (Q3): every item is a screen or a sub-screen built from
existing components, so no build waits for a console-design sheet.

### 3. Settings screen (build A; Q4, Q6)

One root Settings screen (`ConsoleTopBar` with a back arrow and "Settings"), sections as
`SectionHeader`s and rows as `SettingRow`/`SettingSwitchRow`. Every value is mono.

| Section | Row | Kind | Value shown | Opens / does |
|---|---|---|---|---|
| PLAYBACK | Start playback | `SettingRow` | `IF PLAYING` / `ALWAYS` / `NEVER` | Choice sub-screen |
| | Master volume | `SettingRow` | `100%` | Volume sub-screen (Q7) |
| | Bluetooth auto play | `SettingSwitchRow` | switch | Toggles; asks for BLUETOOTH_CONNECT on 12+ as today, stores only once it is granted |
| | Stream buffering | `SettingRow` | `DEFAULT` or `CUSTOM` | Edit sub-screen (Q8) |
| STATIONS | Source | `SettingRow` | source name, e.g. `RADIO BROWSER` | Choice sub-screen |
| | Default country | `SettingRow` | country code, e.g. `PL` | Choice sub-screen (name, code in mono) |
| | Export stations | `SettingRow` | none | `ACTION_CREATE_DOCUMENT` |
| | Import stations | `SettingRow` | none | `ACTION_OPEN_DOCUMENT` |
| NETWORK | Use mobile data | `SettingSwitchRow` | switch | Stores, sends `CMD_NET_CHANGED` |
| | User agent | `SettingRow` | `DEFAULT` or `CUSTOM` | Sub-screen: switch row "Custom user agent" plus a `FieldLabel`/`ConsoleTextField` |
| | Clear cache | `SettingRow` | none | `CMD_CLEAR_CACHE`, then a short "Cache cleared" message |
| DIAGNOSTICS | Send diagnostic report | `SettingRow` | none | Diagnostics sub-screen (Q10) |
| ABOUT | About FreeNetRadio | `SettingRow` | version, e.g. `1.4.0.27` | About sub-screen |

- **Choice sub-screen:** one app-local composable, used for start playback, source and country
  (and in build B for EQ presets). It shows a back arrow and the setting's name, then rows with
  hairlines; the selected row's label is in accent, as DESIGN.md allows for "the selected
  segment". Tapping a row stores the choice and returns. **Source** is the exception, because it
  needs a restart: choosing another source shows one `PrimaryButton` "Restart to apply" below the
  list, and leaving without restarting puts the old source back, as `SourceDialog` does now.
- **About sub-screen:** `KeyValueTable` with VERSION, MEDIA3 and link rows (PROJECT, ISSUES,
  RADIO BROWSER, WEBRADIODB, PLAYLIST PARSER, SWIPE, FONTS), opened with `ACTION_VIEW`. The links
  and texts stay the same.
- **Export/import:** started from the activity through `rememberLauncherForActivityResult`; the
  deprecated `onActivityResult` and the `DialogFragment` overloads of `FileStoreManager` go. A
  thin accent `ProgressLine` runs while a file is written or read, and the result is the same
  success or failure message as today.
- **Stored keys** do not change: every row reads and writes through the same
  `AppPreferencesManager`, `NetworkSettingsStorage`, `CountrySetting` and `SourcesLayer` calls the
  dialogs use, behind interfaces the `SettingsModel` takes in `configureWith`.

### 4. Edit sub-screens and errors (build A; Q8, Q9)

- **Stream buffering:** an edit screen with ✕, the title "Stream buffering", and a
  `SmallPrimaryButton` "Save" in the top bar. Four `FieldLabel`/`ConsoleTextField` (mono,
  numeric keyboard) fields with their units, and "Restore defaults" as a text action. Save
  checks the same rules as today, in one pure function taken out of
  `StreamBufferingDialog.handleOnPause` with the bounds passed in. It stores and returns when the
  values are valid, and otherwise shows the error under the field it concerns. ✕ and back leave
  without storing (difference D4). The range explanation (`stream_buffering_descr`) becomes one
  short line under the fields.
- **User agent:** the text is stored when the screen is left, as today; an empty custom value is
  not stored, and the screen says so under the field.
- **Error display (Q9, recommended b):** one line under the field, `Console.type.small` in
  `textSecondary`, with an outlined error icon. DESIGN.md has no error colour, so a later
  console-design change can give `ConsoleTextField` an error state and replace this line. The
  station form in build C uses the same line.

### 5. Diagnostics (build A; Q10)

A sub-screen "Diagnostic report": the existing warning text (`diagnostic_report_warning`), then
one `PrimaryButton` "Create report". While the logs are collected it shows a progress line and
the button is disabled; when they are ready, the same `ACTION_SEND` chooser opens with the same
`FileProvider` zip; a failure shows the same failure message. This replaces the AlertDialog,
which DESIGN.md does not allow for anything that is not destructive (difference D5).

### 6. Overflow and top bar after build A (Q5)

The main overflow becomes **Settings, Sleep timer, Equalizer, About**: the five settings entries
merge into one, and About stays one tap away from the main screen (difference D6). The Now playing
overflow does not change.

### 7. Later builds, at the depth needed to file them (Q12-Q15)

- **B. Sleep timer:** a sub-screen with `SettingSwitchRow` "Sleep timer" and, when it is on,
  `FieldLabel` DATE and TIME `PickerField`s that open the platform date and time pickers (the
  pickers are system widgets, not app dialogs). The time follows the system 12/24-hour setting,
  which fixes the hard-coded `hh:mm`. Leaving arms or disarms the timer through
  `SleepTimerModel.updateTimer`, and a failure is reported as today. **Equalizer:** the preset
  choice rows, then a `KeyValueTable` of band frequency to level in dB (mono, display-only as
  today), or one line "Equalizer not available" when there is no state.
- **C. Station actions:** long press opens an M3 `DropdownMenu` (the overflow's look) anchored at
  the row, with Edit and Remove for local stations and Move up and Move down for sortable lists
  (difference D7; the NumberPicker goes). Each move sends `CMD_UPDATE_SORT_IDS` with the new
  position, then reloads. **Station form:** ✕, "Add station" / "Edit station", Save; fields NAME,
  STREAM URL, HOME PAGE, IMAGE (`PickerField` opening `ACTION_GET_CONTENT`), COUNTRY and GENRE
  (`PickerField`s opening the choice sub-screen), and a switch row "Add to favourites". Required
  fields are checked on Save with the error line from section 4; the validator's answer
  (`onSuccess`/`onWarning`/`onFailure`) is shown once: failure under the form, success as one
  message on return, and a warning (fnr-fml: home page unreachable) in that same success message.
  **Remove:** an M3 `AlertDialog` under `ConsoleTheme`, with "Remove <name>?", a Remove action in
  `danger` and Cancel (Q12).
- **D. Search:** tapping the search icon turns the top bar into a back arrow and a
  `ConsoleTextField` with the keyboard up. The IME search action runs `BrowseSearch.search` and
  shows the results as today, and an empty query does nothing. Back closes the field. Then the
  cleanup listed in section 1.

### 8. Testing (build A)

- JVM (`:common-ui`): `ScreenStack` (push, pop, back order before the browse stacks, cleared when
  the app data is cleared); `SettingsModel` (each row's value from fakes, each write going to the
  right store with the same value, the Bluetooth permission path, source choice and revert on
  leave, user agent empty and non-empty); the buffering validation function (every rule and
  bound); the About rows. The new classes are listed in `gradle/pure-core-coverage.tsv`.
- Roborazzi, dark and light: Settings with defaults, Settings with changed values, each choice
  sub-screen, Source with "Restart to apply", Stream buffering valid and with an error, User agent
  with an empty custom value, Diagnostics idle and collecting, About. The owner reviews them
  against DESIGN.md and the ntrecv-settings and msgsched-edit images.
- Instrumented (offline, cleared emulator): `SettingsPersistenceJourneyTest`,
  `CountryChangeJourneyTest` and `DiagnosticReportJourneyTest` drive the Compose screens by test
  tag instead of View ids. The buffering journey changes from "recreated with the dialog up
  stores what was typed" to "Save stores, ✕ does not" (if Q8 = a). New: back from every
  sub-screen returns to Settings, and back from Settings returns to the main screen as it was.
  Export and import run against a recorded launcher seam, with no real document UI.

## Differences from DESIGN.md and today's behaviour (each needs the owner's agreement)

- **D1. No bottom sheets.** DESIGN.md allows a screen or a bottom sheet; this design uses only
  screens and sub-screens (Q3).
- **D2. Choice rows.** DESIGN.md has no list-choice component; choices are app-local rows with the
  selected label in accent (section 3).
- **D3. Master volume control** depends on Q7. The recommended stepper is not a DESIGN.md
  component.
- **D4. Stream buffering saves on Save.** Today it saves on leave, whatever closes the dialog.
- **D5. Diagnostics confirmation** becomes a sub-screen instead of an AlertDialog.
- **D6. Overflow** shrinks from eight entries to four (Settings, Sleep timer, Equalizer, About).
- **D7. Sorting** becomes Move up / Move down in a row menu instead of a position picker (build C).
- **D8. Remove confirmation** uses an M3 `AlertDialog` under `ConsoleTheme`, because
  console-design has no dialog component (build C; Q12).
- **D9. Field errors** are a `textSecondary` line with an icon, because DESIGN.md has no error
  colour (Q9).

## Question queue (iteration 1; for the owner, one at a time, in this order)

**Q0 (visual companion, in its own message):** "Some of what we're working on might be easier to
explain if I can show it to you in a web browser. I can put together mockups, diagrams,
comparisons, and other visuals as we go. This feature is still new and can be token-intensive.
Want to try it? (Requires opening a local URL)"

**Q1 (one build or several):** "Stage 5 has fourteen dialogs. Split it into four builds?
(a) A: the settings screens and the battery prompt (this build); B: sleep timer and equaliser;
C: station add, edit, remove and sort, with fnr-fml folded in; D: search, then deleting
`BaseDialogFragment`, `clearDialogs` and the rest of the dialog machinery. B, C and D are filed as
beads that land in that order (recommended). (b) Two builds: settings, sleep timer and EQ; then
stations, search and cleanup. (c) One build for everything."

**Q2 (screen host):** "How do the new screens sit in the app? (a) A small destination stack next
to the main screen's model: settings and the later screens are pushed onto it, back pops it
first, and `MainScreenModel` stays as it is (approach A, recommended). (b) Grow Now playing's
screen enum in `MainScreenModel` to cover every screen (approach B). (c) Add the Navigation
Compose library (approach C), which reverses the stage 4 decision."

**Q3 (bottom sheets):** "DESIGN.md allows a screen or a bottom sheet for settings, sleep timer, EQ
and source. (a) Screens and sub-screens only in stage 5, so nothing waits for console-design
(recommended). (b) Sleep timer and EQ as bottom sheets over the current screen, with a sheet
component added to console-design before build B. (c) Bottom sheets for source and the choice
lists too."

**Q4 (settings organisation):** "(a) One Settings screen with PLAYBACK, STATIONS, NETWORK,
DIAGNOSTICS and ABOUT sections, where every row that is not a switch opens a sub-screen
(recommended; see the table in section 3). (b) A Settings screen that lists groups (General,
Source, Network, Buffering, Storage), each opening its own sub-screen, mirroring today's menu.
(c) Keep separate overflow entries, each opening its own screen."

**Q5 (main overflow after build A):** "(a) Settings, Sleep timer, Equalizer, About, with About
also at the bottom of Settings (recommended). (b) Settings, Sleep timer, Equalizer, with About only
inside Settings. (c) Settings as a gear icon in the top bar, and the overflow keeps Sleep timer,
Equalizer and About."

**Q6 (values on the rows):** "Do the rows show these values, in mono: start playback `IF PLAYING`,
master volume `100%`, stream buffering `DEFAULT`/`CUSTOM`, source name, default country code,
user agent `DEFAULT`/`CUSTOM`, About the version, and no value on Export, Import, Clear cache and
Diagnostics? (a) Yes (recommended). (b) Different (say which)."

**Q7 (master volume control):** "console-design has no slider. (a) A − / + stepper in 5 % steps
with the value in mono, built from `GhostIconButton`, so nothing waits on console-design
(recommended). (b) Add a `ConsoleSlider` to console-design before this build, and use it.
(c) An M3 `Slider` coloured from `Console.colors`, as an app-local component."

**Q8 (stream buffering saving):** "(a) An edit screen with ✕ and Save: Save validates and stores,
and ✕ or back discards (recommended; DESIGN.md's edit-screen pattern). (b) As today: it stores on
leave if valid, and shows an error message otherwise."

**Q9 (field errors, this build and the station form):** "DESIGN.md has no error colour and
`ConsoleTextField` has no error state. (a) Add an error state (and its colour rule) to
console-design first, then use it. (b) An app-local line under the field in secondary text with an
outlined error icon, replaced later if console-design gains an error state (recommended).
(c) Keep the toasts."

**Q10 (diagnostic report):** "(a) A Diagnostics sub-screen with the warning text and one 'Create
report' button, then the same share chooser (recommended). (b) Keep the confirmation as a dialog,
restyled."

**Q11 (battery optimisation):** "`BatteryOptimizationDialog.handle` only writes a log line; no
prompt has been shown since the upstream code was cut down. (a) Delete it and show nothing, as
today (recommended: playback runs in a foreground service, and no user has seen this prompt in this app).
(b) Delete it and add a `WarningBanner` at the top of Settings while the app is
battery-optimised, with an action that opens the system's battery settings. (c) Show a prompt on
start."

**Q12 (remove confirmation, build C):** "(a) Remove stays a dialog, the one destructive
confirmation, as an M3 `AlertDialog` under `ConsoleTheme` with Remove in the danger colour
(recommended). (b) The same, with a `ConsoleConfirmDialog` added to console-design first.
(c) No dialog: remove at once and show an Undo message."

**Q13 (long press, build C):** "(a) A menu at the row: Edit and Remove for your own stations,
Move up and Move down in sortable lists (recommended). (b) A 'Station' sub-screen with Edit,
Remove and a position field, closest to today's dialog. (c) A menu as in (a), with 'Move to
position…' opening a number field instead of up and down."

**Q14 (search, build D):** "(a) The search icon turns the top bar into a text field with a back
arrow; the keyboard's search key runs the query (recommended). (b) A Search sub-screen with a
field and a button."

**Q15 (fnr-fml, build C):** "(a) Fold it into build C: a station whose stream is fine but whose
home page cannot be loaded is added and reported once, with one success message that says the home
page could not be loaded (recommended; it is the bug's groomed default). (b) Leave fnr-fml as its
own bead."

**Q16 (cleanup):** "`BaseDialogFragment`, `UiUtils.clearDialogs`, `MainActivity.showDialog` and
`JourneyDialogs` are deleted by the last build to land (D if Q1 = a), and each earlier build
deletes its own dialogs, layouts and `clearDialogs` lines. (a) Yes (recommended). (b) Different."

## Answered questions

Answered by the owner on 2026-11-27:

- Q0: no visual companion.
- Q1-Q16: the recommended option (a) for each, as written in "Question queue".
- D1-D9: all agreed.

## Unresolved questions

None.

## Risks to check in planning

- `PermissionChecker.requestBluetoothPermission(activity, view)` takes a View, probably for a
  snackbar; the Compose path needs an activity-level equivalent.
- `FileStoreManager`'s `DialogFragment` overloads and its `onActivityResult` request codes must
  move to activity result launchers without changing the file format.
- The source restart (`Runtime.exit(0)`) inside an instrumented test kills the test process; the
  journey must stop at "Restart to apply" or go through a recorded restart seam.

## Acceptance criteria (from fnr-urmil, for build A if Q1 = a)

- Overflow → Settings opens the Compose Settings screen; every former General, Source, Network,
  Buffering, File storage and About function works from it as before, apart from the agreed
  differences.
- Each row shows its current value as agreed in Q6, and changing a value stores it under the same
  key with the same meaning; Android Auto, the service, the browse tree and the `MediaId`s are
  unchanged.
- Back from any sub-screen returns to Settings, and back from Settings returns to the main screen
  as it was; rotation keeps the open screen.
- The battery prompt is handled as Q11 decides.
- GeneralSettingsDialog, SourceDialog, NetworkDialog, StreamBufferingDialog, FileStorageDialog,
  AboutDialog and BatteryOptimizationDialog, with their layouts and `clearDialogs` lines, are
  deleted.
- Beads for builds B, C and D are filed with the decisions from this design and `blocks` edges in
  landing order (if Q1 = a).
- `./gradlew test assembleDebug :app:assembleDebugAndroidTest` and `./gradlew check` pass; the
  instrumented journeys pass on an offline, cleared emulator; the owner has reviewed the new
  Roborazzi baselines in both themes.
