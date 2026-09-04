package org.shatterfish.harness.hooks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the repository the way a reviewer would: the hook markers actually in the upstream tree,
 * the rows actually in {@code docs/UPSTREAM.md}, and the diff against the pinned upstream tag.
 *
 * <p>Everything here is source and git, never runtime state, so the checks hold whether or not a
 * Run can boot on the machine running them.
 */
final class Ledger {

	/** The marker every hook site carries. A prose line may follow it; only the id is parsed. */
	private static final Pattern MARKER = Pattern.compile("shatterfish-hook:(\\d+)");

	/** The pinned-release row in {@code docs/UPSTREAM.md}. */
	private static final Pattern TAG_ROW = Pattern.compile("^\\|\\s*Tag\\s*\\|\\s*`([^`]+)`\\s*\\|");

	/** A row of the hooks table: the id is the first cell. */
	private static final Pattern HOOK_ROW = Pattern.compile("^\\|\\s*(\\d+)\\s*\\|");

	/**
	 * Top-level directories that are not upstream's: our own modules, our own documentation, and
	 * tooling. Matched only at the root, because the registry lives at
	 * {@code core/.../shatterfish/Hooks.java} and a name-anywhere match would skip the one file the
	 * ledger most needs to see.
	 */
	private static final List<String> SKIPPED_ROOT_DIRS =
			List.of("shatterfish", "docs", "_bmad", "_bmad-output", ".github", ".idea", "site", "gradle");

	/** Directory names skipped at any depth: build output and version-control internals. */
	private static final List<String> SKIPPED_ANYWHERE = List.of(".git", ".gradle", "build");

	/** File suffixes a hook could live in: upstream source and build files. */
	private static final List<String> SCANNED_SUFFIXES =
			List.of(".java", ".gradle", ".properties", ".xml");

	private Ledger() {
	}

	/** The repository root, found by walking up from the working directory. */
	static Path repoRoot() {
		Path dir = Path.of("").toAbsolutePath();
		while (dir != null) {
			if (Files.isRegularFile(dir.resolve("settings.gradle"))
					&& Files.isRegularFile(dir.resolve("docs/UPSTREAM.md"))) {
				return dir;
			}
			dir = dir.getParent();
		}
		throw new IllegalStateException(
				"no repository root above " + Path.of("").toAbsolutePath()
						+ " (looked for settings.gradle next to docs/UPSTREAM.md)");
	}

	static Path upstreamDoc() {
		return repoRoot().resolve("docs/UPSTREAM.md");
	}

	private static List<String> upstreamDocLines() {
		try {
			return Files.readAllLines(upstreamDoc(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** The upstream release tag this fork is pinned to, read from {@code docs/UPSTREAM.md}. */
	static String pinnedTag() {
		for (String line : upstreamDocLines()) {
			Matcher m = TAG_ROW.matcher(line);
			if (m.find()) {
				return m.group(1);
			}
		}
		throw new IllegalStateException("no pinned-release Tag row in " + upstreamDoc());
	}

	/** The ids of the rows in the hooks table of {@code docs/UPSTREAM.md}. */
	static SortedSet<Integer> ledgerRowIds() {
		SortedSet<Integer> ids = new TreeSet<>();
		boolean inHooks = false;
		for (String line : upstreamDocLines()) {
			if (line.startsWith("## ")) {
				inHooks = line.trim().equals("## Hooks");
				continue;
			}
			if (!inHooks) {
				continue;
			}
			Matcher m = HOOK_ROW.matcher(line);
			if (m.find()) {
				ids.add(Integer.valueOf(m.group(1)));
			}
		}
		if (ids.isEmpty()) {
			throw new IllegalStateException("no hook rows found under the Hooks heading in " + upstreamDoc());
		}
		return ids;
	}

	/**
	 * Every hook marker in the upstream tree, by repository-relative path. Paths under
	 * {@code shatterfish/} and {@code docs/} are excluded: a marker there would be a marker in our
	 * own code, which is not a hook.
	 */
	static Map<String, SortedSet<Integer>> markersByFile() {
		Path root = repoRoot();
		Map<String, SortedSet<Integer>> found = new LinkedHashMap<>();
		try (Stream<Path> tree = Files.walk(root)) {
			List<Path> files = tree.filter(Files::isRegularFile)
					.filter(p -> !isSkipped(root.relativize(p)))
					.filter(Ledger::isScanned)
					.sorted()
					.toList();
			for (Path file : files) {
				SortedSet<Integer> ids = new TreeSet<>();
				for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
					Matcher m = MARKER.matcher(line);
					while (m.find()) {
						ids.add(Integer.valueOf(m.group(1)));
					}
				}
				if (!ids.isEmpty()) {
					found.put(relative(file), ids);
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return found;
	}

	private static boolean isSkipped(Path relative) {
		Path parent = relative.getParent();
		if (parent == null) {
			return false;
		}
		if (SKIPPED_ROOT_DIRS.contains(parent.getName(0).toString())) {
			return true;
		}
		for (Path part : parent) {
			if (SKIPPED_ANYWHERE.contains(part.toString())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isScanned(Path file) {
		String name = file.getFileName().toString();
		return SCANNED_SUFFIXES.stream().anyMatch(name::endsWith);
	}

	static String relative(Path file) {
		return repoRoot().relativize(file).toString().replace('\\', '/');
	}

	/** Repository-relative paths of files that exist at the pinned tag and differ from it now. */
	static List<String> filesModifiedSinceTag() {
		List<String> modified = new ArrayList<>();
		for (String line : git("diff", "--name-status", pinnedTag())) {
			if (line.startsWith("M\t")) {
				modified.add(line.substring(2).trim());
			}
		}
		return modified;
	}

	/** The lines a file lost relative to the pinned tag, without the leading diff marker. */
	static List<String> linesRemovedSinceTag(String path) {
		List<String> removed = new ArrayList<>();
		for (String line : git("diff", "--unified=0", pinnedTag(), "--", path)) {
			if (line.startsWith("---")) {
				continue;
			}
			if (line.startsWith("-")) {
				removed.add(line.substring(1));
			}
		}
		return removed;
	}

	/** The lines a file gained relative to the pinned tag, without the leading diff marker. */
	static List<String> linesAddedSinceTag(String path) {
		List<String> added = new ArrayList<>();
		for (String line : git("diff", "--unified=0", pinnedTag(), "--", path)) {
			if (line.startsWith("+++")) {
				continue;
			}
			if (line.startsWith("+")) {
				added.add(line.substring(1));
			}
		}
		return added;
	}

	static List<String> readLines(String path) {
		try {
			return Files.readAllLines(repoRoot().resolve(path), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Runs git at the repository root. A failure here fails the check rather than skipping it:
	 * these tests compare the tree against the pinned tag, and a checkout without that tag cannot
	 * answer the question. Continuous integration checks out with full history for this reason.
	 */
	static List<String> git(String... args) {
		List<String> command = new ArrayList<>();
		command.add("git");
		command.addAll(List.of(args));
		try {
			Process process = new ProcessBuilder(command)
					.directory(repoRoot().toFile())
					.redirectErrorStream(true)
					.start();
			List<String> output = new ArrayList<>();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.add(line);
				}
			}
			int status = process.waitFor();
			if (status != 0) {
				throw new IllegalStateException(String.join(" ", command) + " exited " + status + ":\n"
						+ String.join("\n", output)
						+ "\n(a checkout without the pinned tag cannot run this check; fetch full history)");
			}
			return output;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted running " + String.join(" ", command), e);
		}
	}
}
