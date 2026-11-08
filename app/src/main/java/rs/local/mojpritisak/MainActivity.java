package rs.local.mojpritisak;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Log;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int TAKE_PHOTO = 101, CHOOSE_PHOTO = 102, EXPORT_CSV = 103, EXPORT_PDF = 104, IMPORT_CSV = 105;
    private static final int BG = Color.rgb(245,247,251), DARK = Color.rgb(23,38,60);
    private static final int MUTED = Color.rgb(98,111,132), TEAL = Color.rgb(5,127,117);
    private static final int LIGHT = Color.rgb(226,236,242);
    private static final Locale SR = Locale.forLanguageTag("sr-RS");
    private ReadingsDb db;
    private Uri pendingPhoto;
    private File pendingPhotoFile;
    private LinearLayout page;
    private static boolean crashReporterInstalled = false;
    private static final String PREFS = "moj_pritisak_diagnostics";

    private float dp(float n) { return getResources().getDisplayMetrics().density * n; }
    private int px(float n) { return (int)(dp(n)+.5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        installCrashReporter();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        db = new ReadingsDb(this);
        if (state != null && state.getString("photo") != null) {
            pendingPhoto = Uri.parse(state.getString("photo"));
            String path = state.getString("photo_path");
            if (path != null) pendingPhotoFile = new File(path);
        }
        render();
        showPreviousCrash();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pendingPhoto != null) out.putString("photo", pendingPhoto.toString());
        if (pendingPhotoFile != null) out.putString("photo_path", pendingPhotoFile.getAbsolutePath());
    }
    @Override protected void onDestroy() { if (db != null) db.close(); super.onDestroy(); }

    // No crash analytics or network: diagnostic stays on this device until the user copies it.
    private void installCrashReporter() {
        if (crashReporterInstalled) return;
        crashReporterInstalled = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        final Context app = getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                app.getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString("last_crash", sw.toString().substring(0, Math.min(sw.toString().length(), 7000)))
                    .commit();
            } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
    private void showPreviousCrash() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String details = prefs.getString("last_crash", null);
        if (details == null) return;
        prefs.edit().remove("last_crash").apply();
        showDiagnostic("Aplikacija se prethodno zatvorila", "Sačuvan je lokalni izveštaj o grešci.\n\n" + details, false);
    }
    private void showDiagnostic(String title, String details, boolean manual) {
        Log.e("MojPritisak", title + ": " + details);
        if (isFinishing() || isDestroyed()) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(details.length() > 6500 ? details.substring(0,6500) : details)
            .setNegativeButton("Zatvori", null)
            .setNeutralButton("Kopiraj detalje", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Moj Pritisak greška", details));
                toast("Detalji kopirani. Pošalji ih u razgovor radi dijagnostike.");
            });
        if (manual) builder.setPositiveButton("Ručni unos", (dialog, which) -> edit(null,null));
        builder.show();
    }
    private static String details(Exception error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private GradientDrawable background(int color, int radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(radius));
        if (strokeColor != 0) g.setStroke(px(1), strokeColor);
        return g;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private LinearLayout.LayoutParams lp(int w, int h, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.bottomMargin = px(bottom); return p;
    }
    private View card(LinearLayout parent, int pad, int marginBottom) {
        LinearLayout c = column(); c.setPadding(px(pad), px(pad), px(pad), px(pad));
        c.setBackground(background(Color.WHITE, 18, LIGHT));
        parent.addView(c, lp(-1,-2,marginBottom));
        return c;
    }
    private Button button(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : DARK);
        b.setBackground(background(primary ? TEAL : Color.rgb(235,242,245), 12, 0));
        b.setPadding(px(7), px(7), px(7), px(7));
        b.setMinHeight(px(50));
        b.setOnClickListener(click);
        return b;
    }
    private void render() {
        List<Reading> readings = db.all();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        page = column();
        page.setPadding(px(17),px(18),px(17),px(32));
        scroll.addView(page);
        setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
        }

        TextView h = text("MOJ PRITISAK", 26, DARK, true);
        page.addView(h, lp(-1,-2,2));
        TextView sub = text("Privatno beleženje • bez reklama • offline", 12, MUTED, false);
        page.addView(sub, lp(-1,-2,21));

        LinearLayout actions = (LinearLayout)card(page,14,13);
        actions.addView(text("Novo merenje",17,DARK,true),lp(-1,-2,9));
        LinearLayout row = new LinearLayout(this);
        actions.addView(row,lp(-1,-2,8));
        Button camera = button("📷  Skeniraj",true,v->capture());
        Button gallery = button("🖼  Iz galerije",false,v->pickImage());
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0,px(54),1f); a.rightMargin = px(8);
        row.addView(camera,a); row.addView(gallery,new LinearLayout.LayoutParams(0,px(54),1f));
        actions.addView(button("＋  Ručni unos",false,v->edit(null,null)),lp(-1,px(45),0));

        LinearLayout summary = (LinearLayout)card(page,15,13);
        summary.addView(text("Pregled",17,DARK,true),lp(-1,-2,12));
        if (readings.isEmpty()) {
            summary.addView(text("Još nema merenja. Dodaj prvo skeniranjem ili ručno.",14,MUTED,false));
        } else {
            Reading latest = readings.get(0);
            summary.addView(text(latest.sys+" / "+latest.dia+" mmHg",30,TEAL,true),lp(-1,-2,1));
            summary.addView(text("Poslednje merenje · "+formatDate(latest.timestamp)+" · Puls "+latest.pulse,12,MUTED,false),lp(-1,-2,11));
            Stats week = stats(readings,System.currentTimeMillis()-7L*24*60*60*1000,-1);
            Stats month = stats(readings,System.currentTimeMillis()-30L*24*60*60*1000,-1);
            summary.addView(text("7 dana:   "+week.format()+"  ("+week.n+" merenja)",14,DARK,false),lp(-1,-2,5));
            summary.addView(text("30 dana: "+month.format()+"  ("+month.n+" merenja)",14,DARK,false),lp(-1,-2,8));
            Calendar cal=Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
            Stats morning=stats(readings,cal.getTimeInMillis(),0);
            Stats evening=stats(readings,cal.getTimeInMillis(),1);
            summary.addView(text("Danas ujutru: "+morning.format()+"   |   uveče: "+evening.format(),12,MUTED,false));
        }

        LinearLayout chartCard = (LinearLayout)card(page,11,13);
        TextView chartTitle=text("Trend · poslednjih 14 dana",16,DARK,true);
        chartTitle.setPadding(px(4),px(5),0,0);
        chartCard.addView(chartTitle,lp(-1,-2,5));
        chartCard.addView(new PressureChart(this,readings),lp(-1,px(230),0));
        chartCard.addView(text("Dnevni proseci (mmHg)",11,MUTED,false));

        LinearLayout export = (LinearLayout)card(page,13,13);
        export.addView(text("Izveštaj i rezervna kopija",16,DARK,true),lp(-1,-2,8));
        LinearLayout expRow = new LinearLayout(this);
        export.addView(expRow,lp(-1,-2,8));
        Button csv=button("Izvezi CSV",false,v->exportFile(false));
        Button pdf=button("Izvezi PDF",false,v->exportFile(true));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,px(48),1);sp.rightMargin=px(8);
        expRow.addView(csv,sp);expRow.addView(pdf,new LinearLayout.LayoutParams(0,px(48),1));
        export.addView(button("Uvezi merenja iz Instant BP CSV",false,v->importFile()),lp(-1,px(48),0));

        page.addView(text("Istorija · "+readings.size()+" merenja",18,DARK,true),lp(-1,-2,10));
        int max = Math.min(readings.size(),60);
        for (int i=0; i<max; i++) {
            Reading r=readings.get(i);
            LinearLayout item=(LinearLayout)card(page,13,7);
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
            item.addView(line,lp(-1,-2,4));
            TextView main=text(r.sys+" / "+r.dia,20,DARK,true);
            line.addView(main,new LinearLayout.LayoutParams(0,-2,1f));
            TextView puls=text("♥  "+r.pulse,14,TEAL,true);
            line.addView(puls);
            item.addView(text(formatDate(r.timestamp)+(r.note.isEmpty()?"":"   •   "+r.note),12,MUTED,false));
            item.setOnClickListener(v->edit(r,null));
            item.setOnLongClickListener(v->{
                new AlertDialog.Builder(this).setTitle("Obrisati merenje?")
                    .setMessage(formatDate(r.timestamp)+" · "+r.sys+"/"+r.dia)
                    .setNegativeButton("Otkaži",null)
                    .setPositiveButton("Obriši",(d,w)->{db.delete(r.id);render();})
                    .show();return true;
            });
        }
        if (readings.size()>60)
            page.addView(text("Prikazano je poslednjih 60 merenja. CSV i PDF uključuju sva merenja.",12,MUTED,false));
        page.addView(text("Dodirni merenje za izmenu • zadrži prst za brisanje",11,MUTED,false),lp(-1,-2,10));
        page.addView(text("Aplikacija ne meri pritisak i nije zamena za medicinski savet.",11,MUTED,false));
    }

    private String formatDate(long timestamp) {
        return new SimpleDateFormat("dd.MM.yyyy. HH:mm",SR).format(new Date(timestamp));
    }
    private static final class Stats {
        int n; long sys,dia,pulse;
        void add(Reading r) { n++;sys+=r.sys;dia+=r.dia;pulse+=r.pulse; }
        String format() {return n==0 ? "—" : Math.round((double)sys/n)+" / "+Math.round((double)dia/n);}
    }
    /** period: -1 all, 0 morning 05:00-11:59, 1 evening 18:00-23:59. */
    private Stats stats(List<Reading> readings,long since,int period) {
        Stats s=new Stats();Calendar c=Calendar.getInstance();
        for(Reading r:readings) {
            if(r.timestamp<since) continue;
            if(period!=-1) {
                c.setTimeInMillis(r.timestamp);int hour=c.get(Calendar.HOUR_OF_DAY);
                if(period==0 && (hour<5||hour>=12))continue;
                if(period==1 && hour<18)continue;
            }
            s.add(r);
        }
        return s;
    }

    private void capture() {
        try {
            pendingPhotoFile = File.createTempFile("pressure_", ".jpg",getCacheDir());
            pendingPhoto=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",pendingPhotoFile);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,pendingPhoto);
            i.setClipData(ClipData.newRawUri("photo",pendingPhoto));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(i,TAKE_PHOTO);
        } catch(Exception e) { toast("Nije moguće otvoriti kameru: "+e.getMessage()); }
    }
    private void pickImage() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
            startActivityForResult(i,CHOOSE_PHOTO);
        } catch(Exception e) {toast("Nije moguće otvoriti galeriju.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK) {
            if(requestCode==TAKE_PHOTO)cleanupTemp();
            return;
        }
        try {
            if(requestCode==TAKE_PHOTO) {
                if(pendingPhoto!=null)recognize(pendingPhoto);
                else showDiagnostic("Kamera", "Fotografija nije sačuvana (prazan URI).", true);
            } else if(requestCode==CHOOSE_PHOTO) {
                if(data!=null && data.getData()!=null)recognize(data.getData());
                else showDiagnostic("Galerija", "Nije vraćena putanja do fotografije.", true);
            } else if(requestCode==IMPORT_CSV) {
                if(data!=null&&data.getData()!=null)readImport(data.getData());
                else showDiagnostic("CSV uvoz", "Nije vraćena putanja do CSV fajla.", false);
            } else if(requestCode==EXPORT_CSV||requestCode==EXPORT_PDF) {
                if(data!=null&&data.getData()!=null)saveExport(data.getData(),requestCode==EXPORT_PDF);
            }
        } catch (Exception error) {
            showDiagnostic("Greška pri izboru fajla", details(error), requestCode != IMPORT_CSV);
        }
    }
    private void cleanupTemp() {
        if (pendingPhotoFile!=null) pendingPhotoFile.delete();
        pendingPhotoFile=null;pendingPhoto=null;
    }
    private Bitmap decodePhoto(Uri uri) throws IOException {
        // Use ContentResolver for Samsung Gallery/document-provider content:// URIs.
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Galerija nije vratila čitljiv fajl.");
            BitmapFactory.decodeStream(stream, null, opts);
        }
        if (opts.outWidth <= 0 || opts.outHeight <= 0)
            throw new IOException("Format slike nije podržan. Probaj običan JPG/PNG.");
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = 1;
        // Limits unexpectedly large gallery files; 720x720 images remain unchanged.
        while (opts.outWidth / opts.inSampleSize > 1800 || opts.outHeight / opts.inSampleSize > 1800)
            opts.inSampleSize *= 2;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) throw new IOException("Ne mogu da pročitam sadržaj slike.");
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, opts);
            if (bitmap == null) throw new IOException("Dekodiranje slike nije uspelo.");
            return rotateFromExif(bitmap, uri);
        }
    }
    private Bitmap rotateFromExif(Bitmap bitmap, Uri uri) {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            }
        } catch (Exception error) {
            Log.w("MojPritisak", "EXIF orijentacija nije dostupna", error);
        }
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90); matrix.postScale(-1, 1); break;
            case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(270); break;
            default: return bitmap;
        }
        Bitmap transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (transformed != bitmap) bitmap.recycle();
        return transformed;
    }
    private void recognize(Uri uri) {
        toast("Čitam brojeve sa fotografije…");
        new Thread(() -> {
            try {
                final Bitmap bitmap = decodePhoto(uri);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {bitmap.recycle();cleanupTemp();return;}
                    try {
                        final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                        recognizer.process(InputImage.fromBitmap(bitmap, 0))
                            .addOnSuccessListener(result -> {
                                try {
                                    String raw = result.getText();
                                    BpParser.Result parsed = BpParser.parse(raw);
                                    if (parsed.complete()) edit(null, parsed);
                                    else new AlertDialog.Builder(this)
                                        .setTitle("Brojevi nisu potpuno očitani")
                                        .setMessage("Tekst koji je OCR pročitao:\n" +
                                            (raw.trim().isEmpty()?"(nijedan broj nije prepoznat)":raw.substring(0,Math.min(raw.length(),800))) +
                                            "\n\nMožeš ručno dopuniti vrednosti u sledećem prozoru.")
                                        .setNegativeButton("Otkaži", null)
                                        .setPositiveButton("Dopuni ručno", (d,w)->edit(null,parsed))
                                        .show();
                                } catch (Exception error) {
                                    showDiagnostic("Greška prikaza OCR rezultata", details(error), true);
                                }
                            })
                            .addOnFailureListener(error -> showDiagnostic(
                                "OCR prepoznavanje nije uspelo", details(error), true))
                            .addOnCompleteListener(task -> { recognizer.close();bitmap.recycle();cleanupTemp(); });
                    } catch (Exception error) {
                        bitmap.recycle(); cleanupTemp();
                        showDiagnostic("OCR nije mogao da se pokrene", details(error), true);
                    }
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    cleanupTemp();
                    showDiagnostic("Ne mogu da učitam fotografiju", details(error), true);
                });
            }
        }, "bp-photo-decoder").start();
    }

    private EditText numBox(String label,Integer value) {
        EditText field=new EditText(this);
        field.setSingleLine(true);field.setTextSize(20);field.setTextColor(DARK);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setHint(label);
        if (value!=null)field.setText(value.toString());
        return field;
    }
    private int parseNum(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch(Exception exception) {return -1;}
    }
    private void edit(Reading existing,BpParser.Result scanned) {
        LinearLayout content=column();content.setPadding(px(20),px(6),px(20),0);
        EditText sys=numBox("Gornji (SYS)",existing!=null?existing.sys:scanned!=null?scanned.sys:null);
        EditText dia=numBox("Donji (DIA)",existing!=null?existing.dia:scanned!=null?scanned.dia:null);
        EditText pulse=numBox("Puls (PUL)",existing!=null?existing.pulse:scanned!=null?scanned.pulse:null);
        content.addView(text("Gornji · mmHg",12,MUTED,false)); content.addView(sys,lp(-1,-2,9));
        content.addView(text("Donji · mmHg",12,MUTED,false)); content.addView(dia,lp(-1,-2,9));
        content.addView(text("Puls · otkucaji/min",12,MUTED,false)); content.addView(pulse,lp(-1,-2,9));
        EditText note=new EditText(this);note.setSingleLine(false);note.setMaxLines(3);
        note.setTextSize(15);note.setHint("Napomena (opciono)");
        if(existing!=null)note.setText(existing.note);
        content.addView(note,lp(-1,-2,8));
        Calendar selected=Calendar.getInstance();
        if(existing!=null)selected.setTimeInMillis(existing.timestamp);
        Button date=button("Datum i vreme: "+formatDate(selected.getTimeInMillis()),false,v->{});
        date.setOnClickListener(v->{
            new DatePickerDialog(this,(dpicker,year,month,day)->{
                selected.set(year,month,day);
                new TimePickerDialog(this,(picker,hour,minute)->{
                    selected.set(Calendar.HOUR_OF_DAY,hour);
                    selected.set(Calendar.MINUTE,minute);
                    selected.set(Calendar.SECOND,0);selected.set(Calendar.MILLISECOND,0);
                    date.setText("Datum i vreme: "+formatDate(selected.getTimeInMillis()));
                },selected.get(Calendar.HOUR_OF_DAY),selected.get(Calendar.MINUTE),true).show();
            },selected.get(Calendar.YEAR),selected.get(Calendar.MONTH),selected.get(Calendar.DAY_OF_MONTH)).show();
        });
        content.addView(date,lp(-1,px(48),9));
        if(scanned!=null) {
            TextView hint=text("Proveri sve brojeve na aparatu pre čuvanja. OCR može pogrešiti.",12,MUTED,false);
            content.addView(hint,lp(-1,-2,0));
        }
        ScrollView scrolling=new ScrollView(this);scrolling.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(existing==null?"Potvrdi merenje":"Izmeni merenje")
            .setView(scrolling)
            .setNegativeButton("Otkaži",null)
            .setPositiveButton("Sačuvaj",null).create();
        dialog.setOnShowListener(unused->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int s=parseNum(sys),d=parseNum(dia),p=parseNum(pulse);
            if(s<60||s>280||d<30||d>180||p<30||p>220||s<=d) {
                toast("Proveri vrednosti: gornji 60–280, donji 30–180, puls 30–220; gornji mora biti veći.");
                return;
            }
            if(selected.getTimeInMillis()>System.currentTimeMillis()+60000) {
                toast("Datum merenja ne može biti u budućnosti.");return;
            }
            db.save(new Reading(existing==null?-1:existing.id,selected.getTimeInMillis(),s,d,p,note.getText().toString().trim()));
            dialog.dismiss();render();toast("Merenje je sačuvano.");
        }));
        dialog.show();
    }

    private void importFile() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/comma-separated-values","text/plain","application/vnd.ms-excel"});
        try{startActivityForResult(i,IMPORT_CSV);}
        catch(Exception e){toast("Nije moguće otvoriti CSV fajl.");}
    }
    private static String key(Reading r) {
        return r.timestamp+":"+r.sys+":"+r.dia+":"+r.pulse;
    }
    private Reader csvReader(InputStream source) throws IOException {
        PushbackInputStream in = new PushbackInputStream(source, 3);
        byte[] prefix = new byte[3];
        int count = in.read(prefix);
        if (count < 0) count = 0;
        if (count >= 2 && (prefix[0] & 255) == 0xff && (prefix[1] & 255) == 0xfe) {
            if (count > 2) in.unread(prefix, 2, count - 2);
            return new InputStreamReader(in, StandardCharsets.UTF_16LE);
        }
        if (count >= 2 && (prefix[0] & 255) == 0xfe && (prefix[1] & 255) == 0xff) {
            if (count > 2) in.unread(prefix, 2, count - 2);
            return new InputStreamReader(in, StandardCharsets.UTF_16BE);
        }
        if (count >= 3 && (prefix[0] & 255) == 0xef && (prefix[1] & 255) == 0xbb && (prefix[2] & 255) == 0xbf)
            return new InputStreamReader(in, StandardCharsets.UTF_8);
        if (count > 0) in.unread(prefix, 0, count);
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }
    private void readImport(Uri uri) {
        toast("Učitavam CSV...");
        new Thread(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("Ne mogu da otvorim izabrani CSV fajl.");
                // UTF-8 is used by Instant BP in the normal CSV export mode.
                List<Reading> readings = CsvImporter.from(csvReader(in));
                if (readings.isEmpty()) throw new IOException(
                    "CSV nema nijedno valjano merenje. Proveri da je izvezen 'Each Blood Pressure Record', a ne dnevni prosek.");
                Set<String> known = new HashSet<>();
                for (Reading r : db.all()) known.add(key(r));
                List<Reading> unique = new ArrayList<>();
                for (Reading r : readings) if (known.add(key(r))) unique.add(r);
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed()) return;
                    if(unique.isEmpty()) {toast("Sva merenja već postoje, nema novih za uvoz.");return;}
                    new AlertDialog.Builder(this).setTitle("Uvoz merenja")
                        .setMessage("Pronađeno " + unique.size() + " novih merenja. Uvesti u lokalnu bazu?")
                        .setNegativeButton("Otkaži",null)
                        .setPositiveButton("Uvezi",(dialog,which)->{
                            new Thread(() -> {
                                try {
                                    db.saveAll(unique);
                                    runOnUiThread(() -> {if(!isFinishing()&&!isDestroyed()){
                                        render();toast("Uvezeno " + unique.size() + " merenja.");
                                    }});
                                } catch (Exception error) {
                                    runOnUiThread(() -> showDiagnostic("Čuvanje CSV-a nije uspelo", details(error),false));
                                }
                            }, "bp-csv-save").start();
                        }).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> showDiagnostic("Uvoz CSV-a nije uspeo", details(error), false));
            }
        }, "bp-csv-reader").start();
    }

    private void exportFile(boolean pdf) {
        if(db.all().isEmpty()){toast("Nema merenja za izvoz.");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(pdf?"application/pdf":"text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,"moj-pritisak-"+
            new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date())+(pdf?".pdf":".csv"));
        try {startActivityForResult(intent,pdf?EXPORT_PDF:EXPORT_CSV);}
        catch(Exception ex){toast("Izvoz trenutno nije dostupan.");}
    }
    private static String csvCell(String value) {
        if(value==null)value="";
        // Prevent formula execution when the CSV is opened in spreadsheet programs.
        if(!value.isEmpty() && "=+-@".indexOf(value.charAt(0))>=0)value="'"+value;
        return '"'+value.replace("\"","\"\"")+'"';
    }
    private void saveExport(Uri uri,boolean pdf) {
        try(OutputStream stream=getContentResolver().openOutputStream(uri)) {
            if(stream==null)throw new IllegalStateException("Nije moguće otvoriti fajl.");
            if(pdf)writePdf(stream,db.all());
            else writeCsv(stream,db.all());
            toast(pdf?"PDF izveštaj sačuvan.":"CSV sačuvan — može poslužiti i kao rezervna kopija.");
        } catch(Exception e){toast("Izvoz nije uspeo: "+e.getMessage());}
    }
    private void writeCsv(OutputStream stream,List<Reading> readings) throws Exception {
        // UTF-8 BOM for Excel; semicolon delimiter is friendlier to Serbian locale.
        stream.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
        StringBuilder b=new StringBuilder("Datum;Gornji (mmHg);Donji (mmHg);Puls;Napomena\r\n");
        for(Reading r:readings) b.append(csvCell(formatDate(r.timestamp))).append(';')
            .append(r.sys).append(';').append(r.dia).append(';').append(r.pulse).append(';')
            .append(csvCell(r.note)).append("\r\n");
        stream.write(b.toString().getBytes(StandardCharsets.UTF_8));
    }
    private void pdfText(Canvas c,Paint paint,String text,float x,float y,int size,int color,boolean bold) {
        paint.setTypeface(bold?Typeface.create(Typeface.DEFAULT,Typeface.BOLD):Typeface.DEFAULT);
        paint.setTextSize(size);paint.setColor(color);
        c.drawText(text,x,y,paint);
    }
    private void writePdf(OutputStream stream,List<Reading> readings) throws Exception {
        PdfDocument document=new PdfDocument();
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        final int rows=34;
        int pages=Math.max(1,(readings.size()+rows-1)/rows);
        try {
            for(int pageNum=0;pageNum<pages;pageNum++) {
                PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNum+1).create());
                Canvas c=page.getCanvas(); c.drawColor(Color.WHITE);
                pdfText(c,p,"MOJ PRITISAK",42,52,23,DARK,true);
                pdfText(c,p,"Izvestaj o merenjima krvnog pritiska",42,76,12,MUTED,false);
                if(pageNum==0) {
                    Stats s=stats(readings,0,-1);
                    pdfText(c,p,"Ukupan broj merenja: "+s.n+"    Prosek: "+s.format()+" mmHg",42,103,12,TEAL,true);
                }
                p.setColor(LIGHT);p.setStrokeWidth(1); c.drawLine(42,118,550,118,p);
                pdfText(c,p,"Datum / vreme",42,144,11,DARK,true);
                pdfText(c,p,"SYS",283,144,11,DARK,true);
                pdfText(c,p,"DIA",347,144,11,DARK,true);
                pdfText(c,p,"PUL",411,144,11,DARK,true);
                int start=pageNum*rows,end=Math.min(readings.size(),start+rows);
                for(int i=start;i<end;i++) {
                    Reading r=readings.get(i);int y=170+(i-start)*18;
                    if((i-start)%2==0){p.setColor(BG);c.drawRect(40,y-13,551,y+4,p);}
                    pdfText(c,p,formatDate(r.timestamp),43,y,10,DARK,false);
                    pdfText(c,p,Integer.toString(r.sys),283,y,10,DARK,false);
                    pdfText(c,p,Integer.toString(r.dia),347,y,10,DARK,false);
                    pdfText(c,p,Integer.toString(r.pulse),411,y,10,DARK,false);
                }
                pdfText(c,p,"Merenja sa aparata. Ovo nije medicinska dijagnoza.",42,807,9,MUTED,false);
                pdfText(c,p,"Strana "+(pageNum+1)+" / "+pages,475,807,9,MUTED,false);
                document.finishPage(page);
            }
            document.writeTo(stream);
        } finally {document.close();}
    }
}
