---
story: 1.9
key: 1-9-the-observer-part-two-actors-emotes-buffs-and-the-hero
title: "The Observer, part two: actors, emotes, buffs and the hero"
epic: 1
issue: 22
status: review
created: '2026-09-06'
updated: '2026-09-06'
---

# Story 1.9: The Observer, part two: actors, emotes, buffs and the hero

As the bot,
I want the visible characters and my own state as the HUD shows them,
So that I can fight without knowing what the player could not.

Paths abbreviate `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/` as `…/`, and every
line number is at the pinned tag `v3.3.8` (commit `7b8b845a`).

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| Given the rows of ADR-0006 for mobs, mob state, mob buffs and hero buffs, when the Observer builds the actor and hero sections | **Met.** `Observer.actors()` and `Observer.hero()`; each read is the field the sprite, the bar or the HUD reads |
| A character outside the field of view is absent, an invisible character inside it is present with its flag, and a stealthy passive mimic is emitted as a chest heap rather than an actor | **Met.** A mob is present iff `heroFOV[mob.pos]` (`…/scenes/GameScene.java:1447`); `invisible > 0` is the flag for the sprite at alpha 0.4; a hidden mimic is a heap of the map, of the chest kind its sprite shows, and never an actor |
| Health is quantised to the health bar's pixel resolution, never the exact value | **Met.** `HealthBar.level(Char)`'s share, health over the greater of health plus shielding and the maximum (`…/ui/HealthBar.java:82-88`), through the codec's `healthPips`; `ActorLeakTest` holds the formula and the JSON's lack of a health value |
| The only AI state exposed is the emote the sprite shows, read through the accessor hook | **Met.** Hook row 4, `CharSprite.shatterfishEmote()`, ten added lines under the show and hide methods' lock, reads the alert, investigate and lost icons the acts set; the sleep icon, which the sprite derives from the state on every frame, is applied as the next frame would, since the driver's frame runs before the acts; `ActorLeakTest` holds hunting, wandering and fleeing, the target and the seen flag to one Observation |
| Buffs are every buff with an icon, with the turns their description shows, uncapped, for the hero and for visible mobs, and the exact hunger value is absent | **Met.** `icon() != NONE` for both; a flavour buff's visual cooldown in hundredths; hunger as the icon's three states; `HeroSectionTest` holds the JSON to no number behind the hunger icon |
| `ActorLeakTest` asserts that the mob's AI state, target, seen flag, exact hit points and the hunger value cannot be recovered | **Met.** `ActorLeakTest` and `HeroSectionTest`, by equality of Observations across the hidden changes and by searching the serialization |
| `MimicDifferentialTest` asserts a real chest and a stealthy mimic at the same cell produce byte-identical Observations | **Met.** Three pairs, the chest, the locked chest and the crystal chest with its category, each byte-identical to its mimic, then the mimic revealed as an actor |

## What was built

- `core/.../sprites/CharSprite.java`: hook row 4, `shatterfishEmote()`, a read-only accessor for the emote under the show and hide methods' lock; `docs/UPSTREAM.md` row 4, site index and diff budget.
- `shatterfish/harness/src/main/java/org/shatterfish/harness/observer/Observer.java`: `actors()`, `hero()`, hidden mimics as heaps in `map()`, the health, emote, buff and hunger rules.
- `shatterfish/api`: `HeapKind.EBONY_CHEST`, the chest only an ebony mimic wears.
- Tests: `ActorLeakTest`, `HeroSectionTest`, `MimicDifferentialTest`; `Skeleton` takes the actors and the hero.
- Docs: ADR-0006 amendment for story 1.9; rows in `docs/rules/visibility.md` and `docs/rules/buffs.md`.

## What the story found

**A hidden mimic names itself as its chest, and a crystal mimic names its category.** `Mimic.name()`
returns the chest's name while neutral (`…/actors/mobs/Mimic.java:112-118`), the golden and crystal
mimics theirs (`GoldenMimic.java:51-53`; `CrystalMimic.java:59-61`), and a crystal mimic's
description names the category of the first artifact, ring or wand it holds
(`CrystalMimic.java:68-84`), so a real crystal chest and a crystal mimic around the same wand are
one Observation. The ebony mimic wears a chest sprite no heap has
(`…/sprites/ItemSpriteSheet.java:124`), which the screen shows and `HeapKind` now carries.

**A mimic's alignment flips on its act, not on its reveal.** `stopHiding()` sets the state
(`Mimic.java:212-222`) and the alignment turns hostile on the next act (`:134-145`), before the
next Input wait in play; a test that reveals a mimic by hand sets the alignment as the act would.

