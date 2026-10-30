package spireledger.stats;

import java.math.BigDecimal;
import java.util.*;

/** Chinese CSV of one export (metrics.md 8, csv-v2): one table per exported act and view, numbers with their denominators. JDK only. */
public final class Csv {
    public static final String VERSION="csv-v2";
    public static final List<String> COLUMNS=Arrays.asList("表","幕","口径","分档","条目","数值","单位","分子","分母","样本","状态","95%下限","95%上限","含估算","未知不计");

    /** One exported table: its title is repeated in the first column of every row, so a merged file stays filterable. */
    public static final class Table {
        public final String title;final List<String[]> rows=new ArrayList<>();
        public Table(String title){this.title=title;}
        /** A metric row; missing metrics keep a row so the table mirrors what the image says. */
        public Table add(String act,String view,String tier,String label,Metric m){
            if(m==null){rows.add(fields(title,act,view,tier,label,"","","","","","未记录","","","",""));return this;}
            boolean shown=m.shown();String value=shown?number(m.kind.equals(Metric.RATE)?m.value()*100:m.value()):"";
            String low="",high="";double[] ci=shown?m.interval():null;if(ci!=null){boolean rate=m.kind.equals(Metric.RATE);low=number(ci[0]*(rate?100:1));high=number(ci[1]*(rate?100:1));}
            rows.add(fields(title,act,view,tier,label,value,unit(m),numbers(m,0),numbers(m,1),number(m.samples),status(m.status),
                low,high,m.estimated>0?number(m.estimated):"",m.excluded>0?number(m.excluded):""));
            return this;
        }
        /** A picked/offered row (cards) with the same Chinese wording and a Wilson interval. */
        public Table addRate(String act,String view,String tier,String label,double picked,double offered,int samples,String rawStatus,int estimated){
            boolean shown=rawStatus.equals("ok")||rawStatus.equals("zero");
            String value=shown&&offered>0?number(picked/offered*100):"";
            double[] ci=shown?Metric.wilson(picked,offered):null;
            rows.add(fields(title,act,view,tier,label,value,"%",number(picked),number(offered),number(samples),status(rawStatus),
                ci==null?"":number(ci[0]*100),ci==null?"":number(ci[1]*100),estimated>0?number(estimated):"",""));
            return this;
        }
        public boolean empty(){return rows.isEmpty();}
        static String[] fields(String... cells){return cells;}
    }

    /** Header once, then one row per exported item; a blank line separates each act's table. */
    public static String body(List<Table> tables){
        StringBuilder out=new StringBuilder();row(out,COLUMNS.toArray(new String[0]));boolean first=true;
        for(Table table:tables){if(table.empty())continue;if(!first)out.append("\r\n");for(String[] cells:table.rows)row(out,cells);first=false;}
        return out.toString();
    }
    static String status(String raw){switch(raw){case "below_min_sample":return "样本不足";case "missing":return "未记录";case "hidden_by_result_filter":return "结果筛选隐藏";default:return "正常";}}
    static String unit(Metric m){
        if(m.kind.equals(Metric.RATE))return "%";
        switch(m.measure){case "hp":return "HP";case "gold":return "金币";case "seconds":return "秒";case "cards":return "张";case "potions":return "瓶";case "score":return "分";default:break;}
        return m.key.startsWith("potions")?"瓶":"次";
    }
    static String numbers(Metric m,int which){if(m.status.equals("hidden_by_result_filter"))return "";if(!m.shown()&&!m.status.equals("below_min_sample"))return "";
        return number(which==0?m.numerator:m.denominator);}
    static String number(double value){if(!Double.isFinite(value))return "";return BigDecimal.valueOf(value).setScale(6,BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString();}
    static void row(StringBuilder out,String... cells){for(int i=0;i<cells.length;i++){if(i>0)out.append(',');out.append(quoted(cells[i]));}out.append("\r\n");}
    static String quoted(String value){if(value==null)return "";if(value.indexOf(',')<0&&value.indexOf('"')<0&&value.indexOf('\n')<0&&value.indexOf('\r')<0)return value;return '"'+value.replace("\"","\"\"")+'"';}
    private Csv(){}
}
