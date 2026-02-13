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
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger logger = Logger.getLogger(MainV3.class.getName());
    static {
        logger.setLevel(Level.SEVERE); // Hide info/debug messages by default
    }

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
            logger.log(Level.SEVERE, "Error: Invalid monolith path: " + monolithPath);
            System.exit(1);
        }

        if (!outputFormat.equals("json") && !outputFormat.equals("text")) {
            logger.log(Level.SEVERE, "Error: Invalid output format. Use 'json' or 'text'");
            System.exit(1);
        }

        try {
            runAnalysis(rootPath, tableName, outputFormat);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during analysis: " + e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void runAnalysis(Path monolithPath, String tableName, String outputFormat) {
        logger.log(Level.INFO, "=======================================================================");
        logger.log(Level.INFO, "        STATIC IMPACT ANALYSIS TOOL - Version 3.0                ");
        logger.log(Level.INFO, "=======================================================================");
        logger.log(Level.INFO, "");

        // Initialize the analyzer
        ImpactAnalyzer analyzer = new ImpactAnalyzer();

        long startTime = System.currentTimeMillis();
        analyzer.initialize(monolithPath);
        long indexTime = System.currentTimeMillis() - startTime;

        logger.log(Level.INFO, "");
        logger.log(Level.INFO, "Indexing completed in " + indexTime + " ms");
        logger.log(Level.INFO, "");

        // Display statistics
        displayStatistics(analyzer.getStatistics());
        logger.log(Level.INFO, "");

        // Perform impact analysis
        logger.log(Level.INFO, "Analyzing impact for table: " + tableName);
        logger.log(Level.INFO, "-".repeat(70));

        startTime = System.currentTimeMillis();
        ImpactAnalysisResult result = analyzer.analyzeTableImpact(tableName);
        long analysisTime = System.currentTimeMillis() - startTime;

        logger.log(Level.INFO, "Analysis completed in " + analysisTime + " ms");
        logger.log(Level.INFO, "");

        // Generate and output report
        if (outputFormat.equals("json")) {
            JsonReporter jsonReporter = new JsonReporter();
            String jsonReport = jsonReporter.generateReport(result);
            logger.log(Level.INFO, jsonReport);
        } else {
            TextReporter textReporter = new TextReporter();
            String textReport = textReporter.generateReport(result);
            logger.log(Level.INFO, textReport);
        }

        // Save report to file
        try {
            saveReportToFile(result, tableName, outputFormat);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Warning: Could not save report to file: " + e.getMessage());
        }
    }

    private static void displayStatistics(Map<String, Integer> stats) {
        logger.log(Level.INFO, "INDEXING STATISTICS:");
        logger.log(Level.INFO, "-".repeat(70));
        stats.forEach((key, value) ->
            logger.log(Level.INFO, String.format("  %-25s: %,d", key, value))
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
        logger.log(Level.INFO, "Report saved to: " + outputPath.toAbsolutePath());
    }

    private static void printUsage() {
        logger.log(Level.INFO, "Usage: java v3.MainV3 <monolith-root-path> <table-name> [output-format]");
        logger.log(Level.INFO, "");
        logger.log(Level.INFO, "Arguments:");
        logger.log(Level.INFO, "  monolith-root-path  : Path to the root of the Java monolith");
        logger.log(Level.INFO, "  table-name          : Database table name to analyze");
        logger.log(Level.INFO, "  output-format       : Optional. Either 'json' or 'text' (default: text)");
        logger.log(Level.INFO, "");
        logger.log(Level.INFO, "Example:");
        logger.log(Level.INFO, "  java v3.MainV3 /path/to/monolith CUSTOMER_TABLE json");
        logger.log(Level.INFO, "");
        logger.log(Level.INFO, "Description:");
        logger.log(Level.INFO, "  Performs static impact analysis on a Java monolith to determine");
        logger.log(Level.INFO, "  which service methods are affected by changes to a database table.");
        logger.log(Level.INFO, "");
        logger.log(Level.INFO, "  The tool:");
        logger.log(Level.INFO, "    1. Scans all Maven modules");
        logger.log(Level.INFO, "    2. Parses MyBatis mapper XML files");
        logger.log(Level.INFO, "    3. Extracts SQL table references using JSqlParser");
        logger.log(Level.INFO, "    4. Analyzes Java source code using JavaParser AST");
        logger.log(Level.INFO, "    5. Builds the impact chain: Table  Mapper  Service");
        logger.log(Level.INFO, "");
    }
}
