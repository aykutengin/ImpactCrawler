package v3.indexer;

public class CallReference {
    private String filePath;
    private int line;
    private String sourceMethod;

    public CallReference(String filePath, int line, String sourceMethod) {
        this.filePath = filePath;
        this.line = line;
        this.sourceMethod = sourceMethod;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLine() {
        return line;
    }

    public String getSourceMethod() {
        return sourceMethod;
    }

    @Override
    public String toString() {
        return filePath + ":" + line + ":" + sourceMethod;
    }
}
