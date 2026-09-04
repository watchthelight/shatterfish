---
story: 1.2
key: 1-2-the-hooks-registry-the-counting-test-and-the-boundary-rules
title: The Hooks registry, the counting test, and the boundary rules
epic: 1
issue: 15
status: done
created: '2026-09-04'
updated: '2026-09-04'
---

# Story 1.2: The Hooks registry, the counting test, and the boundary rules

As the engineer,
I want one registry class for every upstream edit and the full set of boundary rules,
So that neither an unexplained hook nor a forbidden import can enter the tree.

## Acceptance criteria and how each was met

| Criterion | Outcome |
|---|---|
| `Hooks.java` is added under `core` with one nullable listener field per hook point | **Met, with one point rather than ten.** The registry, its convention and its `clear()` are landed; the only listener point declared is `inputWait`, the one ADR-0015 has already decided. The other nine ledger rows are not all listener rows, and the ones that are belong to stories that have not run |
| `HooksLedgerTest` greps the tree for hook markers and fails if the set of ids differs from the rows in `docs/UPSTREAM.md`, or if the row count exceeds ten | **Met, and then some.** Eleven checks: ids in the tree equal rows in the document; the machine-readable site index equals the markers file by file; nothing looks like a marker without being one; at most ten rows with no id used twice; id 2 confined to the registry; every upstream file changed, added, deleted or renamed relative to the pinned tag carries a marker; each upstream file's changed lines digest to exactly what the ledger declares; the registry declares no method but `clear()`; every top-level directory in the pinned tag is classified as upstream's or ours; nothing tells git to stop reading upstream files as text; and nothing under an upstream module is hidden from git by a rule of ours. It found a real defect on its first run: hook row 1 in `settings.gradle` was marked `//shatterfish hook #1`, which no parser matches |
| `HooksVanillaTest` boots with no listener registered and asserts the vanilla branch runs at every site | **Met for the site where the vanilla branch is reachable, which is one of three.** Both branches of `add(EmoIcon)` are exercised at runtime. The guard branch of all three is exercised. The vanilla branch of the two `cellSelector` sites needs a `CellSelector`, which needs a texture and a camera, so it needs the booted headless application story 1.3 builds; that story owns them. Beyond the runtime checks, the property is held against the pinned tag |
| `./gradlew :desktop:run` still launches the unmodified game | **Not run, and the criterion is wrong.** That task has never worked (story 1.1 finding); the working task is `:desktop:debug`, and story 1.1 ran it against these same three guards. This story adds no site and no call: `Hooks.java` is a new file with no caller, so it cannot change vanilla behaviour, and `:desktop:jar` compiles it |
| `BrainBoundaryTest` asserts that `brain` depends on no game package, no other Shatterfish module but `api`, and none of `java.io`, `java.nio.file`, `java.net` or `java.lang.reflect` | **Met, and the criterion as written is not close to sufficient.** Two adversarial reviews each read hidden game state with every rule the criterion asks for in place. The rule is now an allowlist. See *The rules that did not bind, twice* |
| `ApiBoundaryTest` asserts `api` depends only on the JDK | **Met, and the criterion is again not sufficient.** `api` sits inside the brain's allowlist and ArchUnit is not transitive, so "only the JDK" left reflection one class away from the brain. `api` now carries the same allowlist and the same denied classes |
| The ArchUnit bump to 1.5.0 lands here with every boundary rule green | **Met.** 1.3.0 to 1.5.0 |

## What was built

- `core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java`: the registry,
  hook row 2. The only Shatterfish-authored source file outside `shatterfish/`.
- `shatterfish/harness/src/test/java/org/shatterfish/harness/hooks/`: `Ledger` (markers, ledger rows,
  the site index, and the difference from the pinned tag), `HooksLedgerTest`, `HooksVanillaTest`.
- `shatterfish/brain/src/test/java/org/shatterfish/brain/`: `BrainBoundaryTest` replacing
  `BrainImportsNoGameCodeTest`, and `BrainBoundaryRulesBiteTest`, which checks each rule against a
  class that breaks it.
- `shatterfish/api/src/test/java/org/shatterfish/api/ApiBoundaryTest.java`, replacing
  `ApiDependsOnNothingTest`.
- `settings.gradle`: hook row 1's marker written in the form the parser reads.
- `docs/UPSTREAM.md`: row 2; a machine-readable site index; a table of what each check catches; row
  1's and row 5's guard columns rewritten to say what is tested rather than argued.
