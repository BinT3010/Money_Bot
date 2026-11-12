package com.moneyset.app;

import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/** Bills tab: schedules -> reminders -> payment/linking -> period history and budget reconciliation.
 * Every amount, status, window and reconciliation figure comes from GET /api/bills; this class only
 * renders it and collects input. Reminders exist only when the tab is opened or refreshed. */
final class BillsScreen {
    static final int PAGE = 50;
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** View state only: "recent" (unpaid + 12 months of paid periods) or "all". */
    static String history = "recent";

    static LocalDate today() { return LocalDate.now(ZONE); }
    static String page(int offset) { return "/bills?limit="+PAGE+"&offset="+offset+"&history="+history; }

    static void bills(MainActivity host) {
        host.button(host.content,"Tạo lịch hóa đơn",()->host.billForm());
        host.api.call("GET",page(0),null,r->render(host,(JSONObject)r),host::error);
    }

    static void render(MainActivity host, JSONObject response) throws Exception {
        reminders(host,response.getJSONArray("reminders"));
        obligations(host,response.getJSONObject("obligations"));
        schedules(host,response.getJSONArray("schedules"));
        host.text(host.content,"Các kỳ hóa đơn",20);
        host.text(host.content,history.equals("all")?"Đang xem toàn bộ lịch sử kỳ đã trả.":"Kỳ chưa trả luôn hiển thị; kỳ đã trả chỉ hiển thị 12 tháng gần nhất.",14);
        host.button(host.content,history.equals("all")?"Chỉ xem 12 tháng gần nhất":"Xem toàn bộ lịch sử đã trả",()->{history=history.equals("all")?"recent":"all";host.load();});
        LinearLayout list=host.column();host.content.addView(list);
        appendPeriods(host,list,response);
    }

    static void reminders(MainActivity host, JSONArray reminders) throws Exception {
        host.text(host.content,"Nhắc hạn",20);
        host.text(host.content,"Lời nhắc được tính khi mở hoặc làm mới tab này; ứng dụng không gửi thông báo nền.",13);
        if(reminders.length()==0)host.text(host.content,"Không có hóa đơn chưa trả đến mốc nhắc (còn 3 ngày, 1 ngày hoặc đến hạn hôm nay).",15);
        for(int i=0;i<reminders.length();i++){
            JSONObject n=reminders.getJSONObject(i);int days=n.getInt("days");
            host.text(host.content,"Nhắc hạn: "+n.getString("name")+" · kỳ "+n.getString("month")+" · hạn "+n.getString("due_date")+" · "
                +(days==0?"đến hạn hôm nay":days+" ngày nữa")+" · "+host.money(n.getLong("amount_vnd")),17);
        }
    }

    static String reconciliation(String status) {
        switch(status){ case "ENOUGH": return "Đủ"; case "SHORT": return "Thiếu"; default: return "Chưa đủ thông tin để kết luận"; }
    }

