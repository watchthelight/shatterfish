---
status: accepted
date: 2026-09-03
deciders: watchthelight (product owner), Claude (engineer)
---

# ADR-0003: Shatterfish module layout, package, Java level, and the brain boundary

## Context and problem statement

Shatterfish adds six Gradle modules beside upstream's (`api`, `harness`, `codex`, `brain`, `rig`, `overlay`; bootstrap prompt §4) and must keep every edit to an upstream file minimal and listed (non-negotiable #3). The brain must be unable to import game code, enforced by the build (non-negotiable #1). Decide where the modules live, how they share build configuration, which Java level they use, and how the brain boundary is enforced.

Non-negotiables touched: #1 (information parity), #3 (hooks minimal), #4 (Java, in-process).

## Considered options

**Layout**

1. Top-level directories `api/`, `harness/`, … beside `core/`, `desktop/`. Rejected: mixes ours with upstream's at the root, so an upgrade reviewer cannot tell at a glance which tree is which; six extra root entries.
2. **One directory `shatterfish/` holding all six, with short Gradle project names (`:api`, …) via `projectDir` mapping.** Chosen.
3. Separate repository consumed as a Gradle composite build. Rejected: hooks and the overlay need one tree, and one repo is simpler for a solo engineer.
4. A single `shatterfish` module with packages instead of modules. Rejected: the brain boundary must be a classpath boundary, not a convention.

**Shared build configuration**

5. `subprojects {}` block in the root `build.gradle`. Rejected: edits an upstream file for something a script can do.
6. `buildSrc` convention plugin. Rejected for now: heavier, slows every build's configuration, and `buildSrc` is a root directory upstream may one day claim; revisit if the shared script grows past a screen.
7. **`apply from: "$rootDir/shatterfish/java-module.gradle"` in each module.** Chosen.

**Settings**

8. Edit the includes in the root `settings.gradle` directly. Rejected: every future module is another upstream edit.
9. **One `apply from: 'shatterfish/settings.gradle'` line replacing the two mobile includes (hook #1)**, with the mobile guard and all Shatterfish includes in our file. Chosen.

**Java level**

10. Match upstream's `VERSION_11`. Rejected: no records, sealed types, or pattern matching; nothing forces it since our modules are separate compilation units.
11. Java 17. Considered; 21 is what upstream's own guide recommends and what the laptop and CI run.
12. **Java 21** (`sourceCompatibility`/`targetCompatibility`, no toolchain download). Chosen. Upstream's jpackage runtime is JDK 17, but Shatterfish ships its own launcher, so that runtime is not a constraint. `-Xlint:all -Werror` from the start.

**Brain boundary enforcement**

13. ArchUnit test only. Rejected alone: tests can be skipped.
14. Gradle dependency graph only. Rejected alone: someone adds `project(':core')` in a hurry.
15. **All three: dependency edges as declared, a resolution-time check in `brain/build.gradle` that fails configuration if `:core`, `:SPD-classes`, `:services`, or `:desktop` appear on brain's compile or runtime classpath, and an ArchUnit rule that brain classes depend on nothing in `com.shatteredpixel..` or `com.watabou..` (and on no Shatterfish module but `api`).** Chosen. `api` additionally has an ArchUnit rule that it depends only on `java..`.

## Decision outcome

- Sources at `shatterfish/<module>/src/{main,test}/java`, package root `org.shatterfish.<module>`.
- Dependency edges exactly as §4: `api` → nothing; `harness` → `core`, `api`; `codex` → `core`; `brain` → `api`; `rig` → `harness`, `brain`; `overlay` → `core`, `harness`, `brain`.
- JUnit 5 (5.11.4) and ArchUnit (1.3.0) on every module's test classpath via the shared script.
- The headless backend (`gdx-backend-headless`) needs libGDX's desktop natives even without a window; `harness` declares them `runtimeOnly`.
- Mobile modules (`android`, `ios`) are included only with `-Pshatterfish.mobile=on`; CI passes `off` explicitly because GitHub's ubuntu runners set `ANDROID_HOME`.

### Consequences

- Good: root tree stays recognisably upstream; hooks stay at one line in `settings.gradle`.
- Good: the brain boundary fails at configuration, at compile, and at test.
- Bad: `apply from` scripts are less IDE-friendly than convention plugins; revisit (option 6) if the shared script grows.

## Pre-mortem

*If this is wrong in six months, why?*

- A future upstream tag adds a `shatterfish`-conflicting root entry. Very unlikely; the merge would surface it immediately.
- Java 21 features leak into a hook inside `core` (compiled at 11). Mitigation: hooks are reviewed under the `touches-upstream` label and `core` still compiles at `appJavaCompatibility`, so the compiler catches it.
- The resolution-time check silently stops running after a Gradle upgrade changes configuration names. Mitigation: the ArchUnit test remains; add a negative test in the `upstream-sync` skill that inserts a fake dependency and expects failure (verified manually in Session 2).
