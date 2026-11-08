import rs.local.mojpritisak.BpParser;
public class ParserSmokeTest {
    public static void main(String[] args) {
        check("SYS 142 mmHg\nDIA 83 mmHg\nPUL 68 /min", 142,83,68);
        check("142\n83\n68", 142,83,68);
        check("12:30\n129\n82\n74",129,82,74);
        check("SYS: 120\nDIA: 78\nPULSE: 62",120,78,62);
        System.out.println("OCR parser smoke tests passed");
    }
    private static void check(String s, int a, int b, int c) {
        BpParser.Result r = BpParser.parse(s);
        if (!Integer.valueOf(a).equals(r.sys) || !Integer.valueOf(b).equals(r.dia) || !Integer.valueOf(c).equals(r.pulse))
            throw new AssertionError("Incorrect for " + s + ": " + r.sys + "/" + r.dia + "/" + r.pulse);
    }
}
