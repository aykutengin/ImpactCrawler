package v3.model;

import java.util.Objects;

/**
 * Represents the complete impact chain from table to service method.
 */
public final class TableImpact {
    private final String moduleName;
    private final String mapperXmlFile;
    private final String mapperNamespace;
    private final String mapperMethodId;
    private final String serviceClass;
    private final String serviceMethod;
    private final String soapEndpoint; // optional

    public TableImpact(String moduleName, String mapperXmlFile, String mapperNamespace,
                      String mapperMethodId, String serviceClass, String serviceMethod,
                      String soapEndpoint) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName cannot be null");
        this.mapperXmlFile = Objects.requireNonNull(mapperXmlFile, "mapperXmlFile cannot be null");
        this.mapperNamespace = Objects.requireNonNull(mapperNamespace, "mapperNamespace cannot be null");
        this.mapperMethodId = Objects.requireNonNull(mapperMethodId, "mapperMethodId cannot be null");
        this.serviceClass = Objects.requireNonNull(serviceClass, "serviceClass cannot be null");
        this.serviceMethod = Objects.requireNonNull(serviceMethod, "serviceMethod cannot be null");
        this.soapEndpoint = soapEndpoint; // can be null
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getMapperXmlFile() {
        return mapperXmlFile;
    }

    public String getMapperNamespace() {
        return mapperNamespace;
    }

    public String getMapperMethodId() {
        return mapperMethodId;
    }

    public String getServiceClass() {
        return serviceClass;
    }

    public String getServiceMethod() {
        return serviceMethod;
    }

    public String getSoapEndpoint() {
        return soapEndpoint;
    }

    public String getFullyQualifiedMapperMethod() {
        return mapperNamespace + "." + mapperMethodId;
    }

    public String getFullyQualifiedServiceMethod() {
        return serviceClass + "." + serviceMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TableImpact that = (TableImpact) o;
        return Objects.equals(moduleName, that.moduleName) &&
               Objects.equals(mapperNamespace, that.mapperNamespace) &&
               Objects.equals(mapperMethodId, that.mapperMethodId) &&
               Objects.equals(serviceClass, that.serviceClass) &&
               Objects.equals(serviceMethod, that.serviceMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, mapperNamespace, mapperMethodId, serviceClass, serviceMethod);
    }

    @Override
    public String toString() {
        return "TableImpact{" +
               "module='" + moduleName + '\'' +
               ", mapper='" + getFullyQualifiedMapperMethod() + '\'' +
               ", service='" + getFullyQualifiedServiceMethod() + '\'' +
               '}';
    }
}
