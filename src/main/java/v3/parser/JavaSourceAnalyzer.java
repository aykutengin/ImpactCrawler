package v3.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import v3.model.ServiceMethod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Analyzes Java source files to find service methods that invoke mapper methods.
 * Uses JavaParser for AST-based analysis (no string matching).
 */
public class JavaSourceAnalyzer {

    private final JavaParser javaParser;

    public JavaSourceAnalyzer() {
        this.javaParser = new JavaParser();
    }

    /**
     * Analyzes a Java source file to find service methods that call mapper methods.
     *
     * @param moduleName the Maven module name
     * @param javaFilePath path to the Java source file
     * @param mapperNamespaces set of known mapper namespaces to look for
     * @return list of service methods found
     */
    public List<ServiceMethod> analyzeJavaFile(String moduleName, Path javaFilePath,
                                               Set<String> mapperNamespaces) {
        List<ServiceMethod> serviceMethods = new ArrayList<>();

        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(javaFilePath);

            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse " + javaFilePath);
                return serviceMethods;
            }

            CompilationUnit cu = parseResult.getResult().orElse(null);
            if (cu == null) {
                return serviceMethods;
            }

            // Find all classes in the file
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                if (isServiceClass(classDecl)) {
                    String className = getFullyQualifiedClassName(cu, classDecl);

                    // Find mapper fields in this class
                    Map<String, String> mapperFields = findMapperFields(classDecl, mapperNamespaces);

                    // Analyze each method
                    classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                        Set<String> invokedMapperMethods = findMapperMethodInvocations(method, mapperFields);

                        for (String mapperMethodRef : invokedMapperMethods) {
                            ServiceMethod serviceMethod = new ServiceMethod(
                                moduleName,
                                javaFilePath,
                                className,
                                method.getNameAsString(),
                                mapperMethodRef
                            );
                            serviceMethods.add(serviceMethod);
                        }
                    });
                }
            });

        } catch (IOException e) {
            System.err.println("Error reading Java file " + javaFilePath + ": " + e.getMessage());
        }

        return serviceMethods;
    }

    private boolean isServiceClass(ClassOrInterfaceDeclaration classDecl) {
        String className = classDecl.getNameAsString();

        // Check if class name suggests it's a service
        if (className.endsWith("Service") ||
            className.endsWith("ServiceImpl") ||
            className.contains("Service")) {
            return true;
        }

        // Check for common service annotations
        return classDecl.getAnnotations().stream()
            .anyMatch(ann -> {
                String annName = ann.getNameAsString();
                return annName.equals("Service") ||
                       annName.equals("Component") ||
                       annName.contains("Service");
            });
    }

    private String getFullyQualifiedClassName(CompilationUnit cu, ClassOrInterfaceDeclaration classDecl) {
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");

        String className = classDecl.getNameAsString();

        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    private Map<String, String> findMapperFields(ClassOrInterfaceDeclaration classDecl,
                                                 Set<String> mapperNamespaces) {
        Map<String, String> mapperFields = new HashMap<>();

        classDecl.findAll(FieldDeclaration.class).forEach(field -> {
            String fieldType = field.getCommonType().asString();

            // Check if this field type matches a known mapper namespace
            for (String namespace : mapperNamespaces) {
                String simpleMapperName = getSimpleClassName(namespace);

                if (fieldType.equals(simpleMapperName) || fieldType.equals(namespace)) {
                    // Map field name to mapper namespace
                    field.getVariables().forEach(var -> {
                        mapperFields.put(var.getNameAsString(), namespace);
                    });
                    break;
                }
            }
        });

        return mapperFields;
    }

    private Set<String> findMapperMethodInvocations(MethodDeclaration method,
                                                    Map<String, String> mapperFields) {
        Set<String> mapperMethodRefs = new HashSet<>();

        method.findAll(MethodCallExpr.class).forEach(methodCall -> {
            methodCall.getScope().ifPresent(scope -> {
                String scopeName = scope.toString();

                // Check if this is a call on a mapper field
                if (mapperFields.containsKey(scopeName)) {
                    String mapperNamespace = mapperFields.get(scopeName);
                    String methodName = methodCall.getNameAsString();
                    String fullyQualifiedRef = mapperNamespace + "." + methodName;
                    mapperMethodRefs.add(fullyQualifiedRef);
                }
            });
        });

        return mapperMethodRefs;
    }

    private String getSimpleClassName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? fullyQualifiedName.substring(lastDot + 1) : fullyQualifiedName;
    }
}
