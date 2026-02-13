package v3.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a service-layer method that uses a mapper method.
 */
public final class ServiceMethod {
    private final String moduleName;
    private final Path serviceFilePath;
    private final String serviceClassName;
    private final String methodName;
    private final String mapperMethodReference; // namespace.id

    public ServiceMethod(String moduleName, Path serviceFilePath, String serviceClassName,
                        String methodName, String mapperMethodReference) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName cannot be null");
        this.serviceFilePath = Objects.requireNonNull(serviceFilePath, "serviceFilePath cannot be null");
        this.serviceClassName = Objects.requireNonNull(serviceClassName, "serviceClassName cannot be null");
        this.methodName = Objects.requireNonNull(methodName, "methodName cannot be null");
        this.mapperMethodReference = Objects.requireNonNull(mapperMethodReference, "mapperMethodReference cannot be null");
    }

    public String getModuleName() {
        return moduleName;
    }

    public Path getServiceFilePath() {
        return serviceFilePath;
    }

    public String getServiceClassName() {
        return serviceClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMapperMethodReference() {
        return mapperMethodReference;
    }

    public String getFullyQualifiedMethodName() {
        return serviceClassName + "." + methodName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceMethod that = (ServiceMethod) o;
        return Objects.equals(serviceClassName, that.serviceClassName) &&
               Objects.equals(methodName, that.methodName) &&
               Objects.equals(mapperMethodReference, that.mapperMethodReference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceClassName, methodName, mapperMethodReference);
    }

    @Override
    public String toString() {
        return "ServiceMethod{" +
               "moduleName='" + moduleName + '\'' +
               ", serviceClass='" + serviceClassName + '\'' +
               ", method='" + methodName + '\'' +
               ", mapperReference='" + mapperMethodReference + '\'' +
               '}';
    }
}