    static void obligations(MainActivity host, JSONObject o) throws Exception {
        host.text(host.content,"Đối chiếu nghĩa vụ "+o.getString("start")+" → "+o.getString("end")+": "+reconciliation(o.getString("status")),20);
        host.text(host.content,"Ngân sách tổng tháng: "+(o.isNull("budget_total")?"chưa đặt":host.money(o.getLong("budget_total"))),16);
        host.text(host.content,"Đã chi đã xác nhận: "+host.money(o.getLong("spent")),16);
        host.text(host.content,"Ngân sách còn lại: "+(o.isNull("remaining_budget")?"chưa xác định (thiếu ngân sách tổng)":host.money(o.getLong("remaining_budget"))),16);
        JSONArray items=o.getJSONArray("items");
        host.text(host.content,"Hóa đơn chưa trả trong cửa sổ: "+host.money(o.getLong("obligations"))+" ("+items.length()+" kỳ)",16);
        for(int i=0;i<items.length();i++)host.text(host.content,"• "+periodLine(host,items.getJSONObject(i)),15);
        if(!o.isNull("difference"))host.text(host.content,"Ngân sách còn trừ nghĩa vụ: "+host.money(o.getLong("difference")),16);
        host.text(host.content,o.getString("message"),16);
        if(!o.isNull("excluded"))host.text(host.content,o.getString("excluded"),15);
        int olderCount=o.getInt("older_unpaid_count");
        if(olderCount>0){
            JSONArray older=o.getJSONArray("older_unpaid");
            host.text(host.content,"Nợ tháng trước (không cộng vào đối chiếu): "+olderCount+" kỳ · "+host.money(o.getLong("older_unpaid_total"))
                +(older.length()<olderCount?" · hiển thị "+older.length()+" kỳ gần nhất":""),16);
            for(int i=0;i<older.length();i++)host.text(host.content,"• "+periodLine(host,older.getJSONObject(i)),15);
        }
        JSONArray next=o.getJSONArray("next_month");
        if(next.length()>0){
            host.text(host.content,"Hóa đơn tháng sau đến hạn trong 7 ngày tới, tính cả hôm nay (không cộng vào đối chiếu):",16);
            for(int i=0;i<next.length();i++)host.text(host.content,"• "+periodLine(host,next.getJSONObject(i)),15);
        }
        host.text(host.content,o.getString("note"),13);
    }

    static String status(String state) {
        switch(state){ case "PAID": return "Đã trả"; case "OVERDUE": return "Quá hạn"; case "DUE_TODAY": return "Đến hạn hôm nay";
            case "DUE_SOON": return "Sắp đến hạn"; default: return "Chưa đến hạn"; }
    }

    static String periodLine(MainActivity host, JSONObject row) throws Exception {
        return row.getString("name")+" · kỳ "+row.getString("month")+" · hạn "+row.getString("due_date")+" · "+host.money(row.getLong("amount_vnd"))+" · "+status(row.getString("status"));
    }

    static void schedules(MainActivity host, JSONArray schedules) throws Exception {
        host.text(host.content,"Lịch định kỳ (tạo lịch chưa ghi chi)",20);
        if(schedules.length()==0)host.text(host.content,"Chưa có lịch hóa đơn.",15);
        for(int i=0;i<schedules.length();i++){
            JSONObject s=schedules.getJSONObject(i);String name=s.getString("name");boolean running=s.isNull("end_month");
            host.text(host.content,name+" · "+host.money(s.getLong("amount_vnd"))+" · ngày "+s.getInt("due_day")+" · từ "+s.getString("start_month")
                +(running?" · đang chạy":" · đã ngừng sau kỳ "+s.getString("end_month")),16);
            host.button(host.content,"Sửa lịch: "+name,()->editForm(host,s));
            if(running)host.button(host.content,"Ngừng lịch: "+name,()->stopForm(host,s));
            else host.button(host.content,"Tiếp tục lịch: "+name,()->resumeForm(host,s));
        }
    }

