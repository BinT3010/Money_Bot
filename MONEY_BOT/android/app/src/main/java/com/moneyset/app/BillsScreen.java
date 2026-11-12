package com.moneyset.app;

import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class BillsScreen {
    static void bills(MainActivity host) {
        host.button(host.content,"Tạo lịch hóa đơn",()->host.billForm());
        host.api.call("GET","/bills",null,r->{
            JSONObject response=(JSONObject)r, obligations=response.getJSONObject("obligations");
            UiHelpers.moneyText(host,host.content,"Nghĩa vụ từ "+obligations.getString("start")+" đến "+obligations.getString("end")+": ",obligations.getLong("obligations"),20,true);
            if(obligations.isNull("difference"))UiHelpers.warning(host,host.content,"Chưa đặt ngân sách để đối chiếu.",16);
            else UiHelpers.moneyText(host,host.content,"Ngân sách còn trừ nghĩa vụ: ",obligations.getLong("difference"),16,false);
            if(!obligations.isNull("difference")&&obligations.getLong("difference")<0)UiHelpers.warning(host,host.content,obligations.getString("note"),14);
            else host.text(host.content,obligations.getString("note"),14);
            JSONArray reminders=response.getJSONArray("reminders");
            for(int i=0;i<reminders.length();i++){JSONObject n=reminders.getJSONObject(i);UiHelpers.warning(host,host.content,"Nhắc hạn: "+n.getString("name")+" · "+n.getInt("days")+" ngày nữa",18);}
            JSONArray schedules=response.getJSONArray("schedules");
            host.text(host.content,"Lịch định kỳ (tạo lịch chưa ghi chi)",20);
            for(int i=0;i<schedules.length();i++){JSONObject s=schedules.getJSONObject(i);host.text(host.content,s.getString("name")+" · ngày "+s.getInt("due_day")+" · từ "+s.getString("start_month"),16);}
            host.text(host.content,"Các kỳ hóa đơn",20);
            JSONArray rows=response.getJSONArray("periods");
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String state=row.getString("status");
                String translated=state.equals("PAID")?"Đã trả":state.equals("OVERDUE")?"Quá hạn":state.equals("DUE_TODAY")?"Đến hạn hôm nay":state.equals("DUE_SOON")?"Sắp đến hạn":"Chưa đến hạn";
                TextView period=host.text(host.content,"",17);
                period.setText(UiHelpers.moneyLabel(host,row.getString("name")+" · kỳ "+row.getString("month")+"\n",row.getLong("amount_vnd")," · hạn "+row.getString("due_date")+" · "+translated,true));
                if(state.equals("OVERDUE")||state.equals("DUE_TODAY")||state.equals("DUE_SOON"))UiHelpers.warningStyle(host,period);
                if(!state.equals("PAID"))host.button(host.content,"Đánh dấu đã trả / Liên kết khoản chi",()->host.payForm(row));
            }
        },host::error);
    }
    static void billForm(MainActivity host) {
        LinearLayout box=host.column();EditText name=host.input(box,"Tên hóa đơn","",false),amount=host.input(box,"Số tiền VND","",true),day=host.input(box,"Ngày lặp 1–31","",true),start=host.input(box,"Tháng bắt đầu yyyy-MM",YearMonth.now().toString(),false);
        host.formDialog("Xác nhận tạo lịch",box,()->host.api.call("POST","/bills",new JSONObject().put("name",host.value(name)).put("amount_vnd",host.amount(amount)).put("due_day",Integer.parseInt(host.value(day))).put("start_month",host.value(start)),r->host.load(),host::error));
    }
    static void payForm(MainActivity host, JSONObject row) throws Exception {
        // Chọn giao dịch hiện có thay vì luôn tạo khoản chi thứ hai.
        host.api.call("GET","/transactions?month="+YearMonth.now(),null,r->{
            JSONArray tx=(JSONArray)r;LinearLayout box=host.column();
            host.text(box,"Kỳ "+row.getString("month")+" · "+row.getString("name"),18);
            EditText paid=host.input(box,"Ngày thanh toán yyyy-MM-dd",LocalDate.now().toString(),false);
            host.text(box,"Mặc định tạo một khoản chi mới. Nếu đã ghi khoản này, nhập mã giao dịch để liên kết.",14);
            EditText link=host.input(box,"Mã giao dịch đã có (để trống nếu chưa ghi)","",false);
            for(int i=0;i<tx.length();i++){
                JSONObject t=tx.getJSONObject(i);
                if(t.getLong("amount_vnd")==row.getLong("amount_vnd")&&t.getString("category_id").equals("utilities"))
                    host.button(box,"Chọn: "+t.getString("description")+" · "+t.getString("date"),()->{link.setText(t.getString("id"));paid.setText(t.getString("date"));});
            }
            host.formDialog("Xác nhận đã thanh toán",box,()->host.api.call("POST","/bills/"+row.getString("id")+"/pay",new JSONObject().put("month",row.getString("month")).put("paid_on",host.value(paid)).put("expense_id",host.value(link).isEmpty()?JSONObject.NULL:host.value(link)),v->host.load(),host::error));
        },host::error);
    }
}
