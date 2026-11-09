package rs.local.mojpritisak;

/**
 * Offline seven-segment reader for the close-framed Omron RS3 photographs
 * provided by Instant BP (upright, square photographs showing the whole unit).
 *
 * Unlike general-purpose text OCR this reads the seven dark LCD segments. It
 * does NOT claim to recognise arbitrary devices/crops. Low-confidence matches
 * are rejected, and the UI requires explicit confirmation before saving.
 *
 * No Android APIs: testable with a plain JDK from ARGB pixel data.
 */
public final class Rs3LcdReader {
    private Rs3LcdReader() { }

    public static final class Result {
        public final int sys, dia, pulse;
        public final double confidence;
        Result(int sys, int dia, int pulse, double confidence) {
            this.sys = sys;
            this.dia = dia;
            this.pulse = pulse;
            this.confidence = confidence;
        }
    }

    // Reference layout measured on the supplied upright 720x720 RS3 photo.
    // Digit positions, not fixed pixel sizes; scaled to any square resolution.
    // [x1,y1,x2,y2] for 1,2,9 | 7,3 | 6,3 on the supplied picture.
    private static final int[][] DIGIT_BOXES = {
        {185, 228, 242, 323},
        {249, 229, 311, 325},
        {313, 229, 376, 325},
        {244, 337, 310, 433},
        {314, 337, 376, 433},
        {289, 439, 332, 511},
        {331, 439, 374, 511}
    };

    // LCD segments A: top, B: upper right, C: lower right, D: bottom,
    // E: lower left, F: upper left, G: center. Fractions of a digit box.
    private static final double[][] SEGMENT_PROBES = {
        {.30, .06, .70, .15}, // A
        {.77, .23, .93, .40}, // B
        {.77, .62, .93, .80}, // C
        {.30, .89, .70, .98}, // D for large SYS/DIA digits
        {.06, .62, .23, .80}, // E
        {.06, .23, .23, .40}, // F
        {.30, .45, .70, .56}  // G
    };
    private static final String[] DIGIT_SEGMENTS = {
        "ABCDEF", "BC", "ABDEG", "ABCDG", "BCFG",
        "ACDFG", "ACDEFG", "ABCF", "ABCDEFG", "ABCDFG"
    };

    public static Result read(int width, int height, int[] argb) {
        if (width < 400 || height < 400 || argb == null ||
            argb.length != width * height ||
            Math.abs(width - height) > width * 0.13) return null;

        int[] values = new int[7];
        double confidence = 1.0;
        for (int i = 0; i < DIGIT_BOXES.length; i++) {
            Digit found = digit(width, height, argb, DIGIT_BOXES[i], i >= 5);
            if (found == null) return null;
            values[i] = found.number;
            confidence = Math.min(confidence, found.quality);
        }
        int sys = values[0] * 100 + values[1] * 10 + values[2];
        int dia = values[3] * 10 + values[4];
        int pulse = values[5] * 10 + values[6];
        if (sys < 60 || sys > 280 || dia < 30 || dia > 180 ||
            pulse < 30 || pulse > 220 || sys <= dia) return null;
        return new Result(sys, dia, pulse, confidence);
    }

    private static final class Digit {
        final int number;
        final double quality;
        Digit(int number, double quality) { this.number = number; this.quality = quality; }
    }

    private static Digit digit(int width, int height, int[] argb, int[] ref, boolean smallerPulse) {
        int left = Math.max(0, ref[0] * width / 720);
        int top = Math.max(0, ref[1] * height / 720);
        int right = Math.min(width, ref[2] * width / 720);
        int bottom = Math.min(height, ref[3] * height / 720);
        int w = right - left, h = bottom - top;
        if (w < 12 || h < 16) return null;

        // Otsu local threshold: each digit has its own background / lighting.
        int[] pixels = new int[w * h];
        int[] counts = new int[256];
        long brightnessSum = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int color = argb[(top + y) * width + left + x];
                int r = (color >> 16) & 255;
                int g = (color >> 8) & 255;
                int b = color & 255;
                int gray = (77 * r + 150 * g + 29 * b + 128) >> 8;
                pixels[y * w + x] = gray;
                counts[gray]++;
                brightnessSum += gray;
            }
        }
        double total = w * h;
        int threshold = otsu(counts, (int)total, brightnessSum);
        double[] probes = new double[SEGMENT_PROBES.length];
        for (int s = 0; s < SEGMENT_PROBES.length; s++) {
            double[] box = SEGMENT_PROBES[s];
            if (s == 3 && smallerPulse) box = new double[]{.30, .82, .70, .91};
            int x1 = Math.min(w - 1, Math.max(0, (int)Math.floor(w * box[0])));
            int y1 = Math.min(h - 1, Math.max(0, (int)Math.floor(h * box[1])));
            int x2 = Math.min(w, Math.max(x1 + 1, (int)Math.ceil(w * box[2])));
            int y2 = Math.min(h, Math.max(y1 + 1, (int)Math.ceil(h * box[3])));
            int black = 0;
            for (int y = y1; y < y2; y++) {
                for (int x = x1; x < x2; x++) {
                    if (pixels[y * w + x] <= threshold) black++;
                }
            }
            probes[s] = (double)black / ((x2 - x1) * (y2 - y1));
        }

        int bestDigit = -1;
        double best = -1, second = -1;
        String segments = "ABCDEFG";
        for (int digit = 0; digit < 10; digit++) {
            double score = 0;
            for (int i = 0; i < 7; i++) {
                boolean expected = DIGIT_SEGMENTS[digit].indexOf(segments.charAt(i)) >= 0;
                score += expected ? probes[i] : 1 - probes[i];
            }
            score /= 7;
            if (score > best) { second = best; best = score; bestDigit = digit; }
            else if (score > second) second = score;
        }
        // Ambiguous segment patterns should be entered by hand, not silently used.
        if (best < .77 || best - second < .055) return null;
        return new Digit(bestDigit, best);
    }

    private static int otsu(int[] histogram, int total, long sum) {
        long belowSum = 0;
        int belowCount = 0;
        double max = -1;
        int threshold = 0;
        for (int gray = 0; gray < 256; gray++) {
            belowCount += histogram[gray];
            if (belowCount == 0) continue;
            int aboveCount = total - belowCount;
            if (aboveCount == 0) break;
            belowSum += (long)gray * histogram[gray];
            double meanBelow = (double)belowSum / belowCount;
            double meanAbove = (double)(sum - belowSum) / aboveCount;
            double separation = (double)belowCount * aboveCount *
                (meanBelow - meanAbove) * (meanBelow - meanAbove);
            if (separation > max) { max = separation; threshold = gray; }
        }
        return threshold;
    }
}