    static void appendPeriods(MainActivity host, LinearLayout list, JSONObject response) throws Exception {
        JSONArray rows=response.getJSONArray("periods");
        if(response.getInt("periods_total")==0)host.text(list,"Chưa có kỳ hóa đơn nào.",15);
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String state=row.getString("status");
            String line=row.getString("name")+" · kỳ "+row.getString("month")+"\n"+host.money(row.getLong("amount_vnd"))+" dự kiến · hạn "+row.getString("due_date")+" · "+status(state);
            if(state.equals("PAID"))line+="\nĐã trả ngày "+row.getString("paid_on")+": "
                +(row.isNull("paid_amount")?"không còn khoản chi liên kết":host.money(row.getLong("paid_amount"))
                  +(row.getLong("paid_amount")!=row.getLong("amount_vnd")?" (khác số dự kiến)":""));
            host.text(list,line,17);
            if(!state.equals("PAID"))host.button(list,"Đánh dấu đã trả / Liên kết khoản chi",()->host.payForm(row));
        }
        int shown=response.getInt("offset")+rows.length(),total=response.getInt("periods_total");
        if(total>0)host.text(list,"Đang hiển thị "+shown+"/"+total+" kỳ",13);
        if(response.getBoolean("has_more")){
            Button[] more=new Button[1];
            more[0]=host.button(list,"Tải thêm kỳ hóa đơn",()->host.api.call("GET",page(shown),null,r->{
                list.removeView(more[0]);appendPeriods(host,list,(JSONObject)r);
            },host::error));
        }
    }

    static Integer day(EditText e) throws Exception {
        try { return Integer.parseInt(e.getText().toString().trim()); } catch(NumberFormatException x) { throw new Exception("Ngày lặp phải là số từ 1 đến 31."); }
    }
    static long vnd(EditText e) throws Exception {
        try { return Long.parseLong(e.getText().toString().trim().replaceAll("[.,\\s]","")); } catch(NumberFormatException x) { throw new Exception("Số tiền phải là số nguyên VND."); }
    }

    static void billForm(MainActivity host) {
        LinearLayout box=host.column();EditText name=host.input(box,"Tên hóa đơn","",false),amount=host.input(box,"Số tiền VND","",true),day=host.input(box,"Ngày lặp 1–31","",true),start=host.input(box,"Tháng bắt đầu yyyy-MM",YearMonth.now(ZONE).toString(),false);
        host.formDialog("Xác nhận tạo lịch",box,()->host.api.call("POST","/bills",new JSONObject().put("name",host.value(name)).put("amount_vnd",vnd(amount)).put("due_day",day(day)).put("start_month",host.value(start)),r->host.load(),host::error));
    }

    static void editForm(MainActivity host, JSONObject s) throws Exception {
        LinearLayout box=host.column();
        host.text(box,"Đổi ngày lặp thì hạn của các kỳ chưa trả và lời nhắc được tính lại. Kỳ đã trả phải còn nằm trong lịch.",14);
        EditText name=host.input(box,"Tên hóa đơn",s.getString("name"),false),amount=host.input(box,"Số tiền VND",String.valueOf(s.getLong("amount_vnd")),true),
            day=host.input(box,"Ngày lặp 1–31",String.valueOf(s.getInt("due_day")),true),start=host.input(box,"Tháng bắt đầu yyyy-MM",s.getString("start_month"),false),
            end=host.input(box,"Tháng kết thúc yyyy-MM (để trống = đang chạy)",s.isNull("end_month")?"":s.getString("end_month"),false);
        host.formDialog("Xác nhận sửa lịch",box,()->{
            JSONObject body=new JSONObject().put("name",host.value(name)).put("amount_vnd",vnd(amount)).put("due_day",day(day)).put("start_month",host.value(start))
                .put("end_month",host.value(end).isEmpty()?JSONObject.NULL:host.value(end));
            host.api.call("PUT","/bills/"+s.getString("id"),body,r->{host.load();host.status.setText("Đã cập nhật lịch "+((JSONObject)r).getString("name")+".");},host::error);
        });
    }

    static void stopForm(MainActivity host, JSONObject s) throws Exception {
        LinearLayout box=host.column();
        host.text(box,"Lịch chưa có kỳ nào đã trả sẽ bị xóa hẳn. Lịch đã có kỳ trả được giữ lại để xem lịch sử; kỳ chưa trả trước tháng kết thúc vẫn hiển thị là nợ.",14);
        EditText end=host.input(box,"Tháng kết thúc yyyy-MM (để trống = tự chọn)","",false);
        host.formDialog("Xác nhận ngừng lịch "+s.getString("name"),box,()->{
            String path="/bills/"+s.getString("id")+(host.value(end).isEmpty()?"":"?end_month="+java.net.URLEncoder.encode(host.value(end),"UTF-8"));
            host.api.call("DELETE",path,null,r->{
                JSONObject result=(JSONObject)r;host.load();
                host.status.setText(result.getBoolean("deleted")?"Đã xóa lịch (chưa có kỳ nào được trả).":"Đã ngừng lịch sau kỳ "+result.getString("end_month")+"; lịch sử đã trả được giữ.");
            },host::error);
        });
    }

    static void resumeForm(MainActivity host, JSONObject s) throws Exception {
        LinearLayout box=host.column();
        host.text(box,"Tiếp tục "+s.getString("name")+" từ sau kỳ "+s.getString("end_month")+". Các kỳ từ đó đến tháng hiện tại sẽ hiển thị là chưa trả.",14);
        host.formDialog("Xác nhận tiếp tục lịch",box,()->{
            JSONObject body=new JSONObject().put("name",s.getString("name")).put("amount_vnd",s.getLong("amount_vnd")).put("due_day",s.getInt("due_day"))
                .put("start_month",s.getString("start_month")).put("end_month",JSONObject.NULL);
            host.api.call("PUT","/bills/"+s.getString("id"),body,r->{host.load();host.status.setText("Đã tiếp tục lịch "+s.getString("name")+".");},host::error);
        });
    }

    static void payForm(MainActivity host, JSONObject row) throws Exception {
        // Liệt kê giao dịch Hóa đơn tiện ích tháng của kỳ và tháng hiện tại để liên kết thay vì ghi chi lần hai.
        String current=YearMonth.now(ZONE).toString(),month=row.getString("month");
        host.api.call("GET","/transactions?month="+current,null,first->{
            if(month.equals(current)){payDialog(host,row,(JSONArray)first);return;}
            host.api.call("GET","/transactions?month="+month,null,second->{
                JSONArray all=(JSONArray)first,other=(JSONArray)second;
                for(int i=0;i<other.length();i++)all.put(other.get(i));
                payDialog(host,row,all);
            },host::error);
        },host::error);
    }

    static void payDialog(MainActivity host, JSONObject row, JSONArray tx) throws Exception {
        LinearLayout box=host.column();
        host.text(box,"Kỳ "+row.getString("month")+" · "+row.getString("name")+" · dự kiến "+host.money(row.getLong("amount_vnd")),18);
        EditText paid=host.input(box,"Ngày thanh toán yyyy-MM-dd",today().toString(),false);
        EditText amount=host.input(box,"Số tiền thực trả VND",String.valueOf(row.getLong("amount_vnd")),true);
        host.text(box,"Mặc định tạo một khoản chi mới bằng số tiền thực trả. Nếu đã ghi khoản này, chọn hoặc nhập mã giao dịch để liên kết; khi liên kết, số tiền thực trả lấy theo giao dịch đó và không ghi chi lần nữa.",14);
        EditText link=host.input(box,"Mã giao dịch đã có (để trống nếu chưa ghi)","",false);
        for(int i=0;i<tx.length();i++){
            JSONObject t=tx.getJSONObject(i);
            if(t.getString("category_id").equals("utilities"))
                host.button(box,"Chọn: "+t.getString("description")+" · "+host.money(t.getLong("amount_vnd"))+" · "+t.getString("date"),
                    ()->{link.setText(t.getString("id"));paid.setText(t.getString("date"));amount.setText(String.valueOf(t.getLong("amount_vnd")));});
        }
        host.formDialog("Xác nhận đã thanh toán",box,()->{
            boolean linked=!host.value(link).isEmpty();
            JSONObject body=new JSONObject().put("month",row.getString("month")).put("paid_on",host.value(paid))
                .put("expense_id",linked?host.value(link):JSONObject.NULL).put("amount_vnd",linked?JSONObject.NULL:vnd(amount));
            host.api.call("POST","/bills/"+row.getString("id")+"/pay",body,v->{
                JSONObject result=(JSONObject)v;host.load();
                host.status.setText(result.getBoolean("already_paid")?"Kỳ này đã được ghi nhận trước đó; không ghi chi thêm."
                    :"Đã ghi nhận kỳ "+row.getString("month")+": "+host.money(result.getLong("paid_amount"))+(linked?" (liên kết khoản chi đã có).":"."));
            },host::error);
        });
    }
}
