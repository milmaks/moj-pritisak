# Moj Pritisak v1.4 — adaptivni OCR i live kamera (eksperimentalno)

Android aplikacija za beleženje krvnog pritiska bez reklama, registracije i internet dozvole. Nije medicinsko sredstvo. **Ne meri krvni pritisak**; samo čita fotografiju aparata i čuva korisnički potvrđene vrednosti.

## Šta je novo u v1.4

**Galerija / fotografija:**
1. Nakon izbora slike otvara se ekran za izdvajanje tri numeričke oblasti.
2. ML Kit na *celoj slici* traži natpise `SYS`, `DIA` i `PULSE`, pa na osnovu njihovih pozicija predlaže oblasti za brojeve. Nisu korišćene koordinate modela Omron RS3.
3. Korisnik može izabrati `SYS`, `DIA`, `PULS`, prevući odgovarajući okvir preko cifara i promeniti njegovu veličinu povlačenjem donjeg desnog ugla.
4. Svaka oblast obrađuje se zasebno. Primena dodatne normalizacije kontrasta smanjuje uticaj osvetljenja.
5. Ako obični OCR preskoči sedmosegmentne cifre, poseban `FlexibleSevenSegmentReader` automatski grupiše moguće cifre na osnovu stupaca piksela, detektuje raspone cifara i proverava svih sedam segmenata. Ovaj algoritam nema koordinate konkretnog modela aparata.
6. Ako dva algoritma pročitaju različite brojeve, sporni broj ostaje prazan. Nijedan rezultat se ne čuva bez korisničke potvrde.

**Live kamera (beta):**
1. Dugme `Live čitanje kamerom` koristi CameraX za pregled zadnje kamere.
2. Na približno svakih 2 sekunde traži oznake SYS/DIA/PULSE na kadru, izdvaja njihova numerička polja i pokreće oba OCR postupka.
3. Kada 2 uzastopna potpuna očitavanja daju identičan SYS, DIA i puls, otvara se standardni dijalog za potvrdu. Nema automatskog snimanja.
4. Live režim zahteva dozvolu za kameru. Obrada je offline. Ako automatski okvir nije dobar, u galeriji je dostupan ručni izbor oblasti.

**Uklonjen je v1.3 reader** koji je koristio piksel-koordinate jednog Omron RS3 uzorka.

## Realna ograničenja

- Ovo NIJE univerzalno garantovano očitavanje svake slike ili svih modela aparata. Položaji natpisa, različite vrste LCD-a, odsjaj, veoma kos kadar, slab kontrast i skrivene cifre mogu onemogućiti automatsku detekciju. Ručno podešavanje okvira je fallback, ne zamena za proveru brojeva.
- Automatska lokalizacija se oslanja na oznake `SYS/DIA/PULSE` u kadru. Ako nema oznaka, koriste se početni okviri koje treba podesiti.
- Sedmosegmentni algoritam podnosi promenljive veličine i pomeranja brojčanih grupa, ali ne ispravlja proizvoljnu perspektivu niti trenira sopstveni ML model.
- Podaci sa slike se lokalno obrađuju, ali *OCR može pogrešiti*. **Svaku vrednost obavezno uporedi sa aparatom** pre nego što je sačuvaš.
- Live kamera nije testirana na fizičkom Samsung Galaxy S25; moguće su Android/CameraX prilagodbe.

## Testovi

Lokalno, na jednoj dostavljenoj Omron fotografiji, tri različito isečene numeričke oblasti prošle su test `FlexibleSegmentSmokeTest` sa rezultatom **129 / 73 / 63**. Prošle su i provere na malo široj zoni nakon izdvajanja displeja, kao i Java parser i CSV testovi. Nisu prošle sve transformacije: na nekim pomeranjima ivica, osvetljenjima i dvostruko uvećanom pulsu rezultat se odbacuje ili može biti netačan. Ovi testovi ne predstavljaju evaluaciju na raznovrsnim aparatima i fotografijama.

## Instalacija / GitHub Actions

Potreban je Android build; ovaj ZIP sadrži **izvorni kod, ne APK**.

1. **Sačuvaj CSV rezervnu kopiju** prethodnih merenja u staroj aplikaciji (`Izvezi CSV`), pre bilo kakve deinstalacije.
2. U postojeći GitHub repo kopiraj sadržaj foldera `MojPritisak` iz ZIP-a, uključujući `.github/workflows/build-apk.yml`.
3. GitHub: `Actions` → `Build Android APK` → `Run workflow`. Ako uspe, preuzmi artifact `MojPritisak-debug-apk` i instaliraj `app-debug.apk`.
4. Ako Android odbije ažuriranje zbog različitih potpisa, **ne deinstaliraj staru verziju bez sačuvanog CSV-a**; deinstalacija briše lokalnu bazu. Za dugoročne nadogradnje koristi isti stabilni keystore.
5. Ako build padne, kopiraj poslednji deo loga iz GitHub Actions. SDK/Gradle konfiguracija i dalje koristi Gradle 8.9, AGP 8.7.3, JDK 17 i Android SDK 35.

**Novo:** projekt koristi CameraX 1.5.2 i `androidx.activity:activity:1.10.1` za live pregled kamere. Iz Android Studija možeš pokrenuti i `Build APK(s)` ako imaš instaliran SDK 35.

Ovo okruženje nema pun Android SDK, pa **nisam mogao potvrditi pun Gradle build ni instalaciju na S25**.

## Offline test bez Android SDK-a

`javac -d /tmp/bp app/src/main/java/rs/local/mojpritisak/{Reading,CsvImporter,BpParser,FlexibleSevenSegmentReader}.java ParserSmokeTest.java CsvSmokeTest.java FlexibleSegmentSmokeTest.java`

`java -cp /tmp/bp ParserSmokeTest`

`java -cp /tmp/bp rs.local.mojpritisak.CsvSmokeTest`

`java -cp /tmp/bp FlexibleSegmentSmokeTest /putanja/sys.png /putanja/dia.png /putanja/pulse.png`

Tri putanje su tvoje isečene numeričke oblasti sa vrednostima 129, 73, 63. Slika i tvoj lični CSV nisu uključeni u arhivu.

## Privatnost i sačuvane funkcije

Nema internet dozvole, oglasa i analitike. SQLite baza, grafikon, proseci, ručni unos, izvoz u PDF i CSV, kao i uvoz Instant BP CSV zaglavlja `Date,Time,Top,Bottom,Pulse` ostaju nepromenjeni. ML Kit model je ugrađen u aplikaciju.
