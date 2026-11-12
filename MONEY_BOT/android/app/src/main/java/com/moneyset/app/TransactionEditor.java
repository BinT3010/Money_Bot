package com.moneyset.app;

import android.app.AlertDialog;
import android.widget.*;
import org.json.JSONObject;
import java.time.LocalDate;

/** Expense capture owns transaction editing; statistics only launches it. */
final class TransactionEditor {
    static void transactionForm(MainActivity host, JSONObject row) throws Exception {
        JSONObject f=row==null?new JSONObject():row; LinearLayout box=host.column();
        EditText name=host.input(box,"Nội dung",f.optString("description"),false);
        EditText merchant=host.input(box,"Cửa hàng (tùy chọn)",f.isNull("merchant")?"":f.optString("merchant"),false);
        EditText amount=host.input(box,"Số tiền VND",f.optString("amount_vnd"),true);
        EditText date=host.input(box,"Ngày yyyy-MM-dd",f.optString("date",LocalDate.now().toString()),false);
        Spinner category=host.categoryPicker(box,f.optString("category_id"));
        if(row!=null)host.button(box,"Xóa giao dịch",()->new AlertDialog.Builder(host).setMessage("Xóa khoản chi này?").setNegativeButton("Hủy",null)
            .setPositiveButton("Xóa",(d,w)->host.run(()->host.api.call("DELETE","/transactions/"+row.getString("id"),null,v->host.load(),host::error))).show());
        host.formDialog(row==null?"Xác nhận khoản chi mới":"Sửa khoản chi đã lưu",box,()->{
            JSONObject body=new JSONObject().put("description",host.value(name)).put("amount_vnd",host.amount(amount)).put("date",host.value(date)).put("category_id",host.selected(category)).put("merchant",host.value(merchant));
            host.api.call(row==null?"POST":"PUT","/transactions"+(row==null?"":"/"+row.getString("id")),body,r->host.load(),host::error);
        });
    }
}
