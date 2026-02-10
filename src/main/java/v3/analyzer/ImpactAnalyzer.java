package v3.analyzer;

import v3.indexer.MapperToServiceIndexer;
import v3.indexer.TableToMapperIndexer;
import v3.model.*;
import v3.scanner.ModuleScanner;

import java.nio.file.Path;
import java.util.*;

/**
 * Main impact analyzer that orchestrates the complete analysis pipeline.
 */
public class ImpactAnalyzer {

    private final ModuleScanner moduleScanner;
    private final TableToMapperIndexer tableIndexer;
    private final MapperToServiceIndexer serviceIndexer;

    // Cached indices
    private Map<String, List<MapperMethod>> tableToMapperIndex;
    private Map<String, List<ServiceMethod>> mapperToServiceIndex;
    private Set<String> mapperNamespaces;

    public ImpactAnalyzer() {
        this.moduleScanner = new ModuleScanner();
        this.tableIndexer = new TableToMapperIndexer();
        this.serviceIndexer = new MapperToServiceIndexer();
    }

    /**
     * Initializes the analyzer by scanning modules and building indices.
     * This should be called once before performing analysis.
     *
     * @param monolithRootPath root path of the Java monolith
     */
    public void initialize(Path monolithRootPath) {
        System.out.println("Scanning Maven modules...");
        List<MavenModule> modules = moduleScanner.scanModules(monolithRootPath);
        System.out.println("Found " + modules.size() + " Maven modules");

        System.out.println("Building table -> mapper index...");
        tableToMapperIndex = tableIndexer.buildTableToMapperIndex(modules);
        System.out.println("Indexed " + tableToMapperIndex.size() + " tables");

        System.out.println("Extracting mapper namespaces...");
        mapperNamespaces = tableIndexer.extractMapperNamespaces(modules);
        System.out.println("Found " + mapperNamespaces.size() + " mapper namespaces");

        System.out.println("Building mapper -> service index...");
        mapperToServiceIndex = serviceIndexer.buildMapperToServiceIndex(modules, mapperNamespaces);
        System.out.println("Indexed " + mapperToServiceIndex.size() + " mapper method references");

        System.out.println("Initialization complete!");
    }

    /**
     * Analyzes the impact of a database table change.
     *
     * @param tableName the database table name to analyze
     * @return complete impact analysis result
     */
    public ImpactAnalysisResult analyzeTableImpact(String tableName) {
        if (tableToMapperIndex == null) {
            throw new IllegalStateException("Analyzer not initialized. Call initialize() first.");
        }

        String normalizedTable = tableName.toUpperCase();
        List<TableImpact> impacts = new ArrayList<>();
        List<String> unresolvedReferences = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Step 1: Find mapper methods that use this table
        List<MapperMethod> mapperMethods = tableToMapperIndex.getOrDefault(normalizedTable, new ArrayList<>());

        if (mapperMethods.isEmpty()) {
            warnings.add("No mapper methods found for table: " + tableName);
            return new ImpactAnalysisResult(tableName, impacts, unresolvedReferences, warnings);
        }

        // Step 2: For each mapper method, find service methods that call it
        for (MapperMethod mapperMethod : mapperMethods) {
            String mapperRef = mapperMethod.getFullyQualifiedId();
            List<ServiceMethod> serviceMethods = mapperToServiceIndex.getOrDefault(mapperRef, new ArrayList<>());

            if (serviceMethods.isEmpty()) {
                unresolvedReferences.add(mapperRef);
            } else {
                for (ServiceMethod serviceMethod : serviceMethods) {
                    TableImpact impact = new TableImpact(
                        serviceMethod.getModuleName(),
                        mapperMethod.getMapperXmlPath().getFileName().toString(),
                        mapperMethod.getNamespace(),
                        mapperMethod.getStatementId(),
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

        if (tableToMapperIndex != null) {
            stats.put("Tables", tableToMapperIndex.size());
            int totalMapperMethods = tableToMapperIndex.values().stream()
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

        if (mapperNamespaces != null) {
            stats.put("MapperNamespaces", mapperNamespaces.size());
        }

        return stats;
    }
}