- `shatterfish/java-module.gradle`: ArchUnit 1.5.0.
- `shatterfish/harness/build.gradle`: `docs/UPSTREAM.md` and the upstream tree declared as inputs of
  `:harness:test`.
- `.github/workflows/build.yml`: `fetch-depth: 0`, because the checks compare the tree against the
  pinned tag and a shallow checkout does not have it.

## The rules that did not bind, four times

The first draft of this story passed its own tests and was wrong. Four adversarial fairness review
passes each demonstrated, by writing the class and running the build, that hidden game state was
reachable with every rule green. Each fix was correct as far as it went, and each was walked through
again.

**Round one** used reflection:

```java
Class<?> dungeon = Class.forName("com.shatteredpixel.shatteredpixeldungeon.Dungeon");
MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(dungeon, MethodHandles.lookup());
return lookup.findStaticGetter(dungeon, "level", Object.class).invoke();
```

Three reasons it passed. A package rule sees bytecode dependencies, and `Class.forName` names the
game in a `String`, so `brain_never_depends_on_game_code` had nothing to look at — and the brain
shares a JVM with the game by non-negotiable #4, so the game's classes are on the classpath at run
time whatever `brain` compiles against. The reflection ban named
`java.lang.invoke.MethodHandles..`, which matches no package at all, because `MethodHandles` reports
package `java.lang.invoke`; the exact API the exploit turns on was nominally banned and actually
open. And the positive rule allowed all of `java..`, which is where `Class` and `ClassLoader` live.

**Round two** answered that with a longer denylist — `java.lang.invoke`, `System`, `ServiceLoader`
and nine other names — and was walked through in one move:

```java
Object c = new java.beans.Expression(Class.class, "forName", new Object[]{type}).getValue();
Object f = new java.beans.Expression(c, "getDeclaredField", new Object[]{field}).getValue();
new java.beans.Statement(f, "setAccessible", new Object[]{true}).execute();
return new java.beans.Expression(f, "get", new Object[]{null}).getValue();
```

`java.beans.Expression` dispatches reflectively by string and returns `Object`, so no banned type
appears in the class file at all. It read `Dungeon.seed` — the one thing non-negotiable #1 names by
name. The same review found `java.lang.management` handing back the whole system-property table,
`ProcessHandle` the command line, and `SecureRandom`, `RandomGenerator`, `UUID` and `Date` all
substituting for the generators and clocks that had just been banned.

**The lesson is about the shape of the rule, not the length of the list.** A denylist over the JDK
cannot be finished: the JDK is large, and an attacker needs one door. So the rule is inverted. The
brain may depend on `org.shatterfish.api`, its own package, and a short list of JDK packages that
hold data and arithmetic — `java.lang`, `java.lang.runtime`, `java.util`, `java.util.function`,
`java.util.stream`, `java.math`. Everything else is denied because it is not on the list. A
capability the brain genuinely needs becomes one line and a decision someone makes on purpose.

Two packages have to be allowed whole and are not innocent, so a denylist survives inside them, and
only there: `java.lang` holds `Class`, `System` and `ProcessBuilder`; `java.util` holds `Random`,
`ServiceLoader` and `ResourceBundle`. Both are closed sets fixed by the JDK rather than open-ended
surface. A dozen method-level bans sit alongside them, for doors on classes the brain legitimately
needs and cannot be denied outright: `Boolean.getBoolean`, `Integer.getInteger` and `Long.getLong`
read system properties without naming `System`; `Collections.shuffle` and `StrictMath.random` seed
generators of their own; `parallelStream` and `parallel` reopen the common pool; `getStackTrace`
hands back the caller chain that `StackWalker` is denied for; and `String.format`, `toUpperCase` and
`toLowerCase` follow the host's default locale, so the same Run formats one way in Germany and
another in Turkey. Denying `java.util.Locale` as a type does not reach those last three, because the
overloads that take a locale name it and the ones that do not name nothing at all.

**Round three did not attack the list at all.** It reported that no by-name dispatch primitive
survives inside the allowed packages once reflection, method handles, `java.beans`, `Class`,
`ClassLoader` and `ServiceLoader` are gone — and then went around it twice, because both holes were
in *what the allowlist is applied to* rather than in the list.

First, **`api` was a laundering layer.** The brain's allowlist permits `org.shatterfish.api..`
wholesale, and ArchUnit's dependency rules are not transitive, so every door shut in `brain` was
reopened by one class in `api` — whose own rule still allowed all of `java..`, reflection included.
A fifteen-line reflective reader in `api`, called from `org.shatterfish.brain`, printed
`Dungeon.seed` with every rule in both modules green. `ApiBoundaryTest` now carries the same
allowlist and the same denied classes, deliberately narrower because DTOs need less, and says in as
many words why the two must not diverge.