**The sleep icon lags a frame in the driver.** The mob sprite shows the sleep icon while the mob
is sleeping and hides it otherwise, on every update (`…/sprites/MobSprite.java:39`;
`…/sprites/CharSprite.java:635-639`), and the driver's fenced frame updates the sprites before the
acts of a turn, so at an Input wait the sprite carries the icon of the frame before those acts,
while the Overlay's unfenced frames would carry the fresh one for the same Run. The review found
it; the Observer now applies the sprite's rule as the next frame applies it, reading
`state == SLEEPING` for that one drawn bit, and the alert, investigate and lost icons, which the
acts set themselves, through the accessor.

**One flavour buff prints no turns.** The shadows of foliage describe themselves without a
cooldown (`…/actors/buffs/Shadows.java:125-127`), so a buff's turns are carried only when its
description contains them; the review found the class test alone would have carried a number no
window shows.

**A non-stealthy mimic tells on itself to a player who taps.** Its description appends a hint
(`Mimic.java:121-130`) and the game opens the mob window on it (`GameScene.java:1729-1735`);
ADR-0006 says neither is read, so the bot knows less than a tapping human here.

**The emote is one lock away.** The show and hide methods replace the icon under
`EmoIcon.class` (`…/sprites/CharSprite.java:655-737`); the accessor reads under the same lock.

**A buff's turns are only a flavour buff's.** `FlavourBuff.desc()` prints `visualcooldown()` to two
decimals (`…/actors/buffs/FlavourBuff.java:35-42`; `…/actors/buffs/Buff.java:136-143`); other
buffs print their own numbers in their own words, which the schema's `timed` and hundredths do
not carry.

**The talents pane counts tiers from the level, gated by the subclass and the ability**
(`…/ui/TalentsPane.java:75-86`), so a level-1 hero shows one tier and the section carries that one.

## Decisions taken inside the story

**The accessor as a hook.** Alternatives: (a) reflection into `CharSprite.emo`; (b) a class of
ours in the sprite's package; (c) a public accessor under hook row 4. Chosen (c), as ADR-0006
decided: reflection is confined to the stepper and the package route is what the anchor test
forbids; the accessor reads under the lock and writes nothing, ten lines, ledgered.

**The ebony chest.** Alternatives: (a) emit an ebony mimic as a `CHEST`, hiding a sprite the
player sees; (b) a `HeapKind` member for the sprite only a mimic wears. Chosen (b): the screen
draws it, and a member added to an enum changes no existing bytes.

**A hidden mimic's rule.** Alternatives: (a) every hidden mimic a chest; (b) only a stealthy one,
the rest actors named as chests. Chosen (a), as the row decided: the sprite is the chest's and the
name is the chest's for both; the hint on examine is not read.

**Buff turns.** Alternatives: (a) the visual cooldown for every buff; (b) only for flavour buffs,
whose description prints it; (c) the icon's text. Chosen (b): it is what the description prints;
the rest is a recorded loss.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green, 408 tests across 29 suites, twenty-five of
them the seven observer suites. `mkdocs build --strict`: clean. `HooksLedgerTest` and
`HooksVanillaTest` green with row 4's digest `b5c38c7c41565c37`, ten lines added, none removed.

