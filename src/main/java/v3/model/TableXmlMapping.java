package v3.model;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Represents a MyBatis mapper method with its SQL and metadata.
 */
public final class TableXmlMapping {
    private final String moduleName;
    private final String mapperXmlPath;
    private final String namespace;
    private final String statementId;
    private final String statementType; // select, insert, update, delete
    private final String rawSql;

    public TableXmlMapping(String moduleName, String mapperXmlPath, String namespace,
                           String statementId, String statementType, String rawSql) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName cannot be null");
        this.mapperXmlPath = Objects.requireNonNull(mapperXmlPath, "mapperXmlPath cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.statementId = Objects.requireNonNull(statementId, "statementId cannot be null");
        this.statementType = Objects.requireNonNull(statementType, "statementType cannot be null");
        this.rawSql = Objects.requireNonNull(rawSql, "rawSql cannot be null");
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getMapperXmlPath() {
        return mapperXmlPath;
    }

    public Path getMapperXmlPathAsPath() {
        return Paths.get(mapperXmlPath);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getStatementId() {
        return statementId;
    }

    public String getStatementType() {
        return statementType;
    }

    public String getRawSql() {
        return rawSql;
    }

    public String getFullyQualifiedId() {
        return namespace + "." + statementId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableXmlMapping that = (TableXmlMapping) o;
        return Objects.equals(namespace, that.namespace) &&
               Objects.equals(statementId, that.statementId) &&
               Objects.equals(mapperXmlPath, that.mapperXmlPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, statementId, mapperXmlPath);
    }

    @Override
    public String toString() {
        return "MapperMethod{" +
               "moduleName='" + moduleName + '\'' +
               ", fullyQualifiedId='" + getFullyQualifiedId() + '\'' +
               ", statementType='" + statementType + '\'' +
               '}';
    }
}
