package v2;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Cli cli = Cli.parse(args);
        if (!cli.valid()) {
            Cli.printHelp();
            System.exit(1);
        }

        switch (cli.mode) {
            case INDEX: {
                Path root = Paths.get(cli.root);
                Path indexPath = Paths.get(cli.indexPath);
                var analyzer = CodeAnalyzer.perField();
                CodeIndexer indexer = new CodeIndexer(root, indexPath, analyzer, cli.charsetName, cli.bestEffort);
                indexer.buildOrUpdate();
                System.out.println("Index built at: " + indexPath.toAbsolutePath());
                break;
            }
            case SEARCH: {
                Path indexPath = Paths.get(cli.indexPath);
                var analyzer = CodeAnalyzer.perField();
                ImpactSearcher searcher = new ImpactSearcher(indexPath, analyzer);
                searcher.searchAndPrint(cli.terms, cli.maxHits);
                searcher.close();
                break;
            }
            default:
                Cli.printHelp();
        }
    }
}
