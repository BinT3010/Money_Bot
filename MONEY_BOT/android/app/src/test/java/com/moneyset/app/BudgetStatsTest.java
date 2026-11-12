package com.moneyset.app;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
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

/** Budget and statistics flows through the real screens against the Python backend on localhost:18080
 * (scripts/verify.py). Expected values are worked out by hand from the inputs below, not read from the server. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BudgetStatsTest {
    private ApiClient api;
    private LocalDate today;
    private YearMonth month;
    private interface Call { void invoke(ApiClient.Success ok, ApiClient.Failure fail); }

    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("moneybot", Context.MODE_PRIVATE).edit().clear().commit();
        api = new ApiClient(context);
        api.base("http://127.0.0.1:18080/api");
        api.login((JSONObject) request("POST", "/auth/register", new JSONObject()
            .put("email", "budget-" + UUID.randomUUID() + "@moneybot.test").put("password", "Password123")));
        today = LocalDate.parse(((JSONObject) request("GET", "/health", null)).getString("today"));
        month = YearMonth.from(today);
    }

    // ---- API helpers (setup and independent checks of persisted state) ----
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
    private void spend(String description, long amount, String category, LocalDate date) throws Exception {
        request("POST", "/transactions", new JSONObject().put("amount_vnd", amount).put("description", description)
            .put("category_id", category).put("date", date.toString()));
    }

    // ---- UI helpers ----
    private MainActivity open(String tab) throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        idle(a);
        click(a.getWindow().getDecorView(), tab); idle(a);
        a.month = month.toString(); a.load(); idle(a); // server month (Asia/Ho_Chi_Minh), not the JVM clock
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
    private <T extends View> T described(View view, String description, Class<T> type) {
        if (type.isInstance(view) && description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return type.cast(view);
        if (view instanceof ViewGroup) { ViewGroup g = (ViewGroup) view; for (int i = 0; i < g.getChildCount(); i++) { T r = described(g.getChildAt(i), description, type); if (r != null) return r; } }
        return null;
    }
    private EditText field(View root, String label) {
        EditText e = described(root, label, EditText.class);
        assertNotNull("Missing field " + label, e);
        return e;
    }
    private StatsScreen.MonthlyChart chart(View view) {
        if (view instanceof StatsScreen.MonthlyChart) return (StatsScreen.MonthlyChart) view;
        if (view instanceof ViewGroup) { ViewGroup g = (ViewGroup) view; for (int i = 0; i < g.getChildCount(); i++) { StatsScreen.MonthlyChart r = chart(g.getChildAt(i)); if (r != null) return r; } }
        return null;
    }
    private AlertDialog dialog() { AlertDialog d = ShadowAlertDialog.getLatestAlertDialog(); assertNotNull(d); return d; }
    private String m() { return String.valueOf(month.getMonthValue()); }

    @Test public void warningStatesAndCustomThreshold() throws Exception {
        // food 80 000/100 000 = exactly 80% -> WARNING; transport 50 000/50 000 -> REACHED;
        // utilities 250 000/200 000 -> OVER by 50 000 (125%); shopping limit 0 with no spending; other unset.
        request("PUT", "/budgets/" + month, new JSONObject().put("total", 400000).put("threshold", 80).put("limits", new JSONObject()
            .put("food", 100000).put("shopping", 0).put("transport", 50000).put("utilities", 200000)));
        spend("Cơm", 80000, "food", today);
        spend("Xe", 50000, "transport", today);
        spend("Điện", 250000, "utilities", today);
        spend("Linh tinh", 10000, "other", today);
        MainActivity a = open("Ngân sách"); View root = a.getWindow().getDecorView();

        assertTrue(contains(root, "cảnh báo sớm ở 80%"));
        assertNotNull(described(root, "level:food:WARNING", TextView.class));
        assertTrue(contains(root, "Sắp đạt hạn mức · Ăn uống đã dùng 80.000/100.000đ (80%), còn 20.000đ trong hạn mức tháng " + m() + "."));
        assertNotNull(described(root, "level:transport:REACHED", TextView.class));
        assertTrue(contains(root, "Đi lại đã dùng 50.000/50.000đ (100%), còn 0đ"));
        assertNotNull(described(root, "level:utilities:OVER", TextView.class));
        assertTrue(contains(root, "Hóa đơn tiện ích đã dùng 250.000/200.000đ (125%), vượt 50.000đ so với hạn mức tháng " + m() + "."));
        // Zero limit is a set limit, not "unset".
        assertNotNull(described(root, "level:shopping:NORMAL", TextView.class));
        assertTrue(contains(root, "hạn mức 0đ, chưa chi trong tháng " + m() + "."));
        assertNotNull(described(root, "level:other:UNSET", TextView.class));
        assertTrue(contains(root, "Chưa đặt hạn mức · Khác: chưa đặt hạn mức · đã chi " + a.money(10000) + "."));
        assertNotNull(described(root, "level:entertainment:UNSET", TextView.class));
        // 400 000 - 390 000 spent.
        assertTrue(contains(root, "Ngân sách tổng còn: " + a.money(10000)));

        // Raise the early-warning threshold to 90%: 80% food usage is now normal; 100% and over stay.
        field(root, "Cảnh báo sớm (%)").setText("90");
        click(root, "Xác nhận lưu ngân sách"); idle(a);
        assertEquals(90, ((JSONObject) request("GET", "/budgets/" + month, null)).getInt("threshold"));
        assertTrue(contains(root, "cảnh báo sớm ở 90%"));
        assertNotNull(described(root, "level:food:NORMAL", TextView.class));
        assertNotNull(described(root, "level:transport:REACHED", TextView.class));
        assertNotNull(described(root, "level:utilities:OVER", TextView.class));

        // Overspending the total budget: 390 000 + 20 000 = 410 000 > 400 000.
        spend("Thêm", 20000, "other", today);
        click(root, "Ngân sách"); idle(a);
        assertTrue(contains(root, "Vượt ngân sách tổng: " + a.money(10000)));
        a.finish();
    }

    @Test public void suggestionShowsBasisAndStaysUnsavedUntilConfirmed() throws Exception {
        YearMonth m3 = month.minusMonths(3), m2 = month.minusMonths(2), m1 = month.minusMonths(1);
        for (YearMonth past : new YearMonth[]{m3, m2, m1}) {
            spend("Ăn", 2000000, "food", past.atDay(5));
            spend("Xăng", 1000000, "transport", past.atDay(5));
        }
        spend("Quà", 100000, "other", m1.atDay(6));
        request("PUT", "/budgets/" + month, new JSONObject().put("total", 2400000).put("limits", new JSONObject()));
        // Three-month totals: food 6 000 000, transport 3 000 000, other 100 000 -> 9 100 000.
        // A = 3 033 333,33 > B = 2 400 000, so L_i = floor(total_i * B / 9 100 000):
        // food 1 582 417, transport 791 208, other 26 373; allocated 2 399 998, unallocated 2; B/A = 0,7912.
        MainActivity a = open("Ngân sách"); View root = a.getWindow().getDecorView();
        click(root, "Gợi ý từ 3 tháng trước"); idle(a);

        assertEquals("1582417", field(root, "Ăn uống").getText().toString());
        assertEquals("791208", field(root, "Đi lại").getText().toString());
        assertEquals("26373", field(root, "Khác").getText().toString());
        assertEquals("0", field(root, "Giải trí").getText().toString());
        assertTrue(contains(root, "Gợi ý hạn mức (chưa lưu) · căn cứ " + m3 + " – " + m1));
        assertTrue(contains(root, "B/A = 0,7912"));
        assertTrue(contains(root, "Trung bình tổng A: 3.033.333,33 đ · ngân sách B: " + a.money(2400000) + " · hệ số: 0,7912 (đã thu nhỏ theo B/A)"));
        assertTrue(contains(root, "Đã phân bổ: " + a.money(2399998) + " · chưa phân bổ: " + a.money(2)));
        assertTrue(contains(root, "33.333,33 đ")); // other: 100 000 / 3
        assertTrue(contains(root, m2.toString()));
        assertTrue(a.status.getText().toString().contains("Chưa lưu"));
        // Suggestion is not persisted until confirmation.
        assertEquals(0, ((JSONObject) request("GET", "/budgets/" + month, null)).getJSONObject("limits").length());

        click(root, "Bỏ gợi ý, giữ hạn mức đã lưu"); idle(a);
        assertEquals("", field(root, "Ăn uống").getText().toString());
        assertEquals(0, ((JSONObject) request("GET", "/budgets/" + month, null)).getJSONObject("limits").length());

        click(root, "Gợi ý từ 3 tháng trước"); idle(a);
        click(root, "Xác nhận lưu ngân sách"); idle(a);
        JSONObject saved = ((JSONObject) request("GET", "/budgets/" + month, null)).getJSONObject("limits");
        assertEquals(1582417, saved.getLong("food"));
        assertEquals(791208, saved.getLong("transport"));
        assertEquals(26373, saved.getLong("other"));
        assertEquals(0, saved.getLong("shopping"));
        a.finish();
    }

    @Test public void suggestionReportsMissingInputs() throws Exception {
        MainActivity a = open("Ngân sách"); View root = a.getWindow().getDecorView();
        click(root, "Gợi ý từ 3 tháng trước"); idle(a);
        assertTrue(contains(root, "Không gợi ý được: "));
        assertTrue(contains(root, "Thiếu: ngân sách tổng đã lưu cho tháng " + month));
        assertTrue(contains(root, "Thiếu: lịch sử chi bắt đầu từ " + month.minusMonths(3) + " trở về trước"));
        assertEquals("", field(root, "Ăn uống").getText().toString());
        a.finish();
    }

    @Test public void monthlyHistoryChartAndRefreshAfterEditAndDelete() throws Exception {
        YearMonth m1 = month.minusMonths(1), m5 = month.minusMonths(5);
        spend("Phở test", 120000, "food", today);
        spend("Xe test", 30000, "transport", m1.atDay(10));
        spend("Vé test", 45000, "entertainment", m1.atDay(11));
        spend("Cũ test", 7000, "other", m5.atDay(1));
        spend("Quá cũ test", 9000, "other", month.minusMonths(6).atDay(1)); // outside the 6-month window
        MainActivity a = open("Thống kê"); View root = a.getWindow().getDecorView();

        assertTrue(contains(root, "Đã chi: " + a.money(120000)));
        StatsScreen.MonthlyChart c = chart(root);
        assertNotNull(c);
        assertArrayEquals(new String[]{m5.toString(), month.minusMonths(4).toString(), month.minusMonths(3).toString(),
            month.minusMonths(2).toString(), m1.toString(), month.toString()}, c.months);
        assertArrayEquals(new long[]{7000, 0, 0, 0, 75000, 120000}, c.totals);
        assertTrue(c.getContentDescription().toString().contains(m1 + " " + a.money(75000)));
        TextView previous = described(root, "history:" + m1, TextView.class);
        assertNotNull(previous);
        assertEquals("Đi lại " + a.money(30000) + " · Giải trí " + a.money(45000), previous.getText().toString());
        assertTrue(contains(root, m1.toString()));
        assertNull(described(root, "history:" + month.minusMonths(6), TextView.class));

        // Edit through the shared transaction editor; the screen refreshes from the API.
        View row = find(root, today + " · Phở test", true);
        assertNotNull(row); row.performClick();
        AlertDialog form = dialog();
        field(form.getWindow().getDecorView(), "Số tiền VND").setText("150000");
        form.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(a);
        assertTrue(contains(root, "Đã chi: " + a.money(150000)));
        assertEquals(150000, chart(root).totals[5]);

        // Delete it; the month total, chart and table drop to zero.
        find(root, today + " · Phở test", true).performClick();
        click(dialog().getWindow().getDecorView(), "Xóa giao dịch");
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(a);
        assertTrue(contains(root, "Đã chi: " + a.money(0)));
        assertEquals(0, chart(root).totals[5]);
        assertNull(described(root, "history:" + month, TextView.class));
        assertTrue(contains(root, "Chưa có giao dịch trong tháng này."));
        a.finish();
    }
}
