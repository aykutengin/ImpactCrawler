package v3.analyzer;

import v3.indexer.RepositoryReferenceFinder;
import v3.indexer.TableToXmlIndexer;
import v3.model.*;
import v3.scanner.ModuleScanner;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main impact analyzer that orchestrates the complete analysis pipeline.
 */
public class ImpactAnalyzer {

    private final ModuleScanner moduleScanner;
    private final TableToXmlIndexer tableIndexer;
    private final RepositoryReferenceFinder referenceFinder;
    private final XmlToRepositoryMapper xmlRepoMapper;

    // Cached indices
    private Map<String, List<TableXmlMapping>> tableIndex;
    private Map<String, List<ServiceMethod>> mapperToServiceIndex;
    private Set<String> mapperNamespaces;

    private static final Logger logger = Logger.getLogger(ImpactAnalyzer.class.getName());
    static {
        logger.setLevel(Level.SEVERE); // Hide info/debug messages by default
    }

    public ImpactAnalyzer() {
        this.moduleScanner = new ModuleScanner();
        this.tableIndexer = new TableToXmlIndexer();
        this.referenceFinder = new RepositoryReferenceFinder();
        this.xmlRepoMapper = new XmlToRepositoryMapper();
    }

    /**
     * Initializes the analyzer by scanning modules and building indices.
     * This should be called once before performing analysis.
     *
     * @param monolithRootPath root path of the Java monolith
     */
    public void initialize(Path monolithRootPath) {
        logger.log(Level.SEVERE, "Scanning Maven modules...");
        List<MavenModule> modules = moduleScanner.scanModules(monolithRootPath);
        logger.log(Level.SEVERE, "Found " + modules.size() + " Maven modules");

        // Filter out the base module (root path equals monolithRootPath)
        List<MavenModule> filteredModules = new ArrayList<>();
        for (MavenModule module : modules) {
            if (!module.getRootPath().equals(monolithRootPath)) {
                filteredModules.add(module);
            }
        }

        logger.log(Level.SEVERE, "Building table -> mapper index...");
        tableIndex = tableIndexer.buildTableToMapperIndex(filteredModules);
        logger.log(Level.SEVERE, "Indexed " + tableIndex.size() + " tables");

        logger.log(Level.SEVERE, "Building table -> repository mapping...");
        List<TableRepositoryMapping> repoMappings = xmlRepoMapper.mapXmlToRepository(tableIndex, filteredModules);
        logger.log(Level.SEVERE, "Wrote " + repoMappings.size() + " table-repository mappings");

        logger.log(Level.SEVERE, "Building mapper -> service index...");
        mapperToServiceIndex = referenceFinder.findReferences(repoMappings);
        logger.log(Level.SEVERE, "Indexed " + mapperToServiceIndex.size() + " mapper method references");

        logger.log(Level.SEVERE, "Initialization complete!");
    }

    /**
     * Analyzes the impact of a database table change.
     *
     * @param tableName the database table name to analyze
     * @return complete impact analysis result
     */
    public ImpactAnalysisResult analyzeTableImpact(String tableName) {
        if (tableIndex == null) {
            throw new IllegalStateException("Analyzer not initialized. Call initialize() first.");
        }

        String normalizedTable = tableName.toUpperCase();
        List<TableImpact> impacts = new ArrayList<>();
        List<String> unresolvedReferences = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Step 1: Find mapper methods that use this table
        List<TableXmlMapping> tableXmlMappings = tableIndex.getOrDefault(normalizedTable, new ArrayList<>());

        if (tableXmlMappings.isEmpty()) {
            warnings.add("No mapper methods found for table: " + tableName);
            return new ImpactAnalysisResult(tableName, impacts, unresolvedReferences, warnings);
        }

        // Step 2: For each mapper method, find service methods that call it
        for (TableXmlMapping tableXmlMapping : tableXmlMappings) {
            String mapperRef = tableXmlMapping.getFullyQualifiedId();
            List<ServiceMethod> serviceMethods = mapperToServiceIndex.getOrDefault(mapperRef, new ArrayList<>());

            if (serviceMethods.isEmpty()) {
                unresolvedReferences.add(mapperRef);
            } else {
                for (ServiceMethod serviceMethod : serviceMethods) {
                    TableImpact impact = new TableImpact(
                        serviceMethod.getModuleName(),
                        Paths.get(tableXmlMapping.getMapperXmlPath()).getFileName().toString(),
                        tableXmlMapping.getNamespace(),
                        tableXmlMapping.getStatementId(),
                        serviceMethod.getServiceClassName(),
                        serviceMethod.getMethodName(),
                        null // SOAP endpoint detection not implemented yet
                    );
                    impacts.add(impact);
                }
            }
        }

        return new ImpactAnalysisResult(tableName, impacts, unresolvedReferences, warnings);
    }

    /**
     * Gets statistics about the indexed data.
     *
     * @return map of statistic name to value
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new HashMap<>();

        if (tableIndex != null) {
            stats.put("Tables", tableIndex.size());
            int totalMapperMethods = tableIndex.values().stream()
                .mapToInt(List::size)
                .sum();
            stats.put("MapperMethods", totalMapperMethods);
        }

        if (mapperToServiceIndex != null) {
            stats.put("MapperReferences", mapperToServiceIndex.size());
            int totalServiceMethods = mapperToServiceIndex.values().stream()
                .mapToInt(List::size)
                .sum();
            stats.put("ServiceMethods", totalServiceMethods);
        }

        return stats;
    }
}
