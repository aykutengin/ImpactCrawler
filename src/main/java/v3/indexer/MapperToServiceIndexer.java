package v3.indexer;

import v3.model.MavenModule;
import v3.model.ServiceMethod;
import v3.parser.JavaSourceAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Builds an index mapping mapper methods to service methods.
 */
public class MapperToServiceIndexer {

    private final JavaSourceAnalyzer javaAnalyzer;

    public MapperToServiceIndexer() {
        this.javaAnalyzer = new JavaSourceAnalyzer();
    }

    /**
     * Builds a mapping from mapper method references to service methods.
     *
     * @param modules list of Maven modules to analyze
     * @param mapperNamespaces set of known mapper namespaces
     * @return map of mapper method reference -> list of service methods
     */
    public Map<String, List<ServiceMethod>> buildMapperToServiceIndex(
            List<MavenModule> modules, Set<String> mapperNamespaces) {

        Map<String, List<ServiceMethod>> index = new HashMap<>();

        for (MavenModule module : modules) {
            // Find all Java files in the module
            List<Path> javaFiles = findJavaFiles(module.getRootPath());

            for (Path javaFile : javaFiles) {
                // Analyze each Java file
                List<ServiceMethod> serviceMethods = javaAnalyzer.analyzeJavaFile(
                    module.getModuleName(), javaFile, mapperNamespaces);

                // Index by mapper method reference
                for (ServiceMethod serviceMethod : serviceMethods) {
                    String mapperRef = serviceMethod.getMapperMethodReference();
                    index.computeIfAbsent(mapperRef, k -> new ArrayList<>()).add(serviceMethod);
                }
            }
        }

        return index;
    }

    private List<Path> findJavaFiles(Path sourcePath) {
        List<Path> javaFiles = new ArrayList<>();

        if (!Files.exists(sourcePath)) {
            return javaFiles;
        }

        try (Stream<Path> paths = Files.walk(sourcePath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(javaFiles::add);
        } catch (IOException e) {
            System.err.println("Error finding Java files: " + e.getMessage());
        }

        return javaFiles;
    }
}
