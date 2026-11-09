package rs.local.mojpritisak;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Zoom-free, draggable and corner-resizable numeric region overlay. */
final class ScanOverlayView extends View {
    private final Paint brush=new Paint(3);
    private Bitmap bitmap;
    private final RectF display=new RectF();
    private final ScanRegions regions=new ScanRegions();
    private final int[] colors={0xFF1976D2,0xFF008577,0xFFD36A00};
    private final String[] names={"SYS", "DIA", "PULS"};
    private int active=0, gesture=-1;
    private float previousX,previousY;
    ScanOverlayView(Context c){super(c);setBackgroundColor(0xFF0D1520);}
    ScanRegions regions(){return regions;}
    void setBitmap(Bitmap b){bitmap=b;invalidate();}
    void select(int index){active=index;invalidate();}
    void use(ScanRegions detected){for(int i=0;i<3;i++)regions.regions[i].set(detected.regions[i]);invalidate();}
    private void geometry(){
        if(bitmap==null){display.setEmpty();return;}
        float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
        float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
        display.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);geometry();if(bitmap==null)return;
        brush.setColor(Color.WHITE);brush.setStyle(Paint.Style.FILL);brush.setAlpha(255);
        canvas.drawBitmap(bitmap,null,display,brush);
        for(int i=0;i<3;i++){
            RectF n=regions.regions[i]; RectF r=new RectF(display.left+n.left*display.width(), display.top+n.top*display.height(),display.left+n.right*display.width(),display.top+n.bottom*display.height());
            brush.setColor(colors[i]);brush.setAlpha(i==active?240:155);brush.setStyle(Paint.Style.STROKE);
            brush.setStrokeWidth(i==active?5:3);canvas.drawRoundRect(r,7,7,brush);
            brush.setStyle(Paint.Style.FILL);brush.setAlpha(210);canvas.drawRoundRect(new RectF(r.left,r.top-34,r.left+93,r.top),5,5,brush);
            brush.setColor(Color.WHITE);brush.setTextSize(26);brush.setAlpha(255);canvas.drawText(names[i],r.left+8,r.top-9,brush);
            if(i==active){brush.setColor(colors[i]);canvas.drawCircle(r.right,r.bottom,15,brush);}
        }
        brush.setStyle(Paint.Style.FILL);brush.setAlpha(255);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(bitmap==null)return false;
        geometry();if(display.isEmpty())return false;
        float x=(event.getX()-display.left)/display.width(),y=(event.getY()-display.top)/display.height();
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            RectF n=regions.regions[active];
            float distance=(float)Math.hypot((n.right-x)*display.width(),(n.bottom-y)*display.height());
            gesture=distance<65?1:0;
            if(!n.contains(x,y)&&gesture!=1) {
                for(int i=0;i<3;i++)if(regions.regions[i].contains(x,y)){active=i;break;}
            }
            previousX=x;previousY=y;invalidate();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
            RectF r=regions.regions[active];float dx=x-previousX,dy=y-previousY;
            if(gesture==1){r.right=Math.min(1,Math.max(r.left+.055f,r.right+dx));r.bottom=Math.min(1,Math.max(r.top+.055f,r.bottom+dy));}
            else{RectF moved=ScanRegions.bounded(new RectF(r.left+dx,r.top+dy,r.right+dx,r.bottom+dy));r.set(moved);}
            previousX=x;previousY=y;invalidate();return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){gesture=-1;return true;}
        return true;
    }
}
