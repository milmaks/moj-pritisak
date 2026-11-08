# Moj Pritisak – Android aplikacija bez reklama

Samostalna, besplatna Android aplikacija za beleženje krvnog pritiska. Napravljena kao funkcionalna alternativa aplikaciji Instant Blood Pressure Tracker, ali bez kopiranja njenog izvornog koda ili brendiranja.

## Funkcije

- Slikanje ekrana aparata standardnom Android kamerom ili izbor postojeće slike.
- OCR prepoznavanje SYS (gornji), DIA (donji) i PUL (puls) pomoću *bundled* Google ML Kit modela, lokalno na telefonu.
- Obavezna ručna provera i potvrda OCR rezultata pre snimanja; ne treba se osloniti na OCR za medicinske odluke.
- Ručni unos, uređivanje, brisanje, napomene i datum/vreme.
- Istorija i grafikon dnevnih proseka za 14 dana, proseci za 7 i 30 dana, današnji jutarnji i večernji prosek.
- Lokalna SQLite baza, bez naloga, cloud servisa, oglasa, kupovina i internet dozvole.
- Izvoz svih merenja u CSV ili PDF, kao i uvoz CSV zapisa (pojedinačna merenja) iz Instant BP-a ili ove aplikacije.

## Instalacija na Galaxy S25

**Izvorni kod u ovom ZIP-u nije APK.** Android APK je potrebno kompajlirati. Iz ovog okruženja nije bilo moguće pokrenuti Android SDK i Gradle, pa izvorni kod još nije validiran punim Android build-om niti isproban na fizičkom telefonu.

### Opcija A: Android Studio

1. U Android Studio (na računaru) otvori folder `MojPritisak` kao projekat (`Open`).
2. Postavi **Gradle JDK 17** i preuzmi **Android SDK 35** kada Studio to ponudi. Projekat koristi Android Gradle Plugin 8.7.3 i Gradle 8.9. (Wrapper properties postoje, wrapper JAR nije priložen; Studio koristi svoju Gradle integraciju.)
3. Sačekaj Gradle Sync, zatim izaberi **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Posle uspešnog builda, APK se nalazi u `app/build/outputs/apk/debug/app-debug.apk`.
5. Prebaci APK na telefon (USB, Quick Share, itd.) i otvori ga. Na Samsung One UI možda će biti potrebno da za aplikaciju *My Files* dozvoliš instalaciju iz nepoznatog izvora, a opcija *Auto Blocker* može blokirati sideloading. Uključi zaštitu ponovo nakon instalacije. Instaliraj samo APK koji si sam izgradio.

### Opcija B: GitHub Actions (bez Android Studija)

1. Napravi novi **privatni** GitHub repozitorijum i u njega postavi sadržaj foldera `MojPritisak` (ne sam ZIP).
2. Otvori **Actions > Build Android APK > Run workflow** (ili pushuj na `main`).
3. Ako build uspe, otvori završen workflow, preuzmi artifact `MojPritisak-debug-apk`, raspakuj i instaliraj `app-debug.apk` na telefon.

**Ispravka GitHub Actions (novembar 2026):** `android-actions/setup-android@v3` pokušavao je da instalira uklonjeni Android SDK paket `tools`, pa se workflow prekidao sa `Failed to find package 'tools'`. U ovom projektu je `setup-android` ažuriran na `@v4`, uz `packages: platform-tools`. Ako si prethodnu verziju već postavio na GitHub, dovoljno je da izmeniš samo `.github/workflows/build-apk.yml`.

**Ispravka zavisnosti (novembar 2026):** Ako build pada na `:app:checkDebugDuplicateClasses` zbog `kotlin-stdlib:1.8.22` i `kotlin-stdlib-jdk7/jdk8:1.6.21`, dodaj `implementation platform('org.jetbrains.kotlin:kotlin-bom:1.8.22')` u `dependencies` blok fajla `app/build.gradle`. Ova verzija projekta već sadrži izmenu; nema potrebe da menjaš GitHub Actions workflow.

