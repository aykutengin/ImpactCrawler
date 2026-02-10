package v3.model;

import java.util.List;
import java.util.Objects;

/**
 * Maps a database table to its XML(s) and repository class/method(s).
 */
public class TableRepositoryMapping {
    private final String tableName;
    private final List<String> xmlFiles;
    private final List<String> repositoryClasses;
    private final List<String> repositoryMethods;

    public TableRepositoryMapping(String tableName, List<String> xmlFiles, List<String> repositoryClasses, List<String> repositoryMethods) {
        this.tableName = tableName;
        this.xmlFiles = xmlFiles;
        this.repositoryClasses = repositoryClasses;
        this.repositoryMethods = repositoryMethods;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getXmlFiles() {
        return xmlFiles;
    }

    public List<String> getRepositoryClasses() {
        return repositoryClasses;
    }

    public List<String> getRepositoryMethods() {
        return repositoryMethods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableRepositoryMapping that = (TableRepositoryMapping) o;
        return Objects.equals(tableName, that.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableName);
    }

    @Override
    public String toString() {
        return "TableRepositoryMapping{" +
                "tableName='" + tableName + '\'' +
                ", xmlFiles=" + xmlFiles +
                ", repositoryClasses=" + repositoryClasses +
                ", repositoryMethods=" + repositoryMethods +
                '}';
    }
}

