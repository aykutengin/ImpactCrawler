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
import java.util.logging.Logger;

public class ImpactSearcher {
    private static Logger logger = Logger.getLogger(ImpactSearcher.class.getName());

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
        List<LinkedHashSet> relatedServicesList = new ArrayList<>();
        for (String term : terms) {
            relatedServicesList.add(searchTerm(term, maxHitsPerTerm));
        }
        relatedServicesList.stream().forEach(relatedServices -> {
            relatedServices.stream().forEach(relatedService -> System.out.print(relatedService+ ", "));
        });
//        System.out.println("\n\nRelated services " + relatedServicesList);
    }

    private LinkedHashSet searchTerm(String term, int maxHitsPerTerm) throws IOException {
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
            String searchTerm = crawlerTerm.getSource(); // case-sensitive
            BooleanQuery.Builder bq = new BooleanQuery.Builder();
            bq.add(new TermQuery(new Term("content_exact", searchTerm)), BooleanClause.Occur.SHOULD);
            bq.add(new TermQuery(new Term("content_parts", searchTerm)), BooleanClause.Occur.SHOULD);
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
//        logger.info("relatedServices for " + rootCrawlerTerm.getSource() + ": " + relatedServices);
        return relatedServices;
    }

    public void close() throws IOException {
        reader.close();
        dir.close();
    }
}
