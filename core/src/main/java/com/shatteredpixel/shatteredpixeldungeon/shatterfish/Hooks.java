/*
 * Shatterfish
 * Copyright (C) 2026 watchthelight
 *
 * This file is not part of upstream Shattered Pixel Dungeon. It is added by
 * Shatterfish, an unofficial and unaffiliated downstream fork, and lives inside
 * upstream's package tree because the classes that call it are compiled in the
 * same module and must not depend on any Shatterfish module.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

// shatterfish-hook:2
// The hook registry itself. Row 2 of the ledger in docs/adr/0016-hook-ledger-corrected-by-story-1-1.md,
// listed in docs/UPSTREAM.md. This is the only Shatterfish-authored source file outside
// shatterfish/, and the only hook id that may appear in this file (HooksLedgerTest enforces both).

package com.shatteredpixel.shatteredpixeldungeon.shatterfish;

/**
 * The single door from upstream game code into Shatterfish.
 *
 * <p>Every hook that needs to <em>call</em> Shatterfish calls it through a nullable listener field
 * declared here, so that no upstream file ever imports a Shatterfish module and the dependency
 * edges of ADR-0003 are never reversed ({@code harness} and {@code overlay} depend on
 * {@code core}, never the other way round). A hook site reads the point once into a local and then
 * uses the local:
 *
 * <pre>{@code
 *   // the marker comment, then:
 *   InputWait waiting = Hooks.inputWait;
 *   if (waiting != null) waiting.onInputWait();
 * }</pre>
 *
 * <p>The local matters. A site that tests the field and then reads it again can be interrupted
 * between the two by {@link #clear()} on the thread that ends a Run, and the second read returns
 * null on the game's actor thread. Volatility makes the write visible; it does not make two reads
 * one. Where the hook has to choose rather than notify, the {@code else} branch is the vanilla code
 * unchanged.
 *
 * <p>With nothing registered every site takes the vanilla branch, which is what makes the
 * unmodified game unmodified. {@code HooksVanillaTest} holds that: after {@link #clear()} every
 * point declared here is null, checked reflectively, so a point added later without a line in
 * {@code clear()} fails the build rather than leaking a listener between Runs.
 *
 * <p>Not every hook row needs a point. Rows that are pure null guards, read-only accessors or
 * semantically neutral edits (rows 4, 5's guards, 6) have no listener and nothing here.
 *
 * <p>Adding a point is a change to this file, which is already hook row 2; it does not consume a
 * new row. Adding a <em>site</em> that calls it does, unless the site belongs to a row that already
 * exists.
 *
 * <p>Fields are {@code volatile} because they are written by the thread that starts a Run and read
 * by the game's actor and render threads (ADR-0013). Volatility is what makes a registration
 * visible to those threads; the local-copy idiom above is what makes an unregistration safe.
 *
 * @see <a href="https://github.com/watchthelight/shatterfish/blob/main/docs/UPSTREAM.md">docs/UPSTREAM.md</a>
 */
public final class Hooks {

	private Hooks() {
	}

	/**
	 * Notified at the start of every act of the hero that begins unready: the first statement of
	 * the branch guarded by {@code ready} in {@code Hero.act()}, hook row 5. That is once before
	 * every transition to ready, since {@code ready()} is reached only later in the same act, and
	 * also on each step of a move and each turn of resting; the driver confirms which of these is
	 * the Input wait, the only moment it may submit an Action (ADR-0014, ADR-0015), and drops the
	 * rest. Story 1.5 landed the site.
	 */
	public interface InputWait {
		/** Called on the actor thread, inside {@code Hero.act()}, before the hero observes; must not block. */
		void onInputWait();
	}

	/** @see InputWait */
	public static volatile InputWait inputWait;

	/**
	 * Unregisters every listener. Called when a Run ends, so that a listener belonging to a finished
	 * Run cannot be reached by the next one. Every field declared above must be nulled here.
	 */
	public static void clear() {
		inputWait = null;
	}
}
