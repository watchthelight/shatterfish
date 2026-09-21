# Recipes

!!! info "Generated"

    From `codex/v4.0.0/recipes.json` at upstream tag `v4.0.0`, Codex version 8.
    Never hand edited: run `./gradlew :codex:generate` and commit what it writes.

The alchemy pot's registries in the order its own method tries them, each recipe with its
inputs, output and energy cost where it states them, and with the text of the methods it answers
with where it does not (story 2.6).

[`codex/v4.0.0/recipes.json`](https://github.com/watchthelight/shatterfish/blob/main/codex/v4.0.0/recipes.json) holds 39 entries in 29.2 KB. The entries are not repeated here: they are committed, diffable and cited in that file.

## How a row is shaped

39 entries. Every field any entry of this list holds, by the path it is reached at
(`[]` is a list's element):

| Field | Type |
|---|---|
| `answers` | list |
| `answers[]` | object |
| `answers[].citation` | object |
| `answers[].citation.line` | number |
| `answers[].citation.path` | string |
| `answers[].expression` | string |
| `answers[].what` | string |
| `citation` | object |
| `citation.line` | number |
| `citation.path` | string |
| `className` | string |
| `cost` | number |
| `ingredients` | string |
| `inputs` | list |
| `outQuantity` | number |
| `output` | string |
| `registryCitation` | object |
| `registryCitation.line` | number |
| `registryCitation.path` | string |
| `simple` | boolean |
| `inputs[]` | object |
| `inputs[].className` | string |
| `inputs[].quantity` | number |

## Citations

Every value above was read from the pinned tree at the line the entry carries. The reader
recorded 120 citations in 38 files:

| Source | Citations | Lines |
|---|---:|---|
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/ArcaneResin.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/ArcaneResin.java) | 4 | 150-179 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/LiquidMetal.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/LiquidMetal.java) | 4 | 191-221 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Recipe.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/Recipe.java) | 39 | 174-216 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/bombs/Bomb.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/bombs/Bomb.java) | 4 | 354-440 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/food/Blandfruit.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/food/Blandfruit.java) | 4 | 239-291 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/food/MeatPie.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/food/MeatPie.java) | 4 | 51-94 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/food/StewedMeat.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/food/StewedMeat.java) | 3 | 40-67 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/Potion.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/Potion.java) | 4 | 468-552 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/AquaBrew.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/AquaBrew.java) | 1 | 63 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/BlizzardBrew.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/BlizzardBrew.java) | 1 | 60 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/CausticBrew.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/CausticBrew.java) | 1 | 69 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/InfernalBrew.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/InfernalBrew.java) | 1 | 61 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/ShockingBrew.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/ShockingBrew.java) | 1 | 56 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/UnstableBrew.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/brews/UnstableBrew.java) | 4 | 134-170 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfAquaticRejuvenation.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfAquaticRejuvenation.java) | 1 | 152 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfArcaneArmor.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfArcaneArmor.java) | 1 | 46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfDragonsBlood.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfDragonsBlood.java) | 1 | 46 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfFeatherFall.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfFeatherFall.java) | 1 | 82 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfHoneyedHealing.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfHoneyedHealing.java) | 1 | 81 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfIcyTouch.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfIcyTouch.java) | 1 | 43 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfMight.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfMight.java) | 1 | 70 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfToxicEssence.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/elixirs/ElixirOfToxicEssence.java) | 1 | 43 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/exotic/ExoticPotion.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/potions/exotic/ExoticPotion.java) | 4 | 125-151 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/scrolls/Scroll.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/scrolls/Scroll.java) | 4 | 308-361 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/scrolls/exotic/ExoticScroll.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/scrolls/exotic/ExoticScroll.java) | 4 | 121-147 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/Alchemize.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/Alchemize.java) | 4 | 80-112 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/BeaconOfReturning.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/BeaconOfReturning.java) | 1 | 278 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/CurseInfusion.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/CurseInfusion.java) | 1 | 109 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/MagicalInfusion.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/MagicalInfusion.java) | 1 | 128 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/PhaseShift.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/PhaseShift.java) | 1 | 79 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/ReclaimTrap.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/ReclaimTrap.java) | 1 | 135 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/Recycle.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/Recycle.java) | 1 | 105 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/SummonElemental.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/SummonElemental.java) | 1 | 242 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/TelekineticGrab.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/TelekineticGrab.java) | 1 | 150 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/UnstableSpell.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/UnstableSpell.java) | 4 | 131-167 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/WildEnergy.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/spells/WildEnergy.java) | 1 | 84 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/trinkets/Trinket.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/trinkets/Trinket.java) | 4 | 109-133 |
| [`core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/trinkets/TrinketCatalyst.java`](https://github.com/watchthelight/shatterfish/blob/main/core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/items/trinkets/TrinketCatalyst.java) | 4 | 110-141 |

## Judgments

The reader named no reason in this table: every entry is a fact the pinned tree states, and
nothing was left out or decided.
