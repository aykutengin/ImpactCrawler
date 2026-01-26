package v2.model;

import java.util.ArrayList;
import java.util.List;

public class CrawlerTerm {
    String source;
    String path;
    List<CrawlerTerm> destinations;

    public CrawlerTerm(String source, String path) {
        this.source = source;
        this.path = path;
        this.destinations = new ArrayList<>();
    }


    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<CrawlerTerm> getDestinations() {
        return destinations;
    }

    public void setDestinations(List<CrawlerTerm> destinations) {
        this.destinations = destinations;
    }
}
