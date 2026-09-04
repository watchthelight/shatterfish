---
name: Shatterfish Overlay
description: A visibly separate instrument inside Shattered Pixel Dungeon, built only from the game's own UI toolkit (Noosa nine-patch frames, pixel font, RedButton, Icons). This DESIGN.md specifies the instrument-layer delta on top of the game's native look.
status: final
created: '2026-09-03'
updated: '2026-09-03'
colors:
  # The game draws its own frames and font; these are tint values applied to text
  # and small fills through the toolkit's hardlight/tint calls. Dark surfaces only:
  # the game has no light theme.
  ink: '#F0F0F0'
  ink-muted: '#9C9C9C'
  ink-dim: '#5E5E5E'
  chosen: '#FFB347'
  alternative: '#7FB8FF'
  goal: '#FFE97F'
  ok: '#66DD66'
  warn: '#FFD34D'
  danger: '#FF5555'
  human: '#7FB8FF'
  running: '#66DD66'
  paused: '#FFD34D'
  oracle: '#FF2020'
  panel-scrim: '#000000'
typography:
  # The game's UI text: PixelScene.renderTextBlock renders the platform TTF the game ships
  # (pixel_font.ttf on desktop) at the game's own sizes in UI pixels before defaultZoom.
  # The 3x5 bitmap pixelFont is only for the version and depth numbers. No other font may appear.
  title:
    fontFamily: 'SPD UI font (renderTextBlock)'
    fontSize: 9px
  body:
    fontFamily: 'SPD UI font (renderTextBlock)'
    fontSize: 8px
  small:
    fontFamily: 'SPD UI font (renderTextBlock)'
    fontSize: 6px
  numeric:
    fontFamily: 'SPD UI font (renderTextBlock)'
    fontSize: 8px
    note: 'Right-aligned in fixed-width columns so numbers read as a table; the pixel font is not monospace, so alignment is by column, not by glyph'
rounded:
  # Corners come from the nine-patch frames; no radii are chosen here.
  DEFAULT: 'nine-patch'
spacing:
  '1': 1px
  '2': 2px
  '4': 4px
  '6': 6px
  '8': 8px
  panel-pad: '{spacing.4}'
  row-gap: '{spacing.2}'
  section-gap: '{spacing.6}'
components:
  panel:
    frame: 'Chrome.Type.TOAST_TR_HEAVY'
    padding: '{spacing.panel-pad}'
    ink: '{colors.ink}'
  mode-strip:
    frame: 'Chrome.Type.TOAST_TR'
    running: '{colors.running}'
    paused: '{colors.paused}'
    human: '{colors.human}'
  decision-card:
    chosen-ink: '{colors.chosen}'
    alternative-ink: '{colors.alternative}'
    score-typography: '{typography.numeric}'
  goal-line:
    ink: '{colors.goal}'
    typography: '{typography.title}'
  flag-chip:
    frame: 'Chrome.Type.TAG'
    ok: '{colors.ok}'
    warn: '{colors.warn}'
    danger: '{colors.danger}'
  belief-row:
    ink: '{colors.ink}'
    probability-typography: '{typography.numeric}'
    muted: '{colors.ink-muted}'
  decision-log:
    typography: '{typography.small}'
    ink: '{colors.ink-muted}'
    current-ink: '{colors.ink}'
  control-button:
    frame: 'RedButton'
    typography: '{typography.body}'
    disabled-ink: '{colors.ink-dim}'
  speed-selector:
    frame: 'Chrome.Type.TAB_SET'
    selected: 'Chrome.Type.TAB_SELECTED'
    unselected: 'Chrome.Type.TAB_UNSELECTED'
  stepper:
    frame: 'RedButton'
    typography: '{typography.small}'
    value-typography: '{typography.numeric}'
  map-highlight:
    path: '{colors.chosen}'
    target: '{colors.danger}'
    considered: '{colors.alternative}'
  oracle-border:
    ink: '{colors.oracle}'
    width: '{spacing.2}'
---

