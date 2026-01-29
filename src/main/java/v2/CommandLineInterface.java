package v2;

import java.util.*;

public class CommandLineInterface {
    enum Mode { INDEX, SEARCH }

    Mode mode;
    String root;                // for index
    String indexPath;           // for both
    List<String> terms = List.of();  // for search
    int maxHits = 200;
    String charsetName = "UTF-8";
    boolean bestEffort = false;

    static CommandLineInterface parse(String[] args) {
        CommandLineInterface cli = new CommandLineInterface();
        if (args.length == 0) return cli;

        int i = 0;
        String cmd = args[i++].toLowerCase(Locale.ROOT);
        if ("index".equals(cmd)) cli.mode = Mode.INDEX;
        else if ("search".equals(cmd)) cli.mode = Mode.SEARCH;

        while (i < args.length) {
            String a = args[i++];
            switch (a) {
                case "-root":        cli.root = next(args, i++); break;
                case "-index":       cli.indexPath = next(args, i++); break;
                case "-term":        cli.terms = Arrays.asList(next(args, i++).split(",")); break;
                case "-maxHits":     cli.maxHits = Integer.parseInt(next(args, i++)); break;
                case "-charset":     cli.charsetName = next(args, i++); break;
                case "-bestEffort":  cli.bestEffort = true; break;
                default: /* ignore unknown */ break;
            }
        }
        return cli;
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
