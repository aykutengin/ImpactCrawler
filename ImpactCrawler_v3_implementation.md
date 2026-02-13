# ImpactCrawler V3 Implementation Plan

## Completed Tasks

### Core Infrastructure
- [x] Maven module scanning and filtering
- [x] Properties file configuration (impactcrawler.properties)
- [x] Logging with configurable levels (default: SEVERE)

### Table-to-XML Mapping
- [x] Table-to-XML mapping index (MyBatis/iBatis XMLs)
- [x] SQL table extraction from XML files
- [x] Incremental file writing for table index (table_mapper_index.json)
- [x] Disk-based caching for table index

### Repository Mapping
- [x] Repository class mapping for each XML (DbCmd classes)
- [x] Canonical repository method mapping (RepoClass.methodName → FQCN.methodName)
- [x] Robust handling for missing Java files ([N/A]-ClassName)
- [x] Robust handling for missing methods ([N/A]-ClassName.methodName)
- [x] Incremental writing of table-repository mapping (every 1000 entries)
- [x] Output: table_repo_mapping.json

### Call Reference Indexing
- [x] Service method reference indexing (CalleeMethodIndexer)
- [x] JavaParser integration for method call extraction
- [x] FQCN resolution for method calls
- [x] Incremental writing of call references (every 100 entries)
- [x] Disk-based caching for call references
- [x] Output: call_expression_cache_incremental.jsonl

### Impact Analysis
- [x] BFS traversal algorithm for call chains (3 levels deep)
- [x] Multiple call path tracking (Service → Service → Repository → Table)
- [x] Cycle detection in call chains
- [x] Table impact analysis (analyzeTableImpact)
- [x] CallChain model for representing call paths
- [x] Text reporter with call chain visualization
- [x] JSON reporter for structured output

## In Progress / To Do

### Enhancements
- [ ] Optimize JavaParser caching for better performance
- [ ] Add configuration for max depth of call chain traversal
- [ ] Implement filtering for frequently-used utility methods (toString, etc.)
- [ ] Add support for Spring annotations (@Service, @Repository, @Autowired)
- [ ] Enhance SQL parsing for complex Oracle-specific syntax
- [ ] Add support for stored procedure impact analysis

### Reporting
- [ ] HTML report generation
- [ ] Graph visualization of call chains
- [ ] Statistics dashboard
- [ ] Export to CSV for Excel analysis

### Validation & Testing
- [ ] Unit tests for core components
- [ ] Integration tests for end-to-end flow
- [ ] Performance benchmarking on large codebases
