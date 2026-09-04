---
name: Reality check of the architecture spine and ADR-0005 to ADR-0013
type: review
reviewer: Claude (engineer)
date: '2026-09-03'
scope: ARCHITECTURE-SPINE.md Stack table; docs/adr/0005 to docs/adr/0013
web_checked: '2026-09-03'
working_tree: v3.3.8 (git diff --stat v3.3.8 HEAD -- core SPD-classes desktop is empty)
---

# Review: was the spine reality-checked, or asserted?

## Verdict

Mostly reality-checked. Every version in the Stack table matches a file in this repository;
every one of the twenty-one `path:line` citations opened resolves to a line that says what the
ADR says it says; the four external-method claims (Fishtest GSPRT, Van den Bergh's note,
e-processes, Long et al.'s properties) are all real and, with one attribution slip and one
reversed characterisation, accurately described; and the two load-bearing numbers carried in from the
research report (969 sprite dereferences, the JNI one-loader rule) reproduce exactly. Eight
items are wrong, imprecise, or stale enough to fix before E1 starts — two of them blocking
(C-1, the wrong method named for the Input-wait hook; C-5, `mix(salt, k)` left undefined) — and
they are listed in "Findings" below. Nothing in the spine looks fabricated from training data.

---

## 1. Stack table

### 1.1 Against the repository

| Spine claim | File | Line | Verdict |
|---|---|---|---|
| Java 21 (Shatterfish modules) | `shatterfish/java-module.gradle` | `sourceCompatibility = JavaVersion.VERSION_21` / `targetCompatibility = JavaVersion.VERSION_21` | confirmed |
| upstream compiles for 11 | `build.gradle` | `appJavaCompatibility = JavaVersion.VERSION_11` | confirmed |
| Gradle 9.4 (wrapper) | `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl=...gradle-9.4.0-bin.zip` | confirmed |
| libGDX 1.14.0 | `build.gradle` | `gdxVersion = '1.14.0'` | confirmed |
| `gdx-backend-headless` for the Harness | `shatterfish/harness/build.gradle` | `implementation "com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion"` | confirmed |
| `gdx-platform:natives-desktop` for the Harness | `shatterfish/harness/build.gradle` | `runtimeOnly "com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop"` | confirmed |
| JUnit 5.11.4 | `shatterfish/java-module.gradle` | `junitVersion = '5.11.4'`, consumed as `org.junit:junit-bom` | confirmed |
| ArchUnit 1.3.0 | `shatterfish/java-module.gradle` | `archunitVersion = '1.3.0'`, `com.tngtech.archunit:archunit-junit5` | confirmed |
| MkDocs Material per `docs/requirements.txt` | `docs/requirements.txt` | `mkdocs-material==9.7.7`, `mkdocs==1.6.1` | confirmed |
| Statistics: hand-ported GSPRT, no library | `shatterfish/rig/build.gradle` | only `:harness` and `:brain` | confirmed |

The `brain` → `api`-only rule of AD-1 is also real in the build, not only in prose:
`shatterfish/brain/build.gradle` declares `api project(':api')` and adds an
`incoming.afterResolve` check that throws if `:core`, `:SPD-classes`, `:services` or `:desktop`
ever reaches `compileClasspath` or `runtimeClasspath`.

### 1.2 Current releases from the web (all checked 2026-09-03)

| Library | Pinned here | Current release | Source consulted | Note |
|---|---|---|---|---|
| ArchUnit | 1.3.0 (2024-04-11) | **1.5.0**, published 2026-08-04 | Maven Central `com/tngtech/archunit/archunit/maven-metadata.xml`, `lastUpdated 20260804005325`; `archunit-junit5/1.5.0/*.pom` returns HTTP 200 | the spine's planned bump target is correct and current |
| JUnit (5.x line) | 5.11.4 (2024-12) | **5.14.4** | Maven Central `org/junit/junit-bom/maven-metadata.xml` | three minors behind |
| JUnit (BOM line) | — | **6.1.3**, `lastUpdated 20260807124330` | same | the project is one major behind the live BOM line |
| Gradle | 9.4.0 (wrapper) | **9.7.1**, built 2026-08-19 | `https://services.gradle.org/versions/current` | same major; three minors behind |
| libGDX | 1.14.0 (upstream, released 2025-10) | **1.14.2** (2026-05/06) | `libgdx.com/news/2026/05/gdx-1-14-1`; GitHub releases | upstream's pin; not the architecture's to change at a fixed tag |
| MkDocs Material | 9.7.7 | **9.7.7**, uploaded 2026-07-17 | PyPI `pypi.org/pypi/mkdocs-material/json` | already current |
| MkDocs | 1.6.1 | **1.6.1**, uploaded 2024-08-30 | PyPI `pypi.org/pypi/mkdocs/json` | already current |

**Does `gdx-backend-headless` exist for libGDX 1.14.0? Yes.**
`https://repo1.maven.org/maven2/com/badlogicgames/gdx/gdx-backend-headless/maven-metadata.xml`
lists `1.14.0`, `1.14.1`, `1.14.2` (`lastUpdated 20260605085121`), and
`.../1.14.0/gdx-backend-headless-1.14.0.jar` returns HTTP 200. The harness's dependency
resolves.

> Trap for a future reviewer: `search.maven.org`'s solr index is stale and reports 1.13.1 as
> the newest `gdx-backend-headless`. It is wrong. Read `repo1.maven.org`'s
> `maven-metadata.xml` directly. The same applies to `archunit.org/news`, which as of today
> still lists 1.4.2 (2026-04-18) as the latest release and does not mention 1.5.0.

### 1.3 Stack findings

- **S-1 (stale).** JUnit 5.11.4 carries no bump note while ArchUnit does. It is three minors
  behind the 5.x line and predates the JUnit 6 split. Either bump it in E1 alongside ArchUnit
  or write the reason for staying put into the table.
- **S-2 (stale-ish).** Gradle wrapper 9.4.0 against a current 9.7.1. Same major, so low risk,
  but the table says "9.4 (wrapper)" as if it were chosen; there is no note of why.
- **S-3 (incomplete).** The Stack table has no row for the Android Gradle Plugin that root
  `build.gradle` puts on every build's buildscript classpath
  (`com.android.tools.build:gradle:9.1.0`; verified to exist —
  `dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.1.0/gradle-9.1.0.pom`
  returns HTTP 200). Since `./gradlew build -Pshatterfish.mobile=on` is a documented command,
  the table under-describes the real stack.
- **S-4 (wording).** "ArchUnit 1.3.0 (bump to 1.5.0 in E1 **after a web check of the current
  release**)" reads as though the check were still owed. It was done — the research report's
  citation-check digest records a Maven Central spot check on 2026-08-04 — and it still holds
  today. Reword to "1.5.0, confirmed current on 2026-09-03" so the next reader does not redo
  it. This is the one place in the spine where a *correct* number is presented as unverified,
  which is the opposite of the usual failure and worth keeping.

