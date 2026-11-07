package org.sagisolas.recipehost.mod.snapshot;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Encodes Icon atlases and layouts to PNG with one reused {@link ImageWriter} and an in-memory
 * stream. {@code ImageIO.write} looks the writer up and goes through a
 * disk-backed cache stream on every call, which is about 8× slower for small
 * images like Icon atlases. Not thread-safe: use one encoder per thread.
 */
final class PngEncoder {

    private final ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();

    byte[] encode(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        try (ImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(image);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.setOutput(null);
        }
        return out.toByteArray();
    }
}
