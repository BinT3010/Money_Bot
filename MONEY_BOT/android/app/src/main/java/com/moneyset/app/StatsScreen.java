package com.moneyset.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Monthly totals, category states, a 6-month history chart/table and the transaction list.
 * All totals come from the API (/summary, /summary/history); the client never sums transactions. */
final class StatsScreen {
    static final int HISTORY_MONTHS = 6;

    static void stats(MainActivity host) {
        host.monthPicker();
        host.api.call("GET","/summary?month="+host.month,null,r->{
            JSONObject s=(JSONObject)r;UiHelpers.moneyText(host,host.content,"Đã chi: ",s.getLong("total"),24,true);host.text(host.content,s.getInt("count")+" giao dịch",16);
            if(!s.isNull("remaining"))UiHelpers.moneyText(host,host.content,"Ngân sách còn: ",s.getLong("remaining"),18,false);
            JSONObject budget=s.getJSONObject("budget");
            if(!budget.isNull("total"))UiHelpers.moneyText(host,host.content,"Ngân sách: ",budget.getLong("total"),18,false);
            JSONArray cats=s.getJSONArray("categories");
            for(int i=0;i<cats.length();i++){
                JSONObject row=cats.getJSONObject(i);
                TextView category=host.text(host.content,"",16);
                category.setText(UiHelpers.moneyLabel(host,row.getString("label")+": ",row.getLong("spent")," · "+host.alert(row.getString("level")),true));
                String level=row.getString("level");
                if(BudgetScreen.warns(level)){
                    UiHelpers.warningStyle(host,category);
                    if(!row.isNull("message"))UiHelpers.warning(host,host.content,row.getString("message"),14);
                }
                ProgressBar bar=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
                bar.setProgress((int)Math.min(100,s.getLong("total")==0?0:100.0*row.getLong("spent")/s.getLong("total")));host.content.addView(bar);
            }
            host.api.call("GET","/summary/history?month="+host.month+"&months="+HISTORY_MONTHS,null,h->{
                history(host,(JSONArray)h);
                transactions(host);
            },host::error);
        },host::error);
    }

    private static void transactions(MainActivity host) {
        host.text(host.content,"Giao dịch tháng "+host.month,16);
        host.button(host.content,"Thêm khoản chi thủ công",()->host.transactionForm(null));
        host.api.call("GET","/transactions?month="+host.month,null,list->{
            JSONArray rows=(JSONArray)list;
            if(rows.length()==0)host.text(host.content,"Chưa có giao dịch trong tháng này.",14);
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                host.button(host.content,"",()->host.transactionForm(row)).setText(UiHelpers.moneyLabel(host,row.getString("date")+" · "+row.getString("description")+"\n",row.getLong("amount_vnd")," · "+host.label(row.getString("category_id")),true));
            }
        },host::error);
    }

    /** Ou2: native bar chart plus a month table (total, count, per-category spending) from /summary/history. */
    static void history(MainActivity host, JSONArray months) throws Exception {
        host.text(host.content,"Chi tiêu "+months.length()+" tháng gần nhất (đến "+host.month+")",18);
        String[] names=new String[months.length()]; long[] totals=new long[months.length()];
        for(int i=0;i<months.length();i++){names[i]=months.getJSONObject(i).getString("month");totals[i]=months.getJSONObject(i).getLong("total");}
        MonthlyChart chart=new MonthlyChart(host,names,totals);
        StringBuilder spoken=new StringBuilder("Biểu đồ tổng chi theo tháng: ");
        for(int i=0;i<names.length;i++)spoken.append(names[i]).append(" ").append(host.money(totals[i])).append(i+1<names.length?"; ":".");
        chart.setContentDescription(spoken.toString());
        host.content.addView(chart,new LinearLayout.LayoutParams(-1,host.dp(180)));

        TableLayout table=new TableLayout(host); table.setStretchAllColumns(true);
        TableRow head=new TableRow(host); cell(host,head,"Tháng"); cell(host,head,"Tổng chi"); cell(host,head,"Số giao dịch"); table.addView(head);
        for(int i=months.length()-1;i>=0;i--){
            JSONObject m=months.getJSONObject(i);
            TableRow row=new TableRow(host); cell(host,row,m.getString("month")); cell(host,row,host.money(m.getLong("total"))); cell(host,row,String.valueOf(m.getInt("count")));
            table.addView(row);
            JSONObject by=m.getJSONObject("by_category"); StringBuilder parts=new StringBuilder();
            for(int c=0;c<host.categories.length;c++){
                long spent=by.optLong(host.categories[c]);
                if(spent!=0)parts.append(parts.length()==0?"":" · ").append(host.labels[c]).append(" ").append(host.money(spent));
            }
            if(parts.length()>0){
                TextView detail=new TextView(host); detail.setText(parts); detail.setTextSize(12); detail.setPadding(host.dp(12),0,0,host.dp(4)); detail.setTextColor(UiHelpers.NEUTRAL);
                detail.setContentDescription("history:"+m.getString("month")); table.addView(detail);
            }
        }
        host.content.addView(table);
    }

    private static void cell(MainActivity host, TableRow row, String value) {
        TextView v=new TextView(host); v.setText(value); v.setTextSize(14); v.setPadding(0,host.dp(4),host.dp(8),host.dp(4)); row.addView(v);
    }

    /** Dependency-free bar chart; it only draws the server totals it is given. Created in code only. */
    @android.annotation.SuppressLint("ViewConstructor")
    static final class MonthlyChart extends View {
        final String[] months; final long[] totals;
        private final Paint bar=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG), axis=new Paint();

        MonthlyChart(Context context, String[] months, long[] totals) {
            super(context); this.months=months; this.totals=totals;
            bar.setColor(Color.rgb(78,55,160));
            text.setColor(UiHelpers.NEUTRAL); text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,11,context.getResources().getDisplayMetrics()));
            axis.setColor(Color.rgb(203,213,225));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(months.length==0)return;
            float w=getWidth(), h=getHeight(), label=text.getTextSize()*1.6f, top=label, base=h-label;
            long max=0; for(long t:totals)max=Math.max(max,t);
            float slot=w/months.length, width=slot*0.6f;
            canvas.drawLine(0,base,w,base,axis);
            for(int i=0;i<months.length;i++){
                float x=slot*i+slot/2, height=max==0?0:(base-top)*totals[i]/max;
                canvas.drawRect(x-width/2,base-height,x+width/2,base,bar);
                canvas.drawText(months[i].substring(5)+"/"+months[i].substring(2,4),x,h-label/4,text);
                canvas.drawText(compact(totals[i]),x,base-height-label/4,text);
            }
        }

        /** Short axis label only (e.g. 1,2tr); exact values are in the table and content description. */
        static String compact(long value) {
            if(value>=1_000_000)return trim(value/1_000_000.0)+"tr";
            if(value>=1_000)return trim(value/1_000.0)+"k";
            return String.valueOf(value);
        }
        private static String trim(double v) {
            return String.format(java.util.Locale.ROOT,"%.1f",v).replaceAll("\\.0$","").replace('.',',');
        }
    }
}
