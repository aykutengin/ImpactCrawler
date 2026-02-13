package v3.analyzer;

import v3.indexer.CallReference;
import v3.indexer.CalleeMethodIndexer;
import v3.indexer.TableToXmlIndexer;
import v3.model.*;
import v3.scanner.ModuleScanner;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Main impact analyzer that orchestrates the complete analysis pipeline.
 */
public class ImpactAnalyzer {

    private final ModuleScanner moduleScanner;
    private final TableToXmlIndexer tableIndexer;
    private final CalleeMethodIndexer referenceFinder;
    private final XmlToRepositoryMapper xmlRepoMapper;

    // Cached indices
    private Map<String, List<TableXmlMapping>> tableIndex;
    private Map<String, List<CallReference>> mapperToServiceIndex;
    private Set<String> mapperNamespaces;

    private static final Logger logger = Logger.getLogger(ImpactAnalyzer.class.getName());
    static {
        logger.setLevel(Level.SEVERE); // Hide info/debug messages by default
    }

    private List<TableRepositoryMapping> repoMappings;

    public ImpactAnalyzer() {
        this.moduleScanner = new ModuleScanner();
        this.tableIndexer = new TableToXmlIndexer();
        this.referenceFinder = new CalleeMethodIndexer();
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
        repoMappings = xmlRepoMapper.mapXmlToRepository(tableIndex, filteredModules);
        logger.log(Level.SEVERE, "Wrote " + repoMappings.size() + " table-repository mappings");

        logger.log(Level.SEVERE, "Building mapper -> service index...");

        mapperToServiceIndex = referenceFinder.findReferences(repoMappings, filteredModules);
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
        List<TableImpact> impacts = new ArrayList<>();
        List<CallChain> callChains = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> unresolvedRefs = new ArrayList<>();

        // Step 1: Find all repository methods that interact with this table
        List<String> repositoryMethods = findRepositoryMethodsForTable(tableName);

        if (repositoryMethods.isEmpty()) {
            warnings.add("No repository methods found for table: " + tableName);
            return new ImpactAnalysisResult(tableName, impacts, callChains, unresolvedRefs, warnings);
        }

        logger.log(Level.INFO, "Found " + repositoryMethods.size() + " repository methods for table: " + tableName);

        // Step 2: For each repository method, find all call chains (until service layer)
        for (String repoMethod : repositoryMethods) {
            List<CallChain> chains = findCallChainsToServiceLayer(repoMethod, tableName);
            callChains.addAll(chains);

            if (chains.isEmpty()) {
                warnings.add("No callers found for repository method: " + repoMethod);
            }
        }

        logger.log(Level.INFO, "Found " + callChains.size() + " call chains for table: " + tableName);

        return new ImpactAnalysisResult(tableName, impacts, callChains, unresolvedRefs, warnings);
    }

    /**
     * Finds all repository methods that interact with the given table.
     */
    private List<String> findRepositoryMethodsForTable(String tableName) {
        List<String> repositoryMethods = new ArrayList<>();

        for (TableRepositoryMapping mapping : repoMappings) {
            if (mapping.getTableName().equalsIgnoreCase(tableName)) {
                repositoryMethods.addAll(mapping.getRepositoryMethods());
            }
        }

        return repositoryMethods;
    }

