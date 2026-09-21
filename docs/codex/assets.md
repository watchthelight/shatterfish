# Assets

!!! info "Generated"

    From `codex/v4.0.0/assets.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

Every asset path the game names: the constants of its asset class and the literal strings
loaded outside it, each with the group it belongs to, the asset root it is resolved against,
and whether a file was there when the table was read (story 2.7).

[`codex/v4.0.0/assets.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/assets.json) holds 259 entries in 58.5 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## How a row is shaped

259 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `assetRoot` | string |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `constant` | string |
| `group` | string |
| `path` | string |
| `present` | boolean |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 259 citations in 6 files:

| Source | Citations | Lines |
|---|---:|---|
| [`SPD-classes/src/main/java/com/watabou/noosa/TextInput.java`](https://github.com/watchthelight/shatterfish/blob/main/SPD-classes/src/main/java/com/watabou/noosa/TextInput.java) | 1 | 79 |
| [`SPD-classes/src/main/java/com/watabou/noosa/ui/Cursor.java`](https://github.com/watchthelight/shatterfish/blob/main/SPD-classes/src/main/java/com/watabou/noosa/ui/Cursor.java) | 2 | 40-41 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Assets.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/Assets.java) | 246 | 27-348 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/effects/Fireball.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/effects/Fireball.java) | 2 | 40-46 |
| [`desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopLauncher.java`](https://github.com/watchthelight/shatterfish/blob/main/desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopLauncher.java) | 6 | 189-190 |
| [`desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopPlatformSupport.java`](https://github.com/watchthelight/shatterfish/blob/main/desktop/src/main/java/com/shatteredpixel/shatteredpixeldungeon/desktop/DesktopPlatformSupport.java) | 2 | 127-129 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