**Potpis:** Gradle automatski potpisuje debug APK razvojnim ključem. Za redovna ažuriranja moraš koristiti isti potpisni ključ; GitHub hosted runner može generisati drugačiji debug key za naredni build, pa će za ažuriranje možda biti potrebna deinstalacija (uz gubitak lokalne baze). Za dugoročno korišćenje napravi sopstveni release keystore ili koristi isti računar/keystore i čuvaj CSV rezervne kopije.

## Import iz Instant BP

U originalnoj aplikaciji izaberi **CSV Export > Each Blood Pressure Record** (ne *Daily Blood Pressure Records*). Kopiraj izvezeni CSV na telefon i u aplikaciji `Moj Pritisak` izaberi **Uvezi merenja iz Instant BP CSV**. Podržani su uobičajeni formati datuma i CSV sa separatorima `,` ili `;`. Pre potvrde prikazuje broj pronađenih novih merenja; duplikati sa istim datumom/vremenom, SYS, DIA i pulsom se preskaču. Specifičan CSV format neke verzije originalne aplikacije može tražiti doradu parsera.

## Privatnost i ograničenja

Nema internet dozvole, naloga, reklama ni SDK-a za analitiku. Slike iz kamere su privremene i brišu se nakon obrade; izabrane slike iz galerije se ne kopiraju u bazu. Baza se čuva lokalno. `android:allowBackup=false` onemogućava automatski Android backup, pa povremeno izvezi CSV i čuvaj ga na bezbednom mestu.

OCR oslanja se na kvalitet fotografije i na dizajn ekrana aparata; može naročito grešiti kod sedmosegmentnog displeja ili refleksije. Zato su sva polja izmenjiva i obavezna je provera pre čuvanja. Aplikacija ne meri krvni pritisak i nije medicinsko sredstvo.

## Projekat

- Java / Android SDK 35, min SDK 26.
- `MainActivity.java` – ekran, snimanje, rad sa podacima, CSV/PDF, ML Kit.
- `BpParser.java` – heuristike čitanja očitanih brojeva.
- `ReadingsDb.java` – SQLite podaci.
- `CsvImporter.java` – import pojedinačnih BP merenja.
- `PressureChart.java` – grafikon dnevnih proseka.
- `.github/workflows/build-apk.yml` – automatski Android build APK-a.

Provera čistog Java parsera: `javac -d /tmp/bptest app/src/main/java/rs/local/mojpritisak/BpParser.java ParserSmokeTest.java && java -cp /tmp/bptest ParserSmokeTest`.

## Verzija 1.1 – dijagnostika (Samsung Galaxy S25)

- Izmenjen unos iz galerije: čitanje `content://` slike preko `ContentResolver`, obrada u pozadinskoj niti, bez pretpostavke da je fajl dostupan kao klasična putanja.
- Fotografije 720×720 ostaju u punoj rezoluciji. Čita se i EXIF rotacija (originalni Samsung JPG je 90° bočno rotiran u sirovim pikselima). Ako OCR ne pročita sve tri vrednosti, prikazuje prepoznat tekst i omogućava dopunu.
- Greške nisu sakrivene: detalji mogu da se kopiraju iz dijaloga (nema automatskog slanja). Ako proces padne, lokalni izveštaj se prikazuje pri sledećem pokretanju.
- CSV uvoz ide u pozadini; podržani su UTF-8/UTF-16 BOM, Instant BP pojedinačni zapisi i sopstveni CSV. Zbirni dnevni CSV se odbija umesto pogrešnog uvoza.
- Uvoz merenja upisuje sve redove atomskom SQLite transakcijom.

**Važno:** Build je i dalje potrebno uraditi kroz GitHub Actions; ovaj kod nije testiran na pravom S25, a pouzdanost ML Kit OCR-a na sedmosegmentnom Omron displeju nije zagarantovana.

**Ažuriranje APK-a:** Ako Android prijavi da nova verzija ne može da se instalira preko prethodne, moguće je da GitHub Actions koristi različite debug sertifikate. Sačuvaj postojeće podatke u CSV pre deinstalacije stare verzije.
