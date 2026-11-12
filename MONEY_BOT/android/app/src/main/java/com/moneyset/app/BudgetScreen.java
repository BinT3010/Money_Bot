package com.moneyset.app;

import android.widget.*;
import org.json.JSONObject;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class BudgetScreen {
    static void budget(MainActivity host) {
        host.monthPicker();
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
                host.api.call("PUT","/budgets/"+host.month,new JSONObject().put("total",host.amount(total)).put("threshold",Integer.parseInt(host.value(threshold))).put("limits",values),v->host.load(),host::error);
            });
            host.button(host.content,"Gợi ý từ 3 tháng trước",()->host.api.call("GET","/budgets/"+host.month+"/suggestions",null,v->{
                JSONObject result=(JSONObject)v;
                String note=result.getString("reason")+"\nChưa lưu; kiểm tra rồi bấm Xác nhận lưu ngân sách.";
                if(result.optBoolean("available"))UiHelpers.status(host,note);else UiHelpers.warningStatus(host,note);
                if(result.optBoolean("available"))for(int i=0;i<6;i++)edits[i].setText(result.getJSONObject("limits").getString(host.categories[i]));
            },host::error));
        },host::error);
    }
}
