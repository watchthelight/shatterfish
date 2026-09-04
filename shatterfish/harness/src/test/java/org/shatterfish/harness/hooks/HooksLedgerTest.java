package org.shatterfish.harness.hooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger and the tree must say the same thing.
 *
 * <p>Non-negotiable #3 says every edit to an upstream file is listed in {@code docs/UPSTREAM.md}.
 * ADR-0008 makes that mechanical: each site carries {@code // shatterfish-hook:<id>} and these
 * checks compare the tree with the document. Without them the table is a promise; with them, an
 * unlisted hook and a listed hook that no longer exists both fail the build.
 *
 * <p>Ids alone are not enough. A fourth site added to an already-hooked file under an id that
 * already exists changes no id set, and {@code GameScene} is exactly where an Observer-adjacent
 * leak would be added. So the document carries a site index — one line per file per row, with the
 * number of markers — and the check is equality against it.
 */
class HooksLedgerTest {

	private static final List<Ledger.Marker> MARKERS = Ledger.markers();

	/** The registry itself, hook row 2. The only Shatterfish source file outside {@code shatterfish/}. */
	private static final String REGISTRY =
			"core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java";

	/** The ledger's budget for v1. Raising it needs an ADR, not an edit here. */
	private static final int BUDGET = 10;

	/**
	 * Modified upstream files that are documentation rather than build or game behaviour, listed as
	 * such in {@code docs/UPSTREAM.md} and re-applied on upgrade by taking ours. They carry no marker
	 * because there is no site to mark.
	 */
	private static final List<String> DOCUMENTATION_EXCEPTIONS = List.of("README.md", ".gitignore");

	@Test
	@DisplayName("the registry holds listener points and clear(), and nothing else")
	void the_registry_has_no_other_shape() {
		List<String> methods = new ArrayList<>();
		for (var method : com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.class
				.getDeclaredMethods()) {
			if (!method.isSynthetic()) {
				methods.add(method.getName());
			}
		}
		assertEquals(List.of("clear"), methods,
				"Hooks declares a method other than clear(). The registry is the one file inside core that"
						+ " upstream code is designed to call into, which makes it the softest place in the"
						+ " tree to put an accessor: a single public static returning Dungeon.level would be"
						+ " reachable from anywhere and would look like it belonged. It holds listener points"
						+ " and clear(); anything else belongs in the harness. Found: " + methods);

		for (Class<?> nested : com.shatteredpixel.shatteredpixeldungeon.shatterfish.Hooks.class
				.getDeclaredClasses()) {
			assertTrue(nested.isInterface(),
					"Hooks declares a nested " + nested.getSimpleName() + " that is not an interface. A hook"
							+ " point is an interface the harness implements; a class here could hold state or"
							+ " reach game code");
		}
	}

	@Test
	@DisplayName("every hook id in the tree has a row, and every row has a site")
	void markers_and_ledger_rows_agree() {
		Set<Integer> inTree = new TreeSet<>(
				MARKERS.stream().map(Ledger.Marker::id).collect(Collectors.toSet()));
		Set<Integer> inLedger = new TreeSet<>(Ledger.ledgerRowIds());

		assertEquals(inLedger, inTree,
				"the hook ids marked in the upstream tree and the rows in docs/UPSTREAM.md differ."
						+ " Markers: " + MARKERS
						+ ". A new hook needs a row in the same pull request; a removed hook needs its row"
						+ " removed.");
	}

	@Test
	@DisplayName("the site index names every marker, and only the markers that exist")
	void the_site_index_matches_the_tree() {
		List<String> inTree = new ArrayList<>();
		for (Integer id : new TreeSet<>(MARKERS.stream().map(Ledger.Marker::id).toList())) {
			for (String path : new TreeSet<>(MARKERS.stream()
					.filter(m -> m.id() == id).map(Ledger.Marker::path).toList())) {
				long count = MARKERS.stream()
						.filter(m -> m.id() == id && m.path().equals(path)).count();
				inTree.add(id + " " + count + " " + path);
			}
		}
		List<String> inLedger = Ledger.ledgerSites().stream()
				.map(s -> s.id() + " " + s.markers() + " " + s.path())
				.sorted()
				.toList();

		assertEquals(inLedger, inTree.stream().sorted().toList(),
				"the site index in docs/UPSTREAM.md and the markers in the tree differ. This is what"
						+ " catches a new site added under an id that already exists, which changes no id"
						+ " set and would otherwise be invisible.");
	}

	@Test
	@DisplayName("nothing in the upstream tree looks like a marker without being one")
	void no_marker_is_malformed() {
		// No file is exempt, the registry included. An earlier draft exempted it in case its javadoc
		// spelled the marker out in prose; it does not, and the exemption would have un-checked the one
		// file every other check in this class depends on.
		List<String> malformed = Ledger.malformedMarkers();

		assertTrue(malformed.isEmpty(),
				"these lines mention shatterfish-hook but do not parse as a marker, so they are"
						+ " comments that look like declarations. The form is exactly"
						+ " \"// shatterfish-hook:<id>\" at the end of the line:\n  "
						+ String.join("\n  ", malformed));
	}

	@Test
	@DisplayName("the ledger stays inside its budget of ten rows, with no id used twice")
	void the_budget_is_ten() {
		List<Integer> rows = Ledger.ledgerRowIds();
		Set<Integer> distinct = new LinkedHashSet<>(rows);

		assertEquals(rows.size(), distinct.size(),
				"docs/UPSTREAM.md uses a hook id on more than one row " + rows + ". Two reasons under one"
						+ " id is what ADR-0008 forbids, and it hides a row from the budget.");
		assertTrue(rows.size() <= BUDGET,
				"docs/UPSTREAM.md lists " + rows.size() + " hook rows " + rows + ", over the budget of "
						+ BUDGET + ". ADR-0008 requires an ADR to change the budget, not an edit to this test.");
	}

	@Test
	@DisplayName("the registry carries its own row and hides no other")
	void the_registry_is_not_a_hiding_place() {
		Set<Integer> inRegistry = new TreeSet<>(MARKERS.stream()
				.filter(m -> m.path().equals(REGISTRY)).map(Ledger.Marker::id).toList());

		assertEquals(new TreeSet<>(List.of(2)), inRegistry,
				REGISTRY + " must carry hook id 2 and nothing else. ADR-0008 anticipated the counting test"
						+ " being gamed by moving a hook into the registry, where many sites would become"
						+ " one marker.");

		List<Ledger.Marker> strays = MARKERS.stream()
				.filter(m -> m.id() == 2 && !m.path().equals(REGISTRY)).toList();
		assertTrue(strays.isEmpty(),
				"hook id 2 is the registry file itself and must not mark a site; found " + strays);
	}

	@Test
	@DisplayName("no upstream file is changed, added, deleted or renamed without a hook row")
	void every_upstream_change_is_a_hook() {
		Set<String> markedFiles = MARKERS.stream()
				.map(Ledger.Marker::path).collect(Collectors.toCollection(LinkedHashSet::new));

		for (Ledger.Change change : Ledger.changesSinceTag()) {
			String path = change.path();
			if (DOCUMENTATION_EXCEPTIONS.contains(path)) {
				continue;
			}
			boolean upstream = change.isAddition()
					? Ledger.isUpstreamAddition(path)
					: Ledger.isUpstreamFileAtTheTag(path);
			if (!upstream) {
				continue;
			}
			if (change.isModification() || change.isAddition()) {
				assertTrue(markedFiles.contains(path),
						path + " is " + (change.isAddition() ? "new in" : "changed from") + " upstream at "
								+ Ledger.pinnedTag() + " and carries no shatterfish-hook marker. Every edit to"
								+ " an upstream file is a hook (non-negotiable #3), and a new file inside an"
								+ " upstream module is how a second Shatterfish class would arrive next to the"
								+ " game's own privates: mark it and add the row, or revert it.");
			} else {
				throw new AssertionError(path + " has status " + change.status() + " against "
						+ Ledger.pinnedTag() + ". A deleted or renamed upstream file cannot carry a marker,"
						+ " so it cannot be a hook by the rules of ADR-0008. If upstream code really must"
						+ " move, that needs an ADR, not a row.");
			}
		}
	}

	@Test
	@DisplayName("no upstream file differs from the pinned tag in a way the ledger does not declare")
	void the_diff_budget_is_exact() {
		List<String> declared = Ledger.diffBudget().stream()
				.map(Ledger.Budget::toString)
				.sorted()
				.toList();
		// Modified files and added ones alike. An earlier draft covered only modifications, which left
		// the registry — the one file inside core that upstream code is meant to call into — governed by
		// nothing but its marker, so a public accessor returning Dungeon.level could be added to it with
		// the build green. For a file added since the tag the difference from the tag is its whole
		// content, which is the right thing to digest.
		Set<String> upstreamChanges = Ledger.changesSinceTag().stream()
				.filter(c -> c.isModification() || c.isAddition())
				.filter(c -> c.isAddition() ? Ledger.isUpstreamAddition(c.path())
						: Ledger.isUpstreamFileAtTheTag(c.path()))
				.map(Ledger.Change::path)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		List<String> measured = Ledger.measuredDiff().stream()
				.filter(b -> upstreamChanges.contains(b.path()))
				.filter(b -> !DOCUMENTATION_EXCEPTIONS.contains(b.path()))
				.map(Ledger.Budget::toString)
				.sorted()
				.toList();

		assertEquals(declared, measured,
				"an upstream file differs from " + Ledger.pinnedTag() + " in a way docs/UPSTREAM.md does"
						+ " not declare. Every other check here keys off something a change announces about"
						+ " itself: a marker, a deleted line, a new file. This one does not, which is why it"
						+ " is the one that catches a method added to an already-hooked file, and a line"
						+ " swapped for another line inside a hook block. The digest covers the changed"
						+ " content; the counts are there for a reader. If the change is a real hook, update"
						+ " the block in the same pull request; if it is not, revert it.");
	}

	@Test
	@DisplayName("every top-level directory in the pinned tag is either upstream's or ours")
	void every_upstream_directory_is_classified() {
		for (String directory : Ledger.directoriesAtTheTag()) {
			assertTrue(Ledger.UPSTREAM_CODE_ROOTS.contains(directory)
							|| Ledger.OUR_TERRITORY_AT_THE_TAG.contains(directory),
					"the pinned tag has a top-level directory this project has not classified: "
							+ directory + ". The hook checks watch only the directories named in Ledger, so an"
							+ " upstream module that nobody classified is an upstream module nobody is"
							+ " watching. Add it to UPSTREAM_CODE_ROOTS or to OUR_TERRITORY_AT_THE_TAG.");
		}
	}
}
