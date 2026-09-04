package org.shatterfish.harness.hooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger and the tree must say the same thing.
 *
 * <p>Non-negotiable #3 says every edit to an upstream file is listed in {@code docs/UPSTREAM.md}.
 * ADR-0008 makes that mechanical: each site carries {@code // shatterfish-hook:<id>} and this test
 * compares the ids in the tree with the rows in the document. Without it the table is a promise;
 * with it, an unlisted hook and a listed hook that no longer exists both fail the build.
 *
 * <p>The budget of ten rows (ADR-0008, restated by ADR-0016) is checked here too. Reaching it does
 * not make a change impossible; it makes it require an ADR, which is the point.
 */
class HooksLedgerTest {

	/** The registry itself, hook row 2. The only Shatterfish source file outside {@code shatterfish/}. */
	private static final String REGISTRY =
			"core/src/main/java/com/shatteredpixel/shatteredpixeldungeon/shatterfish/Hooks.java";

	/** The ledger's budget for v1. Raising it needs an ADR, not an edit here. */
	private static final int BUDGET = 10;

	/**
	 * Modified upstream files that are documentation rather than build or game behaviour, listed as
	 * such in {@code docs/UPSTREAM.md} and re-applied on upgrade by taking ours. They carry no
	 * marker because there is no site to mark.
	 */
	private static final List<String> DOCUMENTATION_EXCEPTIONS = List.of("README.md", ".gitignore");

	@Test
	@DisplayName("every hook id in the tree has a row, and every row has a site")
	void markers_and_ledger_rows_agree() {
		SortedSet<Integer> inTree = new TreeSet<>();
		Ledger.markersByFile().values().forEach(inTree::addAll);
		SortedSet<Integer> inLedger = Ledger.ledgerRowIds();

		assertEquals(inLedger, inTree,
				"the hook ids marked in the upstream tree and the rows in docs/UPSTREAM.md differ."
						+ " Markers by file: " + Ledger.markersByFile()
						+ ". A new hook needs a row in the same pull request; a removed hook needs its"
						+ " row removed.");
	}

	@Test
	@DisplayName("the ledger stays inside its budget of ten rows")
	void the_budget_is_ten() {
		SortedSet<Integer> rows = Ledger.ledgerRowIds();
		assertTrue(rows.size() <= BUDGET,
				"docs/UPSTREAM.md lists " + rows.size() + " hook rows " + rows + ", over the budget of "
						+ BUDGET + ". ADR-0008 requires an ADR to change the budget, not an edit to this test.");
	}

	@Test
	@DisplayName("the registry carries its own row and hides no other")
	void the_registry_is_not_a_hiding_place() {
		Map<String, SortedSet<Integer>> markers = Ledger.markersByFile();

		assertEquals(new TreeSet<>(List.of(2)), markers.get(REGISTRY),
				REGISTRY + " must carry hook id 2 and nothing else. ADR-0008 anticipated the counting"
						+ " test being gamed by moving a hook into the registry, where it would be one"
						+ " marker instead of many.");

		markers.forEach((file, ids) -> assertTrue(file.equals(REGISTRY) || !ids.contains(2),
				"hook id 2 is the registry file itself and must not mark a site; found in " + file));
	}

	@Test
	@DisplayName("no upstream file is modified without a hook row")
	void every_modified_upstream_file_is_a_hook() {
		Map<String, SortedSet<Integer>> markers = Ledger.markersByFile();
		for (String modified : Ledger.filesModifiedSinceTag()) {
			if (DOCUMENTATION_EXCEPTIONS.contains(modified)) {
				continue;
			}
			assertTrue(markers.containsKey(modified),
					modified + " differs from the pinned tag " + Ledger.pinnedTag()
							+ " but carries no shatterfish-hook marker. Every edit to an upstream file is a"
							+ " hook (non-negotiable #3): mark the site and add the row, or revert the edit.");
		}
	}
}
