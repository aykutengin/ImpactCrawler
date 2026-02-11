package v3;

import v3.analyzer.ImpactAnalyzer;
import v3.model.ImpactAnalysisResult;
import v3.reporter.JsonReporter;
import v3.reporter.TextReporter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Example demonstrating how to use the V3 Impact Analysis Tool programmatically.
 */
public class ExampleUsage {
    private static final Logger logger = Logger.getLogger(ExampleUsage.class.getName());
    static {
        logger.setLevel(Level.SEVERE); // Hide info/debug messages by default
    }

    public static void main(String[] args) {
        // Example 1: Basic usage
        basicUsageExample();

        // Example 2: Multiple table analysis
        //multipleTableAnalysis();
    }

    private static void basicUsageExample() {
        logger.log(Level.INFO, "=== Example 1: Basic Usage ===\n");

        // Load base path from properties file
        Properties props = new Properties();
        String basePath = null;
        try (InputStream in = ExampleUsage.class.getClassLoader().getResourceAsStream("impactcrawler.properties")) {
            if (in != null) {
                props.load(in);
                basePath = props.getProperty("base.path");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load properties file: " + e.getMessage());
        }
        if (basePath == null || basePath.isEmpty()) {
            logger.log(Level.SEVERE, "base.path property not found or empty. Please check impactcrawler.properties.");
            return;
        }

        // Create the analyzer
        ImpactAnalyzer analyzer = new ImpactAnalyzer();

        // Initialize with the monolith root path from properties
        analyzer.initialize(Paths.get(basePath));

        // Analyze impact for a specific table
        ImpactAnalysisResult result = analyzer.analyzeTableImpact("PINUMBER");

        // Generate text report
        TextReporter textReporter = new TextReporter();
        String textReport = textReporter.generateReport(result);
        logger.log(Level.INFO, textReport);

        // Generate JSON report
        JsonReporter jsonReporter = new JsonReporter();
        String jsonReport = jsonReporter.generateReport(result);
        logger.log(Level.INFO, "\nJSON Output:");
        logger.log(Level.INFO, jsonReport);
    }

    private static void multipleTableAnalysis() {
        logger.log(Level.INFO, "\n\n=== Example 2: Multiple Table Analysis ===\n");

        // Initialize once
        ImpactAnalyzer analyzer = new ImpactAnalyzer();
        analyzer.initialize(Paths.get("./path/to/your/monolith"));

        // View statistics
        logger.log(Level.INFO, "Indexing Statistics:");
        analyzer.getStatistics().forEach((key, value) ->
            logger.log(Level.INFO, String.format("  %s: %d", key, value))
        );

        // Analyze multiple tables efficiently (reuse same analyzer)
        String[] tables = {"PINSTATUS"};

        for (String table : tables) {
            logger.log(Level.INFO, "\n--- Analyzing: " + table + " ---");
            ImpactAnalysisResult result = analyzer.analyzeTableImpact(table);

            logger.log(Level.INFO, String.format("  Found %d impacted service methods", result.getImpacts().size()));
            logger.log(Level.INFO, String.format("  Unresolved references: %d", result.getUnresolvedMapperReferences().size()));
            logger.log(Level.INFO, String.format("  Warnings: %d", result.getWarnings().size()));
        }
    }
}
