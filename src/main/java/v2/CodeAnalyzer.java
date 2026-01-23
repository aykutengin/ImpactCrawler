package v2;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.analysis.pattern.PatternTokenizer;

import java.util.Map;
import java.util.regex.Pattern;

public final class CodeAnalyzer {
    private CodeAnalyzer() {}

    /** Keeps FQNs like com.example.Foo intact (., $, _) and lowercases. */
    public static final class ExactAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new PatternTokenizer(Pattern.compile("[^A-Za-z0-9_$.]+"), -1);
            TokenStream stream = new LowerCaseFilter(tokenizer);
            return new TokenStreamComponents(tokenizer, stream);
        }
    }

    /** Splits on case/numbers but preserves original (good for code tokens). */
    public static final class PartsAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new PatternTokenizer(Pattern.compile("[^A-Za-z0-9_$.]+"), -1);
            TokenStream stream = new LowerCaseFilter(tokenizer);
            int flags =
                    WordDelimiterGraphFilter.GENERATE_WORD_PARTS |
                            WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS |
                            WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE |
                            WordDelimiterGraphFilter.SPLIT_ON_NUMERICS |
                            WordDelimiterGraphFilter.PRESERVE_ORIGINAL |
                            WordDelimiterGraphFilter.CATENATE_ALL;
            stream = new WordDelimiterGraphFilter(stream, flags, null);
            return new TokenStreamComponents(tokenizer, stream);
        }
    }

    public static Analyzer perField() {
        Analyzer defaultAnalyzer = new PartsAnalyzer();
        return new PerFieldAnalyzerWrapper(defaultAnalyzer, Map.of(
                "content_exact", new ExactAnalyzer(),
                "content_parts", new PartsAnalyzer()
        ));
    }
}