    /**
     * Finds all call chains for a given method using BFS traversal.
     * Stops when reaching service layer (class name contains "Service").
     *
     * @param startMethod the method to start from (repository method)
     * @param tableName the table name for context
     * @return list of all call chains found
     */
    private List<CallChain> findCallChainsToServiceLayer(String startMethod, String tableName) {
        List<CallChain> allChains = new ArrayList<>();

        // BFS queue: each entry is (current method, path to reach it, line numbers, visited set)
        Queue<CallChainNode> queue = new LinkedList<>();
        queue.offer(new CallChainNode(startMethod, new ArrayList<>(), new ArrayList<>(), new HashSet<>()));

        while (!queue.isEmpty()) {
            CallChainNode current = queue.poll();

            // Find all methods that call the current method
            List<CallReference> callers = mapperToServiceIndex.getOrDefault(current.method, new ArrayList<>());

            if (callers.isEmpty()) {
                // Dead end - save the chain if we have any path
                if (!current.path.isEmpty()) {
                    CallChain chain = new CallChain(current.path, current.lineNumbers, startMethod, tableName);
                    allChains.add(chain);
                }
            } else {
                // Continue traversing
                for (CallReference caller : callers) {
                    String callerMethod = caller.getSourceMethod();
                    int lineNumber = caller.getLine(); // Get the actual line number from source code

                    // Avoid cycles - don't revisit methods already in this path
                    if (current.visited.contains(callerMethod) || callerMethod.equals(startMethod)) {
                        continue;
                    }

                    // Create new path with this caller
                    List<String> newPath = new ArrayList<>(current.path);
                    newPath.add(0, callerMethod); // Add at the beginning (top of call stack)

                    // Create new line numbers list
                    List<Integer> newLineNumbers = new ArrayList<>(current.lineNumbers);
                    newLineNumbers.add(0, lineNumber); // Add line number at the beginning

                    // Create new visited set
                    Set<String> newVisited = new HashSet<>(current.visited);
                    newVisited.add(callerMethod);

                    // Check if we reached service layer
                    if (isServiceLayer(callerMethod)) {
                        // We reached service layer - save this chain and don't traverse further
                        CallChain chain = new CallChain(newPath, newLineNumbers, startMethod, tableName);
                        allChains.add(chain);
                    } else {
                        // Not service layer yet - continue traversing
                        queue.offer(new CallChainNode(callerMethod, newPath, newLineNumbers, newVisited));
                    }
                }
            }
        }

        // If no chains found at all, create one with just the repository method
        if (allChains.isEmpty()) {
            allChains.add(new CallChain(new ArrayList<>(), new ArrayList<>(), startMethod, tableName));
        }

        return allChains;
    }

    /**
     * Checks if a method belongs to the service layer.
     * Service layer is identified by class name containing "Service", "Facade", or "Manager".
     *
     * @param methodFQCN fully qualified method name (ClassName.methodName)
     * @return true if it's a service layer method
     */
    private boolean isServiceLayer(String methodFQCN) {
        if (methodFQCN == null || methodFQCN.isEmpty()) {
            return false;
        }

        // Extract class name from FQCN
        int lastDot = methodFQCN.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }

        String className = methodFQCN.substring(0, lastDot);

        // Check for common service layer patterns
        return className.contains("Service") ||
               className.contains("Facade") ||
               className.contains("Manager") ||
               className.endsWith("BL") ||  // Business Logic
               className.endsWith("Logic");
    }

    /**
     * Checks if a method belongs to a DbCmd repository class.
     *
     * @param methodFQCN fully qualified method name (ClassName.methodName)
     * @return true if it's a DbCmd class
     */
    private boolean isDbCmdClass(String methodFQCN) {
        if (methodFQCN == null || methodFQCN.isEmpty()) {
            return false;
        }

        // Extract class name from FQCN
        int lastDot = methodFQCN.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }

        String className = methodFQCN.substring(0, lastDot);

        // Check for DbCmd pattern
        return className.contains("DbCmd") ||
               className.endsWith("Cmd") ||
               className.contains("Repository") ||
               className.endsWith("DAO") ||
               className.endsWith("Dao");
    }

    /**
     * Returns statistics about the indexed data.
     *
     * @return map of statistic names to values
     */
    public Map<String, Integer> getStatistics() {
        Map<String, Integer> stats = new LinkedHashMap<>();

        stats.put("Tables indexed", tableIndex != null ? tableIndex.size() : 0);
        stats.put("Repository mappings", repoMappings != null ? repoMappings.size() : 0);
        stats.put("Method references", mapperToServiceIndex != null ? mapperToServiceIndex.size() : 0);

        // Calculate total repository methods
        int totalRepoMethods = 0;
        if (repoMappings != null) {
            for (TableRepositoryMapping mapping : repoMappings) {
                totalRepoMethods += mapping.getRepositoryMethods().size();
            }
        }
        stats.put("Repository methods", totalRepoMethods);

        // Calculate total call references
        int totalCallRefs = 0;
        if (mapperToServiceIndex != null) {
            for (List<CallReference> refs : mapperToServiceIndex.values()) {
                totalCallRefs += refs.size();
            }
        }
        stats.put("Total call references", totalCallRefs);

        return stats;
    }

    /**
     * Helper class for BFS traversal
     */
    private static class CallChainNode {
        String method;
        List<String> path;
        List<Integer> lineNumbers; // Track line numbers for each method in path
        Set<String> visited; // Track visited methods to avoid cycles

        CallChainNode(String method, List<String> path, List<Integer> lineNumbers, Set<String> visited) {
            this.method = method;
            this.path = path;
            this.lineNumbers = lineNumbers;
            this.visited = visited;
        }
    }


}
