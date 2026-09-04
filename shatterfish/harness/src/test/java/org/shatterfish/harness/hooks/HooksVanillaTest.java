package org.shatterfish.harness.hooks;

import com.shatteredpixel.shatteredpixeldungeon.effects.EmoIcon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With nothing registered, the game is the game.
 *
 * <p>This is the vanilla-equivalence obligation story 1.1 left open. It has two halves, because the
 * two branches of a hook are reachable in different places.
 *
 * <p>The <em>Shatterfish</em> branch — the guard firing, every static null — is exercised at
 * runtime here: the three sites of hook row 5 are called with no scene in existence and must do
 * nothing rather than throw, which is precisely what the guards buy and what an unguarded call
 * would fail.
 *
 * <p>The <em>vanilla</em> branch — the guard not firing — cannot be reached from a test at all.
 * Every one of row 5's statics is assigned only by {@code GameScene.create()}, and
 * {@code CellSelector} dereferences its {@code DungeonTilemap} in its own constructor, so there is
 * no way to install a real one without building the scene the harness deliberately does not build
 * (ADR-0015). It is instead proved against the source: a hook may wrap vanilla code, never delete
 * it, checked line by line against the pinned upstream tag. That check covers every hook this
 * repository will ever have, including the ones not written yet, which no runtime test could do.
 * Story 1.3 owns the remaining runtime half, where a harness-owned scene makes the statics
 * non-null.
 */
class HooksVanillaTest {

	/** See {@code HooksLedgerTest}: documentation edits, not behaviour. */
	private static final List<String> DOCUMENTATION_EXCEPTIONS = List.of("README.md", ".gitignore");

	/**
	 * Hooks that move a vanilla line to another file instead of wrapping it in place. Hook row 1
	 * moves the two mobile {@code include} lines into a fragment that restores them under
	 * {@code -Pshatterfish.mobile=on}; the line still exists, so the rule still holds, but not in
	 * the file it left. Any new entry here is a claim that needs its own row in
	 * {@code docs/UPSTREAM.md}.
	 */
	private static final Map<String, String> RELOCATIONS =
			Map.of("settings.gradle", "shatterfish/settings.gradle");

	@BeforeEach
	@AfterEach
	void nothingRegistered() {
		Hooks.clear();
	}

	@Test
	@DisplayName("no listener is registered unless something registers one")
	void nothing_is_registered_by_default() {
		for (Field point : listenerPoints()) {
			assertNull(valueOf(point),
					"Hooks." + point.getName() + " is set with no Run active; a hook site would take the"
							+ " Shatterfish branch inside the unmodified game");
		}
	}

	@Test
	@DisplayName("clear() nulls every point, including ones added later")
	void clear_nulls_every_declared_point() {
		List<Field> points = listenerPoints();
		assertFalse(points.isEmpty(), "Hooks declares no listener point, so this test proves nothing");

		for (Field point : points) {
			assertTrue(point.getType().isInterface(),
					"Hooks." + point.getName() + " must be an interface so that harness and overlay can"
							+ " implement it without core depending on either (ADR-0003)");
			assertTrue(Modifier.isVolatile(point.getModifiers()),
					"Hooks." + point.getName() + " must be volatile: it is written by the thread that"
							+ " starts a Run and read by the actor and render threads (ADR-0013)");
			set(point, Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{point.getType()}, (proxy, method, args) -> null));
		}

		Hooks.clear();

		for (Field point : points) {
			assertNull(valueOf(point),
					"Hooks.clear() left " + point.getName() + " set. Every point declared in Hooks must be"
							+ " nulled there, or a listener belonging to a finished Run outlives it");
		}
	}

	@Test
	@DisplayName("row 5: the guarded sites do nothing when no scene exists")
	void the_guarded_sites_are_inert_with_no_scene() {
		RecordingListener listener = new RecordingListener();

		assertDoesNotThrow(() -> GameScene.selectCell(listener),
				"GameScene.selectCell must return when cellSelector is null. Unguarded it dereferences"
						+ " cellSelector, and then Dungeon.hero, on every Input wait");
		assertFalse(listener.selected, "selectCell called the listener with no cell selector installed");
		assertFalse(listener.prompted, "selectCell asked for a prompt with no scene to show it in");

		assertDoesNotThrow(GameScene::resetKeyHold,
				"GameScene.resetKeyHold must return when cellSelector is null. Hero.interrupt() reaches"
						+ " it on any turn where an enemy becomes visible");

		assertDoesNotThrow(() -> GameScene.add((EmoIcon) null),
				"GameScene.add(EmoIcon) must return when scene is null. A sleeping mob builds an EmoIcon"
						+ " on its first sprite update, and the icon adds itself here");
	}

	@Test
	@DisplayName("a hook wraps vanilla code; it never deletes it")
	void a_hook_wraps_vanilla_code_it_never_deletes() {
		String tag = Ledger.pinnedTag();
		for (String path : Ledger.filesModifiedSinceTag()) {
			if (DOCUMENTATION_EXCEPTIONS.contains(path)) {
				continue;
			}
			List<String> added = Ledger.linesAddedSinceTag(path);
			List<String> elsewhere =
					RELOCATIONS.containsKey(path) ? Ledger.readLines(RELOCATIONS.get(path)) : List.of();

			for (String removed : Ledger.linesRemovedSinceTag(path)) {
				String vanilla = removed.trim();
				if (vanilla.isEmpty() || isComment(vanilla)) {
					continue;
				}
				boolean survives = contains(added, vanilla) || contains(elsewhere, vanilla);
				assertTrue(survives, path + " lost a line that is in no line of the modified file"
						+ (RELOCATIONS.containsKey(path) ? " nor of " + RELOCATIONS.get(path) : "")
						+ ":\n    " + vanilla
						+ "\nA hook guards vanilla code, it does not replace it. Compare against " + tag
						+ ". If the line really must move, name where in the RELOCATIONS map here and say"
						+ " why in its docs/UPSTREAM.md row.");
			}
		}
	}

	private static boolean contains(List<String> lines, String vanilla) {
		return lines.stream().anyMatch(line -> line.contains(vanilla));
	}

	private static boolean isComment(String trimmed) {
		return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
				|| trimmed.startsWith("#");
	}

	/** The listener fields of the registry: public, static, mutable, and not the compiler's. */
	private static List<Field> listenerPoints() {
		List<Field> points = new ArrayList<>();
		for (Field field : Hooks.class.getDeclaredFields()) {
			int modifiers = field.getModifiers();
			if (field.isSynthetic() || !Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
					|| Modifier.isFinal(modifiers)) {
				continue;
			}
			points.add(field);
		}
		return points;
	}

	private static Object valueOf(Field point) {
		try {
			return point.get(null);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Hooks." + point.getName() + " is not readable", e);
		}
	}

	private static void set(Field point, Object value) {
		try {
			point.set(null, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Hooks." + point.getName() + " is not writable", e);
		}
	}

	/** A cell-selection listener that records whether the game asked it for anything. */
	private static final class RecordingListener extends CellSelector.Listener {
		private boolean selected;
		private boolean prompted;

		@Override
		public void onSelect(Integer cell) {
			selected = true;
		}

		@Override
		public String prompt() {
			prompted = true;
			return "";
		}
	}
}
