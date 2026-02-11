package v3.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a Maven module with its source paths.
 */
public final class MavenModule {
    private final String moduleName;
    private final Path rootPath;
    private final Path javaSourcePath;
    private final Path resourcePath;

    public MavenModule(String moduleName, Path rootPath, Path javaSourcePath, Path resourcePath) {
        this.moduleName = Objects.requireNonNull(moduleName, "moduleName cannot be null");
        this.rootPath = Objects.requireNonNull(rootPath, "rootPath cannot be null");
        this.javaSourcePath = javaSourcePath;
        this.resourcePath = resourcePath;
    }

    public String getModuleName() {
        return moduleName;
    }

    public Path getRootPath() {
        return rootPath;
    }

    public Path getJavaSourcePath() {
        return javaSourcePath;
    }

    public Path getResourcePath() {
        return resourcePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenModule that = (MavenModule) o;
        return Objects.equals(moduleName, that.moduleName) &&
               Objects.equals(rootPath, that.rootPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(moduleName, rootPath);
    }

    @Override
    public String toString() {
        return "MavenModule{" +
               "moduleName='" + moduleName + '\'' +
               ", rootPath=" + rootPath +
               '}';
    }
}
