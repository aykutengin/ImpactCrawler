package v3.indexer;

import v3.model.ServiceMethod;
import v3.model.TableRepositoryMapping;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.google.gson.Gson;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.ast.expr.Expression;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds an index mapping mapper methods to service methods.
 */
public class RepositoryReferenceFinder {
    private static final Logger logger = Logger.getLogger(RepositoryReferenceFinder.class.getName());
    private static final String REPO_METHOD_REFERENCES_FILE = "repo_method_references.json";
    private static final String CALL_EXPRESSION_CACHE_FILE = "call_expression_cache.json";
    private static final String CALL_EXPRESSION_CACHE_INCREMENTAL_FILE = "call_expression_cache_incremental.jsonl";
    private final Gson gson = new Gson();
    private final JavaParser javaParser = new JavaParser();
    private final Map<Path, CompilationUnit> parsedFileCache = new HashMap<>();
    private final Map<String, LinkedList<CallReference>> callExpressionCache = new HashMap<>();
    private static final Set<String> EXCLUDED_METHODS = Set.of("toString", "hashCode", "equals", "wait", "notify", "notifyAll", "getClass");
    private final CombinedTypeSolver typeSolver;
    private final JavaSymbolSolver symbolSolver;
    private int incrementalWriteCounter = 0;

    public RepositoryReferenceFinder() {
        this.typeSolver = new CombinedTypeSolver();
        this.typeSolver.add(new ReflectionTypeSolver());
        // Add your project source root for full type resolution
        this.typeSolver.add(new JavaParserTypeSolver(new java.io.File("src/main/java")));
        this.symbolSolver = new JavaSymbolSolver(typeSolver);
        com.github.javaparser.StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
    }

    private CompilationUnit compile(Path javaFile) {
        if(parsedFileCache.containsKey(javaFile)) {
            return  parsedFileCache.get(javaFile);
        }
        try {
            Optional<CompilationUnit> cuOpt = javaParser.parse(javaFile).getResult();
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                parsedFileCache.put(javaFile, cu);
                return cu;
            } else {
                logger.log(Level.WARNING, "Parsing failed for " + javaFile);
                return null;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to parse " + javaFile + ": " + e.getMessage());
            return null;
        }
    }

    public Map<String, List<ServiceMethod>> findReferences(List<TableRepositoryMapping> repoMappings, List<Path> allJavaFiles) {
        // parseAllJavaFiles(allJavaFiles);
        populateCalleeMethodList(allJavaFiles);
        // This method is now only for populating the call expression cache, so return an empty map
        return Collections.emptyMap();
    }