## Brand & Style

The Overlay is an instrument, not a window. It uses nothing but the game's own toolkit (the non-negotiable), so at a glance it belongs on the screen: the same translucent nine-patch backgrounds the game uses for its HUD tags, the same pixel font, the same red buttons. What makes it read as *separate* is posture: it is denser, terser, and colder than anything the game says. The game speaks in sentences ("You see a gnoll scout"); the instrument speaks in labels, numbers, and one-line reasons. Its numbers sit in columns. Its color means something every time it appears. Nothing on it is decorative.

The reference feeling is a tool-assisted speedrun input display married to a chess engine's evaluation bar: the human can see exactly what the bot is about to do, why, what it rejected, and can advance it one turn at a time. The instrument never draws attention to itself; it draws attention to the decision.

## Colors

The game owns the surfaces; the instrument owns a small set of text tints and fills, dark-mode only.

- **Ink (`#F0F0F0`), Ink muted (`#9C9C9C`), Ink dim (`#5E5E5E`)** are the three text weights. Ink is the current Decision and the current log line; muted is context (past log lines, secondary beliefs); dim is disabled controls. Nothing else is grey.
- **Chosen (`#FFB347`)** marks the one Action the Brain will take, on the card and on the map as the planned path. It appears exactly once per turn.
- **Alternative (`#7FB8FF`)** marks the rejected alternatives and the considered cells on the map; it is also the **Human** mode color, because in HUMAN mode the human's move is the alternative the Brain did not choose.
- **Goal (`#FFE97F`)** is the Goal line only.
- **OK (`#66DD66`), Warn (`#FFD34D`), Danger (`#FF5555`)** are the Safety flag semantics and nothing else: green means the flag says safe, amber means conditional, red means unsafe. Green is also **Running** and amber is also **Paused**, so the Mode strip and the flags share one meaning of each color: green is go, amber is hold.
- **Oracle (`#FF2020`)** is reserved for the oracle border and label. It is not the same red as Danger, and it never appears anywhere else, so its presence means one thing.
- **Panel scrim** is the game's translucent nine-patch (`TOAST_TR_HEAVY`); no solid backgrounds are drawn over the dungeon.

Avoid: colored backgrounds behind text, gradients, any color for emphasis that is not in this list, and using red for anything but Danger and Oracle.

## Typography

One font: the game's UI text font through `renderTextBlock` (the game's own sizes are 5 to 12 UI pixels; `docs/rules/ui.md`), **title 9** (the Goal line and section labels), **body 8** (the decision card, controls, beliefs), **small 6** (the decision log and probabilities). No bold, no italics, no other face; the game's `_highlight_` markup is allowed only for the chosen Action's name.

Numbers are the instrument's signature. Scores, probabilities, and turn and Floor counters are right-aligned in fixed-width columns so that the eye can compare them without reading; the pixel font is not monospace, so alignment is by column position, never by padding with characters.

## Layout & Spacing

