package v2;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import v2.model.CrawlerTerm;

import java.io.File;
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
        Queue<CrawlerTerm> queue = new LinkedList<>();
        for (String term : terms) {
            String lowerCaseTerm = term.toLowerCase(Locale.ROOT);
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            bq.add(new TermQuery(new Term("content_exact", lowerCaseTerm)), BooleanClause.Occur.SHOULD);
            bq.add(new TermQuery(new Term("content_parts", lowerCaseTerm)), BooleanClause.Occur.SHOULD);
            TopDocs hits = searcher.search(bq.build(), maxHitsPerTerm);

            System.out.println("\nTERM: " + term + "  (hits: " + hits.totalHits.value() + ")");


            for (ScoreDoc sd : hits.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                System.out.println(" - " + d.get("path"));
                File file = new File(d.get("path"));
                CrawlerTerm discoveredTerm = new CrawlerTerm(term, file.getAbsolutePath());
                discoveredTerm.getDestinations().add(file.getName());
                queue.add(discoveredTerm);
            }
        }
    }

    public void close() throws Exception {
        reader.close();
        dir.close();
    }
}
