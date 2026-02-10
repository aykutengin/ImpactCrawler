package v3.indexer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import v3.model.MapperMethod;
import v3.model.MavenModule;
import v3.parser.MyBatisXmlParser;
import v3.parser.SqlTableExtractor;
import v3.scanner.MapperXmlLocator;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds an index mapping table names to mapper methods.
 */
public class TableToMapperIndexer {

    private final MapperXmlLocator xmlLocator;
    private final MyBatisXmlParser xmlParser;
    private final SqlTableExtractor sqlExtractor;
    private static final int INDEX_WRITE_THRESHOLD = 1000;
    private static final String INDEX_FILE_PATH = "table_mapper_index.json";
    private final Gson gson = new Gson();
    private final Logger logger = Logger.getLogger(TableToMapperIndexer.class.getName());

    public TableToMapperIndexer() {
        this.xmlLocator = new MapperXmlLocator();
        this.xmlParser = new MyBatisXmlParser();
        this.sqlExtractor = new SqlTableExtractor();
        logger.setLevel(Level.SEVERE); // Set default log level to debug
    }

    /**
     * Builds a mapping from table names to mapper methods across all modules.
     *
     * @param modules list of Maven modules to index
     * @return map of table name -> list of mapper methods
     */
    public Map<String, List<MapperMethod>> buildTableToMapperIndex(List<MavenModule> modules) {
        Map<String, List<MapperMethod>> index = loadIndexFromDisk();
        int lastWriteSize = index.size();

        for (MavenModule module : modules) {
            List<Path> mapperXmlFiles = new ArrayList<>();
            if (module.getRootPath() != null) {
                mapperXmlFiles.addAll(xmlLocator.findMapperXmlFiles(module.getRootPath()));
                logger.log(Level.FINE, "Found " + mapperXmlFiles.size() + " mapper XML files in module: " + module.getModuleName());
            }

            for (Path xmlPath : mapperXmlFiles) {
                logger.log(Level.FINE, "Parsing mapper XML: " + xmlPath);
                List<MapperMethod> mapperMethods = xmlParser.parseMapperXml(
                    module.getModuleName(), xmlPath);

                for (MapperMethod mapperMethod : mapperMethods) {
                    Set<String> tables = sqlExtractor.extractTableNames(mapperMethod.getRawSql());
                    logger.log(Level.FINE, "Extracted tables: " + tables + " for method: " + mapperMethod.toString());

                    for (String table : tables) {
                        index.computeIfAbsent(table, k -> new ArrayList<>()).add(mapperMethod);
                        // Write to disk if threshold exceeded
                        if (index.size() - lastWriteSize >= INDEX_WRITE_THRESHOLD) {
                            logger.log(Level.FINE, "Index size threshold exceeded. Writing index to disk.");
                            writeIndexToDisk(index);
                            lastWriteSize = index.size();
                        }
                    }
                }
            }
        }
        logger.log(Level.FINE, "Final write of index to disk.");
        writeIndexToDisk(index); // Final write
        return index;
    }

    private Map<String, List<MapperMethod>> loadIndexFromDisk() {
        try (FileReader reader = new FileReader(INDEX_FILE_PATH)) {
            logger.log(Level.FINE, "Loading table-mapper index from disk: " + INDEX_FILE_PATH);
            return gson.fromJson(reader, new TypeToken<Map<String, List<MapperMethod>>>(){}.getType());
        } catch (com.google.gson.JsonSyntaxException | java.io.EOFException e) {
            logger.log(Level.SEVERE, "Index file is empty or corrupted. Deleting: " + INDEX_FILE_PATH + " (" + e.getMessage() + ")");
            java.io.File file = new java.io.File(INDEX_FILE_PATH);
            if (file.exists()) file.delete();
            return new HashMap<>();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load table-mapper index from disk: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private void writeIndexToDisk(Map<String, List<MapperMethod>> index) {
        try (FileWriter writer = new FileWriter(INDEX_FILE_PATH)) {
            logger.log(Level.FINE, "Saving table-mapper index to disk: " + INDEX_FILE_PATH);
            gson.toJson(index, writer);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save table-mapper index to disk: " + e.getMessage());
        }
    }

    /**
     * Extracts all unique mapper namespaces from the given modules.
     * Useful for Java source analysis.
     *
     * @param modules list of Maven modules
     * @return set of mapper namespaces
     */
    public Set<String> extractMapperNamespaces(List<MavenModule> modules) {
        Set<String> namespaces = new HashSet<>();

        for (MavenModule module : modules) {
            // Collect all mapper XML files from both Java source and resource paths
            List<Path> mapperXmlFiles = new ArrayList<>();

            // Check Java source path (where iBatis XML files are typically located in package folders)
            if (module.getJavaSourcePath() != null) {
                mapperXmlFiles.addAll(xmlLocator.findMapperXmlFiles(module.getJavaSourcePath()));
            }

            // Also check resource path for standard MyBatis setup
            if (module.getResourcePath() != null) {
                mapperXmlFiles.addAll(xmlLocator.findMapperXmlFiles(module.getResourcePath()));
            }

            for (Path xmlPath : mapperXmlFiles) {
                List<MapperMethod> methods = xmlParser.parseMapperXml(module.getModuleName(), xmlPath);
                for (MapperMethod method : methods) {
                    namespaces.add(method.getNamespace());
                }
            }
        }

        return namespaces;
    }
}
