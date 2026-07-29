# Roadmap

OpenRadio is being revived as a trustworthy, local-first internet radio application. The immediate purpose is personal use, particularly through Android Auto. Public distribution comes only after that version is stable and useful.

The roadmap has three targets:

1. A reliable personal-use application
2. An F-Droid release
3. A Play Store release, only if demand justifies the work

## Guiding decisions

- Support phone/tablet Android and Android Auto.
- Abandon the Android TV and native Android Automotive OS application targets. Git history remains the recovery path if a future maintainer volunteers to own and test either target.
- Keep shared car-facing MediaBrowser code until Android Auto testing proves that it is unused.
- Use a new application ID, signing identity, name, and branding before public distribution.
- Preserve the Apache 2.0 license, `NOTICE`, existing copyright notices, and source provenance.
- Prefer local functionality over accounts or hosted services.
- Do not reuse signing material, Firebase configuration, credentials, or backends associated with a previous owner.
- Do not include telemetry, advertising, automatic crash submission, or automatic log upload.
- Diagnostic sharing must be initiated for each report and completed by an external application chosen by the user.

## Target 1: reliable personal use

### Outcome

A clean checkout builds without private files, installs under a new identity, plays internet radio reliably on a phone, and provides a usable Android Auto media experience. The application makes no unexpected connections beyond the station-directory and selected stream services.

### 1. Preserve the recovered baseline

- Record the source release and provenance in the repository.
- Tag the recovered state before substantial modernization.
- Retain `LICENSE`, `NOTICE`, and applicable file-level copyright notices.
- Treat checked-in APKs as historical artifacts; do not use them as build inputs.

### 2. Reduce the supported product surface

**Status: completed.**

- Remove `:tv` and `:automotive` from `settings.gradle`.
- Delete the `tv/` and `automotive/` application modules.
- Remove dependencies and configuration used exclusively by those modules.
- Keep the phone module's Android Auto metadata, `OpenRadioService`, Media3 session integration, and car-oriented browse behavior.
- Remove shared TV or Automotive code only after reference checks and an Android Auto smoke test show that it is not needed.
- Document phone/tablet and Android Auto as the only supported targets.

### 3. Establish an independent application identity

- Choose a new project and application name that cannot be confused with the commercial application.
- Change `applicationId` before creating user data or publishing builds.
- Replace package-dependent authorities, labels, icons, links, and contact details.
- Generate a new release signing key and store it outside the repository with an offline backup.
- Keep debug builds on Android's normal debug signing path.

### 4. Restore a clean, secret-free build

**Progress:** mandatory `sign.properties` loading, inherited signing assignments, unconditional Google Services and Crashlytics plugins, and direct SMTP logging have been removed. A debug APK now builds with Android's standard debug key.

- Make the Gradle wrapper build a debug APK from a fresh checkout.
- Stop `sign.gradle` from requiring `sign.properties` for ordinary builds.
- Require release signing configuration only when a signed release is explicitly requested.
- Remove the unconditional Google Services and Crashlytics plugin requirements.
- Do not add placeholder credentials or a dummy `google-services.json`.
- Update Gradle, Android Gradle Plugin, Kotlin, Media3, and Android SDK settings only as far as needed for a maintainable build and current devices.
- Document one canonical build command and its required JDK/Android SDK versions.

**Exit check:** a fresh checkout with no ignored secret files produces an installable debug APK using the documented command.

### 5. Remove inherited online services

**Status: completed.** Firebase Analytics, Crashlytics, Authentication, Firestore cloud backup, the hosted featured-stations feed, and their account/cloud UI have been removed. Google Cast was removed, and fused location was replaced with Android's platform location API. Local file import/export remains; the application has no Firebase or Google Play Services runtime dependencies.

Remove features tied to unavailable, untrusted, or unnecessary infrastructure:

- Firebase Analytics
- Firebase Crashlytics
- Firebase Authentication
- Firestore cloud backup
- Firestore-hosted featured stations
- Associated account and cloud-storage UI

Keep favorites, local stations, settings, and recent-station state on the device. Do not introduce a replacement backend during this target.

Review other Google dependencies individually:

- Remove Cast because it is not needed for the current personal-use target.
- Keep automatic country selection, but implement it with Android's platform location API.
- Remove dependencies that remain solely for a deleted feature.

**Exit check:** the resolved runtime dependency graph contains no Firebase or Google Play Services artifacts; normal browsing and playback require no Google/Firebase project.

### 6. Replace direct log email with explicit sharing

**Status: completed.** `LoggingLayerImpl` no longer reads packaged credentials or sends mail. The settings action asks for confirmation, collects the report, and opens an `ACTION_SEND` chooser with a `FileProvider` attachment. The user selects the recipient and confirms transmission in the external application.

Replace it with a user-mediated diagnostic flow:

1. The user selects **Prepare diagnostic report** in settings.
2. The application explains that the report may contain application logs, stream or station URLs, application version, and device/build information.
3. Only after confirmation, the application collects the report into its cache directory.
4. The report is exposed through `FileProvider`, never through a raw file path.
5. The application opens an Android `ACTION_SEND` chooser with the report attached and read permission granted to the receiving application.
6. The user chooses an email client or another application, reviews the draft, chooses recipients, and either sends or cancels it.