Second, **the boundary was opt-in by package name.** Every rule selects
`.that().resideInAPackage("org.shatterfish.brain..")`, so a class compiled into the brain module
under any other package is selected by nothing — not scanned, not rejected, absent. A twelve-line
class in `org.shatterfish.peek` did `Class.forName`, `setAccessible` and a static field read, shipped
in `brain.jar`, and left the build green. The whole brain could have been written one package to the
left. `BrainPackageAnchorTest` walks the module's own compiled output and fails on any class outside
`org/shatterfish/brain/`; `ApiBoundaryTest` does the same for `api`.

**Round four stopped attacking the rules and attacked git.** Every check in `HooksLedgerTest` and
`HooksVanillaTest` reads a diff, so anything that stops git producing one disarms all of them at
once. A single `.gitattributes` at the repository root containing `*.java binary` turns every
upstream diff into "Binary files differ": the digest goes quiet, the line counts go quiet, and the
wrap rule passes vacuously because it sees no removed lines. The file itself is a root-level
addition, which by construction no other check looks at. With that in place, `GameScene` gained
`trueSeed()`, `everyMobPositionEvenUnseen()` and `everySecretDoorAndHiddenTrap()`, and
`./gradlew build` was green. The same shape of move works through `.gitignore`: a rule of our own
hiding a new file under `core/` removes it from the diff and from the untracked listing together.

Diffs are now read with `--text`, a file git still calls binary is a failure rather than a skip, no
`.gitattributes` may mark an upstream path binary, and anything ignored under an upstream module must
be build output or ignored by a rule upstream itself shipped at the pinned tag — which is how
`ios/robovm.properties` stays legal without opening the door.

**`BrainBoundaryRulesBiteTest` is the answer to why none of this was caught.** `brain` holds one
trivial class, so every rule passed over almost nothing and not one of them had ever rejected
anything — which makes a rule indistinguishable from a comment. Every rule is now checked against a
class that breaks it, both review exploits are fixtures, and ordinary Java (lambdas, string
concatenation, streams, records, enums, `BigDecimal`) is checked to pass every rule, because a ban
wide enough to catch ordinary code is one that gets deleted the first time it is inconvenient. Two
rules cannot be exercised from inside `brain`, because game code and the other Shatterfish modules
are absent from its classpath; that absence is the guarantee, and putting either there to test the
test would be the hole itself.

## Decisions taken inside the story

**One listener point, not ten.** The criterion says "one nullable listener field per hook point", and
the obvious reading is to declare all ten now. Rejected: four of the ten rows have no listener at all
(a build file, read-only accessors, the identity-order edits, the draw routing), and the remaining
five belong to stories that have not run, so their signatures would be guesses — which is exactly
what ADR-0016's pre-mortem is about. What landed is the mechanism plus `inputWait`, whose site and
meaning ADR-0015 already decided, so the registry has a real contract to test rather than being an
empty shell. Adding a point later edits this file only and consumes no ledger row.

**The diff budget, and then the digest.** Markers, guards and the site index all key off something
the change announces about itself, so none of them sees a change that announces nothing. The second review
added a `public static Object peekEverything()` returning `Dungeon.level` to `GameScene` — no
marker, no removed line, a public accessor to every hidden mob and secret door in the file the
ledger names as the likeliest place for a leak — and the build stayed green. So `docs/UPSTREAM.md`
now also declares how far each upstream file may differ from the pinned tag, as
`<added> <removed> <path>`, checked against `git diff --numstat`.

Counting lines was not enough either, and round three showed it twice. `git diff --numstat` is
content-blind, so an edit that swaps one line for another leaves both counts untouched: a comment
inside the `selectCell` hook block became `Dungeon.hero.viewDistance = 999` — the hero sees the whole
level, the most direct parity break available — with the marker count, the site index, the wrap rule
and the budget all satisfied. And the budget covered only modified files, so the registry, added
since the tag, was governed by nothing but its marker; a `public static Object peek()` returning
`Dungeon.level` went into the one file inside `core` that upstream code is designed to call into,
with the build green.

