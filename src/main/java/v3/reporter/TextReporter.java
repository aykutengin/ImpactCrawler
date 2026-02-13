package v3.reporter;

import v3.model.CallChain;
import v3.model.ImpactAnalysisResult;
import v3.model.TableImpact;

import java.util.List;

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
        report.append("  Total Call Chains: ").append(result.getCallChains().size()).append("\n");
        report.append("  Unresolved Mapper References: ")
              .append(result.getUnresolvedMapperReferences().size()).append("\n");
        report.append("  Warnings: ").append(result.getWarnings().size()).append("\n\n");

        // Call Chains
        if (!result.getCallChains().isEmpty()) {
            report.append("CALL CHAINS (Service → Repository → Table):\n");
            report.append("-".repeat(80)).append("\n");

            int count = 1;
            for (CallChain chain : result.getCallChains()) {
                report.append(String.format("%d. ", count++));

                if (chain.getCallPath().isEmpty()) {
                    // Direct repository access (no callers found)
                    report.append(chain.getRepositoryMethod());
                    report.append("\n   → [Table: ").append(chain.getTableName()).append("]");
                } else {
                    // Full call chain with actual source code line numbers
                    List<String> path = chain.getCallPath();
                    List<Integer> lineNumbers = chain.getLineNumbers();

                    for (int i = 0; i < path.size(); i++) {
                        report.append(path.get(i));
                        if (i < lineNumbers.size() && lineNumbers.get(i) != null && lineNumbers.get(i) > 0) {
                            report.append(" [line:").append(lineNumbers.get(i)).append("]");
                        }
                        report.append("\n   → ");
                    }
                    report.append(chain.getRepositoryMethod());
                    report.append("\n   → [Table: ").append(chain.getTableName()).append("]");
                }
                report.append("\n\n");
            }
        }

        // Impacts (legacy)
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
