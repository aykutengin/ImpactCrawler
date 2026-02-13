package v3.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a call chain from a service method down to a repository method.
 * Example: ServiceA.method1() -> ServiceB.method2() -> RepositoryC.method3()
 */
public final class CallChain {
    private final List<String> callPath; // Ordered list from top (service) to bottom (repository)
    private final List<Integer> lineNumbers; // Line numbers corresponding to each call in callPath
    private final String repositoryMethod;
    private final String tableName;

    public CallChain(List<String> callPath, List<Integer> lineNumbers, String repositoryMethod, String tableName) {
        this.callPath = Collections.unmodifiableList(new ArrayList<>(callPath));
        this.lineNumbers = Collections.unmodifiableList(new ArrayList<>(lineNumbers));
        this.repositoryMethod = Objects.requireNonNull(repositoryMethod);
        this.tableName = Objects.requireNonNull(tableName);
    }

    public List<String> getCallPath() {
        return callPath;
    }

    public List<Integer> getLineNumbers() {
        return lineNumbers;
    }

    public String getRepositoryMethod() {
        return repositoryMethod;
    }

    public String getTableName() {
        return tableName;
    }

    public int getDepth() {
        return callPath.size();
    }

    /**
     * Gets the entry point (top-level caller) of this chain
     */
    public String getEntryPoint() {
        return callPath.isEmpty() ? repositoryMethod : callPath.get(0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < callPath.size(); i++) {
            sb.append(callPath.get(i));
            if (i < lineNumbers.size()) {
                sb.append(" [line ").append(lineNumbers.get(i)).append("]");
            }
            sb.append(" -> ");
        }
        sb.append(repositoryMethod).append(" [Table: ").append(tableName).append("]");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallChain callChain = (CallChain) o;
        return Objects.equals(callPath, callChain.callPath) &&
               Objects.equals(repositoryMethod, callChain.repositoryMethod) &&
               Objects.equals(tableName, callChain.tableName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callPath, repositoryMethod, tableName);
    }
}

