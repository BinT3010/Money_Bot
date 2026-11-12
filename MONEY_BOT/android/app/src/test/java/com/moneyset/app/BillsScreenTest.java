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
        assertTrue(contains(root, "Nhắc hạn: Điện test · kỳ " + month + " · hạn " + today + " · đến hạn hôm nay"));
        assertTrue(contains(root, "không gửi thông báo nền"));
        // 100 000 budget, nothing spent, 80 000 due today -> enough, 20 000 left.
        assertTrue(contains(root, ": Đủ"));
        assertTrue(contains(root, "Hóa đơn chưa trả trong cửa sổ: " + a.money(80000) + " (1 kỳ)"));
        assertTrue(contains(root, "Ngân sách còn trừ nghĩa vụ: " + a.money(20000)));

        click(root, "Đánh dấu đã trả / Liên kết khoản chi"); idle(a);
        AlertDialog d = dialog();
        field(d.getWindow().getDecorView(), "Số tiền thực trả VND").setText("95000");
        confirm(a, d);
        assertTrue(a.status.getText().toString().contains(a.money(95000)));
        assertTrue(contains(root, "Đã trả ngày " + today + ": " + a.money(95000) + " (khác số dự kiến)"));
        JSONArray tx = transactions();
        assertEquals(1, tx.length());
        assertEquals(95000, tx.getJSONObject(0).getLong("amount_vnd"));
        assertEquals("utilities", tx.getJSONObject(0).getString("category_id"));
        assertEquals(95000, period("Điện test", month).getLong("paid_amount"));
        assertFalse(contains(root, "Nhắc hạn: Điện test"));
        a.finish();
    }

    @Test public void linkingExistingPaymentDoesNotChargeTwice() throws Exception {
        request("POST", "/transactions", new JSONObject().put("amount_vnd", 70000).put("description", "Nước đã ghi")
            .put("category_id", "utilities").put("date", today.toString()));
        createBill("Nước test", 65000, today.getDayOfMonth(), month);
        MainActivity a = openBills(); View root = a.getWindow().getDecorView();
        click(root, "Đánh dấu đã trả / Liên kết khoản chi"); idle(a);
        AlertDialog d = dialog(); View form = d.getWindow().getDecorView();
        View choice = find(form, "Chọn: Nước đã ghi", true);
        assertNotNull(choice); choice.performClick();
        assertEquals("70000", field(form, "Số tiền thực trả VND").getText().toString());
        confirm(a, d);
        assertTrue(a.status.getText().toString().contains("liên kết"));
        assertEquals(1, transactions().length());
        JSONObject paid = period("Nước test", month);
        assertEquals("PAID", paid.getString("status"));
        assertEquals(70000, paid.getLong("paid_amount"));
        a.finish();
    }

    @Test public void editStopAndResumeSchedules() throws Exception {
        createBill("Internet test", 50000, 12, month);
        YearMonth start = month.minusMonths(2);
        String gas = createBill("Gas test", 30000, 5, start);
        request("POST", "/bills/" + gas + "/pay", new JSONObject().put("month", start.toString()).put("paid_on", today.toString()));
        MainActivity a = openBills(); View root = a.getWindow().getDecorView();

        click(root, "Sửa lịch: Internet test");
        AlertDialog d = dialog();
        field(d.getWindow().getDecorView(), "Ngày lặp 1–31").setText("20");
        confirm(a, d);
        assertTrue(contains(root, "Internet test · " + a.money(50000) + " · ngày 20 · từ " + month + " · đang chạy"));

        // No paid period -> the schedule is deleted outright.
        click(root, "Ngừng lịch: Internet test"); confirm(a, dialog());
        assertTrue(a.status.getText().toString().startsWith("Đã xóa lịch"));
        assertFalse(contains(root, "Internet test"));

        // Paid history -> kept; current month unpaid -> ends at previous month.
        click(root, "Ngừng lịch: Gas test"); confirm(a, dialog());
        assertTrue(contains(root, "Gas test · " + a.money(30000) + " · ngày 5 · từ " + start + " · đã ngừng sau kỳ " + month.minusMonths(1)));
        assertEquals("PAID", period("Gas test", start).getString("status"));
        assertNotNull(find(root, "Tiếp tục lịch: Gas test", false));

        click(root, "Tiếp tục lịch: Gas test"); confirm(a, dialog());
        assertTrue(contains(root, "Gas test · " + a.money(30000) + " · ngày 5 · từ " + start + " · đang chạy"));
        assertEquals(month.toString(), period("Gas test", month).getString("month"));
        a.finish();
    }

    @Test public void periodPagingAndFullHistory() throws Exception {
        YearMonth start = month.minusMonths(70); // 71 unpaid periods, page size 50
        String id = createBill("Cũ test", 10000, 1, start);
        MainActivity a = openBills(); View root = a.getWindow().getDecorView();
        assertTrue(contains(root, "Đang hiển thị 50/71 kỳ"));
        click(root, "Tải thêm kỳ hóa đơn"); idle(a);
        assertTrue(contains(root, "Đang hiển thị 71/71 kỳ"));
        assertNull(find(root, "Tải thêm kỳ hóa đơn", false));

        // A paid period older than 12 months leaves the default view but stays in full history.
        request("POST", "/bills/" + id + "/pay", new JSONObject().put("month", start.toString()).put("paid_on", today.toString()));
        click(root, "Hóa đơn"); idle(a);
        assertTrue(contains(root, "Đang hiển thị 50/70 kỳ"));
        click(root, "Xem toàn bộ lịch sử đã trả"); idle(a);
        assertTrue(contains(root, "Đang hiển thị 50/71 kỳ"));
        assertTrue(contains(root, "Cũ test · kỳ " + start));
        assertTrue(contains(root, "Nợ tháng trước (không cộng vào đối chiếu): 69 kỳ · " + a.money(690000)));
        a.finish();
    }
}
