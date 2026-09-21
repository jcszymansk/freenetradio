---
id: TASK-066
title: 'Cover the ASX playlist parser, and decide what ENTRYREF may do'
status: To Do
assignee: []
created_date: '2026-09-21 19:59'
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
