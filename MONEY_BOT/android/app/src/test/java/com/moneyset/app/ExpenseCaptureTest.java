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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Java forms -> real Python API -> isolated SQLite (shared verification server). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ExpenseCaptureTest {
    private ApiClient api;
    private String today;
    private interface Call { void run(ApiClient.Success ok, ApiClient.Failure error); }

    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("moneybot", Context.MODE_PRIVATE).edit().clear().commit();
        api = new ApiClient(context);
        api.base("http://127.0.0.1:18080/api");
        api.login((JSONObject) request("POST", "/auth/register", new JSONObject()
            .put("email", "capture-" + UUID.randomUUID() + "@example.test").put("password", "Password123")));
        today = ((JSONObject) request("GET", "/health", null)).getString("today");
    }
    private Object await(Call call) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        call.run(result::set, error::set);
        long end = System.currentTimeMillis() + 30000;
        while (result.get() == null && error.get() == null && System.currentTimeMillis() < end) {
            Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20);
        }
        assertNull(error.get(), error.get());
        assertNotNull("Request timed out", result.get());
        return result.get();
    }
    private Object request(String method, String path, JSONObject body) throws Exception {
        return await((ok, error) -> api.call(method, path, body, ok, error));
    }
    private JSONArray rows() throws Exception {
        return (JSONArray) request("GET", "/transactions?month=" + today.substring(0, 7), null);
    }
    private void idle(MainActivity activity) throws Exception {
        long end = System.currentTimeMillis() + 30000;
        do { Shadows.shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20); }
        while (activity.api.isBusy() && System.currentTimeMillis() < end);
        assertFalse("Activity request timed out", activity.api.isBusy());
    }
    private View find(View view, String text, boolean description) {
        if (description && text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (!description && view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text, description); if (found != null) return found;
            }
        }
        return null;
    }
    private void fill(AlertDialog dialog, String label, String value) {
        View input = find(dialog.getWindow().getDecorView(), label, true);
        assertNotNull(label, input); ((EditText) input).setText(value);
    }
    @Test public void chatMerchantIsCorrectableAndSavedOnlyAfterConfirmation() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        try {
            idle(a);
            View root = a.getWindow().getDecorView();
            ((EditText) find(root, "Nhập câu tiếng Việt", true)).setText("ăn trưa 45k; cửa hàng: Quán Minh");
            find(root, "Gửi", false).performClick(); idle(a);
            assertNotNull(find(root, "Cửa hàng: Quán Minh", false));
            assertEquals(0, rows().length());
            JSONObject current = a.chat.getJSONObject("draft");
            a.chatAction(new JSONObject().put("action", "edit").put("draft_id", current.getString("id"))
                .put("version", current.getInt("version")).put("fields", new JSONObject().put("merchant", JSONObject.NULL)));
            idle(a);
            assertNull(find(root, "Cửa hàng: null", false));
            find(root, "Sửa bản nháp", false).performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            fill(dialog, "Cửa hàng (tùy chọn)", "Quán An");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(a);
            assertNotNull(find(root, "Cửa hàng: Quán An", false));
            assertEquals(0, rows().length());
            find(root, "Xác nhận lưu", false).performClick(); idle(a);
            assertEquals("Quán An", rows().getJSONObject(0).getString("merchant"));
            assertEquals("food", rows().getJSONObject(0).getString("category_id"));
            ApiClient restarted = new ApiClient(RuntimeEnvironment.getApplication());
            JSONArray persisted = (JSONArray) await((ok, error) -> restarted.call("GET", "/transactions?month=" + today.substring(0, 7), null, ok, error));
            assertEquals(1, persisted.length());
            assertEquals("Quán An", persisted.getJSONObject(0).getString("merchant"));
        } finally { a.finish(); }
    }
    @Test public void manualMerchantRoundTripAndExplicitClear() throws Exception {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        try {
            idle(a); a.transactionForm(null);
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            fill(dialog, "Nội dung", "Ăn trưa"); fill(dialog, "Số tiền VND", "45000");
            fill(dialog, "Ngày yyyy-MM-dd", today); fill(dialog, "Cửa hàng (tùy chọn)", "Quán Minh");
            android.widget.Spinner category = (android.widget.Spinner) findSpinner(dialog.getWindow().getDecorView());
            category.setSelection(1);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(a);
            JSONObject row = rows().getJSONObject(0);
            assertEquals("Quán Minh", row.getString("merchant"));
            a.transactionForm(row); dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("Quán Minh", ((EditText) find(dialog.getWindow().getDecorView(), "Cửa hàng (tùy chọn)", true)).getText().toString());
            fill(dialog, "Cửa hàng (tùy chọn)", "");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle(a);
            assertTrue(rows().getJSONObject(0).isNull("merchant"));
            a.transactionForm(rows().getJSONObject(0)); dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertEquals("", ((EditText) find(dialog.getWindow().getDecorView(), "Cửa hàng (tùy chọn)", true)).getText().toString());
            dialog.dismiss();
        } finally { a.finish(); }
    }
    private View findSpinner(View view) {
        if (view instanceof android.widget.Spinner) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { View result = findSpinner(group.getChildAt(i)); if (result != null) return result; }
        }
        return null;
    }
    @Test public void pdfMerchantDraftCanBeCancelledWithoutSaving() throws Exception {
        String[] parts = today.split("-");
        byte[] data = pdf("Store: CAFE AN", "RECEIPT", "ca phe", "TOTAL 45,000", parts[2] + "/" + parts[1] + "/" + parts[0]);
        JSONObject result = (JSONObject) await((ok, error) -> api.upload(data, ok, error));
        assertEquals("CAFE AN", result.getJSONObject("draft").getJSONObject("fields").getString("merchant"));
        assertEquals(0, rows().length());
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        try {
            idle(a); View root = a.getWindow().getDecorView();
            assertNotNull(find(root, "Cửa hàng: CAFE AN", false));
            find(root, "Hủy bản nháp", false).performClick(); idle(a);
            assertEquals(0, rows().length());
            assertTrue(((JSONObject) request("GET", "/chat/android", null)).isNull("draft"));
        } finally { a.finish(); }
    }
    private byte[] pdf(String... lines) throws Exception {
        StringBuilder stream = new StringBuilder("BT /F1 20 Tf 40 750 Td ");
        for (String line : lines) stream.append("(").append(line).append(") Tj 0 -30 Td ");
        stream.append("ET");
        String[] objects = {"<< /Type /Catalog /Pages 2 0 R >>", "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream"};
        ByteArrayOutputStream out = new ByteArrayOutputStream(); out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[5];
        for (int i = 0; i < objects.length; i++) { offsets[i] = out.size(); out.write(((i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n").getBytes(StandardCharsets.US_ASCII)); }
        int xref = out.size(); out.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int offset : offsets) out.write(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset).getBytes(StandardCharsets.US_ASCII));
        out.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }
}
