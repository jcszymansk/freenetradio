---
id: TASK-066
title: 'Cover the ASX playlist parser, and decide what ENTRYREF may do'
status: Done
assignee:
  - '@claude'
created_date: '2026-09-21 19:59'
updated_date: '2026-09-22 18:32'
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
- [x] #1 A decision is recorded on whether an ASX ENTRYREF is followed, and through what, before any test exercises that branch
- [x] #2 No test causes a connection to anything but loopback
- [x] #3 The href lookup is covered for the lower case attribute, the upper case attribute and the element value fallback
- [x] #4 TITLE handling and an entry without a title are covered
- [x] #5 The malformed-XML repair path in validateXML is covered, including input it cannot repair
- [x] #6 ASXPlaylistParser clears the per-class line floor in the JVM coverage report
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add a PlaylistFetcher seam to the parser package; NetUtils adapts DownloaderLayer and Context to it, and OpenRadioService receives the downloader through DependencyRegistryCommon.
2. Make AutoDetectParser the per-parse session: it holds the fetcher, the depth and a visited set shared by every parser of one resolution. Replaces the static AbstractParser.mLastEntry and the static track counters.
3. AbstractParser.parseEntry follows an entry only when its url has a playlist extension, otherwise keeps it as a stream. getStreamExtension and the timeout parameter go.
4. ASX ENTRYREF follows its href through the session; no direct connection, no swallowed exception types.
5. Fix what the tests expose in ASXPlaylistParser (validateXML repair, case handling).
6. ASXPlaylistParserTest with a recording fetcher: href lookup, TITLE, repair path, ENTRYREF, self and two-hop cycles, depth limit. Move the ASX owner in pure-core-coverage.tsv and confirm with verifyPureCoreAttribution.
7. Update AutoDetectParserTest and PlaylistResolutionTest for the new signatures.
<!-- SECTION:PLAN:END -->

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

## Scope decisions (2026-09-22, by the project owner, at the start of implementation)

1. **Every network path in the parser package goes through the downloader, in this task.** Not only ENTRYREF: the nested fetch in AutoDetectParser.parse(url, playlist) and the getStreamExtension probe too. Reason: AutoDetectParserTest.dispatchesSupportedFormatsFromStreams already dials example.com today. Its entry https://example.com/stream?format=m3u has no playlist extension (getFileExtension answers .com/stream?format=m3u), so parseEntry reaches getStreamExtension, which sends a real OkHttp GET. AC #2 cannot hold for the suite while that path exists, and TASK-068 criterion 7 is broken by it.
2. **getStreamExtension is dropped, not replaced.** It needs the Content-Disposition header, and DownloaderLayer returns only bytes. An entry without a playlist extension is kept as a stream. If it is really a playlist, the player fails with UnrecognizedInputFormat and OpenRadioService.handleUnrecognizedInputFormatException resolves it again through extractUrlsFromPlaylist, which does see the MIME type. The probe also sent a GET to every stream url and never closed the response.
3. **Item 1 (the missing content type): dispatch on the url extension, then sniff the content.** A fetched reference or nested playlist whose url has no playlist extension is recognised by its first bytes (#EXTM3U, [playlist], the root element <asx or <playlist). DownloaderLayer is not widened.

## Verification of the criteria

- **#2** Three kinds of evidence. The parser package has no network API left (no openConnection, OkHttp, HttpURLConnection, Socket or InetAddress under common/src/main/java/wseemann). AutoDetectParserTest drives every fixture with a PlaylistFetcher that throws on any read, so an attempted read fails the test rather than going out. And the whole JVM suite passes inside a network namespace with no interface but a loopback nothing binds (320 tests, unshare -rn, --offline). The instrumented suite runs with wifi and data disabled against LoopbackHttpFixture only, 197/197.
- **#3** ASXPlaylistParserTest.refAddressIsFoundInHrefAttributeOfAnyCaseOrInElementText covers href, HREF, Href, the element text fallback, padding around both, and the attribute winning over text. aRefWithoutAnAddressGivesWayToTheNextOne and anEmptyHrefFallsBackToTheElementText cover the blank cases found in review.
- **#4** titleIsTrimmedAndReadBeforeOrAfterTheRef, entryWithoutTitleHasEmptyMetadata, cdataTitleWithBareAmpersandIsKeptExactly, cdataSurvivesTheRepairOfAMismatchedEndTag.
- **#5** The repair is no longer validateXML. That method could not work: its replacement pattern was built as "(?i)</" + tag + ">".toRegex(), which Kotlin evaluates as a string concatenation, so replace() looked for the literal text "(?i)</TAG>" and never matched, and it keyed off Xerces error message text that Android's expat parser does not produce. readDocument parses the input as it is and, on failure, once more with tag names upper cased, which mends the real defect (an end tag differing from its start tag only in case) on any SAX parser. Covered by endTagsThatDifferFromTheirStartTagsOnlyInCaseAreRepaired; input it cannot repair by entryThatIsNeverClosedYieldsNoEntries and contentThatIsNotXmlYieldsNoEntries.
- **#6** verifyPureCoreCoverage passes with ASXPlaylistParser at 47/47 lines and 33/36 branches, from 51/97 and 17/42. verifyPureCoreAttribution confirms ASXPlaylistParserTest carries the class alone.

Follow-ups filed: TASK-081 (.m3u8 entries are followed by every parser but PLS), TASK-082 (ENTRY inside REPEAT is dropped), TASK-083 (the track number nothing reads). TASK-080 was filed and then done here, because the round 1 review ruled the unbounded read a defect of this change.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
ASX ENTRYREF is followed through DownloaderLayer, and so is every other read the parser package used to make on its own: the nested playlist fetch and the getStreamExtension probe, which was dialing example.com from the existing test suite. AutoDetectParser is now the per-resolution session that owns the fetcher, the followed-url set and the depth limit, replacing the static cycle guard and static counters. A fetched playlist is recognised by extension then by content, and a playlist read is bounded at 1 MiB. ASXPlaylistParserTest (40 tests) covers href lookup, titles, the malformed-XML repair and what it cannot repair, ENTRYREF with its failures, self and two-hop cycles and the depth limit; PlaylistResolutionTest pins the ENTRYREF path over loopback. ASXPlaylistParser went from 51/97 lines and 17/42 branches to 47/47 and 33/36. Verified by ./gradlew test (320 tests, also inside a network namespace with no interface), verifyPureCoreCoverage, verifyPureCoreAttribution and the full instrumented suite (197/197, networking disabled).
<!-- SECTION:FINAL_SUMMARY:END -->
