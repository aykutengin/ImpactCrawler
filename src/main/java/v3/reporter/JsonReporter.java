package v3.reporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import v3.model.ImpactAnalysisResult;

/**
 * Generates JSON reports from impact analysis results.
 */
public class JsonReporter {

    private final Gson gson;

    public JsonReporter() {
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    /**
     * Converts an impact analysis result to JSON format.
     *
     * @param result the analysis result
     * @return JSON string
     */
    public String generateReport(ImpactAnalysisResult result) {
        return gson.toJson(result);
    }

    /**
     * Converts an impact analysis result to compact JSON format.
     *
     * @param result the analysis result
     * @return compact JSON string
     */
    public String generateCompactReport(ImpactAnalysisResult result) {
        Gson compactGson = new Gson();
        return compactGson.toJson(result);
    }
}
