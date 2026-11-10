package org.sagisolas.recipehost.mod.snapshot.image;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.Test;

public class PngEncoderTest {

    private static BufferedImage image(int argb) {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, argb);
        return image;
    }

    private static BufferedImage decode(byte[] png) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    public void roundTripsPixelsIncludingAlpha() throws IOException {
        BufferedImage decoded = decode(new PngEncoder().encode(image(0x80FF0000)));

        assertEquals(0x80FF0000, decoded.getRGB(0, 0));
        assertEquals(0, decoded.getRGB(1, 1) >>> 24);
    }

    @Test
    public void reusingTheEncoderGivesIndependentImages() throws IOException {
        PngEncoder encoder = new PngEncoder();

        byte[] first = encoder.encode(image(0xFF00FF00));
        byte[] second = encoder.encode(image(0xFF0000FF));

        assertEquals(0xFF00FF00, decode(first).getRGB(0, 0));
        assertEquals(0xFF0000FF, decode(second).getRGB(0, 0));
    }
}
