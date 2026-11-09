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
        List<Reading> actualFormat = CsvImporter.from(new StringReader(
            "\uFEFFDate,Time,Top,Bottom,Pulse,Med,L/R,Arm or wrist,Posture,Note\r\n" +
            "2026-07-16,10:57,133,65,77,Yes,R,Upper arm,Sitting,\r\n" +
            "2026-11-08,22:59,129,73,63,-,R,Upper arm,Sitting,\r\n"));
        check(actualFormat.size() == 2 && actualFormat.get(0).sys == 133 &&
            actualFormat.get(0).dia == 65 && actualFormat.get(0).pulse == 77 &&
            actualFormat.get(1).sys == 129 && actualFormat.get(1).dia == 73,
            "Actual Instant BP Top/Bottom header with UTF-8 BOM");
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
