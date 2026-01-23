package v1;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v1.SearchReferences
 *
 * A dependency-lite reference crawler for Java codebases.
 * Given a starting term (class name or arbitrary token), it:
 *  1) Finds all .java files that reference the term (token boundary aware).
 *  2) Extracts declared top-level types from those files and pushes both their
 *     simple names and their fully-qualified names as *new* search terms.
 *  3) Repeats via a stack (DFS) until exhausted, collecting a transitive reference map.
 *
 * Usage:
 *   java v1.SearchReferences -root /path/to/repo -term MyService[,OtherSeed] [-ignoreCase] [-dot out.dot]
 */
public class SearchReferences {

    public static void main(String[] args) throws Exception {
        Cli cli = Cli.parse(args);
        if (!cli.valid()) {
            Cli.printHelp();
            System.exit(1);
        }

        Path root = cli.root;
        if (!Files.isDirectory(root)) {
            System.err.println("Root is not a directory: " + root.toAbsolutePath());
            System.exit(2);
        }

        System.out.println("Root: " + root.toAbsolutePath());
        System.out.println("Seed term(s): " + String.join(", ", cli.seeds));
        System.out.println("Ignore case: " + cli.ignoreCase);
        if (cli.dotPath != null) {
            System.out.println("DOT output: " + cli.dotPath.toAbsolutePath());
        }
        System.out.println();

        // Collect Java files
        List<Path> javaFiles = listJavaFiles(root, cli.excludeDirs);
        System.out.println("Indexed .java files: " + javaFiles.size());
        if (javaFiles.isEmpty()) {
            System.out.println("No .java files found. Exiting.");
            return;
        }

        // Lazy cache for file contents and parsed declared types
        Map<Path, String> fileContentCache = new HashMap<>();
        Map<Path, DeclaredTypes> fileTypesCache = new HashMap<>();

        // Results
        Map<String, Set<Path>> termToReferencingFiles = new LinkedHashMap<>();     // term -> files that reference it
        Map<String, Set<String>> expansionGraph = new LinkedHashMap<>();           // term -> new terms discovered from matching files
        Set<String> visitedTerms = new HashSet<>();

        Deque<String> stack = new ArrayDeque<>();
        for (String seed : cli.seeds) {
            String s = seed.trim();
            if (!s.isEmpty()) stack.push(s);
        }

        while (!stack.isEmpty()) {
            String term = stack.pop();
            System.out.printf("Term: %s\n", term);
            if (!visitedTerms.add(term)) {
                continue; // already processed
            }

            // Find files referencing this term
            Set<Path> matches = new LinkedHashSet<>();
            Pattern termPattern = buildTokenPattern(term, cli.ignoreCase);

            for (Path p : javaFiles) {
                String content = fileContentCache.computeIfAbsent(p, SearchReferences::safeRead);
                if (content == null) continue;

                if (containsToken(content, termPattern)) {
                    matches.add(p);
                }
            }

            termToReferencingFiles.put(term, matches);

            // For each matching file: extract declared types -> push as new search terms
            Set<String> newlyDiscoveredTerms = new LinkedHashSet<>();
            for (Path file : matches) {
                DeclaredTypes types = fileTypesCache.computeIfAbsent(file, SearchReferences::extractDeclaredTypes);
                for (String t : types.allSearchableNames()) {
                    if (!visitedTerms.contains(t)) {
                        newlyDiscoveredTerms.add(t);
                        stack.push(t);
                    }
                }
            }
            expansionGraph.put(term, newlyDiscoveredTerms);
        }

        // Print report
        printReport(termToReferencingFiles, expansionGraph);

        // Optionally write Graphviz DOT file
        if (cli.dotPath != null) {
            writeDot(cli.dotPath, termToReferencingFiles, expansionGraph);
            System.out.println("\nDOT graph written to: " + cli.dotPath.toAbsolutePath());
            System.out.println("Render with: dot -Tpng " + cli.dotPath.getFileName() + " -o graph.png");
        }
    }

    // ---------------- CLI ----------------

    static class Cli {
        Path root;
        List<String> seeds = new ArrayList<>();
        boolean ignoreCase = false;
        Path dotPath = null;
        Set<String> excludeDirs = new LinkedHashSet<>(Arrays.asList(
                ".git", ".hg", ".svn", ".idea", ".vscode",
                "target", "build", "out", "node_modules", ".gradle"
        ));

