package com.moneyset.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;

/** Giao diện Java native kế thừa các luồng Chat/Stats/Budget/Bills của FE Kotlin.
 * Activity chỉ thu thập input, hiển thị output. API Python quyết định nghiệp vụ.
 */
public class MainActivity extends Activity {
    private ApiClient api;
    private LinearLayout root, content;
    private TextView status;
    private String tab = "Chat", month = YearMonth.now().toString();
    private JSONObject chat;
    private final String[] categories = {"food", "shopping", "transport", "utilities", "entertainment", "other"};
    private final String[] labels = {"Ăn uống", "Tiêu dùng/Mua sắm", "Đi lại", "Hóa đơn tiện ích", "Giải trí", "Khác"};
    private interface Action { void run() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        api = new ApiClient(this);
        if (saved != null) { tab = saved.getString("tab", "Chat"); month = saved.getString("month", month); }
        if (api.token().isEmpty()) login(); else shell();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("tab", tab); out.putString("month", month); super.onSaveInstanceState(out);
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private void run(Action action) { try { action.run(); } catch (Exception e) { error(e.getMessage()); } }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private void baseScreen() {
        root = column(); root.setPadding(dp(16), dp(12), dp(16), dp(12)); root.setBackgroundColor(Color.rgb(246, 247, 252));
        setContentView(root);
        TextView title = text(root, "Money Bot", 26); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.rgb(78, 55, 160));
        status = text(root, "", 14); status.setTextColor(Color.rgb(110, 70, 30));
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setPadding(0, dp(6), 0, dp(6)); parent.addView(v); return v;
    }
    private Button button(LinearLayout parent, String label, Action action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); parent.addView(b); b.setOnClickListener(v -> run(action)); return b;
    }
    private EditText input(LinearLayout parent, String label, String value, boolean numeric) {
        text(parent, label, 14);
        EditText edit = new EditText(this); edit.setText(value); edit.setHint(label); edit.setContentDescription(label);
        edit.setSingleLine(true); edit.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        parent.addView(edit); return edit;
    }
    private void scroll() {
        ScrollView sc = new ScrollView(this); content = column(); sc.setFillViewport(true); sc.addView(content);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    private String value(EditText e) { return e.getText().toString().trim(); }
    private long amount(EditText e) { return Long.parseLong(value(e)); }
    private String money(long n) { return String.format(Locale.forLanguageTag("vi-VN"), "%,d đ", n); }
    private String label(String cid) { for (int i=0;i<categories.length;i++) if (categories[i].equals(cid)) return labels[i]; return "Chưa chọn"; }
    private String alert(String code) {
        switch (code) { case "WARNING": return "Sắp đạt hạn mức"; case "REACHED": return "Đã đạt 100% hạn mức";
            case "OVER": return "Đã vượt hạn mức"; case "NORMAL": return "Trong hạn mức"; default: return "Chưa đặt hạn mức"; }
    }
    private void error(String message) {
        if (isFinishing() || isDestroyed()) return;
        status.setText(message == null ? "Có lỗi, vui lòng thử lại." : message);
        if (api.token().isEmpty() && !tab.equals("Login")) login();
    }
    private void login() {
        tab = "Login"; baseScreen(); scroll();
        text(content, "Đăng nhập để dùng dữ liệu riêng của bạn", 19);
        EditText server = input(content, "Địa chỉ API", api.base(), false);
        text(content, "Emulator: http://10.0.2.2:8080/api\nMáy thật: dùng IP LAN của máy chạy Python.", 14);
        EditText email = input(content, "Email", "", false);
        EditText password = input(content, "Mật khẩu", "", false);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        for (String operation : new String[]{"login", "register"}) {
            button(content, operation.equals("login") ? "Đăng nhập" : "Tạo tài khoản", () -> {
                if (!value(server).equals(api.base())) api.base(value(server));
                status.setText("Đang kết nối…");
                api.call("POST", "/auth/" + operation, new JSONObject().put("email", value(email)).put("password", value(password)),
                    result -> { api.login((JSONObject) result); tab = "Chat"; shell(); }, this::error);
            });
        }
    }
    private void shell() {
        baseScreen();
        LinearLayout nav = new LinearLayout(this);
        HorizontalScrollView horizontal = new HorizontalScrollView(this); horizontal.addView(nav); root.addView(horizontal);
        for (String name : new String[]{"Chat", "Thống kê", "Ngân sách", "Hóa đơn", "Tài khoản"})
            button(nav, name, () -> { if(api.isBusy()) { error("Đang xử lý, vui lòng chờ."); return; } tab = name; load(); });
        button(root, "Kiểm tra yêu cầu đang chờ / Làm mới", () -> {
            if (api.pending()) api.retry(result -> load(), this::error); else load();
        });
        scroll(); load();
    }
    private void load() {
        if(api.isBusy()) { error("Đang xử lý, vui lòng chờ."); return; }
        content.removeAllViews(); status.setText(api.pending() ? "Có yêu cầu đang chờ kiểm tra kết quả." : "");
        switch (tab) {
            case "Chat": api.call("GET", "/chat/android", null, r -> renderChat((JSONObject) r), this::error); break;
            case "Thống kê": stats(); break;
            case "Ngân sách": budget(); break;
            case "Hóa đơn": bills(); break;
            default: account();
        }
    }
    private void monthPicker() {
        EditText e = input(content, "Tháng (yyyy-MM)", month, false);
        button(content, "Xem tháng", () -> { month = YearMonth.parse(value(e)).toString(); load(); });
    }
    private void chatAction(JSONObject action) {
        status.setText("Đang xử lý…");
        api.call("POST", "/chat/android", action, r -> { status.setText(""); renderChat((JSONObject) r); }, this::error);
    }
    private JSONObject draftAction(String kind, JSONObject draft) throws Exception {
        return new JSONObject().put("action", kind).put("draft_id", draft.getString("id")).put("version", draft.getInt("version"));
    }
    private void renderChat(JSONObject state) throws Exception {
        chat = state; content.removeAllViews();
        text(content, "Trợ lý chi tiêu", 22);
        text(content, "Ghi khoản chi · Hỏi tổng chi · Tạo hóa đơn · Xem đến hạn", 14);
        JSONArray history = state.getJSONArray("history");
        for (int i=Math.max(0, history.length()-20); i<history.length(); i++) {
            JSONObject item = history.getJSONObject(i);
            TextView line = text(content, (item.getString("role").equals("user") ? "Bạn: " : "Bot: ") + item.getString("content"), 16);
            if (item.getString("role").equals("user")) line.setTextColor(Color.rgb(78,55,160));
        }
        if (state.has("ocr_text")) text(content, "Nội dung OCR (cần kiểm tra):\n" + state.getString("ocr_text"), 14);
        JSONObject d = state.optJSONObject("draft");
        if (d != null) {
            text(content, "Bản nháp — chưa lưu", 20);
            JSONObject fields = d.getJSONObject("fields");
            if(fields.has("description"))text(content,fields.getString("description"),18);
            if(fields.has("name"))text(content,fields.getString("name"),18);
            if(fields.has("amount_vnd"))text(content,"Số tiền: "+money(fields.getLong("amount_vnd")),18);
            if(fields.has("category_id"))text(content,"Danh mục: "+label(fields.getString("category_id")),16);
            if(fields.has("date"))text(content,"Ngày chi: "+fields.getString("date"),16);
            if(fields.has("due_day"))text(content,"Ngày lặp: "+fields.getInt("due_day"),16);
            if(fields.has("start_month"))text(content,"Bắt đầu: "+fields.getString("start_month"),16);
            if(fields.has("month"))text(content,"Tháng: "+fields.getString("month"),16);
            JSONObject errors = state.getJSONObject("errors");
            for (java.util.Iterator<String> it=errors.keys(); it.hasNext();) text(content, errors.getString(it.next()), 15);
            button(content, "Sửa bản nháp", () -> draftForm(d));
            Button confirm = button(content, "Xác nhận lưu", () -> chatAction(draftAction("confirm", d)));
            confirm.setEnabled(state.optBoolean("can_confirm"));
            button(content, "Hủy bản nháp", () -> chatAction(draftAction("cancel", d)));
        }
        JSONObject overview = state.optJSONObject("overview");
        if (overview != null) showAlerts(overview);
        EditText message = input(content, "Nhập câu tiếng Việt", "", false);
        button(content, "Gửi", () -> { String t=value(message); if (!t.isEmpty()) chatAction(new JSONObject().put("action","send").put("text",t)); });
        button(content, "Nhập bằng giọng nói", () -> {
            Intent voice=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            voice.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            voice.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"vi-VN");
            try { startActivityForResult(voice,102); } catch(android.content.ActivityNotFoundException e) { error("Thiết bị chưa có dịch vụ nhận giọng nói; hãy nhập bằng bàn phím."); }
        });
        button(content, "Chọn ảnh / PDF hóa đơn", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png","image/jpeg","image/webp","application/pdf"});
            startActivityForResult(intent, 101);
        });
        if (!state.isNull("pending")) button(content, "Kiểm tra kết quả lưu Chat", () -> chatAction(new JSONObject().put("action", "retry")));
    }
    private Spinner categoryPicker(LinearLayout box, String selected) {
        text(box, "Danh mục", 14); Spinner spinner = new Spinner(this);
        String[] choices = new String[7]; choices[0] = "— Hãy chọn —"; System.arraycopy(labels,0,choices,1,6);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices));
        for (int i=0;i<categories.length;i++) if (categories[i].equals(selected)) spinner.setSelection(i+1);
        box.addView(spinner); return spinner;
    }
    private String selected(Spinner spinner) throws Exception {
        if (spinner.getSelectedItemPosition() == 0) throw new Exception("Hãy chọn danh mục.");
        return categories[spinner.getSelectedItemPosition()-1];
    }
    private void formDialog(String title, LinearLayout box, Action save) {
        ScrollView view = new ScrollView(this); view.addView(box); box.setPadding(dp(20),dp(4),dp(20),dp(4));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(view).setNegativeButton("Đóng",null).setPositiveButton("Áp dụng",null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            try { save.run(); dialog.dismiss(); } catch (Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
        });
    }
    private void draftForm(JSONObject draft) throws Exception {
        JSONObject f=draft.getJSONObject("fields"); LinearLayout box=column();
        if(draft.getString("intent").equals("query_spending")){
            EditText queryMonth=input(box,"Tháng yyyy-MM",f.optString("month"),false);
            formDialog("Chọn tháng cần xem",box,()->chatAction(draftAction("edit",draft).put("fields",new JSONObject().put("month",value(queryMonth)))));
            return;
        }
        boolean bill=draft.getString("intent").equals("create_recurring_bill");
        EditText name=input(box,bill?"Tên hóa đơn":"Nội dung",f.optString(bill?"name":"description"),false);
        EditText amount=input(box,"Số tiền VND",f.has("amount_vnd")?f.optString("amount_vnd"):"",true);
        EditText day=input(box,bill?"Ngày lặp 1–31":"Ngày yyyy-MM-dd",f.optString(bill?"due_day":"date"),bill);
        EditText start=bill?input(box,"Tháng bắt đầu yyyy-MM",f.optString("start_month"),false):null;
        Spinner cat=bill?null:categoryPicker(box,f.optString("category_id"));
        formDialog("Sửa bản nháp",box,()->{
            JSONObject changes=new JSONObject().put(bill?"name":"description",value(name)).put("amount_vnd",amount(amount));
            if(bill) changes.put("due_day",Integer.parseInt(value(day))).put("start_month",value(start));
            else changes.put("date",value(day)).put("category_id",selected(cat));
            chatAction(draftAction("edit",draft).put("fields",changes));
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==102&&result==RESULT_OK&&data!=null){
            java.util.ArrayList<String> words=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if(words!=null&&!words.isEmpty())run(()->chatAction(new JSONObject().put("action","send").put("text",words.get(0))));
            return;
        }
        if(request!=101||result!=RESULT_OK||data==null||data.getData()==null)return;
        status.setText("Đang đọc hóa đơn…");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192]; int n;
                while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>10*1024*1024)throw new Exception("Tệp tối đa 10 MB.");}
                byte[] bytes=out.toByteArray();
                runOnUiThread(()->api.upload(bytes,r->{tab="Chat";renderChat((JSONObject)r);status.setText("Kiểm tra kết quả OCR trước khi lưu.");},this::error));
            }catch(Exception e){runOnUiThread(()->error(e.getMessage()));}
        }).start();
    }
    private void showAlerts(JSONObject overview) throws Exception {
        JSONArray rows=overview.getJSONArray("categories");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String level=row.getString("level");
            if(!level.equals("NORMAL")&&!level.equals("UNSET")) text(content,row.getString("label")+": "+alert(level)+" · "+money(row.getLong("spent")),16);
        }
    }
    private void stats() {
        monthPicker();
        api.call("GET","/summary?month="+month,null,r->{
            JSONObject s=(JSONObject)r;text(content,"Đã chi: "+money(s.getLong("total")),24);text(content,s.getInt("count")+" giao dịch",16);
            if(!s.isNull("remaining"))text(content,"Ngân sách còn: "+money(s.getLong("remaining")),18);
            JSONArray cats=s.getJSONArray("categories");
            for(int i=0;i<cats.length();i++){
                JSONObject row=cats.getJSONObject(i);text(content,row.getString("label")+": "+money(row.getLong("spent"))+" · "+alert(row.getString("level")),16);
                ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
                bar.setProgress((int)Math.min(100,s.getLong("total")==0?0:100.0*row.getLong("spent")/s.getLong("total")));content.addView(bar);
            }
            button(content,"Thêm khoản chi thủ công",()->transactionForm(null));
            api.call("GET","/transactions?month="+month,null,list->{
                JSONArray rows=(JSONArray)list;
                for(int i=0;i<rows.length();i++){
                    JSONObject row=rows.getJSONObject(i);
                    button(content,row.getString("date")+" · "+row.getString("description")+"\n"+money(row.getLong("amount_vnd"))+" · "+label(row.getString("category_id")),()->transactionForm(row));
                }
            },this::error);
        },this::error);
    }
    private void transactionForm(JSONObject row) throws Exception {
        JSONObject f=row==null?new JSONObject():row; LinearLayout box=column();
        EditText name=input(box,"Nội dung",f.optString("description"),false);
        EditText amount=input(box,"Số tiền VND",f.optString("amount_vnd"),true);
        EditText date=input(box,"Ngày yyyy-MM-dd",f.optString("date",LocalDate.now().toString()),false);
        Spinner category=categoryPicker(box,f.optString("category_id"));
        if(row!=null)button(box,"Xóa giao dịch",()->new AlertDialog.Builder(this).setMessage("Xóa khoản chi này?").setNegativeButton("Hủy",null)
            .setPositiveButton("Xóa",(d,w)->run(()->api.call("DELETE","/transactions/"+row.getString("id"),null,v->load(),this::error))).show());
        formDialog(row==null?"Xác nhận khoản chi mới":"Sửa khoản chi đã lưu",box,()->{
            JSONObject body=new JSONObject().put("description",value(name)).put("amount_vnd",amount(amount)).put("date",value(date)).put("category_id",selected(category));
            api.call(row==null?"POST":"PUT","/transactions"+(row==null?"":"/"+row.getString("id")),body,r->load(),this::error);
        });
    }
    private void budget() {
        monthPicker();
        api.call("GET","/budgets/"+month,null,r->{
            JSONObject b=(JSONObject)r, limits=b.getJSONObject("limits");
            EditText total=input(content,"Ngân sách tổng VND",b.isNull("total")?"":b.getString("total"),true);
            EditText threshold=input(content,"Cảnh báo sớm (%)",b.getString("threshold"),true);
            EditText[] edits=new EditText[6];
            text(content,"Để trống: chưa đặt hạn mức. Nhập 0: không dành ngân sách cho danh mục.",14);
            for(int i=0;i<6;i++)edits[i]=input(content,labels[i],limits.optString(categories[i]),true);
            button(content,"Xác nhận lưu ngân sách",()->{
                JSONObject values=new JSONObject();for(int i=0;i<6;i++)if(!value(edits[i]).isEmpty())values.put(categories[i],amount(edits[i]));
                api.call("PUT","/budgets/"+month,new JSONObject().put("total",amount(total)).put("threshold",Integer.parseInt(value(threshold))).put("limits",values),v->load(),this::error);
            });
            button(content,"Gợi ý từ 3 tháng trước",()->api.call("GET","/budgets/"+month+"/suggestions",null,v->{
                JSONObject result=(JSONObject)v;
                status.setText(result.getString("reason")+"\nChưa lưu; kiểm tra rồi bấm Xác nhận lưu ngân sách.");
                if(result.optBoolean("available"))for(int i=0;i<6;i++)edits[i].setText(result.getJSONObject("limits").getString(categories[i]));
            },this::error));
        },this::error);
    }
    private void bills() {
        button(content,"Tạo lịch hóa đơn",()->billForm());
        api.call("GET","/bills",null,r->{
            JSONObject response=(JSONObject)r, obligations=response.getJSONObject("obligations");
            text(content,"Nghĩa vụ từ "+obligations.getString("start")+" đến "+obligations.getString("end")+": "+money(obligations.getLong("obligations")),20);
            if(obligations.isNull("difference"))text(content,"Chưa đặt ngân sách để đối chiếu.",16);
            else text(content,"Ngân sách còn trừ nghĩa vụ: "+money(obligations.getLong("difference")),16);
            text(content,obligations.getString("note"),14);
            JSONArray reminders=response.getJSONArray("reminders");
            for(int i=0;i<reminders.length();i++){JSONObject n=reminders.getJSONObject(i);text(content,"Nhắc hạn: "+n.getString("name")+" · "+n.getInt("days")+" ngày nữa",18);}
            JSONArray schedules=response.getJSONArray("schedules");
            text(content,"Lịch định kỳ (tạo lịch chưa ghi chi)",20);
            for(int i=0;i<schedules.length();i++){JSONObject s=schedules.getJSONObject(i);text(content,s.getString("name")+" · ngày "+s.getInt("due_day")+" · từ "+s.getString("start_month"),16);}
            text(content,"Các kỳ hóa đơn",20);
            JSONArray rows=response.getJSONArray("periods");
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String state=row.getString("status");
                String translated=state.equals("PAID")?"Đã trả":state.equals("OVERDUE")?"Quá hạn":state.equals("DUE_TODAY")?"Đến hạn hôm nay":state.equals("DUE_SOON")?"Sắp đến hạn":"Chưa đến hạn";
                text(content,row.getString("name")+" · kỳ "+row.getString("month")+"\n"+money(row.getLong("amount_vnd"))+" · hạn "+row.getString("due_date")+" · "+translated,17);
                if(!state.equals("PAID"))button(content,"Đánh dấu đã trả / Liên kết khoản chi",()->payForm(row));
            }
        },this::error);
    }
    private void billForm() {
        LinearLayout box=column();EditText name=input(box,"Tên hóa đơn","",false),amount=input(box,"Số tiền VND","",true),day=input(box,"Ngày lặp 1–31","",true),start=input(box,"Tháng bắt đầu yyyy-MM",YearMonth.now().toString(),false);
        formDialog("Xác nhận tạo lịch",box,()->api.call("POST","/bills",new JSONObject().put("name",value(name)).put("amount_vnd",amount(amount)).put("due_day",Integer.parseInt(value(day))).put("start_month",value(start)),r->load(),this::error));
    }
    private void payForm(JSONObject row) throws Exception {
        // Chọn giao dịch hiện có thay vì luôn tạo khoản chi thứ hai.
        api.call("GET","/transactions?month="+YearMonth.now(),null,r->{
            JSONArray tx=(JSONArray)r;LinearLayout box=column();
            text(box,"Kỳ "+row.getString("month")+" · "+row.getString("name"),18);
            EditText paid=input(box,"Ngày thanh toán yyyy-MM-dd",LocalDate.now().toString(),false);
            text(box,"Mặc định tạo một khoản chi mới. Nếu đã ghi khoản này, nhập mã giao dịch để liên kết.",14);
            EditText link=input(box,"Mã giao dịch đã có (để trống nếu chưa ghi)","",false);
            for(int i=0;i<tx.length();i++){
                JSONObject t=tx.getJSONObject(i);
                if(t.getLong("amount_vnd")==row.getLong("amount_vnd")&&t.getString("category_id").equals("utilities"))
                    button(box,"Chọn: "+t.getString("description")+" · "+t.getString("date"),()->{link.setText(t.getString("id"));paid.setText(t.getString("date"));});
            }
            formDialog("Xác nhận đã thanh toán",box,()->api.call("POST","/bills/"+row.getString("id")+"/pay",new JSONObject().put("month",row.getString("month")).put("paid_on",value(paid)).put("expense_id",value(link).isEmpty()?JSONObject.NULL:value(link)),v->load(),this::error));
        },this::error);
    }
    private void account() {
        text(content,api.email(),20);text(content,api.base(),14);
        button(content,"Đổi mật khẩu",()->{
            LinearLayout box=column();EditText old=input(box,"Mật khẩu hiện tại","",false),next=input(box,"Mật khẩu mới","",false);
            old.setInputType(129);next.setInputType(129);
            formDialog("Đổi mật khẩu",box,()->api.call("POST","/auth/change-password",new JSONObject().put("old_password",value(old)).put("new_password",value(next)),r->{api.clearLogin();login();},this::error));
        });
        button(content,"Đăng xuất",()->{
            if(api.pending())throw new Exception("Kiểm tra yêu cầu đang chờ trước khi đăng xuất.");
            api.call("POST","/auth/logout",new JSONObject(),r->{api.clearLogin();login();},this::error);
        });
        text(content,"Dữ liệu được lưu tại máy chủ Python. Nhắc hạn hiển thị khi mở/làm mới ứng dụng; không phải thông báo nền khi app đã đóng.",16);
    }
}
