package com.moneyset.app;

import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

/** Budget form, server warning states and limit suggestions with their basis.
 * Every amount, percentage, level and message shown here comes from the API; the screen computes no totals. */
final class BudgetScreen {
    static void budget(MainActivity host) {
        host.monthPicker();
        LinearLayout states = host.column(); host.content.addView(states);
        host.api.call("GET","/budgets/"+host.month,null,r->{
            JSONObject b=(JSONObject)r, limits=b.getJSONObject("limits");
            EditText total=host.input(host.content,"Ngân sách tổng VND",b.isNull("total")?"":b.getString("total"),true);
            total.setTextColor(UiHelpers.FUNDS_GREEN);
            EditText threshold=host.input(host.content,"Cảnh báo sớm (%)",b.getString("threshold"),true);
            EditText[] edits=new EditText[6];
            host.text(host.content,"Để trống: chưa đặt hạn mức. Nhập 0: không dành ngân sách cho danh mục.",14);
            for(int i=0;i<6;i++)edits[i]=host.input(host.content,host.labels[i],limits.optString(host.categories[i]),true);
            host.button(host.content,"Xác nhận lưu ngân sách",()->{
                JSONObject values=new JSONObject();for(int i=0;i<6;i++)if(!host.value(edits[i]).isEmpty())values.put(host.categories[i],host.amount(edits[i]));
                host.api.call("PUT","/budgets/"+host.month,new JSONObject().put("total",host.amount(total)).put("threshold",Integer.parseInt(host.value(threshold))).put("limits",values),v->{
                    host.load(); UiHelpers.status(host,"Đã lưu ngân sách tháng "+host.month+".");
                },host::error);
            });
            LinearLayout basis=host.column();
            host.button(host.content,"Gợi ý từ 3 tháng trước",()->host.api.call("GET","/budgets/"+host.month+"/suggestions",null,v->{
                JSONObject result=(JSONObject)v;
                boolean available=result.optBoolean("available");
                if(available){
                    JSONObject suggested=result.getJSONObject("limits");
                    for(int i=0;i<6;i++)edits[i].setText(suggested.getString(host.categories[i]));
                    UiHelpers.status(host,"Đã điền hạn mức gợi ý. Chưa lưu; kiểm tra rồi bấm Xác nhận lưu ngân sách.");
                } else UiHelpers.warningStatus(host,"Chưa có gợi ý; hãy nhập hạn mức thủ công.");
                suggestion(host,basis,result);
            },host::error));
            host.content.addView(basis);
            host.api.call("GET","/summary?month="+host.month,null,s->states(host,states,(JSONObject)s),host::error);
        },host::error);
    }

