package v3.analyzer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.google.gson.Gson;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import v3.model.TableRepositoryMapping;
import v3.model.TableXmlMapping;
import v3.model.MavenModule;

/**
 * Refactored XmlToRepositoryMapper to use already indexed MapperMethod objects from tableIndex and use JavaParser for method extraction.
 */
public class XmlToRepositoryMapper {

    private static final Logger logger = Logger.getLogger(XmlToRepositoryMapper.class.getName());
    private final JavaParser javaParser = new JavaParser();
    private static final String TABLE_REPO_MAPPING_FILE = "table_repo_mapping.json";
    private final Gson gson = new Gson();

    /**
     * For each table, map XMLs and repository classes/methods.
     * Returns a list of TableRepositoryMapping.
     */
    public List<TableRepositoryMapping> mapXmlToRepository(Map<String, List<TableXmlMapping>> tableIndex, List<MavenModule> modules) {
        List<TableRepositoryMapping> mappings = new ArrayList<>();
        Map<String, Set<String>> dbCmdClassToMethods = new HashMap<>();
        int batchSize = 1000;
        int count = 0;
        for (Map.Entry<String, List<TableXmlMapping>> entry : tableIndex.entrySet()) {
            String tableName = entry.getKey();
            List<TableXmlMapping> methods = entry.getValue();
            Set<String> xmlFiles = new HashSet<>();
            Set<String> repoClasses = new HashSet<>();
            List<String> repoMethods = new ArrayList<>();
            for (TableXmlMapping m : methods) {
                xmlFiles.add(m.getMapperXmlPath());
                Path xmlPath = Path.of(m.getMapperXmlPath());
                String xmlFileName = xmlPath.getFileName().toString();
                String baseName = xmlFileName.replaceFirst("\\.xml$", "");
                String repoClass = baseName;
                Path parentDir = xmlPath.getParent() != null ? xmlPath.getParent().getParent() : null;
                if (parentDir == null) {
                    repoMethods.add("[N/A]-" + repoClass);
                    continue;
                }
                Path dbCmdPath = parentDir.resolve(repoClass + ".java");
                if (!Files.exists(dbCmdPath)) {
                    repoMethods.add("[N/A]-" + repoClass);
                    continue;
                }
                repoClasses.add(repoClass);
                Set<String> repoClassMethods = dbCmdClassToMethods.computeIfAbsent(dbCmdPath.toString(), k -> parseJavaMethods(dbCmdPath));
                boolean found = false;
                for (String repoMethod : repoClassMethods) {
                    if (repoMethod.equals(m.getStatementId())) {
                        repoMethods.add(repoClass + "." + repoMethod);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    repoMethods.add("[N/A]-" + repoClass + "." + m.getStatementId());
                }
            }
            mappings.add(new TableRepositoryMapping(
                tableName,
                new ArrayList<>(xmlFiles),
                new ArrayList<>(repoClasses),
                repoMethods
            ));
            count++;
            if (count % batchSize == 0) {
                writeTableRepoMappingBatch(mappings);
            }
        }
        // Final write for any remaining mappings
        writeTableRepoMappingBatch(mappings);
        return mappings;
    }

    private Set<String> parseJavaMethods(Path javaFile) {
        Set<String> methodNames = new HashSet<>();
        try {
            CompilationUnit cu = javaParser.parse(javaFile).getResult().orElse(null);
            if (cu != null) {
                cu.findAll(MethodDeclaration.class).forEach(md -> methodNames.add(md.getNameAsString()));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error parsing Java file with JavaParser: " + javaFile + ", " + e.getMessage());
        }
        return methodNames;
    }

    private void writeTableRepoMappingBatch(List<TableRepositoryMapping> repoMappings) {
        try (FileWriter writer = new FileWriter(TABLE_REPO_MAPPING_FILE)) {
            gson.toJson(repoMappings, writer);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to write table-repository mapping to disk: " + e.getMessage());
        }
    }
}
