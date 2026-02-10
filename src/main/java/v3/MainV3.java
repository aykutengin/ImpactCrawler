package v3;

import v3.analyzer.ImpactAnalyzer;
import v3.model.ImpactAnalysisResult;
import v3.reporter.JsonReporter;
import v3.reporter.TextReporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Main entry point for the V3 Impact Analysis Tool.
 *
 * This tool performs static analysis on a Java monolith to determine:
 * - Which MyBatis mapper methods access a given database table
 * - Which service-layer methods invoke those mapper methods
 * - The complete impact chain from table to service methods
 *
 * Usage:
 *   java v3.MainV3 <monolith-root-path> <table-name> [output-format]
 *
 * Arguments:
 *   monolith-root-path: Path to the root of the Java monolith
 *   table-name: Database table name to analyze
 *   output-format: Optional. Either "json" or "text" (default: text)
 *
 * Example:
 *   java v3.MainV3 /path/to/monolith CUSTOMER_TABLE json
 */
public class MainV3 {

    public static void main(String[] args) {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }

        String monolithPath = args[0];
        String tableName = args[1];
        String outputFormat = args.length > 2 ? args[2].toLowerCase() : "text";

        // Validate arguments
        Path rootPath = Paths.get(monolithPath);
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            System.err.println("Error: Invalid monolith path: " + monolithPath);
            System.exit(1);
        }

        if (!outputFormat.equals("json") && !outputFormat.equals("text")) {
            System.err.println("Error: Invalid output format. Use 'json' or 'text'");
            System.exit(1);
        }

        try {
            runAnalysis(rootPath, tableName, outputFormat);
        } catch (Exception e) {
            System.err.println("Error during analysis: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runAnalysis(Path monolithPath, String tableName, String outputFormat) {
        System.out.println("=======================================================================");
        System.out.println("        STATIC IMPACT ANALYSIS TOOL - Version 3.0                ");
        System.out.println("=======================================================================");
        System.out.println();

        // Initialize the analyzer
        ImpactAnalyzer analyzer = new ImpactAnalyzer();

        long startTime = System.currentTimeMillis();
        analyzer.initialize(monolithPath);
        long indexTime = System.currentTimeMillis() - startTime;

        System.out.println();
        System.out.println("Indexing completed in " + indexTime + " ms");
        System.out.println();

        // Display statistics
        displayStatistics(analyzer.getStatistics());
        System.out.println();

        // Perform impact analysis
        System.out.println("Analyzing impact for table: " + tableName);
        System.out.println("-".repeat(70));

        startTime = System.currentTimeMillis();
        ImpactAnalysisResult result = analyzer.analyzeTableImpact(tableName);
        long analysisTime = System.currentTimeMillis() - startTime;

        System.out.println("Analysis completed in " + analysisTime + " ms");
        System.out.println();

        // Generate and output report
        if (outputFormat.equals("json")) {
            JsonReporter jsonReporter = new JsonReporter();
            String jsonReport = jsonReporter.generateReport(result);
            System.out.println(jsonReport);
        } else {
            TextReporter textReporter = new TextReporter();
            String textReport = textReporter.generateReport(result);
            System.out.println(textReport);
        }

        // Save report to file
        try {
            saveReportToFile(result, tableName, outputFormat);
        } catch (IOException e) {
            System.err.println("Warning: Could not save report to file: " + e.getMessage());
        }
    }

    private static void displayStatistics(Map<String, Integer> stats) {
        System.out.println("INDEXING STATISTICS:");
        System.out.println("-".repeat(70));
        stats.forEach((key, value) ->
            System.out.printf("  %-25s: %,d%n", key, value)
        );
    }

    private static void saveReportToFile(ImpactAnalysisResult result, String tableName,
                                        String outputFormat) throws IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = String.format("impact_analysis_%s_%s.%s",
            tableName, timestamp, outputFormat);
        Path outputPath = Paths.get(fileName);

        String content;
        if (outputFormat.equals("json")) {
            JsonReporter reporter = new JsonReporter();
            content = reporter.generateReport(result);
        } else {
            TextReporter reporter = new TextReporter();
            content = reporter.generateReport(result);
        }

        Files.writeString(outputPath, content);
        System.out.println("Report saved to: " + outputPath.toAbsolutePath());
    }

    private static void printUsage() {
        System.out.println("Usage: java v3.MainV3 <monolith-root-path> <table-name> [output-format]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  monolith-root-path  : Path to the root of the Java monolith");
        System.out.println("  table-name          : Database table name to analyze");
        System.out.println("  output-format       : Optional. Either 'json' or 'text' (default: text)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java v3.MainV3 /path/to/monolith CUSTOMER_TABLE json");
        System.out.println();
        System.out.println("Description:");
        System.out.println("  Performs static impact analysis on a Java monolith to determine");
        System.out.println("  which service methods are affected by changes to a database table.");
        System.out.println();
        System.out.println("  The tool:");
        System.out.println("    1. Scans all Maven modules");
        System.out.println("    2. Parses MyBatis mapper XML files");
        System.out.println("    3. Extracts SQL table references using JSqlParser");
        System.out.println("    4. Analyzes Java source code using JavaParser AST");
        System.out.println("    5. Builds the impact chain: Table → Mapper → Service");
        System.out.println();
    }
}