So the ledger now declares what each upstream file's difference from the tag *is*: a digest of the
changed lines, hunk headers dropped so it depends on content rather than position, with the counts
kept alongside for a reader and asserted too. Added files are included, the registry among them,
because for an added file the difference from the tag is its whole content. `Hooks` is additionally
pinned to declaring exactly `clear()` and nothing but interfaces, so it cannot grow an accessor even
between digest updates.

**The site index.** Comparing sets of ids does not catch a fourth site added to `GameScene` under id
5, for an unrelated reason, with no change to the ledger — and `GameScene` is precisely where an
Observer-adjacent leak would be added. The prose table abbreviates paths for a reader, so
`docs/UPSTREAM.md` now also carries the same information for a machine: `<id> <markers> <path>`, one
line per row per file, checked for equality against the tree. A mistyped marker is caught separately,
because a marker that does not parse is a comment that reads like a declaration.

**Every status, not only modifications.** The first draft read only `M` from `git diff --name-status`,
so deleting an upstream file, renaming one, or adding a new class inside `core` was invisible to every
check — including to the rule whose message claimed to catch it. Untracked files were invisible too,
which is the ordinary state of a new file mid-story. All four are now failures.

**An allowlist of upstream roots, with a check on the allowlist.** Classifying paths by listing *our*
directories broke the moment a tool added one (`.bmad-loop`), turning an ordinary addition into a
build failure. The rule now names upstream's code modules, and a separate check fails when the pinned
tag has a top-level directory this project has not classified — so the allowlist cannot quietly go
stale across an upgrade.

**Why the vanilla branch is proved two ways, and what was wrong with the first answer.** The first
draft published that the vanilla branch "cannot be reached from a test at all", and used that as the
sole justification for not writing one. It is false: `new GameScene()` constructs headlessly, and
`emoicons` and `scene` are reachable by the same reflection the tests already use, so
`add(EmoIcon)`'s vanilla branch is a short test that now exists. The other two sites really do need a
`CellSelector`, whose constructor dereferences a `DungeonTilemap` (`CellSelector.java:55-56`). An
earlier draft of this paragraph said that tilemap's "only concrete subclass" reads `Dungeon.level`
and a texture, and added "verified by probe, not assumed"; a third review checked it and there are
five (`DungeonTerrainTilemap.java:29`, `DungeonWallsTilemap.java:29`, `GridTileMap.java:29`,
`RaisedTerrainTilemap.java:27`, `TerrainFeaturesTilemap.java:36`), one of which guards
`if (Dungeon.level != null)`. The load-bearing blocker is not level generation: it is the
`TextureFilm` the abstract constructor builds (`DungeonTilemap.java:40-41`, `TextureFilm.java:53-55`),
which needs a graphics binding and the game's assets, and the camera `CellSelector` then reads. That
is the booted headless application of ADR-0015, which is story 1.3's driver.
The source-level rule stays alongside the runtime tests rather than instead of them, because it
covers hooks that do not exist yet.

**A hook encloses vanilla code; it does not delete it.** Every line an upstream file loses relative to
the pinned tag must be enclosed by an added line that begins a condition and ends with that line.
Comments and blank lines are exempt. The first draft accepted the removed text appearing *anywhere*
in the added text, which a commented-out statement satisfies. One hook breaks the rule honestly: row
1 moves the two mobile `include` lines into `shatterfish/settings.gradle`, which restores them under
`-Pshatterfish.mobile=on`. That is a named relocation with the target checked, and a new entry needs
its own argument in its `docs/UPSTREAM.md` row.

**Renames are classified by both paths, and rename detection is turned off.** The first draft
read only the new path of a rename, so `git mv android/proguard-rules.pro shatterfish/...` moved an
upstream file out of upstream with the build green — and whether it did depended on the reviewer's
`diff.renames` setting, in a class whose comment claims a check may not depend on that. Rename
detection is now pinned off, so a move arrives as a deletion and an addition and both paths are
classified.

**Declared task inputs.** `Ledger` reads `docs/UPSTREAM.md` and the upstream tree, neither of which
Gradle could know about, so an unmarked edit left `:harness:test` up to date and the check simply did
not run — the build stayed green because nothing looked. The first fix was incomplete in two ways the
second review demonstrated: `shatterfish/settings.gradle` is read as hook row 1's relocation target
and was not an input, so row 1's vanilla-equivalence claim could be broken with the build green; and
root-level files other than the build scripts were classified as upstream's but not declared, so
`LICENSE.txt` could change unnoticed. Both are now inputs.