        static Cli parse(String[] args) {
            Cli cli = new Cli();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "-root":
                        cli.root = Paths.get(nextArg(args, ++i, "-root"));
                        break;
                    case "-term":
                        String raw = nextArg(args, ++i, "-term");
                        cli.seeds = Arrays.asList(raw.split(","));
                        break;
                    case "-ignoreCase":
                        cli.ignoreCase = true;
                        break;
                    case "-dot":
                        cli.dotPath = Paths.get(nextArg(args, ++i, "-dot"));
                        break;
                    case "-exclude":
                        // comma-separated directory names to exclude (exact dir name matches)
                        String ex = nextArg(args, ++i, "-exclude");
                        cli.excludeDirs.addAll(Arrays.asList(ex.split(",")));
                        break;
                    default:
                        // ignore unknown for simplicity
                        break;
                }
            }
            return cli;
        }

        static String nextArg(String[] a, int i, String flag) {
            if (i >= a.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return a[i];
        }

        boolean valid() {
            return root != null && !seeds.isEmpty();
        }

        static void printHelp() {
            System.out.println("Usage:");
            System.out.println("  java v1.SearchReferences -root <path> -term <seed[,seed2,...]> [-ignoreCase] [-dot out.dot] [-exclude dir1,dir2]");
            System.out.println();
            System.out.println("Examples:");
            System.out.println("  java v1.SearchReferences -root /repo -term MyService");
            System.out.println("  java v1.SearchReferences -root . -term com.myco.billing.InvoiceService -ignoreCase -dot graph.dot");
            System.out.println("  java v1.SearchReferences -root . -term MyService,OtherType -exclude target,build");
        }
    }

    // ------------- File scanning -------------

    static List<Path> listJavaFiles(Path root, Set<String> excludeDirNames) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path name = dir.getFileName();
                if (name != null && excludeDirNames.contains(name.toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(".java")) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }


    static String safeRead(Path p) {
        try {
            // Try UTF-8 first
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (java.nio.charset.MalformedInputException ex) {
            // Fall back to Italian/Western European encodings
            String s;
            try {
                s = Files.readString(p, java.nio.charset.Charset.forName("windows-1252"));
                return s;
            } catch (Exception ignore) {}
            try {
                s = Files.readString(p, java.nio.charset.Charset.forName("ISO-8859-15"));
                return s;
            } catch (Exception ignore) {}
            try {
                s = Files.readString(p, java.nio.charset.StandardCharsets.ISO_8859_1);
                return s;
            } catch (Exception ignore) {}
            try {
                s = Files.readString(p, java.nio.charset.Charset.forName("MacRoman"));
                return s;
            } catch (Exception e2) {
                System.err.println("WARN: Failed to decode " + p + " (" + e2.getMessage() + ")");
                return null;
            }
        } catch (Exception e) {
            System.err.println("WARN: Failed to read " + p + " (" + e.getMessage() + ")");
            return null;
        }
    }


    // ------------- Matching -------------

    /**
     * Builds a regex that matches the term on Java token boundaries:
     * - Not preceded or followed by [A-Za-z0-9_]
     * Works for both simple names and fully-qualified names (dots are allowed).
     */
    static Pattern buildTokenPattern(String term, boolean ignoreCase) {
        String q = Pattern.quote(term);
        String regex = "(?<![A-Za-z0-9_])" + q + "(?![A-Za-z0-9_])";
        return ignoreCase
                ? Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                : Pattern.compile(regex);
    }

    static boolean containsToken(String content, Pattern tokenPattern) {
        Matcher m = tokenPattern.matcher(content);
        return m.find();
    }

    // ------------- Declared type extraction -------------

    /**
     * Represents declared top-level types in a file.
     * Stores package name and all discovered simple names.
     */
    static class DeclaredTypes {
        String packageName;                // may be null
        Set<String> simpleNames = new LinkedHashSet<>();

        Set<String> allSearchableNames() {
            Set<String> out = new LinkedHashSet<>();
            for (String s : simpleNames) {
                out.add(s);
                if (packageName != null && !packageName.isBlank()) {
                    out.add(packageName + "." + s);
                }
            }
            return out;
        }
    }

    /**
     * Light-weight regex extraction of:
     *  - package name:    package com.example.foo;
     *  - top-level types: [modifiers] (class|interface|enum|record) Name
     *
     * This ignores inner classes and may pick false positives if used inside comments.
     */
    static DeclaredTypes extractDeclaredTypes(Path file) {
        DeclaredTypes dt = new DeclaredTypes();
        String content = safeRead(file);
        if (content == null) return dt;

        // package
        Matcher pkg = Pattern.compile("(?m)^\\s*package\\s+([a-zA-Z_][\\w\\.]*)\\s*;")
                .matcher(content);
        if (pkg.find()) {
            dt.packageName = pkg.group(1);
        }

        // declared top-level types (class/interface/enum/record)
        // We try to avoid matching inner types by using ^ anchor and no preceding indentation assumption;
        // but in practice, inner types could also start at column 0 (rare). This is a heuristic.
        Pattern typeDecl = Pattern.compile(
                "(?m)^\\s*(?:public|protected|private|abstract|final|sealed|non-sealed|static|strictfp|\\s)*" +
                        "(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)"
        );
        Matcher m = typeDecl.matcher(content);
        while (m.find()) {
            String name = m.group(2);
            dt.simpleNames.add(name);
        }
        return dt;
    }

    // ------------- Reporting -------------

    static void printReport(Map<String, Set<Path>> termToFiles,
                            Map<String, Set<String>> expansionGraph) {
        System.out.println("\n=== Reference Report ===");
        for (Map.Entry<String, Set<Path>> e : termToFiles.entrySet()) {
            String term = e.getKey();
            Set<Path> files = e.getValue();
            System.out.println("\nTerm: " + term);
            if (files.isEmpty()) {
                System.out.println("  (no references found)");
            } else {
                System.out.println("  Referenced in " + files.size() + " file(s):");
                for (Path p : files) {
                    System.out.println("    - " + p.toString());
                }
            }
            Set<String> expanded = expansionGraph.getOrDefault(term, Collections.emptySet());
            if (!expanded.isEmpty()) {
                System.out.println("  New terms discovered from matching files (" + expanded.size() + "):");
                for (String t : expanded) {
                    System.out.println("    -> " + t);
                }
            }
        }

        // Summary
        System.out.println("\n=== Summary ===");
        System.out.println("Total terms processed: " + termToFiles.size());
        long totalRefs = termToFiles.values().stream().mapToLong(Set::size).sum();
        System.out.println("Total (term -> file) references: " + totalRefs);
    }

    static void writeDot(Path dotFile,
                         Map<String, Set<Path>> termToFiles,
                         Map<String, Set<String>> expansionGraph) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph G {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box, fontsize=10];\n");

        // Create nodes for terms (rounded) and files (plain)
        Set<String> allTerms = termToFiles.keySet();
        for (String term : allTerms) {
            sb.append("  \"").append(term).append("\" [shape=ellipse, style=rounded, color=\"#1f77b4\"];\n");
        }

        // For readability, files are nodes too
        Set<String> fileNodes = new LinkedHashSet<>();
        for (Set<Path> files : termToFiles.values()) {
            for (Path p : files) fileNodes.add(p.toString());
        }
        for (String f : fileNodes) {
            sb.append("  \"").append(f).append("\" [shape=box, color=\"#999999\"];\n");
        }

        // Edges: term -> file (meaning "term is referenced by file")
        for (Map.Entry<String, Set<Path>> e : termToFiles.entrySet()) {
            String term = e.getKey();
            for (Path f : e.getValue()) {
                sb.append("  \"").append(term).append("\" -> \"").append(f.toString())
                        .append("\" [color=\"#2ca02c\", label=\"ref\"];\n");
            }
        }

        // Edges: term -> newTerm (expansion)
        for (Map.Entry<String, Set<String>> e : expansionGraph.entrySet()) {
            String src = e.getKey();
            for (String dst : e.getValue()) {
                sb.append("  \"").append(src).append("\" -> \"").append(dst)
                        .append("\" [style=dashed, color=\"#ff7f0e\", label=\"expands\"];\n");
            }
        }

        sb.append("}\n");

        try {
            Files.writeString(dotFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("WARN: Failed to write DOT file: " + ex.getMessage());
        }
    }
}
