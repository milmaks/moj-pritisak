package rs.local.mojpritisak;

import java.io.StringReader;
import java.util.List;

public class CsvSmokeTest {
    public static void main(String[] args) throws Exception {
        List<Reading> normal = CsvImporter.from(new StringReader(
            "Date,Time,Systolic,Diastolic,Pulse,Medication,Left/Right,Arm/Wrist,Posture,Notes\n" +
            "2026-11-08,08:15,129,73,63,,,,,Omron RS3\n"));
        check(normal.size() == 1 && normal.get(0).sys == 129 && normal.get(0).dia == 73
            && normal.get(0).pulse == 63, "Instant BP standard format");
        List<Reading> local = CsvImporter.from(new StringReader(
            "\uFEFFDatum;Gornji (mmHg);Donji (mmHg);Puls;Napomena\n" +
            "\"08.11.2026. 21:30\";129;73;63;\"Omron; RS3\"\n"));
        check(local.size() == 1 && "Omron; RS3".equals(local.get(0).note), "Local CSV format");
        try {
            CsvImporter.from(new StringReader("Date,Systolic (Morning),Diastolic (Morning),Pulse (Morning)\n2026-11-08,129,73,63\n"));
            throw new AssertionError("Daily CSV should be rejected");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("dnevni"), "Daily format error");
        }
        System.out.println("CSV parser smoke tests passed");
    }
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
    }
}
