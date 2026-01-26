package v2;

/**
 * CodeSanitizer
 * <p>
 * Preprocesses Java source for indexing:
 * - Removes line (//) and block (/* ... * /) comments (incl. Javadoc).
 * - Removes top-level import and import static statements.
 * - Preserves string literals, char literals, and Java text blocks ("""...""").
 * <p>
 * Notes:
 * - Operates without a full parser; it's a careful state machine.
 * - "Top-level" import detection triggers only at start-of-line (ignoring leading spaces/tabs).
 */
public final class CodeSanitizer {
    private CodeSanitizer() {
    }

    public static String stripCommentsAndImports(String src) {
        if (src == null || src.isEmpty()) return src;

        StringBuilder out = new StringBuilder(src.length());
        final int n = src.length();

        int i = 0;
        boolean inLineComment = false;     // //
        boolean inBlockComment = false;    // /* ... */
        boolean inString = false;          // "..."
        boolean inChar = false;            // 'c'
        boolean inTextBlock = false;       // """ ... """
        boolean lineStart = true;          // at start of a logical line (after newline), ignoring leading spaces/tabs
        int pendingIndent = 0;             // number of leading spaces/tabs seen on this line (only emitted if we keep the line)

        while (i < n) {
            char c = src.charAt(i);
            char n1 = (i + 1 < n) ? src.charAt(i + 1) : '\0';

            // --- Handle newlines early (normalize CRLF to LF) ---
            if (c == '\r') {
                // normalize CRLF/CR -> LF
                if (i + 1 < n && src.charAt(i + 1) == '\n') i++;
                if (!inBlockComment && !inLineComment) out.append('\n');
                inLineComment = false;
                lineStart = true;
                pendingIndent = 0;
                i++;
                continue;
            }
            if (c == '\n') {
                if (!inBlockComment && !inLineComment) out.append('\n');
                inLineComment = false;
                lineStart = true;
                pendingIndent = 0;
                i++;
                continue;
            }

            // --- States: inside comments ---
            if (inLineComment) { // skip until EOL (handled above)
                i++;
                continue;
            }

            if (inBlockComment) { // skip until */
                if (c == '*' && n1 == '/') {
                    inBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                // newlines during block comment are handled at the top branch
                continue;
            }

            // --- States: inside literals ---
            if (inTextBlock) {
                // Close on triple quotes
                if (c == '"' && i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                    out.append("\"\"\"");
                    i += 3;
                    inTextBlock = false;
                } else {
                    out.append(c);
                    i++;
                }
                lineStart = false;
                continue;
            }

            if (inString) {
                out.append(c);
                if (c == '\\') { // escape next char
                    if (i + 1 < n) {
                        out.append(src.charAt(i + 1));
                        i += 2;
                    } else {
                        i++;
                    }
                } else if (c == '"') {
                    inString = false;
                    i++;
                } else {
                    i++;
                }
                lineStart = false;
                continue;
            }

            if (inChar) {
                out.append(c);
                if (c == '\\') {
                    if (i + 1 < n) {
                        out.append(src.charAt(i + 1));
                        i += 2;
                    } else {
                        i++;
                    }
                } else if (c == '\'') {
                    inChar = false;
                    i++;
                } else {
                    i++;
                }
                lineStart = false;
                continue;
            }

            // --- Default state ---

            // Accumulate leading indentation (spaces/tabs) at start-of-line
            if (lineStart && (c == ' ' || c == '\t')) {
                pendingIndent++;
                i++;
                continue;
            }

            // If at start-of-line, detect and drop top-level imports ("import" or "import static")
            if (lineStart) {
                int j = i; // j points to first non-space/tab char of the line

                if (startsWithKeyword(src, j, "import")) {
                    int k = j + 6; // after "import"
                    // skip whitespace
                    while (k < n && isWsNoNl(src.charAt(k))) k++;
                    // optional "static"
                    if (startsWithKeyword(src, k, "static")) {
                        k += 6;
                        while (k < n && isWsNoNl(src.charAt(k))) k++;
                    }
                    // now skip up to semicolon (do not cross newline; import should end with ;)
                    while (k < n && src.charAt(k) != ';' && src.charAt(k) != '\n' && src.charAt(k) != '\r') k++;
                    if (k < n && src.charAt(k) == ';') k++; // consume ';'
                    // consume trailing spaces/tabs (leave newline handling to the top branch)
                    while (k < n && isWsNoNl(src.charAt(k))) k++;
                    // drop the entire import statement
                    i = k;
                    // keep lineStart=true so the rest of the (possibly empty) line is handled correctly
                    pendingIndent = 0; // don't emit indent for a removed line part
                    continue;
                }
            }

            // Start of comments?
            if (c == '/' && n1 == '/') {
                inLineComment = true;
                i += 2;
                continue;
            }
            if (c == '/' && n1 == '*') {
                inBlockComment = true;
                i += 2;
                continue;
            }

            // Start of literals?
            if (c == '"') {
                if (i + 2 < n && src.charAt(i + 1) == '"' && src.charAt(i + 2) == '"') {
                    inTextBlock = true;
                    out.append("\"\"\"");
                    i += 3;
                    lineStart = false;
                    continue;
                } else {
                    inString = true;
                    out.append('"');
                    i++;
                    lineStart = false;
                    continue;
                }
            }
            if (c == '\'') {
                inChar = true;
                out.append('\'');
                i++;
                lineStart = false;
                continue;
            }

            // Emit any pending indentation now that we decided to keep the line content
            if (pendingIndent > 0) {
                out.append(" ".repeat(pendingIndent));
                pendingIndent = 0;
            }

            out.append(c);
            i++;
            lineStart = false;
        }

        return out.toString();
    }

    private static boolean isWsNoNl(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }

    /**
     * Checks that s.startsWith(kw, pos) and that kw is on word boundaries,
     * i.e., not part of a larger Java identifier at either side.
     */
    private static boolean startsWithKeyword(String s, int pos, String kw) {
        int n = s.length(), m = kw.length();
        if (pos < 0 || pos + m > n) return false;
        // prefix boundary
        if (pos > 0 && Character.isJavaIdentifierPart(s.charAt(pos - 1))) return false;
        // exact match
        for (int i = 0; i < m; i++) {
            if (s.charAt(pos + i) != kw.charAt(i)) return false;
        }
        // suffix boundary
        int after = pos + m;
        return after >= n || !Character.isJavaIdentifierPart(s.charAt(after));
    }
}