    private void populateCalleeMethodList(List<Path> allJavaFiles) {
        List<Map.Entry<String, CallReference>> incrementalBuffer = new ArrayList<>();
        for (Path javaFile : allJavaFiles) {
            CompilationUnit cu = compile(javaFile);
            if (cu == null) continue;
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = classDecl.getNameAsString();
                String classFQCN = resolveFQCN(cu, className);
                classDecl.findAll(MethodDeclaration.class).forEach(methodDecl -> {
                    String sourceMethodFQCN = classFQCN + "." + methodDecl.getNameAsString();
                    methodDecl.findAll(MethodCallExpr.class).forEach(call -> {
                        String calleeMethod = populateCalleeMethod(call);
                        if (EXCLUDED_METHODS.contains(calleeMethod)) return;
                        String calleeClass = populateCalleeClass(call, classFQCN, cu);
                        String fullName = calleeClass + "." + calleeMethod;
                        int line = call.getBegin().map(p -> p.line).orElse(-1);
                        CallReference ref = new CallReference(javaFile.toString(), line, sourceMethodFQCN);
                        if (callExpressionCache.containsKey(fullName)) {
                            callExpressionCache.get(fullName).add(ref);
                        } else {
                            LinkedList<CallReference> refs = new LinkedList<>();
                            refs.add(ref);
                            callExpressionCache.put(fullName, refs);
                        }
                        incrementalBuffer.add(Map.entry(fullName, ref));
                        incrementalWriteCounter++;
                        if (incrementalWriteCounter % 1000 == 0) {
                            writeCallExpressionCacheIncremental(incrementalBuffer);
                            incrementalBuffer.clear();
                        }
                    });
                });
            });
        }
        // Write any remaining buffered entries
        if (!incrementalBuffer.isEmpty()) {
            writeCallExpressionCacheIncremental(incrementalBuffer);
        }
    }

    /**
     * Enhanced FQCN resolver for method call scopes.
     * Handles:
     * - FQCNs in scope (e.g., com.company.project.Class)
     * - Simple names (resolve via imports/package)
     * - Chained scopes (returns rightmost identifier)
     * Limitations:
     * - Does not resolve field types or chained object references (needs SymbolSolver)
     */
    private String resolveFQCN(CompilationUnit cu, String scope) {
        if (scope == null || scope.isEmpty()) return "";
        // If scope looks like a FQCN, return as is
        if (scope.matches("[a-zA-Z_][a-zA-Z0-9_.]*")) {
            if (scope.contains(".")) {
                // If scope is chained, return rightmost identifier (best effort)
                String[] parts = scope.split("\\.");
                String last = parts[parts.length - 1];
                // Try to resolve last part as class
                for (com.github.javaparser.ast.ImportDeclaration imp : cu.getImports()) {
                    String impName = imp.getNameAsString();
                    if (impName.endsWith("." + last)) {
                        return impName;
                    }
                }
                String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                return pkg.isEmpty() ? last : pkg + "." + last;
            } else {
                // Simple name
                for (com.github.javaparser.ast.ImportDeclaration imp : cu.getImports()) {
                    String impName = imp.getNameAsString();
                    if (impName.endsWith("." + scope)) {
                        return impName;
                    }
                }
                String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
                return pkg.isEmpty() ? scope : pkg + "." + scope;
            }
        }
        // Fallback: return scope as is
        return scope;
        // TODO: Integrate JavaParser SymbolSolver for full type resolution
    }

    private void writeRepoMethodReferences(Map<String, List<Map<String, Object>>> data) {
        try (java.io.FileWriter writer = new java.io.FileWriter(REPO_METHOD_REFERENCES_FILE)) {
            gson.toJson(data, writer);
            logger.log(Level.INFO, "Wrote repo method references to " + REPO_METHOD_REFERENCES_FILE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to write repo method references: " + e.getMessage());
        }
    }

    private void writeCallExpressionCache() {
        try (java.io.FileWriter writer = new java.io.FileWriter(CALL_EXPRESSION_CACHE_FILE)) {
            gson.toJson(callExpressionCache, writer);
            logger.log(Level.INFO, "Wrote call expression cache to " + CALL_EXPRESSION_CACHE_FILE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to write call expression cache: " + e.getMessage());
        }
    }

    private void writeCallExpressionCacheIncremental(List<Map.Entry<String, CallReference>> buffer) {
        try (java.io.FileWriter writer = new java.io.FileWriter(CALL_EXPRESSION_CACHE_INCREMENTAL_FILE, true)) {
            for (Map.Entry<String, CallReference> entry : buffer) {
                Map<String, Object> jsonObj = new HashMap<>();
                jsonObj.put("callee", entry.getKey());
                jsonObj.put("reference", entry.getValue());
                writer.write(gson.toJson(jsonObj));
                writer.write("\n");
            }
            logger.log(Level.INFO, "Appended " + buffer.size() + " call references to " + CALL_EXPRESSION_CACHE_INCREMENTAL_FILE);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to append call references: " + e.getMessage());
        }
    }

    /**
     * Returns the callee class for a method call.
     * - If scope is empty, returns the current class name.
     * - If scope is "this", returns the current class name.
     * - If scope is a variable name, attempts to resolve FQCN using imports and package.
     * - If scope is a chained call, returns the rightmost identifier.
     */
    private String populateCalleeClass(MethodCallExpr call, String currentClassFQCN, CompilationUnit cu) {
        if (call.getScope().isEmpty()) {
            return currentClassFQCN;
        }
        Expression scopeExpr = call.getScope().get();
        if(scopeExpr.isThisExpr()) {
            return currentClassFQCN;
        }
        var className = extractFromContext(call, cu);
        return resolveFQCN(cu, className);
    }

    /**
     * Returns the callee method for a method call.
     * - Returns the method name without parentheses or parameters.
     */
    private String populateCalleeMethod(MethodCallExpr call) {
        // JavaParser already gives clean method name
        return call.getNameAsString();
    }


    /**
     * Attempts to resolve the FQCN of a variable used as the scope in a method call.
     * Checks field variables, local variables, and method parameters.
     * Uses JavaParser SymbolSolver for type resolution.
     */
    private String extractFromContext(MethodCallExpr call, CompilationUnit cu) {
        if (call.getScope().isEmpty()) return "";
        String scopeName = call.getScope().get().toString();
        if (scopeName.contains(".")) {
            String[] parts = scopeName.split("\\.");
            scopeName = parts[0];
        }
        // 1. Check field variables
        for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            for (var field : classDecl.getFields()) {
                for (var varDecl : field.getVariables()) {
                    if (varDecl.getNameAsString().equals(scopeName)) {
                        var type = varDecl.getType();
                        try {
                            var resolvedType = type.resolve();
                            return resolvedType.describe();
                        } catch (Exception e) {
                            logger.log(Level.FINE, "Failed to resolve field type for '" + scopeName + "': " + e.getMessage());
                            return type.asString();
                        }
                    }
                }
            }
        }
        // 2. Check local variables and parameters in enclosing method
        var methodOpt = call.findAncestor(MethodDeclaration.class);
        if (methodOpt.isPresent()) {
            MethodDeclaration methodDecl = methodOpt.get();
            // Parameters
            for (var param : methodDecl.getParameters()) {
                if (param.getNameAsString().equals(scopeName)) {
                    var type = param.getType();
                    try {
                        var resolvedType = type.resolve();
                        return resolvedType.describe();
                    } catch (Exception e) {
                        logger.log(Level.FINE, "Failed to resolve parameter type for '" + scopeName + "': " + e.getMessage());
                        return type.asString();
                    }
                }
            }
            // Local variables
            for (var localVar : methodDecl.findAll(com.github.javaparser.ast.body.VariableDeclarator.class)) {
                if (localVar.getNameAsString().equals(scopeName)) {
                    var type = localVar.getType();
                    try {
                        var resolvedType = type.resolve();
                        return resolvedType.describe();
                    } catch (Exception e) {
                        logger.log(Level.FINE, "Failed to resolve local variable type for '" + scopeName + "': " + e.getMessage());
                        return type.asString();
                    }
                }
            }
        }
        // Fallback: return scope name
        return scopeName;
    }


    /*
     Map<String, List<ServiceMethod>> repoMethodToServiceMethods = new HashMap<>();
        for (TableRepositoryMapping mapping : repoMappings) {
            for (String repoMethod : mapping.getRepositoryMethods()) {
                final String repoFQCN;
                final String repoMethodName;
                if (!repoMethod.startsWith("[N/A]-")) {
                    int lastDot = repoMethod.lastIndexOf('.');
                    if (lastDot > 0) {
                        repoFQCN = repoMethod.substring(0, lastDot);
                        repoMethodName = repoMethod.substring(lastDot + 1);
                    } else {
                        repoFQCN = null;
                        repoMethodName = null;
                    }
                } else {
                    repoFQCN = null;
                    repoMethodName = null;
                }
                List<ServiceMethod> found = new ArrayList<>();
                for (Path javaFile : allJavaFiles) {
                    CompilationUnit cu = parsedFileCache.get(javaFile);
                    if (cu == null) continue;
                    cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                        String className = classDecl.getNameAsString();
                        String classFQCN = resolveFQCN(cu, className);
                        classDecl.findAll(MethodDeclaration.class).forEach(methodDecl -> {
                            methodDecl.findAll(MethodCallExpr.class).forEach(call -> {
                                String calleeClass = populateCalleeClass(call, className, cu);
                                String calleeMethod = populateCalleeMethod(call);
                                String fullName = calleeClass + "." + calleeMethod;
                                if(fullName.endsWith(repoMethod)) {
                                    ServiceMethod serviceMethod = new ServiceMethod(className, javaFile, classFQCN, methodDecl.getNameAsString(), repoMethod);
                                    found.add(serviceMethod);
                                }
                            });
                        });
                    });
                }
                if (!found.isEmpty()) {
                    repoMethodToServiceMethods.put(repoMethod, found);
                    // Write the found references for this repoMethod to a file immediately
                    try (FileWriter writer = new FileWriter(repoMethod + "_references.json")) {
                        gson.toJson(found, writer);
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Failed to write references for " + repoMethod + ": " + e.getMessage());
                    }
                }
            }
        }

        return repoMethodToServiceMethods;
     */
}
