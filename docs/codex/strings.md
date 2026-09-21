# Strings

!!! info "Generated"

    From `codex/v4.0.0/strings.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

Every line of the nine English bundles with the class the game's key rule names, and the
reason where it names none (story 2.7).

[`codex/v4.0.0/strings.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/strings.json) holds 4976 entries in 1.7 MB. The entries are not repeated here: they are committed, diffable and cited in that file.

## How a row is shaped

4976 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `bundle` | string |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `key` | string |
| `reason` | string |
| `suffix` | string |
| `value` | string |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 4976 citations in 9 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/assets/messages/actors/actors.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/actors/actors.properties) | 1609 | 2-1928 |
| [`core/src/main/assets/messages/items/items.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/items/items.properties) | 2073 | 2-2515 |
| [`core/src/main/assets/messages/journal/journal.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/journal/journal.properties) | 178 | 1-190 |
| [`core/src/main/assets/messages/levels/levels.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/levels/levels.properties) | 241 | 2-312 |
| [`core/src/main/assets/messages/misc/misc.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/misc/misc.properties) | 235 | 1-237 |
| [`core/src/main/assets/messages/plants/plants.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/plants/plants.properties) | 67 | 1-80 |
| [`core/src/main/assets/messages/scenes/scenes.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/scenes/scenes.properties) | 148 | 3-163 |
| [`core/src/main/assets/messages/ui/ui.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/ui/ui.properties) | 49 | 1-53 |
| [`core/src/main/assets/messages/windows/windows.properties`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/assets/messages/windows/windows.properties) | 376 | 1-409 |

## Judgments

Where the reader could not simply read a value it recorded why, and every such entry is here
in full -- these are the judgments a human audits, and the list is short on purpose. 24 of the
table's 4976 entries name a reason:

| Entry | Reason |
|---|---|
| `actors.buffs.earthimbue.name` | no class the game compiles has this key's name |
| `actors.buffs.earthimbue.desc` | no class the game compiles has this key's name |
| `actors.buffs.revealedchar.name` | no class the game compiles has this key's name |
| `actors.buffs.revealedchar.desc` | no class the game compiles has this key's name |
| `actors.mobs.tengu$bombability$bombblob.desc` | no class the game compiles has this key's name |
| `items.quest.corpsedust&dustwraith.rankings_desc` | no class the game compiles has this key's name |
| `items.spells.magicalporter.name` | no class the game compiles has this key's name |
| `items.spells.magicalporter.inv_title` | no class the game compiles has this key's name |
| `items.spells.magicalporter.nowhere` | no class the game compiles has this key's name |
| `items.spells.magicalporter.desc` | no class the game compiles has this key's name |
| `items.stones.stoneofdisarming.name` | no class the game compiles has this key's name |
| `items.stones.stoneofdisarming.desc` | no class the game compiles has this key's name |
| `items.weapon.missiles.boomerang.name` | no class the game compiles has this key's name |
| `items.weapon.missiles.boomerang.desc` | no class the game compiles has this key's name |
| `items.weapon.missiles.boomerang.durability` | no class the game compiles has this key's name |
| `items.merchantsbeacon.name` | no class the game compiles has this key's name |
| `items.merchantsbeacon.ac_use` | no class the game compiles has this key's name |
| `items.merchantsbeacon.desc` | no class the game compiles has this key's name |
| `ui.updatenotification.title` | no class the game compiles has this key's name |
| `ui.updatenotification$wndupdate.title` | no class the game compiles has this key's name |
| `ui.updatenotification$wndupdate.versioned_title` | no class the game compiles has this key's name |
| `ui.updatenotification$wndupdate.desc` | no class the game compiles has this key's name |
| `ui.updatenotification$wndupdate.button` | no class the game compiles has this key's name |
| `windows.wndclass.mastery` | no class the game compiles has this key's name |