---

## 2. `path:line` citations against the pinned tree

The working tree equals v3.3.8: `git diff --stat v3.3.8 HEAD -- core SPD-classes desktop`
is empty, and `git describe --tags --match "*3.3.8*"` gives `v3.3.8-28-g3896eb3e7` (the 28
commits are Shatterfish's own docs and planning artifacts).

ADR-0011 and ADR-0012 carry **no** `path:line` citations at all, so the sample is drawn from
ADR-0009 and ADR-0013, with a bonus pass over ADR-0005, ADR-0007 and ADR-0008. Twenty-one
citations opened; twenty-one resolve.

### ADR-0009

| Citation | What the ADR claims | What the line is | Verdict |
|---|---|---|---|
| `…/Dungeon.java:624-704` | `saveGame` and `saveLevel` write the save | 624 `public static void saveGame( int save ) {`; `saveLevel` at 650; `saveAll` at 657 | confirmed |
| `…/Dungeon.java:723-840` | loading is `loadGame` plus `loadLevel` | 723 `public static void loadGame( int save, boolean fullLoad ) throws IOException {`; 840 closes `loadLevel` | confirmed |
| `…/Dungeon.java:508` | `switchLevel` clears `hero.curAction` | 508 `hero.curAction = hero.lastAction = null;` | confirmed, exact |
| `…/actors/Actor.java:170-192` | `saveAll` runs `Actor.fixTime` first | 170 `public static synchronized void fixTime() {`; 191 `now -= min;` | confirmed |
| `…/scenes/InterlevelScene.java:733-747` | `loadGame` + `loadLevel` + `switchLevel` | 733 `private void restore() throws IOException {`; 739 `Dungeon.loadGame`; 741-745 `loadLevel`/`switchLevel` | confirmed |
| `SPD-classes/…/utils/Bundle.java:365-376` | a save is a `Bundle` of `org.json` objects | 365 `public void put( String key, Bundlable object )`, writing `CLASS_NAME` + `storeInBundle` | confirmed |
| `SPD-classes/…/utils/Bundle.java:483-502` | ...written as gzip | 489 `public static Bundle read( InputStream stream )`, the GZIP-header sniff | **points at the read path**, see C-4 |

"No random state is saved" also checks out independently: `com.watabou.utils.Random` is not
`Bundlable`, has no `storeInBundle`, and `Bundle.java` never touches the generator stack.

### ADR-0013

| Citation | What the ADR claims | What the line is | Verdict |
|---|---|---|---|
| `…/scenes/GameScene.java:826-828` | actor thread notified at most 60×/s | 826-827 `//the actor thread processes at a maximum of 60 times a second`; 828 `private float notifyDelay = 1/60f;` | confirmed, exact |
| `…/scenes/GameScene.java:865-888` | the "SHPD Actor Thread" is created by `update()` | 865 `if (!Actor.processing() ...)`; 868 `actorThread = new Thread()`; 879 `setName("SHPD Actor Thread")`; 883-888 the `notify()` | confirmed, exact |
| `…/scenes/GameScene.java:838` | `update` is `synchronized` on the scene | 838 `public synchronized void update() {` | confirmed, exact |
| `…/scenes/GameScene.java:1054-1066` | the actor thread takes the same lock to add sprites | 1054 `private synchronized void addMobSprite( Mob mob )`; 1065 `synchronized (scene)` in `sortMobSprites` | confirmed |
| `…/actors/hero/Hero.java:863-881` | hero parks by returning `false` from `act()` after `ready()` | 863 `if (curAction == null) {`; 869 `ready();`; 881 `actResult = false;` | confirmed, exact |
| `…/actors/Actor.java:304-322` | the thread parks on its own monitor | 304 `if (!doNext){`; 305 `synchronized (Thread.currentThread())`; 315 `notify()`; 318 `wait()` | confirmed, exact |
| `…/scenes/CellSelector.java:152-171` | a click is delivered on the render thread | 152 `public void select( int cell, int button ) {`, dispatching to `listener.onSelect` | confirmed (the `Hero.handle`/`next` pair is in the companion citation, correctly) |
| `…/scenes/GameScene.java:1750-1756` | ...which calls `Hero.handle(cell)` then `hero.next()` | 1750 `defaultCellListener`; 1753 `Dungeon.hero.handle( cell )`; 1754 `Dungeon.hero.next();` | confirmed, exact |
| `…/actors/Actor.java:274-286` | `Actor.process()` waits on moving sprites | 274 `if (acting instanceof Char && ((Char) acting).sprite != null)`; 278-281 `sprite.wait()` | confirmed, exact |
| `…/sprites/CharSprite.java:824-862` | turn resolution ends in render-thread animation callbacks | 824 `} else if (tweener == motion) {`; 826 `synchronized (this)`; 830 `ch.onMotionComplete()`; 858 `ch.onOperateComplete()` | confirmed |
| `…/actors/hero/Hero.java:1019-1035` | windows shown via `Game.runOnRenderThread` after the hero is ready | 1019 `private boolean actBuy(...)`; 1023 `ready();`; 1026 `Game.runOnRenderThread(...)` showing `WndTradeItem` | confirmed for this site (one instance generalised — acceptable) |
| `…/actors/hero/Hero.java:935-946` | "the branch that calls `Dungeon.observe()`" | 935-946 is `private void ready()` and calls **no** `Dungeon.observe()` | **wrong**, see C-1 |

### Bonus pass (ADR-0005, ADR-0007, ADR-0008)

All confirmed exact: `…/tiles/FogOfWar.java:288-299` (`getCellFog`),
`…/tiles/DungeonTileSheet.java:427` (`SECRET_TRAP` → `EMPTY`'s visual) and `:464`
(`SECRET_DOOR` → `WALL`'s visual), `…/items/potions/Potion.java:377-379`
(`isKnown() ? super.name() : Messages.get(this, color)`),
`…/scenes/GameScene.java:1441-1448` (mob sprite visibility from `heroFOV`),
`…/ui/BuffIndicator.java:192-196` (`if (buff.icon() != NONE)`),
`SPD-classes/…/utils/Random.java:57-66` (the MX3 scramble, credited in-source to Jon Maiga),
`:202-229` (`chances(HashMap)`, whose `chances.keySet().toArray()` is exactly the hash-order
dependence ADR-0007 hooks), `:249-254` (`element(Collection)`),
`…/Dungeon.java:254` (`Random.resetGenerators();`), and `…/actors/hero/Hero.java:936-945`
(the body of `ready()`, dereferencing `sprite` and the `GameScene`/`AttackIndicator` statics).

AD-5's turn formula is also independently sound: `Actor.fixTime` does `Statistics.duration += min`
and `now -= min` in the same pass, so `Statistics.duration + Actor.now()` is the quantity fixTime
preserves. That is a real invariant, not a guess.

---

## 3. Claims about external methods

### 3.1 Fishtest's GSPRT — **confirmed, with one attribution slip**

ADR-0012 option 6 says: "GSPRT ported from Fishtest's `sprt.py`: the log-likelihood ratio uses
the normal approximation with the sample variance of the pair scores (approximation 2.1 in Van
den Bergh's note), regularized, with the per-pair increment clamped; ... bounds `log(β/(1-α))`
and `log((1-β)/α)`".

Checked against the live sources:

- **Bounds.** `fishtest/stats/sprt.py` line 24-25:
  `self.a = math.log(beta / (1 - alpha))`, `self.b = math.log((1 - beta) / alpha)`.
  Verbatim match with the ADR.
- **Clamping.** `sprt.py` `set_state`: sets `self.clamped = True` when
  `llr > 1.03*b or llr < 1.03*a`, then clamps `llr` to `a` or `b` and rescales `T`. Confirmed.
- **Regularization.** `fishtest/stats/LLRcalc.py` `results_to_pdf` opens with
  `results = regularize(results)`. Confirmed.
- **Approximation 2.1.** `LLRcalc.LLR_alt2` is
  `(s1 - s0) * (2 * s - s0 - s1) / var / 2.0`, documented in-source as pointing at
  `https://www.cantate.be/Fishtest/GSPRT_approximation.pdf`. That PDF is Michel Van den Bergh,
  *A Practical Introduction to the GSPRT*, and its numbered equation **(2.1)** reads
  `L(θ̂₁;x) − L(θ̂₀;x) ≈ ½ (φ₁ − φ₀)(2φ̂ − φ₀ − φ₁) / V(φ̂)`, preceded by
  "We claim that under suitable regularity conditions we have the following very convenient
  approximation for (1.1)". The code is the formula. The ADR's phrase "approximation 2.1 in
  Van den Bergh's note" is exactly right, down to the number.
- **Van den Bergh's §1** also states the stopping interval as
  `[log(β/(1−α)), log((1−β)/α)]` — the same bounds the ADR quotes.
- **Trinomial vs pentanomial.** Van den Bergh's own example gives the trinomial model as
  `(p₁,p₂,p₃) = (w,d,l)` with `a₁=1, a₂=1/2, a₃=0`, which is precisely ADR-0012's pair score
  `{1, 0.5, 0}`. The pentanomial is his model "for paired games with reversed colors" — so
  ADR-0012 option 10's rejection ("our pair is one comparison, not two games with sides") is
  well grounded.

**Slip (C-3 below):** the ADR names `sprt.py` as the port target. The LLR approximation and the
regularization live in `LLRcalc.py`; `sprt.py` holds the bounds, the clamp, and the Elo/CI
machinery the Rig does not need. The port is from two files.

### 3.2 E-processes — **confirmed**

ADR-0012 option 8 describes an "E-process (test supermartingale, mixture or betting form) that
needs no pre-registered alternative and allows continued testing." This matches the standard
account: an e-process is a non-negative process whose expectation under the null is at most 1 at
every stopping time, inducing a valid sequential test that rejects when it first exceeds `1/α`;
the betting/testing-by-betting formulation (Ramdas and colleagues) supports anytime-valid
inference under optional stopping and optional continuation. The ADR's framing is accurate and
appropriately hedged (it is an alternative to be *evaluated in E3*, not a promise).

### 3.3 Long et al.'s properties — **names confirmed verbatim; one justification wrong, and one property does not transfer**

Confirmed against the primary source this time, not a summary: the paper PDF was fetched from
`webdocs.cs.ualberta.ca/~nathanst/papers/pimc.pdf` and its text extracted. Authors are
**Jeffrey Long, Nathan R. Sturtevant, Michael Buro and Timothy Furtak** (University of Alberta),
AAAI 2010. (Not Bowling — worth noting because he is a frequent co-author on adjacent work.)

The paper's own definitions, verbatim from its Methodology section:

> - **Leaf Correlation, lc**, gives the probability all sibling, terminal nodes have the same
>   payoff value. Low leaf node correlation indicates a game where it is nearly always possible
>   for a player to affect their payoff even very late in a game.
> - **Bias, b**, determines the probability that the game will favor a particular player over
>   the other.
> - **Disambiguation factor, df**, determines how quickly the number of nodes in a player's
>   information set shrinks with regard to the depth of the tree.

ADR-0010 names all three correctly, and its gloss "low disambiguation means determinizations
mislead" is a fair reading of `df` (the paper's own contrast is trick-taking games, where each
play reveals a card, against poker, where "no private information is directly revealed until the
game is over").

**The pre-mortem's justification is wrong.** ADR-0010 says "Long et al.'s recipe **assumes a
perfect-information game** and the properties are ill-defined here." The paper's abstract opens:
"Perfect Information Monte Carlo (PIMC) search is a practical technique for playing **imperfect
information games** that are too large to be optimally solved." The whole paper is about
imperfect-information games; `df` is only definable when hidden information exists. The ADR has
the field exactly backwards.

**And the correct objection is sharper than the ADR's instinct.** The paper's framing is
two-player zero-sum throughout ("The ideal solution, at least for two-player zero-sum games, is
to use a solution technique that can produce a Nash equilibrium"), and that framing is baked
into the property definitions themselves:

- **Bias is undefined for Shatterfish.** It is literally "the probability that the game will
  favor a particular player **over the other**". SPD is single-player against a stochastic
  environment. There is no other player, so `b` has no value to compute — not a noisy value, no
  value.
- **Leaf correlation transfers** with a reinterpretation: sibling terminal nodes sharing a
  payoff becomes sibling rollout outcomes sharing a Composite outcome. Definable, and worth
  measuring.
- **Disambiguation factor transfers directly.** The hero's information set over unidentified
  items and unseen mobs does shrink as a Run proceeds; that is exactly `df`.

This is actionable, not cosmetic. ADR-0010's measurement table commits E6 to computing "Leaf
correlation, bias and disambiguation factor ... with random playouts from the same snapshots,
following the recipe in research §4". One of the three cannot be computed as defined, so the
measurement story would either stall or silently invent a substitute. Fix the pre-mortem's
reason, and either drop `bias` from the measurement row or state the single-player surrogate
being used in its place (an obvious candidate: the base rate at which the environment rather
than the policy determines the outcome, but that is a new definition and should be labeled as
Shatterfish's own, not attributed to Long et al.).

### 3.4 SplitMix64 — **real algorithm, under-specified use**

SplitMix64 is a real and well-characterised 64-bit mixing/generation function (Steele, Lea and
Flood, *Fast Splittable Pseudorandom Number Generators*, OOPSLA 2014; it is the basis of
`java.util.SplittableRandom`'s advance and finalizer). Nothing invented here.

But ADR-0007's decision outcome says only that "`mix` is SplitMix64 over the two longs".
SplitMix64 takes **one** 64-bit input. "Over the two longs" does not say how `salt` and `k` are
combined — seed the state with `salt` and advance `k` times? finalize `salt + k * GOLDEN`?
finalize `salt ^ splitmix64(k)`? Each gives a different stream, and the entire determinism
guarantee (AD-6, the two-JVM determinism test, every Run tuple, every Registration) is defined
in terms of `mix(salt, k)`. This is the single least-specified load-bearing thing in the whole
spine. See C-5.

### 3.5 JNI's one-loader rule — **confirmed verbatim, but absent from the ADRs**

The Oracle JNI specification (Java 21, *Invocation API*, "Library and Version Management")
states verbatim: *"The same JNI native library cannot be loaded into more than one class
loader."* Fetched and confirmed at
`https://docs.oracle.com/en/java/javase/21/docs/specs/jni/invocation.html`.

The rule appears in the brief addendum, the PRD addendum and the research report, each with the
citation. **It does not appear in any of ADR-0005 to ADR-0013.** ADR-0009 option 10 encodes its
*consequence* — "libGDX and natives in a shared parent loader, game classes per child" — with no
statement of why. A reader of the ADR alone sees an arbitrary-looking constraint. Worth one
sentence and the spec link, since the E1 isolation spike will be scoped from this ADR. See C-6.

### 3.6 Numbers carried in from the research report

- **"969 sprite dereferences"** (ADR-0008 pre-mortem). Reproduced exactly at v3.3.8:
  `grep -roh "sprite\." core/.../actors` gives **596** and `.../items` gives **373**,
  total **969**. The research report's split (596 + 373) matches to the digit. This one is real.
- **"chess pairing saves about 15%"** and the ≈ −0.15 within-pair correlation (ADR-0012
  context). Traced in the research report to fishtest issue #348 (2019-01-08). **I did not
  re-open that issue.** The ADR treats it correctly as a prior to be measured, not as a fact
  about Shatterfish, so the exposure is low — but it is the one quantitative claim in
  ADR-0012 I am relaying rather than confirming. See U-1.
- **"the project's own 2020 deadlock came from the two threads sharing monitors"** (ADR-0013
  context). Traced in the research report to upstream issue #431 (2020-06-30). **Not
  re-opened.** The mechanism is independently visible in the code I read (the actor thread waits
  on `sprite`'s monitor at `Actor.java:278-281` while `GameScene.update` holds the scene monitor
  at `:838` and `addMobSprite` takes it at `:1054`), so the design conclusion stands on its own
  evidence even if the issue number is off. See U-2.

---

## 4. Findings: things to fix

### C-1 — ADR-0013 names the wrong method for the Input-wait hook (**correctness, blocks E1**)

> "the hook of ADR-0008 row 3 fires from `Hero.ready()` on the actor thread the first time the
> hero becomes ready (the branch that calls `Dungeon.observe()`, `…/actors/hero/Hero.java:935-946`)"

`Hero.ready()` occupies 935-946 and its entire body is `sprite.idle()`, `curAction = null`,
`damageInterrupt = true`, `waitOrPickup = false`, `ready = true`, `canSelfTrample = true`,
`AttackIndicator.updateState()`, `GameScene.ready()`. **There is no `Dungeon.observe()` call in
it.** The `Dungeon.observe()` calls in `Hero.java` are at 843, 1363, 1717 and 2220.

The branch the ADR is describing is at **`Hero.java:840-848`, inside `Hero.act()`**:

```java
if (!ready) {
    //do a full observe (including fog update) if not resting.
    if (!resting || buff(MindVision.class) != null || buff(Awareness.class) != null) {
        Dungeon.observe();
    } else {
        Dungeon.level.updateFieldOfView(this, fieldOfView);
    }
}
```

The pre-mortem's mitigation — "the hook fires only on the `!ready` branch" — describes this
`act()` site too, not `ready()`. So two different things are conflated under one citation, and
they are on different sides of the same turn: `act()`'s `!ready` branch runs when the hero is
*not yet* ready, `ready()` runs when it *becomes* ready.

This matters because ADR-0008's hook row 5 is scoped as "`Hero.ready()` / `Hero.interrupt()`
guards" and ADR-0013's row 3 notification is scoped to `Hero.ready()`. Whichever site is
actually wanted, E1 needs the ADR to name it unambiguously before a hook is written against it.
Fix: decide the site, correct the citation, and correct the pre-mortem's "`!ready` branch"
wording to match.

### C-2 — ADR-0009's "Unseen mobs" scrub row is contradicted by a line ADR-0005 itself cites (**fairness**)

ADR-0009's hidden-element table:

| Unseen mobs | Remove every mob outside `heroFOV`; re-add the sample's remembered mobs ... | *mobs are present iff in FOV* |

The justification is not what the code does. `GameScene.java:1441-1448` — the same range
ADR-0005 cites for its own "shows a mob iff `heroFOV[mob.pos]`" claim — reads:

```java
if (mob instanceof Mimic && mob.state == mob.PASSIVE && ((Mimic) mob).stealthy() && Dungeon.level.visited[mob.pos]){
    //mimics stay visible in fog of war after being first seen
    mob.sprite.visible = true;
} else {
    mob.sprite.visible = Dungeon.level.heroFOV[mob.pos];
}
```

A stealthy passive Mimic on a VISITED cell outside `heroFOV` **is** drawn. Scrubbing "every mob
outside `heroFOV`" would therefore change the Observation, and the redetermination differential
test would fail on exactly this case — which is the good outcome, but it means the table as
written is a known-bad specification. ADR-0006 does carry a Mimic row (`Mimic.java:62-64`), so
the knowledge exists in the program; ADR-0009's table just does not inherit it. Add the
exception to the row, or state that the row defers to ADR-0006's per-rule table.

The same "iff" over-simplification appears in ADR-0005's Facts-from-the-code paragraph. Lower
stakes there (it is context, not a rule), but worth the same qualifier.

### C-3 — ADR-0012 names one Fishtest file where the port needs two (**precision**)

"GSPRT ported from Fishtest's `sprt.py`" understates the port. The approximation-2.1 LLR
(`LLR_alt2`), the drift/variance form the test actually monitors
(`LLR_drift_variance_alt2`), and the regularization (`results_to_pdf` → `regularize`) are all in
`fishtest/stats/LLRcalc.py`. `sprt.py` contributes the bounds and the clamp. Name both files so
the E3 implementer knows where to look, and record the two exact formulas
(`(s1-s0)*(2*s-s0-s1)/var/2` and the bounds) in the ADR, since that is the whole port.

### C-4 — ADR-0009's gzip citation points at the read path (**precision**)

`SPD-classes/…/utils/Bundle.java:483-502` is `Bundle.read(InputStream)` and its GZIP-header
sniff (`header[0] == 0x1f && header[1] == 0x8b`). It is cited in a sentence about *writing*
("a save is a gzip `Bundle` ... written by `Dungeon.saveGame` and `saveLevel`"). The gzip fact
is true; the pointer faces the wrong way. The write side is `Bundle.write(Bundle, OutputStream)`
at `:535` and `write(Bundle, OutputStream, boolean)` at `:539`, gated by
`compressByDefault = true` at `:485`.

Related, in the same ADR: option 1 writes the API as "`Bundle.write(OutputStream)`". The real
signature is the static two-argument `Bundle.write(Bundle, OutputStream)`. One-word fix, but it
is the seam the whole snapshot mechanism sits on.

### C-5 — `mix(salt, k)` is undefined (**correctness, blocks E1**)

See §3.4. `mix` appears in AD-6, AD-7, ADR-0007, ADR-0009 and ADR-0013 as the definition of
every random stream Shatterfish controls, and it is specified only as "SplitMix64 over the two
longs". SplitMix64 takes one input. Write the exact composition into ADR-0007 — including the
constants and the finalizer — and give it a test vector, the same way ADR-0011's pre-mortem
gives the canonical-JSON chain a test vector. Without it, two implementations of the spine
produce different Runs from the same Run tuple, which is precisely what NFR-2 forbids.

### C-6 — ADR-0009's classloader option states a constraint without its source (**traceability**)

Option 10's "libGDX and natives in a shared parent loader, game classes per child" is a
consequence of the JNI one-loader rule, which is confirmed verbatim in the Oracle spec and
already cited in the brief and PRD addenda — but nowhere in the ADR set. Non-negotiable #8
("Codex over folklore") is about game mechanics, but the same discipline should apply to a
platform constraint that scopes an E1 spike. One sentence plus the spec URL.

### C-7 — ADR-0008 cites one range for two methods (**minor**)

"`Hero.ready()` and `Hero.interrupt()` dereference scene statics (`…/actors/hero/Hero.java:936-945`)".
936-945 is the body of `ready()` only; `interrupt()` begins at 948. Add the second range.

### C-8 — ADR-0010 has Long et al. backwards, and commits E6 to computing an undefined quantity (**correctness, scopes an E6 story**)

Two separate problems, both in ADR-0010, both confirmed against the paper's own text (§3.3):

1. The pre-mortem says "Long et al.'s recipe assumes a perfect-information game". The paper is
   about **imperfect**-information games — that is its first sentence. The mitigation the ADR
   reaches is still right; the reason given for it is the opposite of the truth, and a reader
   who trusts it will mis-scope the E6 measurement.
2. The measurement table commits E6 to computing "Leaf correlation, **bias** and disambiguation
   factor". Bias is defined in the paper as "the probability that the game will favor a
   particular player **over the other**" — a two-player quantity. Shatterfish is single-player
   against a stochastic environment, so bias has no value to compute. Leaf correlation and
   disambiguation factor both transfer (see §3.3); bias does not.

Fix: correct the pre-mortem's reason to the real mismatch (two-player zero-sum versus
single-player stochastic), and either drop bias from the measurement row or name the
single-player surrogate explicitly as Shatterfish's own definition rather than attributing it to
Long et al.

---

## 5. Assumptions stated as fact (no repository or web evidence either way)

These are not errors. They are places where the prose asserts and the program has not yet
measured, and where a reader could mistake the assertion for a finding.

- **A-1. AD-6, "identity-hash order is removed by the identity-order hook row."** ADR-0007
  option 9 is honest that the *frequency* of hash-order ties biting is unknown ("the mechanism
  is certain even if the frequency is unknown"), and option 10 lists six specific sites. The
  spine's flat "is removed" claims completeness for a six-site enumeration that has not been
  run against a real Run. Suggest "is addressed by" until the two-JVM determinism test is green.
- **A-2. ADR-0007 option 15, "a headless Run and an Overlay Run consume different draw counts
  within a turn ... only the routing can [fix it]".** The mechanism is well argued from real
  citations (`EmoIcon.java:89`, `Emitter.java:92`, `Mob.java:229-237`), but "required rather
  than conditional" is a prediction about draw counts that no test has yet produced. It is the
  right default; it should be labeled as a prediction with an E5 test that confirms it.
- **A-3. ADR-0013, "about 17 ms at 60 fps" of hand-off latency.** The arithmetic is right
  (1/60 s = 16.7 ms) but "one frame of latency per hand-off" is the best case. A Decision that
  completes just after a poll waits nearly a full frame to be *seen*, and the Action is
  executed on that same frame — so one frame is right for the poll, but the ADR should say
  "up to one frame", not "about 17 ms", and should note the second frame if the executor
  defers.
- **A-4. ADR-0009, "rollouts inside a Decision horizon never generate a floor"** (the
  justification for resetting generator decks and `LimitedDrops` to tag defaults). Plausible
  given ADR-0010's horizon of 2 to 4 hero turns, but a hero on a descent staircase with a
  2-turn horizon is a counterexample the ADR does not address. Either constrain the rollout to
  refuse a level transition, or widen the scrub row.
- **A-5. ADR-0009, "Snapshots ... cheap enough to take at every Input wait in the Overlay."**
  Listed as a decision driver and then asserted in the outcome ("in the Overlay one snapshot per
  Input wait lives in memory"). The pre-mortem does flag the risk, and the E1 spike is supposed
  to measure it — but the outcome section commits to per-wait snapshots before the measurement
  exists. Make the commitment conditional on the spike, the way ADR-0010 makes its search
  choice conditional.
- **A-6. ADR-0012, "Pairing on (seed, salt) is the strongest common-random-numbers design
  available."** A superlative with nothing behind it. The ADR's own pre-mortem then concedes
  that two Brains diverge at their first differing Decision and "'paired' is a name only". The
  two sentences sit two pages apart and disagree in tone. Soften the first.
- **A-7. Spine AD-2, "`Tile` has no `SECRET_*`."** This is a design rule for a type that does
  not exist yet, phrased as a statement of fact. Fine as a rule; should read "must have no".

---

## 6. Unconfirmed

- **U-1.** Fishtest's ≈ 15% variance saving and ≈ −0.15 within-pair correlation, cited in
  ADR-0012's context paragraph via the research report (fishtest issue #348, 2019-01-08). Not
  re-opened in this review. Low exposure: the ADR treats it as a prior to be measured in E3, and
  the design explicitly "does not depend on it being large".
- **U-2.** The 2020 upstream render/actor deadlock (upstream issue #431, 2020-06-30), cited in
  ADR-0013's context via the research report. Not re-opened. Low exposure: the shared-monitor
  mechanism it is used to justify is independently visible in the code cited above.
- **U-3.** The exact composition of `mix` (see C-5) — unconfirmable, because it is not written
  down anywhere in the repository.

---

## 7. What was checked and found sound, for the record

So the next reviewer does not repeat it:

- Every Stack row against the build files (§1.1) and against the current release of each
  library on the web (§1.2), on 2026-09-03.
- `gdx-backend-headless:1.14.0` exists and resolves (§1.2).
- The working tree's upstream code equals tag v3.3.8.
- 21 `path:line` citations across ADR-0005, 0007, 0008, 0009 and 0013 (§2). All resolve;
  one (C-1) does not support the sentence it is attached to, one (C-4) faces the wrong way,
  and the other 19 are accurate, several exact to the line.
- Fishtest's bounds, clamping and regularization, and Van den Bergh's equation (2.1), read
  from the live sources and matched against the ADR's own words (§3.1).
- E-processes (§3.2); Long et al.'s three property definitions, read verbatim from the paper's
  own PDF rather than a summary, with authors Long, Sturtevant, Buro and Furtak (§3.3);
  SplitMix64's provenance (§3.4); and the JNI one-loader rule verbatim from the Oracle Java 21
  spec (§3.5).
- The 969 sprite dereferences, reproduced exactly at v3.3.8 (§3.6).
- That `Random` is not `Bundlable` and no RNG state is saved, supporting ADR-0009 (§2).
- That `Statistics.duration + Actor.now()` is the quantity `Actor.fixTime` preserves,
  supporting AD-5 (§2).
