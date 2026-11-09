package rs.local.mojpritisak;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Label-based localization followed by *independent* numeric ROI OCR.
 * Never infer a reading from a screen's printed threshold markers (135/85).
 * Runs offline on the device, with no model-specific image coordinates. */
final class SmartNumberReader {
    interface DetectionCallback {void done(ScanRegions regions);}
    interface Callback {void done(BpParser.Result result, String details);}
    private final TextRecognizer recognizer;
    SmartNumberReader(TextRecognizer recognizer){this.recognizer=recognizer;}

    void locate(Bitmap photo,DetectionCallback callback){
        recognizer.process(InputImage.fromBitmap(photo,0))
            .addOnSuccessListener(text->callback.done(ScanRegions.fromLabels(text,photo.getWidth(),photo.getHeight())))
            .addOnFailureListener(e->callback.done(new ScanRegions()));
    }

    void read(Bitmap photo,ScanRegions regions,boolean enhanced,Callback callback){
        final ArrayList<Bitmap> bitmaps=new ArrayList<>();
        final ArrayList<Task<Text>> tasks=new ArrayList<>();
        final ArrayList<Integer> rowOrder=new ArrayList<>();
        try{
            for(int row=0;row<3;row++){
                Bitmap cut=regions.crop(photo,row);
                Bitmap normalized=normalize(cut,false);
                if(cut!=normalized && !cut.isRecycled())cut.recycle();
                bitmaps.add(normalized);rowOrder.add(row);
                tasks.add(recognizer.process(InputImage.fromBitmap(normalized,0)));
                if(enhanced){
                    Bitmap contrast=normalize(regions.crop(photo,row),true);
                    bitmaps.add(contrast);rowOrder.add(row);
                    tasks.add(recognizer.process(InputImage.fromBitmap(contrast,0)));
                }
            }
        }catch(Throwable error){
            recycle(bitmaps);
            callback.done(new BpParser.Result(null,null,null), "Greška izdvajanja: "+error.getMessage());
            return;
        }
        Tasks.whenAllComplete(tasks).addOnCompleteListener(done->{
            @SuppressWarnings("unchecked") Map<Integer,Integer>[] votes=new HashMap[3];
            for(int i=0;i<3;i++)votes[i]=new HashMap<>();
            StringBuilder diagnostic=new StringBuilder();
            for(int i=0;i<tasks.size();i++){
                Task<Text> task=tasks.get(i);int row=rowOrder.get(i);
                if(task.isSuccessful()){
                    String raw=task.getResult().getText();
                    diagnostic.append(row).append(':').append(raw.replace('\n',' ')).append(" | ");
                    Integer v=extract(raw,row);
                    if(v!=null)votes[row].put(v,votes[row].getOrDefault(v,0)+1);
                }else if(task.getException()!=null){
                    diagnostic.append("OCR ").append(row).append(": ").append(task.getException().getMessage()).append(" | ");
                }
            }
            Integer sys=choose(votes[0]),dia=choose(votes[1]),pul=choose(votes[2]);
            recycle(bitmaps);
            final Integer[] mlkit={sys,dia,pul};
            final String trace=diagnostic.toString();
            // Seven-segment-specific OCR, independent from photographed device
            // layout: runs on each user-adjustable/cropped numeric area.
            new Thread(()->{
                Integer[] resolved=mlkit.clone();
                StringBuilder detail=new StringBuilder(trace);
                try {
                    for(int row=0;row<3;row++) {
                        Bitmap cut=regions.crop(photo,row);
                        int w=cut.getWidth(),h=cut.getHeight();
                        int[] pixels=new int[w*h];cut.getPixels(pixels,0,w,0,0,w,h);
                        cut.recycle();
                        FlexibleSevenSegmentReader.Reading digit=FlexibleSevenSegmentReader.read(w,h,pixels,row);
                        if(digit!=null){
                            detail.append(" SEG").append(row).append('=').append(digit.value);
                            // Disagreement is an ambiguous read: never guess.
                            if(resolved[row]!=null && !resolved[row].equals(digit.value)) resolved[row]=null;
                            else if(digit.confidence>=.77) resolved[row]=digit.value;
                        }
                    }
                } catch(Exception error){detail.append(" Segment scan: ").append(error.getMessage());}
                if(resolved[0]!=null&&resolved[1]!=null&&resolved[0]<=resolved[1]){
                    resolved[0]=null;resolved[1]=null;
                }
                BpParser.Result result=new BpParser.Result(resolved[0],resolved[1],resolved[2]);
                new Handler(Looper.getMainLooper()).post(()->callback.done(result,detail.toString()));
            },"bp-segment-decoder").start();
        });
    }
    private static void recycle(List<Bitmap> b){for(Bitmap x:b)if(!x.isRecycled())x.recycle();}
    private static Integer choose(Map<Integer,Integer> votes){
        Integer choice=null;int count=0;
        for(Map.Entry<Integer,Integer> e:votes.entrySet())if(e.getValue()>count){count=e.getValue();choice=e.getKey();}
        // Reject ambiguous equally-scored OCR outputs rather than guess.
        for(Map.Entry<Integer,Integer> e:votes.entrySet())if(!e.getKey().equals(choice)&&e.getValue()==count)return null;
        return choice;
    }
    private static final Pattern NUM=Pattern.compile("(?<!\\d)(\\d{2,3})(?!\\d)");
    private static Integer extract(String raw,int row){
        if(raw==null)return null;
        String text=raw.replace('O','0').replace('o','0').replace('I','1').replace('l','1').replace('S','5');
        Matcher m=NUM.matcher(text);
        Integer found=null;
        while(m.find()){
            int n=Integer.parseInt(m.group(1));
            boolean valid=row==0?n>=60&&n<=280:row==1?n>=30&&n<=180:n>=30&&n<=220;
            if(!valid)continue;
            // More than one candidate in the same strip is ambiguous.
            if(found!=null&&found!=n)return null;
            found=n;
        }
        return found;
    }
    private static Bitmap normalize(Bitmap src,boolean contrast){
        int w=src.getWidth(),h=src.getHeight();
        float scale=Math.min(3.5f,Math.min(900f/w,500f/h));
        if(scale<.15f)scale=.15f;
        int nw=Math.max(1,Math.round(w*scale)),nh=Math.max(1,Math.round(h*scale));
        Bitmap sized=Bitmap.createScaledBitmap(src,nw,nh,true);
        if(src!=sized && !src.isRecycled())src.recycle();
        if(!contrast)return sized;
        int[] p=new int[nw*nh];sized.getPixels(p,0,nw,0,0,nw,nh);
        int[] hist=new int[256];for(int c:p)hist[((c>>16&255)*77+(c>>8&255)*150+(c&255)*29)>>8]++;
        int low=0,high=255;int sum=0;
        for(int i=0;i<256;i++){sum+=hist[i];if(sum>p.length/50){low=i;break;}}
        sum=0;for(int i=255;i>=0;i--){sum+=hist[i];if(sum>p.length/50){high=i;break;}}
        if(high<=low+8){sized.recycle();return Bitmap.createBitmap(p,nw,nh,Bitmap.Config.ARGB_8888);}
        for(int i=0;i<p.length;i++){
            int c=p[i];int v=((c>>16&255)*77+(c>>8&255)*150+(c&255)*29)>>8;
            v=Math.max(0,Math.min(255,(v-low)*255/(high-low)));
            p[i]=Color.rgb(v,v,v);
        }
        Bitmap result=Bitmap.createBitmap(p,nw,nh,Bitmap.Config.ARGB_8888);
        sized.recycle();return result;
    }
}
