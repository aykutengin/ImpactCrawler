package v3.scanner;

import v3.model.MavenModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Scans a directory structure to discover Maven modules.
 */
public class ModuleScanner {
    private static final Logger logger = Logger.getLogger(ModuleScanner.class.getName());
    static {
        logger.setLevel(Level.SEVERE); // Hide info/debug messages by default
    }

    /**
     * Scans the given root path for Maven modules (directories containing pom.xml).
     *
     * @param rootPath the root directory to scan
     * @return list of discovered Maven modules
     */
    public List<MavenModule> scanModules(Path rootPath) {
        List<MavenModule> modules = new ArrayList<>();

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return modules;
        }

        try {
            findModulesRecursively(rootPath, modules);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error scanning modules: " + e.getMessage());
        }

        return modules;
    }

    private void findModulesRecursively(Path currentPath, List<MavenModule> modules) throws IOException {
        Path pomFile = currentPath.resolve("pom.xml");

        if (Files.exists(pomFile)) {
            // Found a Maven module
            String moduleName = currentPath.getFileName().toString();
            Path javaPath = currentPath.resolve("src/");
            Path resourcePath = currentPath.resolve("src/resources");

            MavenModule module = new MavenModule(
                moduleName,
                currentPath,
                Files.exists(javaPath) ? javaPath : null,
                Files.exists(resourcePath) ? resourcePath : null
            );

            modules.add(module);
        }

        // Continue searching in subdirectories
        try (Stream<Path> paths = Files.list(currentPath)) {
            paths.filter(Files::isDirectory)
                 .forEach(subDir -> {
                     try {
                         findModulesRecursively(subDir, modules);
                     } catch (IOException e) {
                         logger.log(Level.SEVERE, "Error scanning directory " + subDir + ": " + e.getMessage());
                     }
                 });
        }
    }
}
