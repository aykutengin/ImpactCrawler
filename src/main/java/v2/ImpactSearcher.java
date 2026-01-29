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
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ImpactSearcher {
    private final Directory dir;
    private final DirectoryReader reader;
    private final IndexSearcher searcher;
    @SuppressWarnings("unused")
    private final Analyzer analyzer;

    public ImpactSearcher(Path indexPath, Analyzer analyzer) throws IOException {
        this.dir = FSDirectory.open(indexPath);
        this.reader = DirectoryReader.open(dir);
        this.searcher = new IndexSearcher(reader);
        this.analyzer = analyzer;
    }

    public void searchAndPrint(List<String> terms, int maxHitsPerTerm) throws IOException {
        for (String term : terms) {
            searchTerm(term, maxHitsPerTerm);
        }
    }

    private void searchTerm(String term, int maxHitsPerTerm) throws IOException {
        Queue<CrawlerTerm> queue = new LinkedList<>();
        TreeSet<CrawlerTerm> crawlerTermTreeSet = new TreeSet<>();
        CrawlerTerm rootCrawlerTerm = new CrawlerTerm(term, null);
        queue.add(rootCrawlerTerm);
        LinkedHashSet<String> relatedServices = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            CrawlerTerm crawlerTerm = queue.poll();
            if (crawlerTermTreeSet.contains(crawlerTerm)) {
                continue;
            }
            String lowerCaseTerm = crawlerTerm.getSource().toLowerCase(Locale.ROOT);
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            bq.add(new TermQuery(new Term("content_exact", lowerCaseTerm)), BooleanClause.Occur.SHOULD);
            bq.add(new TermQuery(new Term("content_parts", lowerCaseTerm)), BooleanClause.Occur.SHOULD);
            TopDocs hits = searcher.search(bq.build(), maxHitsPerTerm);
            crawlerTermTreeSet.add(crawlerTerm);
//            System.out.println("\nTERM: " + crawlerTerm + "  (hits: " + hits.totalHits.value() + ")");

            for (ScoreDoc sd : hits.scoreDocs) {
                Document d = searcher.storedFields().document(sd.doc);
                File file = new File(d.get("path"));
                String fileName = file.getName().replace(".java", "");
                if (fileName.equals(crawlerTerm.getSource())) {
                    continue;
                }
                if (fileName.endsWith("Service")) {
                    relatedServices.add(fileName);
                }
                CrawlerTerm discoveredTerm = new CrawlerTerm(fileName, file.getAbsolutePath());
                crawlerTerm.getDestinations().add(discoveredTerm);
                queue.add(discoveredTerm);
//                System.out.println("discoveredTerm : " + discoveredTerm);
            }
        }
        System.out.println("relatedServices for " + rootCrawlerTerm.getSource() + ": " + relatedServices);
    }

    public void close() throws IOException {
        reader.close();
        dir.close();
    }
}
