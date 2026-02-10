package v3.indexer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import v3.model.TableXmlMapping;
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
public class TableToXmlIndexer {

    private final MapperXmlLocator xmlLocator;
    private final MyBatisXmlParser xmlParser;
    private final SqlTableExtractor sqlExtractor;
    private static final int INDEX_WRITE_THRESHOLD = 1000;
    private static final String INDEX_FILE_PATH = "table_xml_mapping.json";
    private final Gson gson = new Gson();
    private final Logger logger = Logger.getLogger(TableToXmlIndexer.class.getName());

    static {
        Logger.getLogger(TableToXmlIndexer.class.getName()).setLevel(Level.SEVERE); // Hide debug/info messages by default
    }

    public TableToXmlIndexer() {
        this.xmlLocator = new MapperXmlLocator();
        this.xmlParser = new MyBatisXmlParser();
        this.sqlExtractor = new SqlTableExtractor();
    }

    /**
     * Builds a mapping from table names to mapper methods across all modules.
     *
     * @param modules list of Maven modules to index
     * @return map of table name -> list of mapper methods
     */
    public Map<String, List<TableXmlMapping>> buildTableToMapperIndex(List<MavenModule> modules) {
        // If the index file exists, load and return it
        java.io.File file = new java.io.File(INDEX_FILE_PATH);
        if (file.exists()) {
            Map<String, List<TableXmlMapping>> loaded = loadIndexFromDisk();
            if (!loaded.isEmpty()) {
                logger.log(Level.FINE, "Loaded table-mapper index from disk, skipping indexing.");
                // Rebuild cachedNamespaces from loaded index
                return loaded;
            }
        }
        // Otherwise, run indexing and save
        Map<String, List<TableXmlMapping>> index = new HashMap<>();
        int lastWriteSize = 0;
        for (MavenModule module : modules) {
            List<Path> mapperXmlFiles = new ArrayList<>(xmlLocator.findMapperXmlFiles(module.getRootPath()));
            logger.log(Level.FINE, "Found " + mapperXmlFiles.size() + " mapper XML files in module: " + module.getModuleName());
            for (Path xmlPath : mapperXmlFiles) {
                logger.log(Level.FINE, "Parsing mapper XML: " + xmlPath);
                List<TableXmlMapping> tableXmlMappings = xmlParser.parseMapperXml(
                    module.getModuleName(), xmlPath);
                for (TableXmlMapping tableXmlMapping : tableXmlMappings) {
                    Set<String> tables = sqlExtractor.extractTableNames(tableXmlMapping.getRawSql());
                    logger.log(Level.FINE, "Extracted tables: " + tables + " for method: " + tableXmlMapping.toString());
                    for (String table : tables) {
                        index.computeIfAbsent(table, k -> new ArrayList<>()).add(tableXmlMapping);
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

    private Map<String, List<TableXmlMapping>> loadIndexFromDisk() {
        try (FileReader reader = new FileReader(INDEX_FILE_PATH)) {
            logger.log(Level.FINE, "Loading table-mapper index from disk: " + INDEX_FILE_PATH);
            return gson.fromJson(reader, new TypeToken<Map<String, List<TableXmlMapping>>>(){}.getType());
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

    private void writeIndexToDisk(Map<String, List<TableXmlMapping>> index) {
        try (FileWriter writer = new FileWriter(INDEX_FILE_PATH)) {
            logger.log(Level.FINE, "Saving table-mapper index to disk: " + INDEX_FILE_PATH);
            gson.toJson(index, writer);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save table-mapper index to disk: " + e.getMessage());
        }
    }
}
