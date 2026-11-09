package rs.local.mojpritisak;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Continuous camera scanning; two matching complete readings before confirmation. */
public final class LiveScanActivity extends ComponentActivity {
    private static final int REQUEST_CAMERA=760;
    private ExecutorService executor;
    private TextRecognizer ocr;
    private SmartNumberReader reader;
    private ProcessCameraProvider provider;
    private PreviewView preview;
    private TextView message;
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private long lastScan=0;
    private String previous="";
    private int streak=0;
    private boolean stopped=false;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        executor=Executors.newSingleThreadExecutor();
        ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        reader=new SmartNumberReader(ocr);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFFF4F7FA);
        message=new TextView(this);message.setText("Usmeri kameru na ekran aparata. Drži telefon mirno i izbegavaj odsjaj.");
        message.setTextSize(16);message.setTextColor(0xFF152E48);message.setGravity(Gravity.CENTER);message.setPadding(12,12,12,12);
        root.addView(message,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout frame=new FrameLayout(this);
        preview=new PreviewView(this);preview.setScaleType(PreviewView.ScaleType.FIT_CENTER);
        frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        frame.addView(new Guide(this),new FrameLayout.LayoutParams(-1,-1));
        root.addView(frame,new LinearLayout.LayoutParams(-1,0,8));
        TextView info=new TextView(this);info.setText("Live: čita se približno svake 2 sekunde. Potrebna su 2 uzastopna ista očitavanja. Rezultat se NE čuva automatski.");
        info.setTextSize(13);info.setTextColor(0xFF40506B);info.setGravity(Gravity.CENTER);
        root.addView(info,new LinearLayout.LayoutParams(-1,0,1));
        Button cancel=new Button(this);cancel.setText("Zatvori kameru");root.addView(cancel);cancel.setOnClickListener(v->finish());
        setContentView(root);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA},REQUEST_CAMERA);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==REQUEST_CAMERA){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startCamera();else{message.setText("Kamera nije dozvoljena. Možeš koristiti galeriju.");}}
    }
    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            try{
                provider=future.get();
                Preview p=new Preview.Builder().build();p.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis=new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setTargetResolution(new Size(960,720)).build();
                analysis.setAnalyzer(executor,this::analyze);
                provider.unbindAll();
                provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis);
            }catch(Exception error){message.setText("Kamera nije pokrenuta: "+error.getMessage());}
        },ContextCompat.getMainExecutor(this));
    }
    private void analyze(ImageProxy proxy){
        long now=android.os.SystemClock.elapsedRealtime();
        if(stopped||now-lastScan<2000||!busy.compareAndSet(false,true)){proxy.close();return;}
        lastScan=now;
        Bitmap image=null;
        try{image=copyImage(proxy);}catch(Exception error){busy.set(false);}
        finally{proxy.close();}
        if(image==null)return;
        final Bitmap frame=image;
        runOnUiThread(()->{
            if(stopped||isFinishing()){frame.recycle();busy.set(false);return;}
            reader.locate(frame,regions->{
                if(stopped||isFinishing()){frame.recycle();busy.set(false);return;}
                reader.read(frame,regions,false,(result,diagnostic)->{
                    frame.recycle();busy.set(false);
                    if(stopped||isFinishing())return;
                    String values=(result.sys==null?"?":result.sys)+" / "+(result.dia==null?"?":result.dia)+" · "+(result.pulse==null?"?":result.pulse);
                    message.setText("Live: "+values+"\n"+(result.complete()?"Proveravam stabilnost očitavanja…":"Približi brojeve i smanji odsjaj. Za ručno označavanje koristi galeriju."));
                    if(result.complete()&&result.sys>result.dia){
                        String signature=result.sys+":"+result.dia+":"+result.pulse;
                        if(signature.equals(previous))streak++;else{previous=signature;streak=1;}
                        if(streak>=2){stopped=true;
                            Intent output=new Intent();output.putExtra("sys",result.sys);output.putExtra("dia",result.dia);output.putExtra("pulse",result.pulse);
                            setResult(RESULT_OK,output);finish();}
                    }else{streak=0;previous="";}
                });
            });
        });
    }
    private Bitmap copyImage(ImageProxy image){
        if(image.getFormat()!=android.graphics.PixelFormat.RGBA_8888)throw new IllegalStateException("RGBA format");
        int w=image.getWidth(),h=image.getHeight();
        ByteBuffer source=image.getPlanes()[0].getBuffer();int stride=image.getPlanes()[0].getRowStride();
        byte[] pixels=new byte[w*h*4];
        for(int row=0;row<h;row++){
            source.position(row*stride);
            source.get(pixels,row*w*4,w*4);
        }
        Bitmap raw=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        raw.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        int rotation=image.getImageInfo().getRotationDegrees();
        if(rotation==0)return raw;
        Matrix matrix=new Matrix();matrix.postRotate(rotation);
        Bitmap result=Bitmap.createBitmap(raw,0,0,w,h,matrix,true);raw.recycle();return result;
    }
    @Override protected void onDestroy(){stopped=true;if(provider!=null)provider.unbindAll();if(ocr!=null)ocr.close();if(executor!=null)executor.shutdown();super.onDestroy();}
    private static final class Guide extends View{
        Paint p=new Paint(3);Guide(android.content.Context c){super(c);setBackgroundColor(Color.TRANSPARENT);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);
            float l=getWidth()*.16f,r=getWidth()*.84f,t=getHeight()*.26f,b=getHeight()*.75f;
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xFF00C4AA);
            c.drawRoundRect(new RectF(l,t,r,b),12,12,p);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTextSize(28);
            c.drawText("Ekran aparata u okvir",l,t-15,p);
        }
    }
}
