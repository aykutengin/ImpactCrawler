package v3.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Locates MyBatis/iBatis mapper XML files within Maven modules.
 * Supports finding XML files in both resources folder and Java source folders.
 */
public class MapperXmlLocator {
    private static final Logger logger = Logger.getLogger(MapperXmlLocator.class.getName());
    static {
        logger.setLevel(Level.SEVERE); // Hide info/debug messages by default
    }

    /**
     * Finds all MyBatis/iBatis mapper XML files in the given directory.
     * Looks for files typically containing "mapper" in their name or path.
     * This method can scan both resource directories and Java source directories.
     *
     * @param searchPath the directory to scan (can be resources or Java source path)
     * @return list of paths to mapper XML files
     */
    public List<Path> findMapperXmlFiles(Path searchPath) {
        List<Path> mapperFiles = new ArrayList<>();

        if (searchPath == null || !Files.exists(searchPath)) {
            logger.log(Level.WARNING, "MapperXmlLocator: Search path does not exist or is null: " + searchPath);
            return mapperFiles;
        }

        logger.log(Level.FINE, "MapperXmlLocator: Scanning for XML files in: " + searchPath);

        try {
            findMapperXmlFilesRecursively(searchPath, mapperFiles);
            logger.log(Level.FINE, "MapperXmlLocator: Found " + mapperFiles.size() + " mapper XML file(s)");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error scanning mapper XML files in " + searchPath + ": " + e.getMessage());
        }

        return mapperFiles;
    }

    private void findMapperXmlFilesRecursively(Path currentPath, List<Path> mapperFiles) throws IOException {
        if (!Files.exists(currentPath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(currentPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> {
                     boolean isXml = p.toString().toLowerCase().endsWith(".xml");
                     if (isXml) {
                         logger.log(Level.FINE, "  Found XML file: " + p);
                     }
                     return isXml;
                 })
                 .filter(p -> {
                     boolean isMapper = isLikelyMapperFile(p);
                     if (!isMapper) {
                         logger.log(Level.FINE, "  Skipping non-mapper XML: " + p);
                     }
                     return isMapper;
                 })
                 .forEach(mapperFiles::add);
        }
    }

    private boolean isLikelyMapperFile(Path xmlFile) {
        String pathStr = xmlFile.toString().toLowerCase();
        String fileName = xmlFile.getFileName().toString().toLowerCase();

        // Exclude common non-mapper XML files
        return pathStr.contains("\\src\\com\\");

        // For iBatis/MyBatis, include all XML files that aren't explicitly excluded
        // This is more inclusive as iBatis files may not have "mapper" in the name
    }
}
