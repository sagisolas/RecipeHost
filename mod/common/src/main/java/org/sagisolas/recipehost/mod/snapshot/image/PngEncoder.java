/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.sagisolas.recipehost.mod.snapshot.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Encodes Icon atlases and layouts to PNG with one reused {@link ImageWriter} and an in-memory
 * stream. {@code ImageIO.write} looks the writer up and goes through a
 * disk-backed cache stream on every call, which is about 8× slower for small
 * images like Icon atlases. Not thread-safe: use one encoder per thread.
 */
public final class PngEncoder {

    private final ImageWriter writer =
            ImageIO.getImageWritersByFormatName("png").next();

    public byte[] encode(BufferedImage image) {
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
