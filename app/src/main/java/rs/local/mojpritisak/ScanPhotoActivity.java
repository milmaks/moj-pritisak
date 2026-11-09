package rs.local.mojpritisak;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.InputStream;

/** Works for arbitrary photo positions via detected labels and editable ROI boxes. */
public final class ScanPhotoActivity extends Activity {
    private ScanOverlayView overlay;
    private Bitmap photo;
    private TextRecognizer recognizer;
    private SmartNumberReader reader;
    private TextView status;
    private Button scan;
    private boolean reading;
    private int green=0xFF007F75;
    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private Button button(String title){Button b=new Button(this);b.setText(title);b.setAllCaps(false);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        reader=new SmartNumberReader(recognizer);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(8),dp(7),dp(8),dp(7));layout.setBackgroundColor(0xFFF6F8FA);
        TextView header=new TextView(this);header.setText("Označi SAMO cifre za SYS, DIA i PULS");header.setTextColor(Color.BLACK);header.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.setTextSize(17);header.setGravity(Gravity.CENTER);layout.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        overlay=new ScanOverlayView(this);layout.addView(overlay,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);
        String[] labels={"SYS", "DIA", "PULS"};
        for(int i=0;i<3;i++){final int index=i;Button b=button(labels[i]);row.addView(b,new LinearLayout.LayoutParams(0,dp(48),1));b.setOnClickListener(v->overlay.select(index));}
        layout.addView(row);
        status=new TextView(this);status.setText("Prevuci obojene okvire preko brojeva. Ugao okvira menja veličinu.");status.setTextSize(13);status.setTextColor(0xFF34465D);status.setPadding(dp(7),dp(4),dp(7),dp(4));layout.addView(status);
        LinearLayout buttons=new LinearLayout(this);
        Button auto=button("Nađi oznake");scan=button("OČITAJ");scan.setTextColor(green);
        buttons.addView(auto,new LinearLayout.LayoutParams(0,dp(55),1));buttons.addView(scan,new LinearLayout.LayoutParams(0,dp(55),1));layout.addView(buttons);
        setContentView(layout);
        auto.setOnClickListener(v->detect());scan.setOnClickListener(v->read());
        String uri=getIntent().getStringExtra("photo_uri");
        if(uri==null){finish();return;}
        status.setText("Učitavam fotografiju…");
        new Thread(()->{
            try{Bitmap b=decode(Uri.parse(uri));runOnUiThread(()->{if(isFinishing()){b.recycle();return;}photo=b;overlay.setBitmap(b);detect();});}
            catch(Exception error){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Fotografija nije učitana").setMessage(error.toString()).setPositiveButton("U redu",(d,w)->finish()).show());}
        },"bp-photo").start();
    }
    private void detect(){
        if(photo==null)return;
        status.setText("Pronalazim natpise SYS / DIA / PULSE…");
        reader.locate(photo,r->{if(isFinishing())return;overlay.use(r);status.setText("Proveri tri okvira; ako neki nije tačan, izaberi njegovo dugme, pomeri okvir i pritisni OČITAJ.");});
    }
    private void read(){
        if(photo==null||reading)return;
        reading=true;scan.setEnabled(false);status.setText("Čitam samo brojeve u označenim zonama…");
        reader.read(photo,overlay.regions(),true,(result,detail)->{
            if(isFinishing())return;
            reading=false;scan.setEnabled(true);
            String values=(result.sys==null?"?":result.sys)+" / "+(result.dia==null?"?":result.dia)+" · puls "+(result.pulse==null?"?":result.pulse);
            status.setText("Očitano: "+values+". Proveri rezultat na aparatu.");
            new AlertDialog.Builder(this).setTitle("Očitano: "+values)
                .setMessage("Ako broj nedostaje ili nije tačan, možeš dodatno podesiti okvire ili ručno izmeniti vrednosti.\n\nOCR detalji: "+detail)
                .setNegativeButton("Podesi okvire",null)
                .setPositiveButton("Proveri / unesi ručno",(d,w)->{
                    android.content.Intent intent=new android.content.Intent();
                    intent.putExtra("sys",result.sys==null?-1:result.sys);
                    intent.putExtra("dia",result.dia==null?-1:result.dia);
                    intent.putExtra("pulse",result.pulse==null?-1:result.pulse);
                    setResult(RESULT_OK,intent);finish();
                }).show();
        });
    }
    private Bitmap decode(Uri uri)throws Exception {
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
        if(opts.outWidth<1||opts.outHeight<1)throw new IllegalArgumentException("Nepoznata slika");
        opts.inJustDecodeBounds=false;opts.inSampleSize=1;
        while(opts.outWidth/opts.inSampleSize>1900||opts.outHeight/opts.inSampleSize>1900)opts.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,opts);}
        if(bitmap==null)throw new IllegalArgumentException("Slika nije dekodirana");
        try(InputStream in=getContentResolver().openInputStream(uri)){
            ExifInterface exif=new ExifInterface(in);
            int deg=0;
            switch(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)){
                case ExifInterface.ORIENTATION_ROTATE_90:deg=90;break;
                case ExifInterface.ORIENTATION_ROTATE_180:deg=180;break;
                case ExifInterface.ORIENTATION_ROTATE_270:deg=270;break;
            }
            if(deg!=0){Matrix m=new Matrix();m.postRotate(deg);Bitmap upright=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);bitmap.recycle();return upright;}
        }catch(Exception ignored){}
        return bitmap;
    }
    @Override protected void onDestroy(){super.onDestroy();if(recognizer!=null)recognizer.close();if(photo!=null&&!photo.isRecycled()&&!reading)photo.recycle();}
}
