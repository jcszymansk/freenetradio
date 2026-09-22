---
id: TASK-077
title: Keep a single-entry array when reading equalizer state
status: To Do
assignee: []
created_date: '2026-09-22 12:40'
labels:
  - bug
milestone: m-0
dependencies: []
type: bug
ordinal: 91000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
JsonUtils.getShortArray and JsonUtils.getIntArray return an empty array whenever the split of the stored value yields one element. The guard exists because splitting an empty string yields one empty element, and dropping that is right; it cannot tell that case from a real lone entry, so a value of "1500" comes back as nothing. EqualizerStateJsonDeserializer reads band levels and centre frequencies through these two accessors, and both are as long as the band count, so a one-band equalizer loses its levels and its frequencies on every reload while the preset name and band count survive. getListValue, which reads the preset names, already distinguishes the two cases by testing the raw value for emptiness before splitting; that is the shape the array accessors are missing. TASK-063 pinned the current behaviour in JsonUtilsTest, in shortArrayLosesASingleEntryAndIsEmptyForAnEmptyValueOrAMissingKey and its int counterpart, so those two assertions have to be inverted by whoever fixes this.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 getShortArray returns the single value for a one-entry value, and an empty array for an empty value and a missing key
- [ ] #2 getIntArray returns the single value for a one-entry value, and an empty array for an empty value and a missing key
- [ ] #3 A one-band equalizer state survives a serialize and deserialize round trip with its band levels and centre frequencies intact
- [ ] #4 JsonUtilsTest asserts the corrected behaviour rather than the loss it pins today
<!-- AC:END -->
