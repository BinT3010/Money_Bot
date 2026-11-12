package com.moneyset.app;

import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class StatsScreen {
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
                if(!level.equals("NORMAL")&&!level.equals("UNSET"))UiHelpers.warningStyle(host,category);
                ProgressBar bar=new ProgressBar(host,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
                bar.setProgress((int)Math.min(100,s.getLong("total")==0?0:100.0*row.getLong("spent")/s.getLong("total")));host.content.addView(bar);
            }
            host.button(host.content,"Thêm khoản chi thủ công",()->host.transactionForm(null));
            host.api.call("GET","/transactions?month="+host.month,null,list->{
                JSONArray rows=(JSONArray)list;
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);
                    host.button(host.content,"",()->host.transactionForm(row)).setText(UiHelpers.moneyLabel(host,row.getString("date")+" · "+row.getString("description")+"\n",row.getLong("amount_vnd")," · "+host.label(row.getString("category_id")),true));
                }
            },host::error);
        },host::error);
    }
}
