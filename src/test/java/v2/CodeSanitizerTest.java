package v2;

public class CodeSanitizerTest {
    public static void main(String[] args) {
        String str = """
                int i = 0;
                        boolean inLineComment = false;     // //
                        boolean inBlockComment = false;     /* ... */ asdasdasdasdasdasd
                        boolean inString = false;          // "..."
                        boolean inChar = false;            // 'c'
                        boolean inTextBlock = false;       // ""\" ... ""\"
                        boolean lineStart = true;          // at start of a logical line (after newline), ignoring leading spaces/tabs
                        int pendingIndent = 0;             // number of leading spaces/tabs seen on this line (only emitted if we keep the line)
                """;

        System.out.println(CodeSanitizer.stripCommentsAndImports(str));
    }

}
