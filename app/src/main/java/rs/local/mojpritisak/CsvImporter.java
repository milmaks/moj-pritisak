package rs.local.mojpritisak;

import java.io.IOException;
import java.io.Reader;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Imports individual measurements from Instant BP Tracker and our own CSV export. */
final class CsvImporter {
    private CsvImporter() {}

    private static List<List<String>> parseRows(Reader reader) throws IOException {
        StringBuilder b=new StringBuilder();char[] buffer=new char[4096];int n;
        while((n=reader.read(buffer))!=-1){
            if(b.length()+n>5_000_000)throw new IOException("CSV je prevelik (maks. 5 MB).");
            b.append(buffer,0,n);
        }
        String input=b.toString().replace("\uFEFF","");
        char sep=',';
        int firstEnd=input.indexOf('\n');
        String head=firstEnd>=0?input.substring(0,firstEnd):input;
        long commas=head.chars().filter(c->c==',').count(),semis=head.chars().filter(c->c==';').count();
        if(semis>commas)sep=';';
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();
        StringBuilder cell=new StringBuilder();boolean quoted=false;
        for(int i=0;i<input.length();i++){
            char ch=input.charAt(i);
            if(ch=='"') {
                if(quoted && i+1<input.length() && input.charAt(i+1)=='"'){cell.append('"');i++;}
                else quoted=!quoted;
            } else if(ch==sep&&!quoted){row.add(cell.toString());cell.setLength(0);}
            else if((ch=='\n'||ch=='\r')&&!quoted){
                if(ch=='\r'&&i+1<input.length()&&input.charAt(i+1)=='\n')i++;
                row.add(cell.toString());cell.setLength(0);
                rows.add(row);row=new ArrayList<>();
            } else cell.append(ch);
        }
        if(cell.length()>0||!row.isEmpty()) {row.add(cell.toString());rows.add(row);}
        return rows;
    }
    private static int idx(List<String> row,String... names){
        for(int i=0;i<row.size();i++){
            String val=row.get(i).toLowerCase(Locale.ROOT).trim();
            for(String s:names)if(val.startsWith(s))return i;
        }
        return -1;
    }
    private static String at(List<String> row,int index){
        return index<0||index>=row.size()?"":row.get(index).trim();
    }
    private static int integer(String s){
        try{return Integer.parseInt(s.replaceAll("[^0-9]",""));}catch(Exception e){return -1;}
    }
    private static long date(String date,String time) {
        String raw=(date+" "+time).trim();
        String[] patterns={
            "dd.MM.yyyy. HH:mm", "dd.MM.yyyy HH:mm", "dd.MM.yyyy HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss","yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm:ss","yyyy/MM/dd HH:mm",
            "yyyy-MM-dd h:mm a", "MM/dd/yyyy HH:mm", "MM/dd/yyyy h:mm a",
            "dd/MM/yyyy HH:mm", "yyyy/MM/dd h:mm a", "dd.MM.yyyy.","dd.MM.yyyy",
            "yyyy-MM-dd", "yyyy/MM/dd", "MM/dd/yyyy", "dd/MM/yyyy"
        };
        for(String pattern:patterns){
            SimpleDateFormat fmt=new SimpleDateFormat(pattern,Locale.US);
            fmt.setLenient(false);
            ParsePosition pos=new ParsePosition(0);
            Date parsed=fmt.parse(raw,pos);
            if(parsed!=null&&pos.getIndex()==raw.length())return parsed.getTime();
        }
        return -1;
    }
    static List<Reading> from(Reader reader) throws IOException {
        List<List<String>> rows=parseRows(reader);
        List<Reading> result=new ArrayList<>();
        if(rows.isEmpty())return result;
        List<String> header=rows.get(0);
        int date=idx(header,"date","datum");
        int time=idx(header,"time","vreme");
        int sys=idx(header,"systolic","gornji");
        int dia=idx(header,"diastolic","donji");
        int pulse=idx(header,"pulse","puls");
        int note=idx(header,"notes","note","napomena");
        if(date<0||sys<0||dia<0||pulse<0)
            throw new IOException("Nepoznat format. Izvezi pojedinačna merenja u CSV formatu.");
        for(int i=1;i<rows.size();i++){
            List<String> row=rows.get(i);
            long at=date(at(row,date),at(row,time));
            int s=integer(at(row,sys)),d=integer(at(row,dia)),p=integer(at(row,pulse));
            if(at<0||s<60||s>280||d<30||d>180||p<30||p>220||s<=d)continue;
            result.add(new Reading(-1,at,s,d,p,at(row,note)));
        }
        return result;
    }
}
