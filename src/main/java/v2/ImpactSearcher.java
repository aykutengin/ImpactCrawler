package v2;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Path;
import java.util.*;

public class ImpactSearcher {
    private final Directory dir;
    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    @SuppressWarnings("unused")
    private final Analyzer analyzer;

    public ImpactSearcher(Path indexPath, Analyzer analyzer) throws Exception {
        this.dir = FSDirectory.open(indexPath);
        this.reader = DirectoryReader.open(dir);
        this.searcher = new IndexSearcher(reader);
        this.analyzer = analyzer;
    }

    public void searchAndPrint(List<String> terms, int maxHitsPerTerm) throws Exception {
        for (String t : terms) {
            String q = t.toLowerCase(Locale.ROOT);
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            bq.add(new TermQuery(new Term("content_exact", q)), BooleanClause.Occur.SHOULD);
            bq.add(new TermQuery(new Term("content_parts", q)), BooleanClause.Occur.SHOULD);
            TopDocs hits = searcher.search(bq.build(), maxHitsPerTerm);

            System.out.println("\nTERM: " + t + "  (hits: " + hits.totalHits.value() + ")");
            for (ScoreDoc sd : hits.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                System.out.println(" - " + d.get("path"));
            }
        }
    }

    public void close() throws Exception {
        reader.close();
        dir.close();
    }
}
