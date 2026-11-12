package com.moneyset.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.RectF;
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
            host.text(host.content,"Chi tiêu theo danh mục",18);
            long[] categoryAmounts=new long[cats.length()]; String[] categoryNames=new String[cats.length()];
            for(int i=0;i<cats.length();i++){
                JSONObject row=cats.getJSONObject(i);
                categoryAmounts[i]=row.getLong("spent"); categoryNames[i]=row.getString("label");
                TextView category=host.text(host.content,"",16);
                category.setText(UiHelpers.moneyLabel(host,row.getString("label")+": ",row.getLong("spent"),"",true));
                String level=row.getString("level");
                ProgressBar bar=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100); bar.setProgress((int)Math.min(100,s.getLong("total")==0?0:100.0*row.getLong("spent")/s.getLong("total")));
                boolean warning=BudgetScreen.warns(level);
                int color=warning?UiHelpers.EXPENSE_RED:UiHelpers.CHART_COLORS[i%UiHelpers.CHART_COLORS.length];
                bar.setProgressTintList(ColorStateList.valueOf(color));
                bar.setProgressBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.rgb(226,232,240)));
                bar.setContentDescription(row.getString("label")+": "+row.getString("level"));
                LinearLayout.LayoutParams barLayout=new LinearLayout.LayoutParams(-1,host.dp(18));
                barLayout.topMargin=host.dp(3);barLayout.bottomMargin=host.dp(12);host.content.addView(bar,barLayout);
                if(warning){
                    ValueAnimator pulse=ValueAnimator.ofFloat(.45f,1f);pulse.setDuration(650);pulse.setRepeatCount(ValueAnimator.INFINITE);pulse.setRepeatMode(ValueAnimator.REVERSE);
                    pulse.addUpdateListener(animation->bar.setAlpha((Float)animation.getAnimatedValue()));
                    bar.setTag(pulse);
                View.OnAttachStateChangeListener pulseLifecycle=new View.OnAttachStateChangeListener(){
                        @Override public void onViewAttachedToWindow(View view){pulse.start();}
                        @Override public void onViewDetachedFromWindow(View view){pulse.cancel();view.setAlpha(1f);}
                    };
                bar.addOnAttachStateChangeListener(pulseLifecycle);
                if(bar.isAttachedToWindow())pulse.start();
                }
            }
            CategoryChart categoryChart=new CategoryChart(host,categoryNames,categoryAmounts,s.getLong("total"));
            categoryChart.setContentDescription(categoryDescription(host,categoryNames,categoryAmounts));
            host.content.addView(categoryChart,new LinearLayout.LayoutParams(-1,host.dp(180)));
            host.api.call("GET","/summary/history?month="+host.month+"&months="+HISTORY_MONTHS,null,h->{
                history(host,(JSONArray)h);
                transactions(host);
            },host::error);
        },host::error);
    }

    private static String categoryDescription(MainActivity host,String[] names,long[] amounts){
        StringBuilder text=new StringBuilder("Chi tiêu theo danh mục: ");
        for(int i=0;i<names.length;i++)text.append(names[i]).append(" ").append(host.money(amounts[i])).append(i+1<names.length?"; ":".");
        return text.toString();
    }

    /** Category share ring; values are the server's category spending amounts. */
    @android.annotation.SuppressLint("ViewConstructor")
    static final class CategoryChart extends View {
        final String[] names; final long[] amounts; final long total; final Paint slice=new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint legend=new Paint(Paint.ANTI_ALIAS_FLAG); final Paint label=new Paint(Paint.ANTI_ALIAS_FLAG);
        CategoryChart(android.content.Context context,String[] names,long[] amounts,long total){
            super(context);this.names=names;this.amounts=amounts;this.total=total;
            legend.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,10,context.getResources().getDisplayMetrics()));
            legend.setColor(UiHelpers.INK);label.setColor(UiHelpers.NEUTRAL);label.setTextAlign(Paint.Align.CENTER);
            label.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,11,context.getResources().getDisplayMetrics()));
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
            float cx=getWidth()*.28f,cy=getHeight()*.5f,r=Math.min(getHeight()*.39f,getWidth()*.25f);
            if(total>0){RectF oval=new RectF(cx-r,cy-r,cx+r,cy+r);float start=-90;
                for(int i=0;i<amounts.length;i++)if(amounts[i]>0){slice.setColor(UiHelpers.CHART_COLORS[i%UiHelpers.CHART_COLORS.length]);float sweep=360f*amounts[i]/total;canvas.drawArc(oval,start,sweep,true,slice);start+=sweep;}
                slice.setColor(UiHelpers.CANVAS);canvas.drawCircle(cx,cy,r*.57f,slice);
                label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);canvas.drawText("100%",cx,cy+label.getTextSize()*.35f,label);
            } else {label.setTypeface(android.graphics.Typeface.DEFAULT);canvas.drawText("Chưa có chi tiêu",cx,cy,label);}
            float x=getWidth()*.55f,y=hostLineStart(density);
            for(int i=0;i<names.length;i++){
                slice.setColor(UiHelpers.CHART_COLORS[i%UiHelpers.CHART_COLORS.length]);canvas.drawRoundRect(x,y-10*density,x+11*density,y+1*density,3*density,3*density,slice);
                canvas.drawText(names[i]+"  "+MonthlyChart.compact(amounts[i]),x+17*density,y,legend);y+=25*density;
            }
        }
        private float hostLineStart(float density){return Math.max(18*density,(getHeight()-names.length*25*density)*.5f+15*density);}
    }

    private static void transactions(MainActivity host) {
        host.text(host.content,"Giao dịch tháng "+host.month,16);
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

        LinearLayout rows=host.column();
        for(int i=months.length()-1;i>=0;i--){
            JSONObject m=months.getJSONObject(i);
            LinearLayout card=host.column();card.setPadding(host.dp(10),host.dp(5),host.dp(10),host.dp(5));
            card.setBackground(UiHelpers.shape(host,Color.WHITE,Color.rgb(226,232,240),10));
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);cardParams.bottomMargin=host.dp(5);rows.addView(card,cardParams);
            LinearLayout headline=new LinearLayout(host);headline.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView month=host.text(headline,m.getString("month"),13);month.setTypeface(null,android.graphics.Typeface.BOLD);
            TextView amount=new TextView(host);amount.setText(UiHelpers.moneyLabel(host,"",m.getLong("total"),"",true));amount.setTextSize(13);
            headline.addView(amount,new LinearLayout.LayoutParams(-2,-2));
            // Keep the month at the left and its server total at the right.
            month.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));card.addView(headline);
            JSONObject by=m.getJSONObject("by_category"); StringBuilder parts=new StringBuilder();
            for(int c=0;c<host.categories.length;c++){
                long spent=by.optLong(host.categories[c]);
                if(spent!=0)parts.append(parts.length()==0?"":" · ").append(host.labels[c]).append(" ").append(host.money(spent));
            }
            TextView detail=host.text(card,m.getInt("count")+" giao dịch"+(parts.length()==0?"":" · "+parts),11);
            detail.setTextColor(UiHelpers.NEUTRAL);detail.setContentDescription("history:"+m.getString("month"));
        }
        host.content.addView(rows);
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
            return String.format(java.util.Locale.US,"%,d",value);
        }
    }
}
