package org.shatterfish.harness.hooks;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the repository the way a reviewer would: the hook markers actually in the upstream tree,
 * the rows and sites actually listed in {@code docs/UPSTREAM.md}, and the difference between the
 * tree and the pinned upstream tag.
 *
 * <p>Everything here is source and git, never runtime state, so the checks hold whether or not a
 * Run can boot on the machine running them.
 */
final class Ledger {

	/**
	 * A hook marker. The id is parsed; a prose line usually follows it and is ignored. The pattern
	 * is deliberately strict, and {@link #LOOSE_MARKER} exists to catch a marker that was meant to
	 * match this and does not, rather than letting it pass as a comment.
	 */
	private static final Pattern MARKER = Pattern.compile("// shatterfish-hook:(\\d+)$");

	/** Anything that looks like it was trying to be a marker. */
	private static final Pattern LOOSE_MARKER = Pattern.compile("shatterfish-hook");

	/** The pinned-release row in {@code docs/UPSTREAM.md}. */
	private static final Pattern TAG_ROW = Pattern.compile("^\\|\\s*Tag\\s*\\|\\s*`([^`]+)`\\s*\\|");

	/** A row of the hooks table: the id is the first cell. */
	private static final Pattern HOOK_ROW = Pattern.compile("^\\|\\s*(\\d+)\\s*\\|");

	/** A line of the site index: {@code <id> <sites> <path>}. */
	private static final Pattern SITE_ROW = Pattern.compile("^(\\d+)\\s+(\\d+)\\s+(\\S.*)$");

	/**
	 * Upstream's own source and build modules: the territory where an edit is a hook. This is an
	 * allowlist rather than a list of our directories, because the list of ours grows with every tool
	 * anyone adds and a forgotten entry there would turn an ordinary addition into a build failure.
	 * The allowlist's own risk — a module appearing in a newer upstream tag and going unwatched — is
	 * closed by {@code every_upstream_directory_is_classified}, which fails when the pinned tag has a
	 * top-level directory this file does not name.
	 */
	static final List<String> UPSTREAM_CODE_ROOTS =
			List.of("core", "SPD-classes", "desktop", "services", "android", "ios");

	/**
	 * Top-level directories that exist at the pinned tag but belong to the project around the code
	 * rather than to the game: documentation, continuous integration, the Gradle wrapper. We edit
	 * these freely and they are not hooks.
	 */
	static final List<String> OUR_TERRITORY_AT_THE_TAG = List.of("docs", ".github", "gradle");

	/** Directory names skipped at any depth: build output and version-control internals. */
	private static final List<String> SKIPPED_ANYWHERE = List.of(".git", ".gradle", "build");

