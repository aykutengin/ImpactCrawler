package v3.indexer;

public class CallReferenceRecord {
    public String callee;
    public CallReference reference;

    public CallReferenceRecord(String callee, CallReference reference) {
        this.callee = callee;
        this.reference = reference;
    }
}

