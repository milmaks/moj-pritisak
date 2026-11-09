import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import rs.local.mojpritisak.Rs3LcdReader;

/** Run with upright/exif-normalised JPG or PNG, e.g. /tmp/omron-upright.png. */
public class Rs3LcdSmokeTest {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Provide a test image path");
        BufferedImage image = ImageIO.read(new File(args[0]));
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        Rs3LcdReader.Result result = Rs3LcdReader.read(image.getWidth(), image.getHeight(), pixels);
        if (result == null || result.sys != 129 || result.dia != 73 || result.pulse != 63)
            throw new AssertionError("Expected 129 / 73, pulse 63; got " +
                    (result == null ? "null" : result.sys + "/" + result.dia + "/" + result.pulse));
        System.out.println("PASS: 129 / 73, pulse 63. Confidence " + result.confidence);
    }
}
