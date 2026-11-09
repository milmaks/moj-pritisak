package rs.local.mojpritisak;

import java.util.ArrayList;
import java.util.Collections;

/** Image-size independent LCD digit reader for a *single cropped number*.
 * Detects digit columns from local background contrast, then tests seven
 * segment positions. Unlike the v1.3 RS3 reader it contains NO device-specific
 * pixel rectangles. Rejects weak/ambiguous results. No Android dependencies. */
public final class FlexibleSevenSegmentReader {
    private FlexibleSevenSegmentReader(){}
    private static final String[] SEGMENTS={"ABCDEF","BC","ABDEG","ABCDG","BCFG","ACDFG","ACDEFG","ABCF","ABCDEFG","ABCDFG"};
    private static final double[][] PROBES={
        {.28,.02,.72,.15}, {.75,.17,.93,.43}, {.75,.60,.93,.84},
        {.28,.87,.72,.99}, {.07,.60,.25,.84}, {.07,.17,.25,.43}, {.28,.43,.72,.58}
    };
    public static final class Reading {public final int value;public final double confidence;Reading(int v,double c){value=v;confidence=c;}}
    public static Reading read(int width,int height,int[] argb,int row){
        if(width<35||height<25||width>1600||height>900||argb==null||argb.length!=width*height)return null;
        // Normalize every chosen crop to a similar digit scale. This avoids
        // pixel-resolution dependence (e.g. 480px vs 1440px gallery photos).
        if(height>115){
            int newH=100,newW=Math.max(8,Math.min(1600,Math.round(width*100f/height)));
            int[] resized=new int[newW*newH];
            for(int y=0;y<newH;y++)for(int x=0;x<newW;x++){
                int sourceX=Math.min(width-1,(int)((x+.5)*width/newW));
                int sourceY=Math.min(height-1,(int)((y+.5)*height/newH));
                resized[y*newW+x]=argb[sourceY*width+sourceX];
            }
            return read(newW,newH,resized,row);
        }
        int n=width*height;float[] gray=new float[n];
        for(int i=0;i<n;i++){
            int c=argb[i];gray[i]=((c>>16&255)*77+(c>>8&255)*150+(c&255)*29)/256f;
        }
        int kernel=Math.min(31,Math.min(width,height)/2*2+1);
        if(kernel<7)return null;
        float[] coefficients=new float[kernel];double weight=0;double sigma=Math.max(1,kernel*.153);
        for(int i=0;i<kernel;i++){int x=i-kernel/2;coefficients[i]=(float)Math.exp(-.5*x*x/(sigma*sigma));weight+=coefficients[i];}
        for(int i=0;i<kernel;i++)coefficients[i]/=weight;
        int radius=kernel/2;
        float[] blurredX=new float[n];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            float v=0;
            for(int k=-radius;k<=radius;k++){
                int xx=Math.max(0,Math.min(width-1,x+k));v+=gray[y*width+xx]*coefficients[k+radius];
            }
            blurredX[y*width+x]=v;
        }
        boolean[] ink=new boolean[n];
        int[] colCount=new int[width];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            float bg=0;
            for(int k=-radius;k<=radius;k++){
                int yy=Math.max(0,Math.min(height-1,y+k));bg+=blurredX[yy*width+x]*coefficients[k+radius];
            }
            int i=y*width+x;
            ink[i]=bg-gray[i]>5;
            if(ink[i])colCount[x]++;
        }
        ArrayList<int[]> spans=new ArrayList<>();int start=-1,last=-1;
        int maxGap=Math.max(2,Math.min(7,height/24));
        for(int x=0;x<width;x++){
            if(colCount[x]>=Math.max(2,height/20)){
                if(start<0)start=x;
                else if(x-last>maxGap){if(last-start>=3)spans.add(new int[]{start,last});start=x;}
                last=x;
            }
        }
        if(start>=0&&last-start>=3)spans.add(new int[]{start,last});
        // Remove tiny indicators and choose the best cluster of adjacent digits.
        // This rejects printed labels, ticks and symbols elsewhere on the LCD.
        if(spans.size()<2)return null;
        int widest=0;
        for(int[] v:spans)widest=Math.max(widest,v[1]-v[0]+1);
        ArrayList<int[]> substantive=new ArrayList<>();
        int[] visibleRows=new int[spans.size()];int mostVisible=0;
        for(int i=0;i<spans.size();i++){
            int[] v=spans.get(i);int spanWidth=v[1]-v[0]+1;
            for(int y=0;y<height;y++){
                int nInk=0;
                for(int x=v[0];x<=v[1];x++)if(ink[y*width+x])nInk++;
                if(nInk>=Math.max(2,(int)(spanWidth*.1)))visibleRows[i]++;
            }
            mostVisible=Math.max(mostVisible,visibleRows[i]);
        }
        for(int i=0;i<spans.size();i++){
            int[] v=spans.get(i);
            if(v[1]-v[0]+1>=Math.max(4,widest*.16f)&&visibleRows[i]>=mostVisible*.63f)substantive.add(v);
        }
        int allowableGap=Math.max(8,(int)(widest*.72));
        ArrayList<ArrayList<int[]>> clusters=new ArrayList<>();
        for(int[] v:substantive){
            if(clusters.isEmpty()||v[0]-clusters.get(clusters.size()-1).get(clusters.get(clusters.size()-1).size()-1)[1]>allowableGap){
                clusters.add(new ArrayList<>());
            }
            clusters.get(clusters.size()-1).add(v);
        }
        ArrayList<int[]> chosen=null;
        for(ArrayList<int[]> cluster:clusters){
            if(cluster.size()<2||cluster.size()>3)continue;
            if(chosen==null||cluster.size()>chosen.size())chosen=cluster;
        }
        if(chosen==null)return null;
        spans=chosen;
        // Search the longest band of numerical ink. Excludes text/marks below
        // the readout and accommodates user crops with generous top/bottom margins.
        int[] rowCount=new int[height];int totalWidth=0;
        for(int[] span:spans){
            totalWidth+=span[1]-span[0]+1;
            for(int y=0;y<height;y++)for(int x=span[0];x<=span[1];x++)if(ink[y*width+x])rowCount[y]++;
        }
        int top=0,bottom=height-1,bestLength=-1,runStart=-1,runLast=-1;
        int required=Math.max(3,(int)(totalWidth*.04));
        for(int y=0;y<height;y++){
            if(rowCount[y]>=required){
                if(runStart<0)runStart=y;
                else if(y-runLast>4){
                    if(runLast-runStart>bestLength){bestLength=runLast-runStart;top=runStart;bottom=runLast;}
                    runStart=y;
                }
                runLast=y;
            }
        }
        if(runStart>=0&&runLast-runStart>bestLength){bestLength=runLast-runStart;top=runStart;bottom=runLast;}
        if(bestLength<height*.28)return null;
        int digitHeight=bottom-top+1;
        ArrayList<Integer> widths=new ArrayList<>();for(int[] span:spans)widths.add(span[1]-span[0]+1);
        Collections.sort(widths);int median=widths.get(widths.size()/2);
        int value=0;double weakest=10;
        for(int[] span:spans){
            int widthDigit=span[1]-span[0]+1;
            if(widthDigit<median*.48&&widthDigit>4){value=value*10+1;weakest=Math.min(weakest,5.9);continue;}
            double[] p=new double[7];
            for(int seg=0;seg<7;seg++){
                double[] v=PROBES[seg];int x1=span[0]+(int)(widthDigit*v[0]),x2=span[0]+(int)Math.ceil(widthDigit*v[2]);
                int y1=top+(int)(digitHeight*v[1]),y2=top+(int)Math.ceil(digitHeight*v[3]);
                x1=Math.max(0,Math.min(width-1,x1));x2=Math.min(width,Math.max(x1+1,x2));
                y1=Math.max(0,Math.min(height-1,y1));y2=Math.min(height,Math.max(y1+1,y2));
                int count=0,total=(x2-x1)*(y2-y1);
                for(int y=y1;y<y2;y++)for(int x=x1;x<x2;x++)if(ink[y*width+x])count++;
                double ratio=count/(double)total;
                p[seg]=1d/(1d+Math.exp(-(ratio-.2)*12));
            }
            int bestDigit=-1;double best=-1,second=-1;
            for(int digit=0;digit<10;digit++){
                double score=0;for(int seg=0;seg<7;seg++){
                    boolean on=SEGMENTS[digit].indexOf((char)('A'+seg))>=0;
                    score+=on?p[seg]:1-p[seg];
                }
                if(score>best){second=best;best=score;bestDigit=digit;}
                else if(score>second)second=score;
            }
            if(best<5.45||best-second<.37)return null;
            value=value*10+bestDigit;weakest=Math.min(weakest,best);
        }
        boolean inRange=row==0?value>=60&&value<=280:row==1?value>=30&&value<=180:value>=30&&value<=220;
        return inRange?new Reading(value,weakest/7):null;
    }
}
