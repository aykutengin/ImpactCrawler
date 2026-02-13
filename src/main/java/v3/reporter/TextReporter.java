package v3.reporter;

import v3.model.ImpactAnalysisResult;
import v3.model.TableImpact;

/**
 * Generates human-readable text reports from impact analysis results.
 */
public class TextReporter {

    /**
     * Generates a human-readable summary report.
     *
     * @param result the analysis result
     * @return formatted text report
     */
    public String generateReport(ImpactAnalysisResult result) {
        StringBuilder report = new StringBuilder();

        report.append("=".repeat(80)).append("\n");
        report.append("IMPACT ANALYSIS REPORT\n");
        report.append("=".repeat(80)).append("\n");
        report.append("Table: ").append(result.getTableName()).append("\n");
        report.append("-".repeat(80)).append("\n\n");

        // Summary
        report.append("SUMMARY:\n");
        report.append("  Total Impacts: ").append(result.getImpacts().size()).append("\n");
        report.append("  Unresolved Mapper References: ")
              .append(result.getUnresolvedMapperReferences().size()).append("\n");
        report.append("  Warnings: ").append(result.getWarnings().size()).append("\n\n");

        // Impacts
        if (!result.getImpacts().isEmpty()) {
            report.append("IMPACTED SERVICE METHODS:\n");
            report.append("-".repeat(80)).append("\n");

            int count = 1;
            for (TableImpact impact : result.getImpacts()) {
                report.append(String.format("%d. Module: %s\n", count++, impact.getModuleName()));
                report.append(String.format("   Mapper XML: %s\n", impact.getMapperXmlFile()));
                report.append(String.format("   Mapper Method: %s\n",
                    impact.getFullyQualifiedMapperMethod()));
                report.append(String.format("   Service Method: %s\n",
                    impact.getFullyQualifiedServiceMethod()));
                if (impact.getSoapEndpoint() != null) {
                    report.append(String.format("   SOAP Endpoint: %s\n", impact.getSoapEndpoint()));
                }
                report.append("\n");
            }
        }

        // Unresolved references
        if (!result.getUnresolvedMapperReferences().isEmpty()) {
            report.append("UNRESOLVED MAPPER REFERENCES:\n");
            report.append("-".repeat(80)).append("\n");
            for (String ref : result.getUnresolvedMapperReferences()) {
                report.append("  - ").append(ref).append("\n");
            }
            report.append("\n");
        }

        // Warnings
        if (!result.getWarnings().isEmpty()) {
            report.append("WARNINGS:\n");
            report.append("-".repeat(80)).append("\n");
            for (String warning : result.getWarnings()) {
                report.append("  - ").append(warning).append("\n");
            }
            report.append("\n");
        }

        report.append("=".repeat(80)).append("\n");

        return report.toString();
    }
}
