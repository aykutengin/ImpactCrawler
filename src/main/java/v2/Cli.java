package v2;

import java.util.*;

public class Cli {
    enum Mode { INDEX, SEARCH }

    Mode mode;
    String root;                // for index
    String indexPath;           // for both
    List<String> terms = List.of();  // for search
    int maxHits = 200;
    String charsetName = "UTF-8";
    boolean bestEffort = false;

    static Cli parse(String[] args) {
        Cli c = new Cli();
        if (args.length == 0) return c;

        int i = 0;
        String cmd = args[i++].toLowerCase(Locale.ROOT);
        if ("index".equals(cmd)) c.mode = Mode.INDEX;
        else if ("search".equals(cmd)) c.mode = Mode.SEARCH;

        while (i < args.length) {
            String a = args[i++];
            switch (a) {
                case "-root":        c.root = next(args, i++); break;
                case "-index":       c.indexPath = next(args, i++); break;
                case "-term":        c.terms = Arrays.asList(next(args, i++).split(",")); break;
                case "-maxHits":     c.maxHits = Integer.parseInt(next(args, i++)); break;
                case "-charset":     c.charsetName = next(args, i++); break;
                case "-bestEffort":  c.bestEffort = true; break;
                default: /* ignore unknown */ break;
            }
        }
        return c;
    }

    static String next(String[] a, int i) { if (i >= a.length) throw new IllegalArgumentException("Missing value"); return a[i]; }

    boolean valid() {
        if (mode == null) return false;
        if (mode == Mode.INDEX) return root != null && indexPath != null;
        if (mode == Mode.SEARCH) return indexPath != null && !terms.isEmpty();
        return false;
    }

    static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  java -jar impact-crawler.jar index  -root <path> -index <path> [-charset UTF-8] [-bestEffort]");
        System.out.println("  java -jar impact-crawler.jar search -index <path> -term <t1[,t2,...]> [-maxHits 200]");
    }
}
