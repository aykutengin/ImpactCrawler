package v2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public final class SafeReader {
    private static Logger logger = Logger.getLogger(SafeReader.class.getName());

    private SafeReader() {}

    public static String safeRead(Path path, String preferredCharsetName, boolean bestEffort) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            logger.warning("WARN: Read failed: " + path + " (" + e.getMessage() + ")");
            return null;
        }

        Charset bom = detectBom(bytes);
        if (bom != null) {
            return decode(bytes, bom, bestEffort);
        }

        try {
            Charset preferred = Charset.forName(preferredCharsetName);
            String s = decode(bytes, preferred, bestEffort);
            if (s != null) return s;
        } catch (Exception ignore) {}

        List<Charset> fallbacks = Arrays.asList(
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16LE,
                StandardCharsets.UTF_16BE,
                Charset.forName("windows-1252"),
                Charset.forName("ISO-8859-15"),
                StandardCharsets.ISO_8859_1,
                Charset.forName("MacRoman")
        );

        for (Charset cs : fallbacks) {
            String s = decode(bytes, cs, bestEffort);
            if (s != null) return s;
        }

        logger.warning("WARN: Could not decode: " + path);
        return null;
    }

    private static Charset detectBom(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF)
            return StandardCharsets.UTF_8;
        if (b.length >= 2) {
            int b0 = b[0] & 0xFF, b1 = b[1] & 0xFF;
            if (b0 == 0xFE && b1 == 0xFF) return StandardCharsets.UTF_16BE;
            if (b0 == 0xFF && b1 == 0xFE) return StandardCharsets.UTF_16LE;
        }
        return null;
    }

    private static String decode(byte[] bytes, Charset cs, boolean bestEffort) {
        try {
            CharsetDecoder dec = cs.newDecoder();
            if (bestEffort) {
                dec.onMalformedInput(CodingErrorAction.REPLACE);
                dec.onUnmappableCharacter(CodingErrorAction.REPLACE);
            } else {
                dec.onMalformedInput(CodingErrorAction.REPORT);
                dec.onUnmappableCharacter(CodingErrorAction.REPORT);
            }
            return dec.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