Everything is on the game's UI-pixel grid (1, 2, 4, 6, 8) before the interface scale multiplies it, and everything is snapped with `PixelScene.align` so the frames and text stay crisp at every scale. Panel padding is 4; rows are 2 apart; sections are 6 apart. The Panel is a single column at the right edge of the dungeon view, to the left of the inventory pane's column (187 wide, bottom right) and below the status pane's row, ending above the toolbar (Tier 3 for those sizes). It is drawn translucently over the dungeon, never over the game's HUD. The Overlay offsets the world camera horizontally so the hero stays centered in the uncovered area; the game's own offset is vertical-only and conditional, and `GameScene.layoutTags` overwrites it, so the Overlay re-applies its own after every layout (`docs/rules/ui.md`). Width: target 200 UI pixels, minimum 160 (the Mode strip alone needs about 35 characters of body text). Height: the sections above the Decision log take what they need; the log's `ScrollPane` takes the rest, never fewer than three lines. Collapse rule: when the uncovered map would be narrower than 200 UI pixels or the view shorter than 200, the Panel collapses to the Mode strip. At UI zoom 3 (`PixelScene.defaultZoom`, the game's choice for a 1280 by 720 window on the full UI) the view is 427 by 240 UI pixels, where the full Panel cannot fit beside the inventory pane; zoom 2 or a larger window shows it in full (`docs/rules/ui.md`; the mock `mockups/key-panel-paused.html` uses that geometry). `[ASSUMPTION: the E5 layout story confirms the collapse thresholds on the running game.]`

→ Composition reference: `mockups/key-panel-paused.html` (full Panel and Explain expansion), `mockups/key-panel-states.html` (RUNNING, HUMAN, THINKING, collapsed, Oracle). The spine wins on conflict.

## Elevation & Depth

No shadows; the toolkit has none. Depth is translucency: the Panel's `TOAST_TR_HEAVY` frame sits over the dungeon at the game's own HUD opacity; the Mode strip uses the lighter `TOAST_TR`. The oracle border is drawn above everything.

## Shapes

All shapes are the toolkit's nine-patches: `TOAST_TR_HEAVY` for the Panel, `TOAST_TR` for the Mode strip, `TAG` for Safety flag chips, `TAB_SET` / `TAB_SELECTED` / `TAB_UNSELECTED` for the speed selector, `RedButton` for controls. No custom shapes, no custom textures.

## Components

- **Panel.** `TOAST_TR_HEAVY` frame, padding 4, single column, sections in fixed order: Mode strip, Goal line, Decision card, Safety flags, Belief summary, Decision log, Controls. Ink `{colors.ink}`.
- **Mode strip.** One line: mode word in its color (`RUNNING` green, `PAUSED` amber, `HUMAN` blue), then the speed mode with its interval, and the turn and Floor in numeric columns. The Panel's collapsed form (`EXPERIENCE.md` State Patterns).
- **Goal line.** Title size, `{colors.goal}`; normally one line, never ellipsized, wrapping to two lines at most.
- **Decision card.** The chosen Action name in `{colors.chosen}` with its score right-aligned; below it up to three alternatives in `{colors.alternative}` with scores in the same column and a one-line reason each in `{colors.ink-muted}`. The card is the Explain expansion's anchor.
- **Flag chip.** `TAG` nine-patch, small text, tinted `{colors.ok}` / `{colors.warn}` / `{colors.danger}`; the text always states the flag, the color never carries meaning alone.
- **Belief row.** Item name in ink, top candidate and probability in numeric columns, further candidates muted; floor facts and chapter counters as plain rows.
- **Decision log.** Small size, muted ink for past lines, ink for the current line; a `ScrollPane` with the newest line at the bottom.
- **Control button.** `RedButton` with body text; disabled state in `{colors.ink-dim}`.
- **Speed selector.** A `TAB_SET` with three tabs: Next Step, Human, Fast; the selected tab uses `TAB_SELECTED`.
- **Stepper.** Two tiny `RedButton`s (minus, plus) flanking a numeric value; small text, value in a numeric column.
- **Map highlight.** Cell outlines drawn in the dungeon view: planned path in `{colors.chosen}`, target in `{colors.danger}`, considered cells in `{colors.alternative}`; never filled, never over sprites. In Oracle mode only, unseen enemies are outlined in `{colors.oracle}`.
- **Oracle border.** A 2-pixel `{colors.oracle}` frame around the whole game view plus an `ORACLE` label in the Mode strip; drawn only when the oracle flag is set.

## Do's and Don'ts

- Do use only the toolkit's frames, font, buttons, and icons; the build fails on anything else.
- Do keep every number in a column and every color on the list above.
- Do post every visual change to the render thread; the instrument never draws from the Brain's thread.
- Don't draw solid backgrounds over the dungeon; the scrim is the game's translucent nine-patch.
- Don't animate the Panel; the game animates, the instrument updates.
- Don't use the game's title color or its message-log colors; the instrument must not be mistaken for the game's own text.
- Don't ever show oracle red without the ORACLE label.
