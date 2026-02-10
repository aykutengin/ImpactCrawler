package v3.parser;

import v3.model.MapperMethod;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses MyBatis mapper XML files to extract mapper methods and SQL.
 */
public class MyBatisXmlParser {

    private static final String[] SQL_STATEMENT_TAGS = {"select", "insert", "update", "delete"};

    /**
     * Parses a MyBatis mapper XML file and extracts all mapper methods.
     *
     * @param moduleName the name of the Maven module
     * @param xmlPath path to the mapper XML file
     * @return list of mapper methods found in the file
     */
    public List<MapperMethod> parseMapperXml(String moduleName, Path xmlPath) {
        List<MapperMethod> methods = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlPath.toFile());

            Element root = doc.getDocumentElement();

            // Get the mapper namespace
            String namespace = root.getAttribute("namespace");
            if (namespace == null || namespace.isEmpty()) {
                System.err.println("Warning: No namespace found in " + xmlPath);
                return methods;
            }

            // Extract all SQL statements
            for (String tag : SQL_STATEMENT_TAGS) {
                NodeList nodes = root.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element element = (Element) nodes.item(i);
                    String id = element.getAttribute("id");

                    if (id != null && !id.isEmpty()) {
                        String sql = extractSqlText(element);

                        MapperMethod method = new MapperMethod(
                            moduleName,
                            xmlPath,
                            namespace,
                            id,
                            tag,
                            sql
                        );

                        methods.add(method);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing mapper XML " + xmlPath + ": " + e.getMessage());
        }

        return methods;
    }

    private String extractSqlText(Element element) {
        StringBuilder sql = new StringBuilder();
        extractTextRecursively(element, sql);
        String result = sql.toString().replaceAll("\\s+", " ").trim();
        // Debug: print the extracted SQL for verification
        System.out.println("[DEBUG] Extracted SQL for id '" + element.getAttribute("id") + "':\n" + result);
        return result;
    }

    private void extractTextRecursively(Node node, StringBuilder sql) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE ||
                child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String text = child.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    sql.append(text.trim()).append(" ");
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                // Recursively handle nested elements like <if>, <where>, <set>, etc.
                extractTextRecursively(child, sql);
            }
        }
    }
}
