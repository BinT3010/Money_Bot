package com.moneyset.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.YearMonth;

/** Giao diện Java native kế thừa các luồng Chat/Stats/Budget/Bills của FE Kotlin.
 * Activity chỉ thu thập input, hiển thị output. API Python quyết định nghiệp vụ.
 */
public class MainActivity extends Activity {
    ApiClient api;
    LinearLayout root, content;
    TextView status;
    String tab = "Thêm khoản chi", month = YearMonth.now().toString();
    JSONObject chat;
    final String[] categories = {"food", "shopping", "transport", "utilities", "entertainment", "other"};
    final String[] labels = {"Ăn uống", "Tiêu dùng/Mua sắm", "Đi lại", "Hóa đơn tiện ích", "Giải trí", "Khác"};
    interface Action { void run() throws Exception; }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        api = new ApiClient(this);
        if (saved != null) { tab = saved.getString("tab", "Thêm khoản chi"); if(tab.equals("Chat"))tab="Thêm khoản chi"; month = saved.getString("month", month); }
        if (api.token().isEmpty()) login(); else shell();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("tab", tab); out.putString("month", month); super.onSaveInstanceState(out);
    }
    int dp(int value) { return UiHelpers.dp(this, value); }
    void run(Action action) { UiHelpers.run(this, action); }
    LinearLayout column() { return UiHelpers.column(this); }
    void baseScreen() { UiHelpers.baseScreen(this); }
    TextView text(LinearLayout parent, String value, int size) { return UiHelpers.text(this, parent, value, size); }
    Button button(LinearLayout parent, String label, Action action) { return UiHelpers.button(this, parent, label, action); }
    EditText input(LinearLayout parent, String label, String value, boolean numeric) { return UiHelpers.input(this, parent, label, value, numeric); }
    void scroll() { UiHelpers.scroll(this); }
    String value(EditText e) { return UiHelpers.value(this, e); }
    long amount(EditText e) { return UiHelpers.amount(this, e); }
    String money(long n) { return UiHelpers.money(this, n); }
    String label(String cid) { return UiHelpers.label(this, cid); }
    String alert(String code) { return UiHelpers.alert(this, code); }
    void error(String message) { UiHelpers.error(this, message); }
    void login() { AuthScreen.login(this); }
    void shell() {
        baseScreen();
        scroll(); UiHelpers.navigation(this); load();
    }
    void load() {
        if(api.isBusy()) { error("Đang xử lý, vui lòng chờ."); return; }
        content.removeAllViews(); UiHelpers.warningStatus(this, api.pending() ? "Có yêu cầu đang chờ kiểm tra kết quả." : "");
        switch (tab) {
            case "Thêm khoản chi": api.call("GET", "/chat/android", null, r -> renderChat((JSONObject) r), this::error); break;
            case "Thống kê": stats(); break;
            case "Ngân sách": budget(); break;
            case "Hóa đơn": bills(); break;
            default: account();
        }
    }
    void monthPicker() { UiHelpers.monthPicker(this); }
    void chatAction(JSONObject action) { ChatScreen.chatAction(this, action); }
    JSONObject draftAction(String kind, JSONObject draft) throws Exception { return ChatScreen.draftAction(this, kind, draft); }
    void renderChat(JSONObject state) throws Exception { ChatScreen.renderChat(this, state); }
    Spinner categoryPicker(LinearLayout box, String selected) { return UiHelpers.categoryPicker(this, box, selected); }
    String selected(Spinner spinner) throws Exception { return UiHelpers.selected(this, spinner); }
    void formDialog(String title, LinearLayout box, Action save) { UiHelpers.formDialog(this, title, box, save); }
    void draftForm(JSONObject draft) throws Exception { ChatScreen.draftForm(this, draft); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==102&&result==RESULT_OK&&data!=null){
            java.util.ArrayList<String> words=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
            if(words!=null&&!words.isEmpty())run(()->chatAction(new JSONObject().put("action","send").put("text",words.get(0))));
            return;
        }
        if(request!=101||result!=RESULT_OK||data==null||data.getData()==null)return;
        UiHelpers.status(this, "Đang đọc hóa đơn…");
        new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(data.getData()); ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[8192]; int n;
                while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>10*1024*1024)throw new Exception("Tệp tối đa 10 MB.");}
                byte[] bytes=out.toByteArray();
                runOnUiThread(()->api.upload(bytes,r->{tab="Thêm khoản chi";renderChat((JSONObject)r);UiHelpers.warningStatus(this, "Kiểm tra kết quả OCR trước khi lưu.");},this::error));
            }catch(Exception e){runOnUiThread(()->error(e.getMessage()));}
        }).start();
    }
    void showAlerts(JSONObject overview) throws Exception { UiHelpers.showAlerts(this, overview); }
    void stats() { StatsScreen.stats(this); }
    void transactionForm(JSONObject row) throws Exception { TransactionEditor.transactionForm(this, row); }
    void budget() { BudgetScreen.budget(this); }
    void bills() { BillsScreen.bills(this); }
    void billForm() { BillsScreen.billForm(this); }
    void payForm(JSONObject row) throws Exception { BillsScreen.payForm(this, row); }
    void account() { AuthScreen.account(this); }
}
