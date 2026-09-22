---
id: TASK-066
title: 'Cover the ASX playlist parser, and decide what ENTRYREF may do'
status: To Do
assignee: []
created_date: '2026-09-21 19:59'
updated_date: '2026-09-22 15:48'
labels:
  - test
milestone: m-0
dependencies: []
type: chore
ordinal: 81000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
ASXPlaylistParser is the weakest class in the pure-core set at 51 of 97 lines and 17 of 42 branches, below the 60% floor the TASK-007 gate enforces. It has one fixture, a single ENTRY with a REF, in AutoDetectParserTest. Untested are the case-insensitive href lookup and the fall back to the element value, the TITLE element, the malformed-XML repair in validateXML which rewrites an unterminated element and retries, and the whole ENTRYREF branch.

ENTRYREF is why this is not just a matter of adding fixtures. That branch takes an href out of downloaded playlist content and calls URL.openConnection directly: a raw HttpURLConnection GET to an address the application never chose, with no connectivity check, no allowlist and no DownloaderLayer, then feeds the response back into AutoDetectParser. It is dormant today only because the one fixture has no ENTRYREF element, so a naive fixture would make the suite dial out and break criterion 7 of TASK-007 while trying to satisfy criterion 4. Covering it means first deciding whether an ASX entry reference should be followed at all, and if so through what. There is a LoopbackHttpFixture in app/src/androidTest, but nothing equivalent on the JVM.

Two smaller things in the same class: sNumberOfFiles is static mutable state shared across every parse, which is an order dependency waiting to be discovered, and the ENTRYREF branch swallows three exception types into a log line.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A decision is recorded on whether an ASX ENTRYREF is followed, and through what, before any test exercises that branch
- [ ] #2 No test causes a connection to anything but loopback
- [ ] #3 The href lookup is covered for the lower case attribute, the upper case attribute and the element value fallback
- [ ] #4 TITLE handling and an entry without a title are covered
- [ ] #5 The malformed-XML repair path in validateXML is covered, including input it cannot repair
- [ ] #6 ASXPlaylistParser clears the per-class line floor in the JVM coverage report
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Decision on ENTRYREF (2026-09-22, by the project owner)

An ASX ENTRYREF **is followed**, and the fetch goes **through DownloaderLayer**, not through the raw HttpURLConnection at ASXPlaylistParser.kt:114. Real ASX playlists use ENTRYREF to chain to another playlist, so dropping it would break those stations. Putting the fetch behind the downloader subjects it to the same connectivity gate as every other request, and gives a JVM test a seam it can drive with a recording fake, which is how AC #2 gets met without a loopback server.

This settles AC #1. It is left unticked so the session that implements the task can tick it in its first commit on the task branch, together with setting the status to In Progress.

## What following it through DownloaderLayer involves

Found while reading the code for this decision. Nothing has been changed yet.

1. **The content type goes missing.** The current branch passes conn.contentType into AutoDetectParser.parse, which dispatches on both MIME type and extension. DownloaderLayer.downloadDataFromUri returns only a ByteArray, so a referenced playlist whose URL has no playlist extension can no longer be recognised by its MIME type. Choose between dispatching the referenced URL on its extension alone and widening what the downloader returns. Either way, record which one and why. Do not widen the interface as a side effect.

2. **The parser package has no way to receive the downloader.** wseemann.media.jplaylistparser is vendored and constructs its own collaborators. ASXPlaylistParser is built inside both AutoDetectParser.parse overloads. AutoDetectParser is built in AbstractParser.parseEntry, in the ENTRYREF branch, and in NetUtils.extractUrlsFromPlaylist, the only entry point from app code. downloadDataFromUri also takes a Context. The downloader therefore has to be passed down explicitly from NetUtils. Do not reach it through a static or a registry lookup from inside the parser.

3. **ENTRYREF is not the only direct network call in the package.** Every entry of every format (ASX ENTRY, M3U, M3U8, PLS, XSPF) goes through AbstractParser.parseEntry into AutoDetectParser.parse(url, playlist). For an entry with a playlist extension, that overload opens its own HttpURLConnection (AutoDetectParser.kt, after the dispatch). For an entry without one, it calls getStreamExtension, which sends an OkHttp request through a client built on the spot. If only ENTRYREF is routed through the downloader, the playlist it fetches still goes to parsers whose entries make those calls directly. Decide at the start whether this task routes those paths through the downloader too or files them as a separate task. The existing AutoDetectParserTest ASX fixtures stay offline only because their REF uses ftp://, which HttpUrl.parse rejects before getStreamExtension sends anything (AutoDetectParser.kt:198-200). An http(s) REF in a new fixture would go out. Also check whether the https fixtures in dispatchesSupportedFormatsFromStreams reach getStreamExtension. If they do, criterion 7 of TASK-068 is already broken.

4. **Following references needs a cycle limit.** A referenced playlist can ENTRYREF back to itself or to the playlist that referenced it. The only cycle guard is AbstractParser.mLastEntry. It is a static field that compares an entry with the one just before it, so it misses A to B to A and is shared by every parse. Following ENTRYREF needs a depth limit or a visited set held for a single parse. Cover both a self reference and a two-hop cycle.

5. The static sNumberOfFiles counter and the three exception types the branch catches and only logs are already in the description. The static mLastEntry in item 4 has the same order-dependency problem and should be fixed at the same time.
<!-- SECTION:NOTES:END -->
