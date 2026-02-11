package v3.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Complete impact analysis result for a table.
 */
public final class ImpactAnalysisResult {
    private final String tableName;
    private final List<TableImpact> impacts;
    private final List<String> unresolvedMapperReferences;
    private final List<String> warnings;

    public ImpactAnalysisResult(String tableName, List<TableImpact> impacts,
                               List<String> unresolvedMapperReferences, List<String> warnings) {
        this.tableName = Objects.requireNonNull(tableName, "tableName cannot be null");
        this.impacts = Collections.unmodifiableList(Objects.requireNonNull(impacts));
        this.unresolvedMapperReferences = Collections.unmodifiableList(
            Objects.requireNonNull(unresolvedMapperReferences));
        this.warnings = Collections.unmodifiableList(Objects.requireNonNull(warnings));
    }

    public String getTableName() {
        return tableName;
    }

    public List<TableImpact> getImpacts() {
        return impacts;
    }

    public List<String> getUnresolvedMapperReferences() {
        return unresolvedMapperReferences;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    @Override
    public String toString() {
        return "ImpactAnalysisResult{" +
               "tableName='" + tableName + '\'' +
               ", impactCount=" + impacts.size() +
               ", unresolvedCount=" + unresolvedMapperReferences.size() +
               ", warningCount=" + warnings.size() +
               '}';
    }
}
