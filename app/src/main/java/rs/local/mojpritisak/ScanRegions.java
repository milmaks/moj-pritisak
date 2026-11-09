package rs.local.mojpritisak;

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Bitmap;
import com.google.mlkit.vision.text.Text;
import java.util.List;
import java.util.Locale;

/** Three independent regions of interest in normalized image coordinates. */
final class ScanRegions {
    final RectF[] regions = new RectF[3];
    ScanRegions() {
        regions[0] = new RectF(.24f,.31f,.56f,.46f);
        regions[1] = new RectF(.27f,.46f,.56f,.62f);
        regions[2] = new RectF(.32f,.62f,.56f,.74f);
    }
    static RectF bounded(RectF r) {
        float w=Math.max(.055f, Math.min(.95f,r.width()));
        float h=Math.max(.055f, Math.min(.8f,r.height()));
        float l=Math.max(0,Math.min(1-w,r.left));
        float t=Math.max(0,Math.min(1-h,r.top));
        return new RectF(l,t,l+w,t+h);
    }
    /** Crop numerical regions based on *detected* SYS/DIA/PULSE label positions.
     * Does not rely on Omron model, image resolution or absolute pixel coordinates.
     * Users can override boxes by dragging them on the photo preview. */
    static ScanRegions fromLabels(Text recognized, int width, int height) {
        ScanRegions out = new ScanRegions();
        Rect[] labels = new Rect[3];
        for (Text.TextBlock block:recognized.getTextBlocks()) for(Text.Line line:block.getLines())
            for(Text.Element element:line.getElements()) {
                String token = element.getText().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
                int index = token.equals("SYS") || token.equals("SYSTOLIC") ? 0 :
                            token.equals("DIA") || token.equals("DIASTOLIC") ? 1 :
                            token.equals("PUL") || token.equals("PULSE") || token.equals("BPM") ? 2 : -1;
                if(index>=0 && labels[index]==null) labels[index] = element.getBoundingBox();
            }
        int found=0;
        for(Rect r:labels)if(r!=null)found++;
        if(found<2)return out;
        int typicalGap = Math.max(20,Math.round(height*.16f));
        if(labels[0]!=null && labels[1]!=null) typicalGap=labels[1].top-labels[0].top;
        else if(labels[1]!=null && labels[2]!=null) typicalGap=labels[2].top-labels[1].top;
        if (typicalGap<height*.06 || typicalGap>height*.4) typicalGap=Math.round(height*.16f);
        for(int i=0;i<3;i++){
            if(labels[i]==null) continue;
            Rect r=labels[i];
            // LCD digits tend to be substantially taller than small printed labels.
            float left=(r.right+width*.028f)/width;
            float right=(r.right+width*.33f)/width;
            float top=(r.top-typicalGap*.13f)/height;
            float bottom=(r.top+typicalGap*(i==2?.55f:.88f))/height;
            out.regions[i]=bounded(new RectF(left, top, right, bottom));
        }
        // Infer unlabelled rows only when the two neighbours establish an orientation.
        if(labels[0]!=null && labels[1]!=null && labels[2]==null) {
            RectF a=out.regions[1]; float delta=typicalGap/(float)height;
            out.regions[2]=bounded(new RectF(a.left,a.top+delta,a.right,a.bottom+delta));
        }
        return out;
    }
    Bitmap crop(Bitmap bitmap,int index) {
        RectF r=bounded(regions[index]);
        int x=Math.max(0, Math.min(bitmap.getWidth()-1,(int)(r.left*bitmap.getWidth())));
        int y=Math.max(0, Math.min(bitmap.getHeight()-1,(int)(r.top*bitmap.getHeight())));
        int right=Math.min(bitmap.getWidth(),Math.max(x+1,(int)(r.right*bitmap.getWidth())));
        int bottom=Math.min(bitmap.getHeight(),Math.max(y+1,(int)(r.bottom*bitmap.getHeight())));
        return Bitmap.createBitmap(bitmap,x,y,right-x,bottom-y);
    }
}
