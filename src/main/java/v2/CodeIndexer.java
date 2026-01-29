package v2;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class CodeIndexer {
    private final Path root;
    private final Directory dir;
    private final IndexWriter writer;
    private final String charsetName;
    private final boolean bestEffort;

    public CodeIndexer(Path root, Path indexPath, Analyzer analyzer, String charsetName, boolean bestEffort) throws IOException {
        this.root = root;
        this.dir = FSDirectory.open(indexPath);
        this.charsetName = charsetName;
        this.bestEffort = bestEffort;

        IndexWriterConfig cfg = new IndexWriterConfig(analyzer)
                .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND)
                .setRAMBufferSizeMB(256);
        this.writer = new IndexWriter(dir, cfg);
    }

    public void buildOrUpdate() throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (name.equals(".git") || name.equals("target") || name.equals("build") || name.equals(".gradle") || name.equals("out") || name.equals("node_modules")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().endsWith(".java")) return FileVisitResult.CONTINUE;

                String content = SafeReader.safeRead(file, charsetName, bestEffort);
                if (content == null) return FileVisitResult.CONTINUE;

                content = CodeSanitizer.stripCommentsAndImports(content);

                Document doc = new Document();
                doc.add(new StringField("path", file.toString(), Field.Store.YES));
                doc.add(new TextField("content_exact", content, Field.Store.NO));
                doc.add(new TextField("content_parts", content, Field.Store.NO));

                writer.updateDocument(new Term("path", file.toString()), doc);
                return FileVisitResult.CONTINUE;
            }
        });
        writer.commit();
        writer.flush();
        writer.close();
        dir.close();
    }
}
