package com.moneyset.app;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Bills flow through the real BillsScreen against the Python backend on localhost:18080 (scripts/verify.py).
 * Expected values are derived from the inputs below, not from server output. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BillsScreenTest {
    private Context context;
    private ApiClient api;
    private final LocalDate today = BillsScreen.today();
    private final YearMonth month = YearMonth.from(today);
    private interface Call { void invoke(ApiClient.Success ok, ApiClient.Failure fail); }

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("moneybot", Context.MODE_PRIVATE).edit().clear().commit();
        BillsScreen.history = "recent";
        api = new ApiClient(context);
        api.base("http://127.0.0.1:18080/api");
        JSONObject body = new JSONObject().put("email", "bills-" + UUID.randomUUID() + "@moneybot.test").put("password", "Password123");
        api.login((JSONObject) request("POST", "/auth/register", body));
    }

    // ---- API helpers (test setup and independent checks of persisted state) ----
    private Object await(Call call) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        call.invoke(result::set, error::set);
        long deadline = System.currentTimeMillis() + 30000;
        while (result.get() == null && error.get() == null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20);
        }
        if (error.get() != null) throw new Exception(error.get());
        assertNotNull("HTTP request timed out", result.get());
        return result.get();
    }
    private Object request(String method, String path, JSONObject body) throws Exception {
        return await((ok, fail) -> api.call(method, path, body, ok, fail));
    }
    private String createBill(String name, long amount, int dueDay, YearMonth start) throws Exception {
        JSONObject body = new JSONObject().put("name", name).put("amount_vnd", amount).put("due_day", dueDay).put("start_month", start.toString());
        return ((JSONObject) request("POST", "/bills", body)).getString("id");
    }
    private JSONObject period(String name, YearMonth m) throws Exception {
        JSONArray rows = ((JSONObject) request("GET", "/bills?history=all&limit=500", null)).getJSONArray("periods");
        for (int i = 0; i < rows.length(); i++)
            if (rows.getJSONObject(i).getString("name").equals(name) && rows.getJSONObject(i).getString("month").equals(m.toString())) return rows.getJSONObject(i);
        throw new AssertionError("Missing period " + name + " " + m);
    }
    private JSONArray transactions() throws Exception { return (JSONArray) request("GET", "/transactions?month=" + month, null); }

    // ---- UI helpers ----
    private MainActivity openBills() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(a);
        click(a.getWindow().getDecorView(), "Hóa đơn"); idle(a);
        return a;
    }
    private void idle(MainActivity a) throws Exception {
        long end = System.currentTimeMillis() + 30000;
        do { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(30); } while (a.api.isBusy() && System.currentTimeMillis() < end);
        assertFalse("Activity request timed out", a.api.isBusy());
    }
    private View find(View view, String text, boolean prefix) {
        if (view instanceof TextView) {
            String value = ((TextView) view).getText().toString();
            if (prefix ? value.startsWith(text) : value.equals(text)) return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) { View r = find(g.getChildAt(i), text, prefix); if (r != null) return r; }
        }
        return null;
    }
    private boolean contains(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) { ViewGroup g = (ViewGroup) view; for (int i = 0; i < g.getChildCount(); i++) if (contains(g.getChildAt(i), text)) return true; }
        return false;
    }
    private void click(View root, String label) {
        View v = find(root, label, false);
        assertNotNull("Missing button " + label, v);
        v.performClick();
    }
    private void clickBill(View root,String name) {
        View header=findBillHeader(root,name);
        assertNotNull("Missing bill row "+name,header);header.performClick();
    }
    private View findBillHeader(View view,String name) {
        CharSequence description=view.getContentDescription();
        if(view.isClickable()&&description!=null&&description.toString().startsWith(name+" · "))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findBillHeader(group.getChildAt(i),name);if(found!=null)return found;}}
        return null;
    }
    private EditText field(View form, String label) {
        View v = findDescription(form, label);
        assertNotNull("Missing field " + label, v);
        return (EditText) v;
    }
    private View findDescription(View view, String label) {
        if (view instanceof EditText && label.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) { ViewGroup g = (ViewGroup) view; for (int i = 0; i < g.getChildCount(); i++) { View r = findDescription(g.getChildAt(i), label); if (r != null) return r; } }
        return null;
    }
    private AlertDialog dialog() { AlertDialog d = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(d); return d; }
    private void confirm(MainActivity a, AlertDialog d) throws Exception { d.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(a); }

    @Test public void reminderObligationAndActualPaidAmount() throws Exception {
        request("PUT", "/budgets/" + month, new JSONObject().put("total", 100000).put("limits", new JSONObject()));
        createBill("Điện test", 80000, today.getDayOfMonth(), month);
        MainActivity a = openBills(); View root = a.getWindow().getDecorView();
        JSONObject billState=(JSONObject)request("GET","/bills",null);
        assertEquals(1,billState.getJSONArray("reminders").length());
        assertEquals("ENOUGH",billState.getJSONObject("obligations").getString("status"));
        assertEquals(20000,billState.getJSONObject("obligations").getLong("difference"));

        clickBill(root,"Điện test");
        click(root, "Đánh dấu đã trả / Liên kết khoản chi"); idle(a);
        AlertDialog d = dialog();
        field(d.getWindow().getDecorView(), "Số tiền thực trả VND").setText("95000");
        confirm(a, d);
        assertTrue(a.status.getText().toString().contains(a.money(95000)));
        clickBill(root,"Điện test");
        assertTrue(contains(root, "Đã trả " + a.money(95000) + " ngày " + today));
        JSONArray tx = transactions();
        assertEquals(1, tx.length());
        assertEquals(95000, tx.getJSONObject(0).getLong("amount_vnd"));
        assertEquals("utilities", tx.getJSONObject(0).getString("category_id"));
        assertEquals(95000, period("Điện test", month).getLong("paid_amount"));
        assertEquals(0,((JSONObject)request("GET","/bills",null)).getJSONArray("reminders").length());
        a.finish();
    }

    @Test public void linkingExistingPaymentDoesNotChargeTwice() throws Exception {
        request("POST", "/transactions", new JSONObject().put("amount_vnd", 70000).put("description", "Nước đã ghi")
            .put("category_id", "utilities").put("date", today.toString()));
        createBill("Nước test", 65000, today.getDayOfMonth(), month);
        MainActivity a = openBills(); View root = a.getWindow().getDecorView();
        clickBill(root,"Nước test");
        click(root, "Đánh dấu đã trả / Liên kết khoản chi"); idle(a);
        AlertDialog d = dialog(); View form = d.getWindow().getDecorView();
        View choice = find(form, "Chọn: Nước đã ghi", true);
        assertNotNull(choice); choice.performClick();
        assertEquals("70,000", field(form, "Số tiền thực trả VND").getText().toString());
        confirm(a, d);
        assertTrue(a.status.getText().toString().contains("liên kết"));
        assertEquals(1, transactions().length());
        JSONObject paid = period("Nước test", month);
        assertEquals("PAID", paid.getString("status"));
        assertEquals(70000, paid.getLong("paid_amount"));
        a.finish();
    }

    @Test public void editStopAndResumeSchedules() throws Exception {
        String internet=createBill("Internet test", 50000, 12, month);
        YearMonth start = month.minusMonths(2);
        String gas = createBill("Gas test", 30000, 5, start);
        request("POST", "/bills/" + gas + "/pay", new JSONObject().put("month", start.toString()).put("paid_on", today.toString()));
        MainActivity a = openBills(); View root = a.getWindow().getDecorView();
        assertTrue(contains(root,"Internet test"));
        assertNull(find(root,"Sửa lịch: Internet test",false));
        assertNull(find(root,"Ngừng lịch: Gas test",false));

        request("PUT","/bills/"+internet,new JSONObject().put("name","Internet test").put("amount_vnd",50000)
            .put("due_day",20).put("start_month",month.toString()).put("end_month",JSONObject.NULL));
        assertTrue(period("Internet test",month).getString("due_date").endsWith("-20"));
        assertTrue(((JSONObject)request("DELETE","/bills/"+internet,null)).getBoolean("deleted"));

        request("DELETE","/bills/"+gas,null);
        assertEquals("PAID", period("Gas test", start).getString("status"));
        assertEquals("PAID",period("Gas test",start).getString("status"));
        request("PUT","/bills/"+gas,new JSONObject().put("name","Gas test").put("amount_vnd",30000)
            .put("due_day",5).put("start_month",start.toString()).put("end_month",JSONObject.NULL));
        assertEquals(month.toString(), period("Gas test", month).getString("month"));
        a.finish();
    }

    @Test public void periodPagingAndFullHistory() throws Exception {
        YearMonth start = month.minusMonths(70); // 71 unpaid periods, page size 50
        String id = createBill("Cũ test", 10000, 1, start);
        MainActivity a = openBills(); View root = a.getWindow().getDecorView();
        JSONObject first=(JSONObject)request("GET","/bills?limit=50&offset=0&history=recent",null);
        assertEquals(50,first.getJSONArray("periods").length());
        click(root, "Tải thêm kỳ hóa đơn"); idle(a);
        assertTrue(contains(root,"Cũ test"));
        assertNull(find(root, "Tải thêm kỳ hóa đơn", false));

        // A paid period older than 12 months leaves the default view but stays in full history.
        request("POST", "/bills/" + id + "/pay", new JSONObject().put("month", start.toString()).put("paid_on", today.toString()));
        click(root, "Hóa đơn"); idle(a);
        assertEquals(70,((JSONObject)request("GET","/bills?limit=50&offset=0&history=recent",null)).getInt("periods_total"));
        click(root,"∞");idle(a);
        click(root,"Tải thêm kỳ hóa đơn");idle(a);
        assertTrue(contains(root,"Cũ test"));
        assertNotNull(findBillHeader(root,"Cũ test"));
        a.finish();
    }
}
