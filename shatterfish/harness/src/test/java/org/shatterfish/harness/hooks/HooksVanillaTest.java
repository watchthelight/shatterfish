package org.shatterfish.harness.hooks;

import com.shatteredpixel.shatteredpixeldungeon.effects.EmoIcon;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;

import org.junit.jupiter.api.AfterEach;
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
 * <p>This is the vanilla-equivalence obligation story 1.1 left open, and it is checked three ways
 * because the two branches of a hook are reachable in different places.
 *
 * <p>The <em>Shatterfish</em> branch — the guard firing, every static null — is exercised at
 * runtime: the three sites of hook row 5 are called with no scene in existence and must do nothing
 * rather than throw, which is precisely what the guards buy and what an unguarded call would fail.
 *
 * <p>The <em>vanilla</em> branch — the guard not firing — is exercised at runtime for the site where
 * that is possible. {@code GameScene.add(EmoIcon)} needs only a {@code GameScene} instance, which
 * constructs headlessly, and its {@code emoicons} group. The other two sites need a
 * {@code CellSelector}, and every {@code DungeonTilemap} builds a {@code TextureFilm} from a
 * texture in its abstract constructor, so any subclass needs a graphics binding and the game's
 * assets; {@code CellSelector} then reads {@code map.camera()}, which needs {@code Camera.main}.
 * That is the booted headless application of ADR-0015, the driver story 1.3 builds, and that
 * story owns the remaining two sites.
 *
 * <p>Beyond both, the property is checked against the pinned upstream tag: a hook wraps vanilla code
 * and does not delete it. That covers hooks not written yet, which no runtime test can do.
 */
class HooksVanillaTest {

	/** See {@code HooksLedgerTest}: documentation edits, not behaviour. */
	private static final List<String> DOCUMENTATION_EXCEPTIONS = List.of("README.md", ".gitignore");

	/**
	 * Hooks that move a vanilla line to another file instead of wrapping it in place. Hook row 1
	 * moves the two mobile {@code include} lines into a fragment that restores them under
	 * {@code -Pshatterfish.mobile=on}; the line still exists, so the rule still holds, but not in the
	 * file it left. Any new entry here is a claim that needs its own row in
	 * {@code docs/UPSTREAM.md}.
	 */
	private static final Map<String, String> RELOCATIONS =
			Map.of("settings.gradle", "shatterfish/settings.gradle");

	@AfterEach
	void leaveNothingRegistered() {
		Hooks.clear();
	}

	@Test
	@DisplayName("the registry declares only listener points, so the checks below see all of them")
	void every_field_in_the_registry_is_a_listener_point() {
		for (Field field : Hooks.class.getDeclaredFields()) {
			if (field.isSynthetic()) {
				continue;
			}
			int modifiers = field.getModifiers();
			assertTrue(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
							&& !Modifier.isFinal(modifiers),
					"Hooks." + field.getName() + " is not a public non-final static, so the reflective"
							+ " checks in this class do not see it. The registry holds listener points and"
							+ " nothing else; state that belongs to a Run belongs in the harness.");
			assertTrue(field.getType().isInterface(),
					"Hooks." + field.getName() + " must be an interface so that harness and overlay can"
							+ " implement it without core depending on either (ADR-0003)");
			assertTrue(Modifier.isVolatile(modifiers),
					"Hooks." + field.getName() + " must be volatile: it is written by the thread that"
							+ " starts a Run and read by the actor and render threads (ADR-0013)");
		}
	}

	@Test
	@DisplayName("clear() nulls every point, including ones added later")
	void clear_nulls_every_declared_point() {
		List<Field> points = listenerPoints();
		assertFalse(points.isEmpty(), "Hooks declares no listener point, so this test proves nothing");

		for (Field point : points) {
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
	@DisplayName("row 5: add(EmoIcon) runs the vanilla branch when a scene exists")
	void the_emote_site_runs_the_vanilla_branch_when_a_scene_exists() throws Exception {
		GameScene scene = new GameScene();
		RecordingGroup icons = new RecordingGroup();
		Field emoicons = GameScene.class.getDeclaredField("emoicons");
		emoicons.setAccessible(true);
		emoicons.set(scene, icons);

		Field sceneField = GameScene.class.getDeclaredField("scene");
		sceneField.setAccessible(true);
		try {
			sceneField.set(null, scene);
			GameScene.add((EmoIcon) null);
			assertTrue(icons.added,
					"with a scene in place the guard must not fire: the icon has to reach"
							+ " scene.emoicons exactly as it does in the running game");
		} finally {
			// Leaving a scene in this static would change what every other test in this module sees.
			sceneField.set(null, null);
		}
	}

	@Test
	@DisplayName("a hook wraps vanilla code; it never deletes it")
	void a_hook_wraps_vanilla_code_it_never_deletes() {
		String tag = Ledger.pinnedTag();
		for (Ledger.Change change : Ledger.changesSinceTag()) {
			String path = change.path();
			if (!change.isModification() || !Ledger.isUpstreamFileAtTheTag(path)
					|| DOCUMENTATION_EXCEPTIONS.contains(path)) {
				continue;
			}
			List<String> guards = Ledger.linesAddedSinceTag(path).stream()
					.map(String::trim)
					.filter(line -> !isComment(line))
					.toList();
			List<String> elsewhere =
					RELOCATIONS.containsKey(path) ? Ledger.readLines(RELOCATIONS.get(path)) : List.of();

			for (String removed : Ledger.linesRemovedSinceTag(path)) {
				String vanilla = removed.trim();
				if (vanilla.isEmpty() || isComment(vanilla)) {
					continue;
				}
				boolean wrapped = guards.stream()
						.anyMatch(guard -> guard.startsWith("if (") && guard.endsWith(vanilla));
				boolean relocated = elsewhere.stream().anyMatch(line -> line.trim().equals(vanilla));
				assertTrue(wrapped || relocated, path + " lost a line that no added line guards"
						+ (RELOCATIONS.containsKey(path) ? " and that " + RELOCATIONS.get(path)
								+ " does not carry" : "")
						+ ":\n    " + vanilla
						+ "\nA hook encloses vanilla code in a condition; it does not replace it. Compare"
						+ " against " + tag + ". If the line really must move, name where in the RELOCATIONS"
						+ " map here and say why in its docs/UPSTREAM.md row.");
			}
		}
	}

	private static boolean isComment(String trimmed) {
		return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")
				|| trimmed.startsWith("#");
	}

	/** The listener fields of the registry, which {@code every_field_in_the_registry} pins to all of them. */
	private static List<Field> listenerPoints() {
		List<Field> points = new ArrayList<>();
		for (Field field : Hooks.class.getDeclaredFields()) {
			if (!field.isSynthetic()) {
				points.add(field);
			}
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

	/** Stands in for the scene's emote group and records that the vanilla branch reached it. */
	private static final class RecordingGroup extends Group {
		private boolean added;

		@Override
		public synchronized Gizmo add(Gizmo g) {
			added = true;
			return g;
		}
	}
}