    /** Ou4 states for the month: unset vs zero limits, exact 100%, custom threshold and overspending, all decided by the server. */
    static void states(MainActivity host, LinearLayout box, JSONObject s) throws Exception {
        box.removeAllViews();
        JSONObject budget=s.getJSONObject("budget");
        host.text(box,"Trạng thái hạn mức tháng "+s.getString("month")+" · cảnh báo sớm ở "+budget.getInt("threshold")+"%",16);
        if(budget.isNull("total")) host.text(box,"Chưa đặt ngân sách tổng tháng này.",14);
        else {
            long remaining=s.getLong("remaining");
            box.addView(row(host,UiHelpers.moneyLabel(host,"Ngân sách tổng: ",budget.getLong("total"),"",false)));
            box.addView(row(host,UiHelpers.moneyLabel(host,"Đã chi tháng: ",s.getLong("total"),"",true)));
            TextView left=host.text(box,"",14);
            if(remaining<0){left.setText(UiHelpers.moneyLabel(host,"Vượt ngân sách tổng: ",-remaining,"",true));UiHelpers.warningStyle(host,left);}
            else left.setText(UiHelpers.moneyLabel(host,"Ngân sách tổng còn: ",remaining,"",false));
        }
        JSONArray rows=s.getJSONArray("categories");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String level=row.getString("level");
            String message=row.isNull("message")?row.getString("label")+": chưa đặt hạn mức · đã chi "+host.money(row.getLong("spent"))+"."
                :row.getString("message");
            TextView line=host.text(box,host.alert(level)+" · "+message,14);
            line.setContentDescription("level:"+row.getString("category_id")+":"+level);
            if(warns(level))UiHelpers.warningStyle(host,line);
        }
    }

    static boolean warns(String level) { return level.equals("WARNING")||level.equals("REACHED")||level.equals("OVER"); }

    private static TextView row(MainActivity host, CharSequence value) {
        TextView v=new TextView(host); v.setText(value); v.setTextSize(14); return v;
    }

    /** Suggestion basis (G2/2.1): months, per-month history, averages, budget B, factor B/A, allocated and unallocated. */
    static void suggestion(MainActivity host, LinearLayout box, JSONObject r) throws Exception {
        box.removeAllViews();
        JSONArray months=r.getJSONArray("months");
        String span=months.getString(0)+" – "+months.getString(months.length()-1);
        if(!r.optBoolean("available")){
            UiHelpers.warning(host,box,"Không gợi ý được: "+r.getString("reason"),14);
            JSONArray missing=r.optJSONArray("missing");
            if(missing!=null)for(int i=0;i<missing.length();i++){
                String m=missing.getString(i);
                host.text(box,"Thiếu: "+(m.equals("budget")?"ngân sách tổng đã lưu cho tháng "+host.month
                    :m.equals("history")?"lịch sử chi bắt đầu từ "+months.getString(0)+" trở về trước":m),14);
            }
            if(r.has("history"))history(host,box,r,months,null);
            return;
        }
        host.text(box,"Gợi ý hạn mức (chưa lưu) · căn cứ "+span,16);
        host.text(box,r.getString("reason"),14);
        history(host,box,r,months,r.getJSONObject("averages"));
        JSONObject limits=r.getJSONObject("limits");
        for(int i=0;i<6;i++){
            String cid=host.categories[i];
            host.text(box,"Gợi ý "+host.labels[i]+": "+host.money(limits.getLong(cid)),14);
        }
        host.text(box,"Trung bình tổng A: "+average(host,r.getDouble("average_total"))+" · ngân sách B: "+host.money(r.getLong("budget_total"))
            +" · hệ số: "+factor(r.getDouble("factor"))+(r.getDouble("factor")<1?" (đã thu nhỏ theo B/A)":" (giữ nguyên trung bình)"),14);
        host.text(box,"Đã phân bổ: "+host.money(r.getLong("allocated"))+" · chưa phân bổ: "+host.money(r.getLong("unallocated")),14);
        host.button(box,"Bỏ gợi ý, giữ hạn mức đã lưu",host::load);
    }

    private static void history(MainActivity host, LinearLayout box, JSONObject r, JSONArray months, JSONObject averages) throws Exception {
        JSONObject history=r.getJSONObject("history");
        TableLayout table=new TableLayout(host); table.setStretchAllColumns(true);
        TableRow head=new TableRow(host); cell(host,head,"Danh mục");
        for(int m=0;m<months.length();m++)cell(host,head,months.getString(m));
        if(averages!=null)cell(host,head,"TB/tháng");
        table.addView(head);
        for(int i=0;i<6;i++){
            String cid=host.categories[i]; TableRow line=new TableRow(host); cell(host,line,host.labels[i]);
            for(int m=0;m<months.length();m++)cell(host,line,host.money(history.getJSONObject(months.getString(m)).getLong(cid)));
            if(averages!=null)cell(host,line,average(host,averages.getDouble(cid)));
            table.addView(line);
        }
        box.addView(table);
    }

    private static void cell(MainActivity host, TableRow row, String value) {
        TextView v=new TextView(host); v.setText(value); v.setTextSize(12); v.setPadding(0,host.dp(2),host.dp(6),host.dp(2)); row.addView(v);
    }

    /** Server averages may carry two decimals; show them as returned instead of rounding money on the client. */
    static String average(MainActivity host, double value) {
        long whole=(long)Math.floor(value);
        long cents=Math.round((value-whole)*100);
        if(cents==100){whole++;cents=0;}
        if(cents==0)return host.money(whole);
        String frac=String.format(Locale.ROOT,"%02d",cents).replaceAll("0+$","");
        return host.money(whole).replace(" đ",","+frac+" đ");
    }

    static String factor(double value) {
        return String.format(Locale.ROOT,"%.4f",value).replaceAll("0+$","").replaceAll("\\.$","").replace('.',',');
    }
}