**The registry's documented idiom reads the point into a local.** Testing the volatile field and then
reading it again can be interrupted by `clear()` on the thread that ends a Run, and the second read
returns null on the game's actor thread. Volatility makes a registration visible; it does not make
two reads one.

**Directory names are matched at the root, not at any depth.** The first marker scan skipped any
directory named `shatterfish`, which skipped
`core/.../shatteredpixeldungeon/shatterfish/Hooks.java` — the one file the ledger most needs to see.
The test caught it: the registry was invisible and its row looked orphaned.

## Evidence

`./gradlew build -Pshatterfish.mobile=off`: green. `mkdocs build --strict`: clean.

A test that cannot fail is not evidence. Each check was run against a deliberate break, on a clean
tree, **without** `--rerun-tasks`, so that a mutation which leaves the task up to date would show as a
pass rather than hide.

| Mutation | Failed |
|---|---|
| An upstream file gains `// shatterfish-hook:9`, with no row for it | `every hook id in the tree has a row, and every row has a site`, and the site index |
| An upstream file is edited with no marker at all | `no upstream file is changed, added, deleted or renamed without a hook row` |
| A fourth site in `GameScene` under id 5, which already has a row | `the site index names every marker, and only the markers that exist` |
| A marker mistyped as `// shatterfish-hook: 9` | `nothing in the upstream tree looks like a marker without being one` |
| A new, unmarked, untracked class added inside `core/` | `no upstream file is changed, added, deleted or renamed without a hook row` |
| An upstream file deleted | the same check |
| A row deleted from `docs/UPSTREAM.md`, nothing else touched | `every hook id in the tree has a row, and every row has a site` |
| A guard replaces the vanilla statement (`if (cellSelector != null) return;`) | `a hook wraps vanilla code; it never deletes it` |
| A guard comments the vanilla statement out | the same check |
| The `selectCell` guard deleted, as an upgrade merge could do | `row 5: the guarded sites do nothing when no scene exists`, and `HeadlessTurnSpikeTest` |
| A listener point added to `Hooks` without a line in `clear()` | `clear() nulls every point, including ones added later` |
| An unmarked `public static Object peekEverything()` added to `GameScene`, which already carries a row | `no upstream file differs from the pinned tag by more than the ledger declares` |
| An upstream file moved out of upstream with `git mv` | `no upstream file is changed, added, deleted or renamed without a hook row` |
| `shatterfish/settings.gradle` changed so hook row 1 no longer restores the mobile modules | `a hook wraps vanilla code; it never deletes it`, and the task re-ran rather than staying up to date |
| `LICENSE.txt` changed | three checks, and the task re-ran |
| Round one's `Class.forName` plus `MethodHandles` exploit, placed in `brain` | two rules |
| Round two's `java.beans.Expression` exploit, which read `Dungeon.seed` | two rules |
| A comment inside the `selectCell` hook block swapped for `Dungeon.hero.viewDistance = 999`, leaving both line counts unchanged | `no upstream file differs from the pinned tag in a way the ledger does not declare` |
| A `public static Object peek()` returning `Dungeon.level` added to the registry | that check, and `the registry holds listener points and clear(), and nothing else` |
| Round three's reflective reader in `api`, called from `brain` | both `ApiBoundaryTest` rules |
| Round three's brain class in `org.shatterfish.peek`, shipped in `brain.jar` | `every class compiled into brain lives under org.shatterfish.brain` |
| A root `.gitattributes` marking `*.java binary`, then `GameScene` changed freely and its budget row deleted | `nothing tells git to stop reading upstream files`, and two more |
| A new class under `core/` hidden by an ignore rule of our own | `nothing under an upstream module is hidden from git` |
| A checkout without the pinned commit (CI's first run, before the pin was resolved by commit) | five checks, loudly, rather than skipping |
| `Boolean.getBoolean`, `ResourceBundle.getBundle`, `ManagementFactory`, `ProcessHandle`, `SecureRandom`, `RandomGenerator`, `UUID`, `Date`, `Preferences`, `Executors`, `Collections.shuffle`, `Class::forName` as a method reference, `Thread.currentThread().getContextClassLoader()` | at least one rule each, all as fixtures in `BrainBoundaryRulesBiteTest` |

Three notes on that table. The guard-deletion mutation fails the unit check *and* story 1.1's spike,
which is the pair working as intended: one says which guard, the other says that a turn no longer
resolves. And an earlier run of this battery was invalid — its restore step used `git checkout`,
which silently reverted two edits that were not yet committed, so every result in it was
contaminated. The table above is from a clean rerun after committing. And the second review left a
probe file behind in `brain/src/main` while reporting the tree clean; finding it was useful rather
than not, because it demonstrated two channels the review had not written up —
`Boolean.getBoolean` reading a system property without naming `System`, and
`ResourceBundle.getBundle` loading a classpath file without a `ClassLoader` or `java.io`. Both are
now fixtures.

Rig numbers: not applicable, no Brain exists until E4.

## Deviations

- The criterion naming `./gradlew :desktop:run` is not met and is not met-able; see the table.
  Nothing in this story changes a hook site, and story 1.1 ran `:desktop:debug` against these guards.
- One listener point rather than ten, argued above.
- The boundary rules go beyond the four packages the criterion names. The criterion's list is not
  sufficient, as the exploit above shows.
- The two existing boundary tests were renamed rather than extended in place, to the names the
  criteria use.

## Known limitations, handed forward

- **The vanilla branch of the two `cellSelector` sites is still not exercised at runtime.** Story 1.3
  owns it: once its driver can build a level and a tilemap, a real `CellSelector` can be installed.
- **`brain_never_depends_on_game_code` and `brain_depends_on_no_other_shatterfish_module` have no
  bite test**, because the classes they forbid are not on the module's classpath. That absence is a
  stronger guarantee than a test, but it does mean these two rules are the only ones taken on trust.
- **The "encloses, does not delete" rule is a syntactic check over one diff format.** It will fire on
  a legitimate hook that changes a condition rather than wrapping a statement — row 9's input gate is
  the known example — which will need a relocation-style exception with a stated reason. It also
  cannot see a vanilla statement moved into a private method that nothing calls.
- **The pin is resolved by commit, not by tag name.** The first CI run failed on every git-based
  check with "unknown revision `v3.3.8`": the tag exists in upstream's repository and in a developer's
  clone, but was never pushed to this fork, and CI clones the fork. `main` descends from the pinned
  commit, so resolving by commit works everywhere and cannot be moved. Pushing the tag to the fork
  would additionally make `git checkout v3.3.8` work for anyone cloning it; that is worth doing and is
  not done here, because it is a change to the remote rather than to this branch.
- **The checks require git and the pinned commit.** They fail loudly rather than skipping, which is
  deliberate: a check that quietly skips is a check that quietly rots. CI checks out with full
  history.
- **The brain cannot use `String.format` or `toUpperCase`.** Both follow the host's default locale,
  and a Run must be determined by (tag, seed, action list) alone. String concatenation and a
  `StringBuilder` are unaffected; a brain that needs a locale-independent format will have to say so.
- **`java.lang.Class` is denied, which constrains how the brain is written.** A bare `getClass()`
  passes, but calling anything on the result does not, so a hand-written `equals` compares with
  `instanceof`. That is the better idiom anyway, and it is stated in the bite test rather than left
  to be discovered.
- **The two allowlists live in two modules and are kept in step by hand.** `api` sits inside the
  brain's allowlist, so if its list ever becomes the looser of the two, it is a laundering layer
  again. Nothing mechanical enforces the relationship, because the modules cannot see each other's
  tests; both files say so at the top.
- **The allowlist allows `java.lang` and `java.util` whole**, minus twenty-six named classes and
  six named methods. Those two packages are fixed by the JDK, so the list is closable in a way a
  denylist over the whole platform is not — but it is still a list, and a JDK upgrade that adds a
  class to either package adds it to the brain's reach. Re-reading it belongs in the upgrade
  procedure.
- **Nothing constrains identity-hash ordering inside the brain.** ADR-0016 row 6 removes it from the
  game's own collections; a brain iterating a `HashSet` of its own would reintroduce it. That is an
  E4 implementation concern rather than something a dependency rule can see, and it is written here
  so it is not discovered late.
- **`brain` has one trivial class**, so the rules still pass over almost nothing in the module itself.
  `BrainBoundaryRulesBiteTest` is what makes them meaningful before there is a brain to constrain.

## Follow-ups for later stories

- Story 1.3 lands the harness-owned scene and the runtime vanilla branch of the two remaining row 5
  sites, and should delete nothing from these tests when it does.
- Story 1.5 lands the `Hooks.inputWait` call site inside `Hero.act()`, using the local-copy idiom, and
  stays inside row 5. Its site changes the site index.
- Story 1.13's targeted-item work inherits story 1.1's named risk about `selectCell`.
- The `epics.md` criterion for this story says `./gradlew :desktop:run`; later stories that quote it
  should read `:desktop:debug`.