Required properties:

- No embedded SMTP username or password
- No Jakarta Mail dependency
- No application-owned email transport
- No background or automatic submission
- No persistent “always send logs” option
- A fresh, explicit action for every report
- No claim that opening the chooser means a report was sent
- Old report files cleaned from the application cache without deleting a file while a receiving application may still be reading it

Prefilling a support address, subject, application version, and short problem-report template is acceptable. The external application remains responsible for transmission and gives the user the final decision.

Review the collected fields before retaining them. Include only information useful for diagnosis; omit broad device identifiers and unrelated system logs where application-process logs are sufficient.

**Exit check:** without visible user interaction through the Android chooser, no diagnostic data can leave the application.

### 7. Restore core phone functionality

Validate and repair the smallest useful feature set:

- Browse categories and countries
- Search stations
- Play, pause, stop, and switch stations
- Display current station and stream metadata
- Add and remove favorites
- Add, edit, and remove local stations
- Persist the last station and user settings
- Handle direct streams and supported playlist formats
- Recover cleanly from unavailable or malformed streams
- Continue playback in the background with a correct foreground notification
- Pause or resume appropriately for audio focus, output changes, and Bluetooth events

Defer cloud synchronization, recommendation systems, accounts, telemetry, and new discovery features.

### 8. Validate Android Auto end to end

Use the actual phone and vehicle/head unit as the primary acceptance environment. The Desktop Head Unit may supplement, but not replace, that test.

Exercise at least:

- Application discovery by Android Auto
- Root and child browse nodes
- Countries, categories, favorites, and search
- Starting a station from the car display
- Play, pause, previous, next, and steering-wheel controls where available
- Metadata and artwork updates
- Reconnection after unplugging or leaving the vehicle
- Wired and wireless operation when supported by the equipment
- Network loss, mobile-data restrictions, and recovery
- Audio interruption by calls, navigation, and other media applications
- Bluetooth disconnect and reconnect behavior
- Background playback and service shutdown

Record the phone model, Android version, Android Auto version, connection type, and head unit used for each evaluation cycle.

**Exit check:** the application can be used for routine listening in the real vehicle without ADB intervention after installation.

### 9. Produce a personal release

- Build a locally signed release APK with the new signing identity.
- Keep the signing key and passwords outside the repository.
- Record the exact source revision and build command.
- Add a short manual upgrade and rollback procedure.
- Use the application for an evaluation period before starting public distribution work.

**Target 1 is complete when:** the application builds from a clean checkout, is the maintainer's normal Android Auto radio player, has no inherited backend dependency, and shares diagnostics only through an explicit per-report chooser.

## Target 2: F-Droid release

Start this target only after the personal release has been stable in regular use.

### Broad steps

1. Audit the complete release dependency graph and generated APK for non-free libraries, trackers, and unexpected network services.
2. Remove or isolate anything incompatible with the current [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/).
3. Ensure the release builds unattended from tagged source without bundled secrets, downloaded binaries, or maintainer-local files.
4. Make versioning deterministic and remove release tasks that modify tracked files as a side effect.
5. Add accurate application metadata, screenshots, license information, source links, changelog, privacy statement, and reproducible build instructions.
6. Document that Android Auto may require its developer/unknown-sources setting for an app installed outside Google Play.
7. Test installation, upgrade, background playback, and Android Auto behavior using an F-Droid-signed or equivalently non-Play-signed build.
8. Submit to an appropriate open-source application repository and address reproducibility or policy findings without adding distribution-only telemetry or services.

**Target 2 is complete when:** an F-Droid-compatible release is built from public source and accepted into the intended repository, with installation and Android Auto limitations documented honestly.

## Target 3: Play Store release

This target is optional. Start it only if the application is stable, there is meaningful user interest, and the ongoing policy and support burden is justified.

### Broad steps

1. Confirm ownership of the new name, package ID, branding, signing key, and store assets.
2. Update the application to the Play Store's then-current target SDK, foreground-service, privacy, data-safety, and account-deletion requirements as applicable.
3. Pass the then-current Android Auto media application quality requirements on supported Android and Android Auto versions.
4. Prepare store listing text, screenshots, privacy policy, support contact, content declarations, and release notes.
5. Use staged internal and closed testing before production.
6. Test upgrades from the public F-Droid/personal code line while accepting that differently signed builds cannot update one another in place.
7. Establish a sustainable process for vulnerability updates, policy deadlines, crash reports voluntarily supplied by users, and user support.
8. Publish only if the application can remain free of fleeceware behavior: no deceptive trials, subscriptions, advertising pressure, or hidden data collection.

**Target 3 is complete when:** a separately signed Play build passes Android Auto and Play review, behaves consistently with the open-source version, and has a support process that can be sustained without compromising the project's principles.

## Explicitly out of scope

Unless a future maintainer takes ownership, the roadmap does not include:

- Android TV support
- Native Android Automotive OS support
- A hosted user-account system
- Cloud favorites backup
- Analytics or automatic crash reporting
- Automatic diagnostic upload
- A custom mail or log collection backend
- Feature parity with the commercial application
