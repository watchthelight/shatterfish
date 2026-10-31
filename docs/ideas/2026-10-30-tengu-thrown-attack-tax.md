# Tengu: stop paying the thrown-attack tax

Story 10.21 found that about half of the hit points a hero loses to Tengu are his thrown attacks, landed while the hero
walks back to him after each jump (the jump lands five to seven cells away in phase 2, and his ranged skill is 20
against 10 adjacent). Tuning the threat costs and the drink moves the kill count by almost nothing, so the next story
should change the walk, not a number:

- Choose where to stand so the next jump is shorter: the arena is an ellipse and he jumps to a cell five to seven away
  from the hero, so standing near a wall may bound where he lands. Check against the pinned jump code before trying.
- Spend the walk on something: throw the missiles held while he is at range, for the classes that hold some.
- Put the bomb countdown into the Observation if the renderer shows it, so the dodge can start a turn earlier (the
  open item from story 10.20).
- Test the Mage's staff from range against standing beside him; no number tests that trade yet.

Needs: the jump landing rule cited with `path:line` from `upstream-reader`, and a practice slice per idea (40 per class,
salts 1000 and 3000).
