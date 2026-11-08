package rs.local.mojpritisak;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Heuristics for seven-segment BP displays; every result must be checked by the user. */
public final class BpParser {
    private BpParser() {}

    public static final class Result {
        public final Integer sys, dia, pulse;
        Result(Integer sys, Integer dia, Integer pulse) {
            this.sys = sys; this.dia = dia; this.pulse = pulse;
        }
        public boolean complete() { return sys != null && dia != null && pulse != null; }
    }

    private static Integer labelValue(String text, String name, int min, int max) {
        // The number may be on the same line as the label, before the next label.
        Matcher m = Pattern.compile("(?i)\\b(?:" + name + ")\\b[^\\n\\r\\d]{0,22}(\\d{2,3})\\b").matcher(text);
        while (m.find()) {
            int v = Integer.parseInt(m.group(1));
            if (v >= min && v <= max) return v;
        }
        return null;
    }

    public static Result parse(String raw) {
        if (raw == null) raw = "";
        String text = raw.replace('：', ':').replace('Ｏ', '0').replace('０', '0')
                .replace('１','1').replace('２','2').replace('３','3').replace('４','4')
                .replace('５','5').replace('６','6').replace('７','7').replace('８','8').replace('９','9');
        Integer sys = labelValue(text, "SYS|SYSTOLIC", 60, 280);
        Integer dia = labelValue(text, "DIA|DIASTOLIC", 30, 180);
        Integer pulse = labelValue(text, "PUL|PULSE|HR|BPM", 30, 220);
        if (sys != null && dia != null && pulse != null && sys > dia) return new Result(sys, dia, pulse);

        List<Integer> numbers = new ArrayList<>();
        Matcher m = Pattern.compile("(?<![0-9])([0-9]{2,3})(?![0-9])").matcher(text);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 30 && n <= 280) numbers.add(n);
        }

        int bestScore = Integer.MIN_VALUE;
        Result best = null;
        // Contiguous numeric triplets are likelier to be SYS/DIA/PUL than arbitrary dates.
        for (int i = 0; i + 2 < numbers.size(); i++) {
            int a = numbers.get(i), b = numbers.get(i + 1), c = numbers.get(i + 2);
            if (a < 60 || a > 280 || b < 30 || b > 180 || c < 30 || c > 220 || a <= b) continue;
            int score = 20;
            if (a >= 90 && a <= 180) score += 4;
            if (b >= 50 && b <= 110) score += 4;
            if (c >= 45 && c <= 110) score += 3;
            if (a - b >= 20 && a - b <= 90) score += 4;
            // A reading often appears near the beginning of the OCR text.
            score -= i;
            if (score > bestScore) { bestScore = score; best = new Result(a, b, c); }
        }
        if (best != null) return new Result(
                sys != null ? sys : best.sys,
                dia != null ? dia : best.dia,
                pulse != null ? pulse : best.pulse);
        return new Result(sys, dia, pulse);
    }
}