**Mutation battery**, twenty mutations of `Observer.java` on the committed tree at
`055d58d5e` (seventeen first run at `82107dcdf`, three added for the review's rules), each
applied to a clean tree, run against `ActorLeakTest`, `HeroSectionTest`, `MimicDifferentialTest`
and `MapLeakTest` without `--rerun-tasks`, restored with `git checkout`, and the tree verified
clean after each:

| # | Mutation | Caught by |
|---|---|---|
| M1 | actors are emitted whether or not in view | `ActorLeakTest` (drawn only in view; the Observation refuses an actor out of view) |
| M2 | a hidden mimic is emitted as an actor | `MimicDifferentialTest` (never an actor while hidden) |
| M3 | health is the value, not the pips | `ActorLeakTest` (health pips) |
| M4 | the invisible flag is dropped | `ActorLeakTest` (invisible) |
| M5 | the emote is the state alone, no alert, investigate or lost icon | `ActorLeakTest` (the alert icon through the accessor) |
| M6 | buffs without an icon are listed | `ActorLeakTest` (buffs with icons only), `HeroSectionTest` |
| M7 | a flavour buff shows no turns | `ActorLeakTest`, `HeroSectionTest` (the flavour buff's turns) |
| M8 | hunger ignores the icon | `HeroSectionTest` (hunger is the icon) |
| M9 | every hidden mimic is a plain chest | `MimicDifferentialTest` (the locked pair; the ebony mimic out of view) |
| M10 | a crystal mimic names no category | `MimicDifferentialTest` (the crystal pair) |
| M11 | a stealthy mimic out of view is not drawn once visited | `MimicDifferentialTest` (out of view) |
| M12 | the strength bonus is dropped | `HeroSectionTest` (changes on screen) |
| M13 | every talent tier is listed whatever the pane shows | `HeroSectionTest` (the HUD's talents) |
| M14 | a placeholder quickslot reads as an item | `HeroSectionTest` (changes on screen) |
| M15 | the hero shield is dropped | `HeroSectionTest` (changes on screen) |
| M16 | the accessor is bypassed: no icon but sleep ever | `ActorLeakTest` (the alert icon) |
| M17 | the unspent talent points are dropped | `HeroSectionTest` (changes on screen) |
| M18 | the sleep icon is read from the stale sprite, not as the next frame draws it | `ActorLeakTest` (asleep before any frame is drawn) |
| M19 | a flavour buff is timed whatever its description prints | `HeroSectionTest` (the shadows buff) |
| M20 | an ebony mimic is not flagged faint | `MimicDifferentialTest` (the ebony mimic) |

All twenty caught, in two invocations: M1 to M6, then M7 to M20 after two anchors were rewritten
to the lines the review commit left.

## The fairness review

Run as an isolated `fairness-reviewer` on commit `82107dcdf`. Verdict: FINDINGS, none blocking:
no read gives the bot what the screen does not draw, beyond a one-frame staleness the review
found and the story then removed. Seven should-fix findings, all taken in the review commit:

1. **A flavour buff whose description prints no turns read as timed.** The shadows of foliage
   describe themselves without a cooldown (`…/actors/buffs/Shadows.java:125-127`), so the class
   test alone carried a number no window shows. A buff's turns are now carried only when the
   description the window shows contains them; `HeroSectionTest` holds a shadowed hero's buff
   untimed, and M19 shows the test catches the class test alone.
2. **The sleep icon lagged a frame in the driver.** The mob sprite derives the sleep icon from
   the state on every update (`…/sprites/MobSprite.java:39`; `…/sprites/CharSprite.java:635-639`),
   and the driver's fenced frame updates the sprites before the acts of a turn, so a mob put to
   sleep or woken during the turn read one frame stale, and the Overlay's unfenced frames would
   have read the fresh icon for the same Run, a reproducibility trap. The Observer now applies the
   sprite's rule as the next frame applies it, reading `state == SLEEPING` for that one drawn bit
   and dropping a sleep icon the next frame would hide; the alert, investigate and lost icons,
   which the acts set themselves, come through the accessor. ADR-0006's amendment records the one
   read of the state and why; `ActorLeakTest` holds the sleep before the frame, the frame's
   agreement, the waking with the stale icon still on the sprite, and M18 the stale reading.
3. **A wrong cite in four places.** The mimic's alignment flips in its act at
   `…/actors/mobs/Mimic.java:134-145`, not `:46-49`; the amendment, the story file, the rule row
   and the test's comment now say so.
4. **Row 4's count.** The hook adds ten lines, the trailing blank included, as the digest line
   says; the row's text said nine.
5. **No determinism read for the new sections.** `ActorLeakTest` now holds two readings of one
   wait equal, records and bytes, with `Level.mobs` rebuilt in reverse order between them, and
   `HeroSectionTest` two readings of the hero.
6. **An ebony mimic hides at alpha 0.2** (`…/sprites/MimicSprite.java:121-125`), drawn faint, yet
   was flagged not hidden; it carries the flag a faint heap carries, `HeapKind` says so, and
   `MimicDifferentialTest` holds it, with M20 behind it.
7. **One cite off by a line** in `HeroSectionTest`: the strength lines are `WndHero.java:189-192`.

The review also confirmed what the story relies on: every `stopHiding` caller and the interaction
path set the alignment hostile before any Input wait, so a neutral mimic that is not passive
never reaches an Observation; life-linked ghouls leave `Level.mobs` and are omitted, a loss; the
accessor exposes nothing beyond a drawn icon, since the icon's visibility follows the sprite's and
only mobs in view are emitted.

## Deviations

- The hook edits `CharSprite.java`, an upstream file: labelled `touches-upstream`, ledgered as row 4 with its digest, and the file is written with LF as the index holds it.
- No manual `:desktop:debug` check: the accessor is dead code to the game.

## Known limitations, handed forward

- **No `observe()` yet**: the inventory, journal, log and Prompt (1.10), then blobs, feeling, transitions and the danger count (1.11).
- **A buff that is not a flavour buff shows no turns**, though its description prints its own numbers; a later schema may carry the icon's text.
- **A non-stealthy hidden mimic's examine hint is not read**, as ADR-0006 decided; the bot knows less than a tapping human.
- **Two identical buff icons draw twice**; the schema lists the buff once.
- **The sleep icon is read as the next frame draws it**, from `state == SLEEPING`, because the driver's frame runs before the acts of a turn; the Overlay (ADR-0013) must observe at the same phase or apply the same rule.

## Follow-ups for later stories

- Story 1.10: the inventory, the journal, the log listener, the Prompt gate.
- Story 1.11: blobs, feeling, transitions, the danger count from the actors, `observe()`, the checklist over ADR-0006's rows.
