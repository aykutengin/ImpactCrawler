package v3;

import v3.analyzer.ImpactAnalyzer;
import v3.model.ImpactAnalysisResult;
import v3.reporter.JsonReporter;
import v3.reporter.TextReporter;

import java.nio.file.Paths;

/**
 * Example demonstrating how to use the V3 Impact Analysis Tool programmatically.
 */
public class ExampleUsage {

    public static void main(String[] args) {
        // Example 1: Basic usage
        basicUsageExample();

        // Example 2: Multiple table analysis
        //multipleTableAnalysis();
    }

    private static void basicUsageExample() {
        System.out.println("=== Example 1: Basic Usage ===\n");

        // Create the analyzer
        ImpactAnalyzer analyzer = new ImpactAnalyzer();

        // Initialize with the monolith root path
        // This scans modules and builds the indices (one-time cost)
        analyzer.initialize(Paths.get("C:\\Users\\DB17UV\\dev\\Microservices\\ms-DGW\\P09343-ita-dgw"));

        // Analyze impact for a specific table
        ImpactAnalysisResult result = analyzer.analyzeTableImpact("PINUMBER");

        // Generate text report
        TextReporter textReporter = new TextReporter();
        String textReport = textReporter.generateReport(result);
        System.out.println(textReport);

        // Generate JSON report
        JsonReporter jsonReporter = new JsonReporter();
        String jsonReport = jsonReporter.generateReport(result);
        System.out.println("\nJSON Output:");
        System.out.println(jsonReport);
    }

    private static void multipleTableAnalysis() {
        System.out.println("\n\n=== Example 2: Multiple Table Analysis ===\n");

        // Initialize once
        ImpactAnalyzer analyzer = new ImpactAnalyzer();
        analyzer.initialize(Paths.get("./path/to/your/monolith"));

        // View statistics
        System.out.println("Indexing Statistics:");
        analyzer.getStatistics().forEach((key, value) ->
            System.out.printf("  %s: %d%n", key, value)
        );

        // Analyze multiple tables efficiently (reuse same analyzer)
        String[] tables = {"PINSTATUS"};

        for (String table : tables) {
            System.out.println("\n--- Analyzing: " + table + " ---");
            ImpactAnalysisResult result = analyzer.analyzeTableImpact(table);

            System.out.printf("  Found %d impacted service methods%n", result.getImpacts().size());
            System.out.printf("  Unresolved references: %d%n", result.getUnresolvedMapperReferences().size());
            System.out.printf("  Warnings: %d%n", result.getWarnings().size());
        }
    }
}
