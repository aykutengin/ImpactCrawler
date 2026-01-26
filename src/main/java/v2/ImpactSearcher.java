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
        TreeSet<CrawlerTerm> set = new TreeSet<>();
        CrawlerTerm rootCrawlerTerm = new CrawlerTerm(terms.get(0), null);
        queue.add(rootCrawlerTerm);
        List<String> relatedServices = new ArrayList<>();
        while (!queue.isEmpty()) {
            CrawlerTerm term = queue.poll();
            if (set.contains(term)) {
                continue;
            }
            String lowerCaseTerm = term.getSource().toLowerCase(Locale.ROOT);
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            bq.add(new TermQuery(new Term("content_exact", lowerCaseTerm)), BooleanClause.Occur.SHOULD);
            bq.add(new TermQuery(new Term("content_parts", lowerCaseTerm)), BooleanClause.Occur.SHOULD);
            TopDocs hits = searcher.search(bq.build(), maxHitsPerTerm);
            set.add(term);
//            System.out.println("\nTERM: " + term + "  (hits: " + hits.totalHits.value() + ")");

            for (ScoreDoc sd : hits.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                File file = new File(d.get("path"));
                String fileName = file.getName().replace(".java", "");
                if (fileName.equals(term.getSource())) {

                    continue;
                }
                if (fileName.endsWith("Service")) {
                    relatedServices.add(fileName);
                }
                CrawlerTerm discoveredTerm = new CrawlerTerm(fileName, file.getAbsolutePath());
                term.getDestinations().add(discoveredTerm);
                queue.add(discoveredTerm);
//                System.out.println("discoveredTerm : " + discoveredTerm);
            }
        }

//        System.out.println("rootCrawlerTerm : " + rootCrawlerTerm.getSource());
        System.out.println("relatedServices for " + rootCrawlerTerm.getSource() + ": " + relatedServices);
    }

    public void close() throws Exception {
        reader.close();
        dir.close();
    }
}
