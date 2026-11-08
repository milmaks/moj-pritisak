package rs.local.mojpritisak;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Last 14 local calendar days, using daily mean of all recorded measurements. */
final class PressureChart extends View {
    private List<Reading> readings;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int blue = Color.rgb(46, 99, 197);
    private final int teal = Color.rgb(4, 137, 122);
    private final int slate = Color.rgb(93, 106, 125);

    PressureChart(Context c, List<Reading> data) { super(c); readings = data; }
    void update(List<Reading> data) { readings = data; invalidate(); }

    private float dp(float n) { return n * getResources().getDisplayMetrics().density; }
    private void label(Canvas canvas, String s, float x, float y, int color, float size) {
        pen.setColor(color); pen.setStrokeWidth(1); pen.setTextSize(dp(size));
        pen.setStyle(Paint.Style.FILL); canvas.drawText(s, x, y, pen);
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), (int)dp(230));
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        final int days = 14;
        double[] sys = new double[days], dia = new double[days];
        int[] count = new int[days];
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);
        Calendar first = (Calendar)today.clone(); first.add(Calendar.DATE, -13);
        long minTime = first.getTimeInMillis();
        for (Reading r : readings) {
            if (r.timestamp < minTime) continue;
            Calendar date = Calendar.getInstance(); date.setTimeInMillis(r.timestamp);
            date.set(Calendar.HOUR_OF_DAY, 0); date.set(Calendar.MINUTE, 0);
            date.set(Calendar.SECOND, 0); date.set(Calendar.MILLISECOND, 0);
            // Compare calendar days rather than milliseconds (DST-safe).
            long diff = (date.get(Calendar.YEAR) - first.get(Calendar.YEAR)) * 366L;
            if (diff < -400) continue; // quick skip for distant years
            int i = 0;
            Calendar day = (Calendar)first.clone();
            while (i < days && day.getTimeInMillis() != date.getTimeInMillis()) {
                day.add(Calendar.DATE, 1); i++;
            }
            if (i >= days) continue;
            sys[i] += r.sys; dia[i] += r.dia; count[i]++;
        }
        boolean any = false;
        double low = 60, high = 160;
        for (int i = 0; i < days; i++) if (count[i] > 0) {
            any = true; sys[i] /= count[i]; dia[i] /= count[i];
            low = Math.min(low, dia[i] - 10); high = Math.max(high, sys[i] + 10);
        }
        float left=dp(38), right=getWidth()-dp(12), top=dp(18), bottom=getHeight()-dp(43);
        if (!any) {
            label(c, "Grafikon će se pojaviti nakon prvog merenja.", dp(10), getHeight()/2f, slate, 13);
            return;
        }
        // Horizontal ticks.
        for (int j = 0; j <= 4; j++) {
            double value = low + (high-low)*(4-j)/4.0;
            float y = top + (bottom-top)*j/4f;
            pen.setColor(Color.rgb(225, 232, 239)); pen.setStrokeWidth(dp(1));
            c.drawLine(left,y,right,y,pen);
            label(c, String.valueOf(Math.round(value)), dp(4), y+dp(4), slate, 10);
        }
        for (int j = 0; j < 2; j++) {
            pen.setColor(j==0 ? blue : teal); pen.setStrokeWidth(dp(2.4f));
            float prevX=0, prevY=0;
            boolean hasPrev=false;
            for (int i=0;i<days;i++) {
                if(count[i]==0) { hasPrev=false; continue; }
                double v = j==0 ? sys[i] : dia[i];
                float x = left + (right-left)*i/(days-1f);
                float y = bottom-(float)((v-low)/(high-low))*(bottom-top);
                if (hasPrev) c.drawLine(prevX,prevY,x,y,pen);
                pen.setStyle(Paint.Style.FILL); c.drawCircle(x,y,dp(3),pen);
                prevX=x; prevY=y; hasPrev=true;
            }
        }
        SimpleDateFormat df = new SimpleDateFormat("dd.MM", Locale.forLanguageTag("sr-RS"));
        label(c, df.format(new Date(minTime)), left, bottom+dp(20), slate, 10);
        String last = df.format(new Date(today.getTimeInMillis()));
        pen.setTextSize(dp(10));
        label(c,last,right-pen.measureText(last),bottom+dp(20),slate,10);
        label(c,"● Gornji",left,bottom+dp(38),blue,11);
        label(c,"● Donji",left+dp(91),bottom+dp(38),teal,11);
    }
}
