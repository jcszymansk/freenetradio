# Source Provenance

This repository continues the Apache-licensed OpenRadio source originally published at:

```text
https://github.com/ChernyshovYuriy/OpenRadio
```

The original GitHub repository was no longer available when this baseline was established. Its archived Git history was recovered from [Software Heritage](https://archive.softwareheritage.org/browse/origin/directory/?origin_url=https://github.com/ChernyshovYuriy/OpenRadio).

## Imported upstream baseline

| Field | Value |
| --- | --- |
| Original origin | `https://github.com/ChernyshovYuriy/OpenRadio` |
| Archived visit | 2024-01-29 17:47:48 UTC |
| Upstream revision | `37d2dedd9d9b58f36cce76e0cfa5c9d97769264c` |
| Upstream revision date | 2024-01-29 00:37:22 UTC |
| Upstream revision author | Yurii Chernyshov |
| Upstream revision message | `Enable file export for Automotive` |
| Configured version name | `15.0.0` |
| Configured version code | `716` |
| Local tag | `upstream-15.0.0` |

Permanent Software Heritage identifiers:

- Snapshot: [`swh:1:snp:6cfdf04bb0940bec460c1fb5fa8028ee03a75002`](https://archive.softwareheritage.org/swh:1:snp:6cfdf04bb0940bec460c1fb5fa8028ee03a75002)
- Revision: [`swh:1:rev:37d2dedd9d9b58f36cce76e0cfa5c9d97769264c`](https://archive.softwareheritage.org/swh:1:rev:37d2dedd9d9b58f36cce76e0cfa5c9d97769264c)
- Directory: [`swh:1:dir:388c9a5bc70d9e288b26e15eceadfc7a8fcf64ec`](https://archive.softwareheritage.org/swh:1:dir:388c9a5bc70d9e288b26e15eceadfc7a8fcf64ec)

The revision was imported from Software Heritage's cooked bare-Git archive, preserving Git objects and commit metadata rather than reconstructing history from a directory export.

## Earlier recovered checkout

Before the Software Heritage history was found, this repository used revision:

```text
403ac72d402d8a0e400a2f264f9c641d28bea73b
```

That state reports version `14.1.1`, version code `674`, and is preserved as tag `recovered-local-14.1.1`.

The earlier revision and the imported upstream history share this parent:

```text
44f374e987f11f1d3bcd967ab50599f0eaf314c4
```

They then diverge:

- `403ac72` changes the icon, logo, and TV banner.
- The archived upstream branch continues for eight commits by the original author, ending at `37d2dedd`.

The icon and TV-banner revision was not applied to the new main line. The project plans independent branding and does not plan to maintain the TV target. The tag retains the complete earlier state if it is ever needed.

## Migration record

The baseline migration performed these checks:

1. The downloaded bare repository resolved its `master` branch to `37d2dedd9d9b58f36cce76e0cfa5c9d97769264c`.
2. Git calculated `44f374e987f11f1d3bcd967ab50599f0eaf314c4` as the merge base between the earlier checkout and the archived tip.
3. Git counted eight upstream commits between that merge base and the archived tip.
4. Repository-authored documentation commits were reapplied on top of the imported upstream revision.

## License and historical artifacts

The imported baseline contains the project's Apache License 2.0 `LICENSE` file and its `NOTICE` attribution file. Existing copyright and attribution notices are retained.

Prebuilt APKs under `app/store/` are treated as unverified historical artifacts. They are not trusted as build inputs or as evidence that the current source produces a particular binary.
