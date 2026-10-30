package org.shatterfish.harness.driver;

import java.util.List;

/**
 * What a Run has already played, as plain values (issue #390, ADR-0025): the journal, the badges and the few upstream
 * statics that no save carries. It is the second thing that crosses from the live Run's class loader into an isolated
 * one, beside {@link ScrubbedStart}, and it is a holder and nothing else: no field is a game type, so the loader may
 * share this class by name, and every line of code that touches the game's classes ({@link RunHistoryCodec}) is the
 * copy's own in whichever loader runs it.
 *
 * <p>Why a rollout needs it. The swap-in-place host plays a rollout in the live Run's process, so the game reads the live
 * Run's profile while it plays: whether the hero has found or read a guide page decides whether a hint is shown
 * ({@code core/.../actors/hero/Hero.java:1758}, {@code Snake.java:65}, {@code Hunger.java:109},
 * {@code EquipableItem.java:59}, and a hint is a log line), what the catalogs hold decides when a researcher badge is
 * awarded ({@code Badges.java:632-660}), and a count such as the snake's dodges decides the turn a hint comes
 * ({@code Snake.java:58-70}). An isolated world has a profile of its own, empty, because every Run's profile begins empty
 * ({@code Profile.java}, ADR-0007); handed no history it answers a rollout as a Run that has played nothing would, and
 * the two hosts differ in the log (issue #390: 11 of 207 results on the standard set).
 *
 * <p>Everything here is something a player at the same screen has: the journal and the badges are windows of the game
 * (the journal button, the badges page; the badge set also holds the hidden bookkeeping badges the page leaves out, which
 * are profile data and gate only hints and awards), the dodges are the snake's misses counted only while it is in the
 * hero's view ({@code Snake.java:62-64}), the flail's charge and the ring's flare are set by the hero's own acts, and the
 * pending hit or miss icon is the last roll's, cleared when a sprite shows it ({@code Char.java:594}); that one is set for
 * any pair of fighters, in sight or not, so it is the least plain of the values, and it is carried because the swap host
 * plays on it and a rollout's draws differ without it. None of it is read from the unseen part of the world's
 * layout, items or monsters, and the swap host has always played on exactly these values. The rankings are not carried: a Run in progress has none (they change when a hero dies or
 * wins, which ends the Run), so the world's empty rankings are the live Run's.
 */
public final class RunHistory {

    /** The history of a Run that has played nothing: a world that is handed it stays at its own baseline. */
    public static final RunHistory NONE = new RunHistory(null, List.of(), new boolean[0], new int[0]);

    private final byte[] journal;
    private final List<String> badges;
    private final boolean[] flags;
    private final int[] counters;

    /** Plain values only; the codec that reads the game builds one, and the codec that writes the game reads it. */
    public RunHistory(byte[] journal, List<String> badges, boolean[] flags, int[] counters) {
        this.journal = journal == null ? null : journal.clone();
        this.badges = List.copyOf(badges);
        this.flags = flags.clone();
        this.counters = counters.clone();
    }

    /** Whether this carries nothing, so that applying it changes nothing. */
    public boolean isNone() {
        return journal == null;
    }

    /** The catalogs, bestiary and guide pages, as the game bundles them (JSON text). */
    public byte[] journal() {
        return journal == null ? null : journal.clone();
    }

    /** The names of the badges the Run has earned. */
    public List<String> badges() {
        return badges;
    }

    public boolean[] flags() {
        return flags.clone();
    }

    public int[] counters() {
        return counters.clone();
    }

    @Override
    public String toString() {
        return isNone() ? "RunHistory[none]" : "RunHistory[" + journal.length + " bytes of journal, " + badges.size() + " badges]";
    }
}
