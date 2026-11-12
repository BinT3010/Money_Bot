package com.moneyset.app;

import android.app.AlertDialog;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class StatsScreen {
    static void stats(MainActivity host) {
        host.monthPicker();
        host.api.call("GET","/summary?month="+host.month,null,r->{
            JSONObject s=(JSONObject)r;host.text(host.content,"Đã chi: "+host.money(s.getLong("total")),24);host.text(host.content,s.getInt("count")+" giao dịch",16);
            if(!s.isNull("remaining"))host.text(host.content,"Ngân sách còn: "+host.money(s.getLong("remaining")),18);
            JSONArray cats=s.getJSONArray("categories");
            for(int i=0;i<cats.length();i++){
                JSONObject row=cats.getJSONObject(i);host.text(host.content,row.getString("label")+": "+host.money(row.getLong("spent"))+" · "+host.alert(row.getString("level")),16);
                ProgressBar bar=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
                bar.setProgress((int)Math.min(100,s.getLong("total")==0?0:100.0*row.getLong("spent")/s.getLong("total")));host.content.addView(bar);
            }
            host.button(host.content,"Thêm khoản chi thủ công",()->host.transactionForm(null));
            host.api.call("GET","/transactions?month="+host.month,null,list->{
                JSONArray rows=(JSONArray)list;
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);
                    host.button(host.content,row.getString("date")+" · "+row.getString("description")+"\n"+host.money(row.getLong("amount_vnd"))+" · "+host.label(row.getString("category_id")),()->host.transactionForm(row));
                }
            },host::error);
        },host::error);
    }
    static void transactionForm(MainActivity host, JSONObject row) throws Exception {
        JSONObject f=row==null?new JSONObject():row; LinearLayout box=host.column();
        EditText name=host.input(box,"Nội dung",f.optString("description"),false);
        EditText amount=host.input(box,"Số tiền VND",f.optString("amount_vnd"),true);
        EditText date=host.input(box,"Ngày yyyy-MM-dd",f.optString("date",LocalDate.now().toString()),false);
        Spinner category=host.categoryPicker(box,f.optString("category_id"));
        if(row!=null)host.button(box,"Xóa giao dịch",()->new AlertDialog.Builder(host).setMessage("Xóa khoản chi này?").setNegativeButton("Hủy",null)
            .setPositiveButton("Xóa",(d,w)->host.run(()->host.api.call("DELETE","/transactions/"+row.getString("id"),null,v->host.load(),host::error))).show());
        host.formDialog(row==null?"Xác nhận khoản chi mới":"Sửa khoản chi đã lưu",box,()->{
            JSONObject body=new JSONObject().put("description",host.value(name)).put("amount_vnd",host.amount(amount)).put("date",host.value(date)).put("category_id",host.selected(category));
            host.api.call(row==null?"POST":"PUT","/transactions"+(row==null?"":"/"+row.getString("id")),body,r->host.load(),host::error);
        });
    }
}