	/**
	 * Suffixes never scanned. This is a denylist rather than an allowlist on purpose: an allowlist
	 * makes an edit to an unlisted file type unmarkable, so the "every changed upstream file carries
	 * a marker" check would have no legal remedy and the build would be permanently red.
	 */
	private static final List<String> BINARY_SUFFIXES =
			List.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".bmp", ".ogg", ".mp3", ".wav",
					".ttf", ".otf", ".jar", ".zip", ".gz", ".bin", ".pdf", ".class", ".keystore", ".dll",
					".so", ".dylib", ".exe");

	/** Files above this size are assets, not source, and are not scanned. */
	private static final long MAX_SCANNED_BYTES = 2L * 1024 * 1024;

	private Ledger() {
	}

	/** One hook marker at one place in one file. */
	record Marker(String path, int id, int line) {
	}

	/** One line of the site index in {@code docs/UPSTREAM.md}. */
	record Site(int id, int markers, String path) {
	}

	/** One path that differs from the pinned tag: {@code M}, {@code A}, {@code D} or {@code R…}. */
	record Change(String status, String path) {

		boolean isModification() {
			return status.equals("M");
		}

		boolean isAddition() {
			return status.equals("A");
		}
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
		return readLines("docs/UPSTREAM.md");
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

	/**
	 * The ids of the rows in the hooks table, in document order and with duplicates kept, so that a
	 * repeated id is visible rather than silently collapsing two rows into one.
	 */
	static List<Integer> ledgerRowIds() {
		List<Integer> ids = new ArrayList<>();
		boolean inHooks = false;
		for (String line : upstreamDocLines()) {
			if (line.startsWith("## ")) {
				inHooks = line.trim().equals("## Hooks");
				continue;
			}
			if (inHooks) {
				Matcher m = HOOK_ROW.matcher(line);
				if (m.find()) {
					ids.add(Integer.valueOf(m.group(1)));
				}
			}
		}
		if (ids.isEmpty()) {
			throw new IllegalStateException("no hook rows found under the Hooks heading in " + upstreamDoc());
		}
		return ids;
	}

	/**
	 * The site index: the fenced block after the hooks table naming every file each row marks and how
	 * many markers it carries there. The prose table abbreviates paths for a reader; this is the same
	 * information written for a machine, so that a new site in an already-hooked file is a diff to
	 * this document rather than an invisible change.
	 */
	static List<Site> ledgerSites() {
		List<Site> sites = new ArrayList<>();
		boolean inBlock = false;
		boolean seenIndex = false;
		for (String line : upstreamDocLines()) {
			if (line.startsWith("<!-- site-index -->")) {
				seenIndex = true;
				continue;
			}
			if (seenIndex && line.startsWith("```")) {
				if (inBlock) {
					break;
				}
				inBlock = true;
				continue;
			}
			if (inBlock) {
				Matcher m = SITE_ROW.matcher(line.trim());
				if (m.matches()) {
					sites.add(new Site(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
							m.group(3).trim()));
				}
			}
		}
		if (sites.isEmpty()) {
			throw new IllegalStateException(
					"no site index found in " + upstreamDoc() + "; expected a fenced block after a"
							+ " <!-- site-index --> comment, one 'id markers path' line per file");
		}
		return sites;
	}

	/**
	 * Every hook marker in the upstream tree, in path then line order. Paths under
	 * {@code shatterfish/} and {@code docs/} are excluded: a marker there would be a marker in our
	 * own code, which is not a hook.
	 */
	static List<Marker> markers() {
		List<Marker> markers = new ArrayList<>();
		forEachUpstreamLine((path, lineNumber, text) -> {
			Matcher strict = MARKER.matcher(text.trim());
			if (strict.find()) {
				markers.add(new Marker(path, Integer.parseInt(strict.group(1)), lineNumber));
			}
		});
		return markers;
	}

	/**
	 * Lines that mention {@code shatterfish-hook} but do not parse as a marker. A mistyped marker
	 * would otherwise be a comment: invisible to the id comparison while looking, to a reader, like a
	 * hook that had been declared.
	 */
	static List<String> malformedMarkers() {
		List<String> malformed = new ArrayList<>();
		forEachUpstreamLine((path, lineNumber, text) -> {
			String trimmed = text.trim();
			if (LOOSE_MARKER.matcher(trimmed).find() && !MARKER.matcher(trimmed).find()) {
				malformed.add(path + ":" + lineNumber + "  " + trimmed);
			}
		});
		return malformed;
	}

	private interface LineVisitor {
		void visit(String path, int lineNumber, String text);
	}

	private static void forEachUpstreamLine(LineVisitor visitor) {
		Path root = repoRoot();
		try (Stream<Path> tree = Files.walk(root)) {
			List<Path> files = tree.filter(Files::isRegularFile)
					.filter(p -> !isSkipped(root.relativize(p)))
					.filter(Ledger::isScannable)
					.sorted()
					.toList();
			for (Path file : files) {
				List<String> lines;
				try {
					lines = Files.readAllLines(file, StandardCharsets.UTF_8);
				} catch (MalformedInputException notText) {
					continue;
				}
				String path = relative(file);
				for (int i = 0; i < lines.size(); i++) {
					visitor.visit(path, i + 1, lines.get(i));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static boolean isSkipped(Path relative) {
		Path parent = relative.getParent();
		if (parent == null) {
			return false;
		}
		if (!UPSTREAM_CODE_ROOTS.contains(parent.getName(0).toString())) {
			return true;
		}
		for (Path part : parent) {
			if (SKIPPED_ANYWHERE.contains(part.toString())) {
				return true;
			}
		}
		return false;
	}

	private static boolean isScannable(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (BINARY_SUFFIXES.stream().anyMatch(name::endsWith)) {
			return false;
		}
		try {
			return Files.size(file) <= MAX_SCANNED_BYTES;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}


	/**
	 * Whether a path added since the pinned tag lands in upstream's territory. Only the code modules
	 * count: a new file at the repository root, or under our own directories, cannot be an upstream
	 * file, whereas a new class under {@code core/} sits next to every private the game has.
	 */
	static boolean isUpstreamAddition(String path) {
		return path.contains("/") && UPSTREAM_CODE_ROOTS.contains(path.substring(0, path.indexOf('/')));
	}

	/**
	 * Whether a path that existed at the pinned tag is upstream's to protect. Root-level files are
	 * included because {@code settings.gradle} is hook row 1 and {@code build.gradle} would be the
	 * next one; documentation and continuous integration are ours.
	 */
	static boolean isUpstreamFileAtTheTag(String path) {
		if (!path.contains("/")) {
			return true;
		}
		return UPSTREAM_CODE_ROOTS.contains(path.substring(0, path.indexOf('/')));
	}

	/** The top-level directories present at the pinned tag. */
	static List<String> directoriesAtTheTag() {
		List<String> directories = new ArrayList<>();
		for (String line : git("ls-tree", "--name-only", pinnedTag())) {
			String name = line.trim();
			if (!name.isEmpty() && Files.isDirectory(repoRoot().resolve(name))) {
				directories.add(name);
			}
		}
		return directories;
	}

	static String relative(Path file) {
		return repoRoot().relativize(file).toString().replace('\\', '/');
	}

	/**
	 * Every path that differs from the pinned tag, with its status. Additions, deletions and renames
	 * are reported alongside modifications: a file deleted from {@code core} is as much an edit to
	 * upstream as a line changed in it, and a file added there is how a second Shatterfish class
	 * would arrive inside the game's own module.
	 */
	static List<Change> changesSinceTag() {
		List<Change> changes = new ArrayList<>();
		for (String line : git("diff", "--name-status", pinnedTag())) {
			String[] fields = line.split("\t");
			if (fields.length < 2) {
				continue;
			}
			// A rename is "R<score>\told\tnew"; report the new path, which is where a marker would be.
			changes.add(new Change(fields[0].substring(0, 1), fields[fields.length - 1].trim()));
		}
		// git diff compares the tag against tracked content only, so a file that has been created but
		// not yet added is invisible to it. That is the ordinary state of a new file mid-story, and it
		// is exactly how a new class would arrive inside core without any check noticing.
		for (String untracked : git("ls-files", "--others", "--exclude-standard")) {
			String path = untracked.trim();
			if (!path.isEmpty()) {
				changes.add(new Change("A", path));
			}
		}
		return changes;
	}

	/** The lines a file lost relative to the pinned tag, without the leading diff marker. */
	static List<String> linesRemovedSinceTag(String path) {
		return diffLines(path, '-', "---");
	}

	/** The lines a file gained relative to the pinned tag, without the leading diff marker. */
	static List<String> linesAddedSinceTag(String path) {
		return diffLines(path, '+', "+++");
	}

	private static List<String> diffLines(String path, char sign, String header) {
		List<String> lines = new ArrayList<>();
		for (String line : git("diff", "--unified=0", pinnedTag(), "--", path)) {
			if (line.startsWith(header)) {
				continue;
			}
			if (!line.isEmpty() && line.charAt(0) == sign) {
				lines.add(line.substring(1));
			}
		}
		return lines;
	}

	static List<String> readLines(String path) {
		try {
			return Files.readAllLines(repoRoot().resolve(path), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Runs git at the repository root. A failure here fails the check rather than skipping it: these
	 * tests compare the tree against the pinned tag, and a checkout without that tag cannot answer
	 * the question. Continuous integration checks out with full history for this reason.
	 *
	 * <p>The invocation pins every setting the parsing depends on, because a check whose answer
	 * depends on the reviewer's git configuration is not a check. Standard error is read separately
	 * so that a warning line can never be parsed as diff output.
	 */
	static List<String> git(String... args) {
		// Every setting the parsing depends on is pinned as configuration rather than as a trailing
		// flag, because a flag after "-- <path>" would be read as a pathspec.
		List<String> command = new ArrayList<>(List.of("git",
				"-c", "core.quotepath=false",
				"-c", "color.ui=never",
				"-c", "color.diff=never",
				"-c", "diff.noprefix=false",
				"--no-pager"));
		int subcommand = command.size();
		command.addAll(List.of(args));
		if (args.length > 0 && args[0].equals("diff")) {
			// Immediately after the subcommand, never at the end: a flag after "-- <path>" is a pathspec.
			command.addAll(subcommand + 1, List.of("--no-color", "--no-ext-diff"));
		}
		try {
			Process process = new ProcessBuilder(command).directory(repoRoot().toFile()).start();
			List<String> output = read(process.getInputStream());
			List<String> errors = read(process.getErrorStream());
			int status = process.waitFor();
			if (status != 0) {
				throw new IllegalStateException(String.join(" ", command) + " exited " + status + ":\n"
						+ String.join("\n", errors)
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

	private static List<String> read(java.io.InputStream stream) throws IOException {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
		}
		return lines;
	}
}
