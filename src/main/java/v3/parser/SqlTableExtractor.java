package v3.parser;

import java.util.*;
import java.util.regex.*;

/**
 * Extracts table names from SQL statements using a token-based approach.
 * Handles SELECT, UPDATE, INSERT, JOIN, and removes aliases.
 */
public class SqlTableExtractor {

    private static final Set<String> SQL_KEYWORDS = Set.of(
        "DUAL", "SELECT", "FROM", "WHERE", "AND", "OR", "ON", "AS", "SET", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "VALUES", "UPDATE", "INSERT", "DELETE", "MERGE", "INTO", "USING", "GROUP", "ORDER", "BY", "HAVING", "DISTINCT", "LIMIT", "OFFSET", "CASE", "WHEN", "THEN", "ELSE", "END", "IN", "EXISTS", "NOT", "NULL", "IS", "LIKE", "BETWEEN", "ASC", "DESC", "WITH", "PARTITION"
    );

    /**
     * Extracts all table names referenced in the given SQL statement.
     * Handles JOINs, aliases, and subqueries.
     *
     * @param sql the SQL statement to parse
     * @return set of normalized table names
     */
    public Set<String> extractTableNames(String sql) {
        Set<String> tables = new HashSet<>();
        if (sql == null || sql.trim().isEmpty()) {
            return tables;
        }
        String cleanedSql = cleanMyBatisDynamicSql(sql);
        tables.addAll(tokenBasedTableExtraction(cleanedSql));
        return tables;
    }

    /**
     * Cleans MyBatis/iBatis dynamic SQL tags and removes empty lines.
     */
    private String cleanMyBatisDynamicSql(String sql) {
        // Remove MyBatis/iBatis parameter placeholders like #{...}, ${...}, and :param
        String cleaned = sql.replaceAll("#\\{[^}]*\\}", "?");
        cleaned = cleaned.replaceAll("\\$\\{[^}]*\\}", "?");
        cleaned = cleaned.replaceAll(":[a-zA-Z0-9_]+", "?"); // :param style
        cleaned = cleaned.replaceAll("#", "?"); // Remove stray #
        cleaned = cleaned.replaceAll("\\$", "?"); // Remove stray $
        // Remove common MyBatis/iBatis XML tags (keep the content)
        cleaned = cleaned.replaceAll("</?if[^>]*>", "");
        cleaned = cleaned.replaceAll("</?where[^>]*>", "");
        cleaned = cleaned.replaceAll("</?set[^>]*>", "");
        cleaned = cleaned.replaceAll("</?choose[^>]*>", "");
        cleaned = cleaned.replaceAll("</?when[^>]*>", "");
        cleaned = cleaned.replaceAll("</?otherwise[^>]*>", "");
        cleaned = cleaned.replaceAll("</?trim[^>]*>", "");
        cleaned = cleaned.replaceAll("</?foreach[^>]*>", "");
        cleaned = cleaned.replaceAll("</?bind[^>]*>", "");
        cleaned = cleaned.replaceAll("<!\\[CDATA\\[", "");
        cleaned = cleaned.replaceAll("\\]\\]>", "");
        // Remove line comments
        cleaned = cleaned.replaceAll("--[^\n]*", "");
        // Remove empty lines
        cleaned = cleaned.replaceAll("(?m)^\\s*$\\r?\\n", "");
        // Replace multiple whitespaces with single space
        cleaned = cleaned.replaceAll("\\s+", " ");
        return cleaned.trim();
    }

    /**
     * Token-based extraction for table names from SQL.
     * Handles SELECT, UPDATE, INSERT, JOIN, and removes aliases.
     */
    private Set<String> tokenBasedTableExtraction(String sql) {
        Set<String> tables = new HashSet<>();
        String upperSql = sql.toUpperCase();
        // FROM clause
        Matcher fromMatcher = Pattern.compile("(?i)FROM\\s+([^\s,;()]+)").matcher(sql);
        while (fromMatcher.find()) {
            tables.addAll(splitAndNormalizeTables(fromMatcher.group(1)));
        }
        // JOIN clause
        Matcher joinMatcher = Pattern.compile("(?i)JOIN\\s+([^\s,;()]+)").matcher(sql);
        while (joinMatcher.find()) {
            tables.addAll(splitAndNormalizeTables(joinMatcher.group(1)));
        }
        // UPDATE clause
        Matcher updateMatcher = Pattern.compile("(?i)UPDATE\\s+([^\s,;()]+)").matcher(sql);
        while (updateMatcher.find()) {
            tables.addAll(splitAndNormalizeTables(updateMatcher.group(1)));
        }
        // INSERT INTO clause
        Matcher insertMatcher = Pattern.compile("(?i)INSERT\\s+INTO\\s+([^\s,;()]+)").matcher(sql);
        while (insertMatcher.find()) {
            tables.addAll(splitAndNormalizeTables(insertMatcher.group(1)));
        }
        // DELETE FROM clause
        Matcher deleteMatcher = Pattern.compile("(?i)DELETE\\s+FROM\\s+([^\s,;()]+)").matcher(sql);
        while (deleteMatcher.find()) {
            tables.addAll(splitAndNormalizeTables(deleteMatcher.group(1)));
        }
        // MERGE INTO clause
        Matcher mergeMatcher = Pattern.compile("(?i)MERGE\\s+INTO\\s+([^\s,;()]+)").matcher(sql);
        while (mergeMatcher.find()) {
            tables.addAll(splitAndNormalizeTables(mergeMatcher.group(1)));
        }
        // USING clause (for MERGE)
        Matcher usingMatcher = Pattern.compile("(?i)USING\\s+([^\s,;()]+)").matcher(sql);
        while (usingMatcher.find()) {
            tables.addAll(splitAndNormalizeTables(usingMatcher.group(1)));
        }
        return tables;
    }

    /**
     * Splits table section by comma and removes aliases.
     */
    private Set<String> splitAndNormalizeTables(String section) {
        Set<String> tables = new HashSet<>();
        String[] parts = section.split(",");
        for (String part : parts) {
            String table = part.trim();
            if (!table.isEmpty()) {
                // Remove alias: split by whitespace, take first token
                String[] tokens = table.split("\\s+");
                String baseTable = tokens[0];
                // Remove schema prefix if present
                if (baseTable.contains(".")) {
                    String[] schemaSplit = baseTable.split("\\.");
                    baseTable = schemaSplit[schemaSplit.length - 1];
                }
                // Remove quotes/backticks
                baseTable = baseTable.replaceAll("[`'\"]", "");
                // Convert to uppercase
                baseTable = baseTable.toUpperCase();
                // Filter out placeholders and SQL keywords
                if (!baseTable.equals("?") && !baseTable.isEmpty() && !SQL_KEYWORDS.contains(baseTable)) {
                    tables.add(baseTable);
                }
            }
        }
        return tables;
    }
}
