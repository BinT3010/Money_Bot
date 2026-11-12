package com.moneyset.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.util.UUID;

/** HTTP client duy nhất. Không chứa logic tiền, phân loại hay kho tài chính riêng.
 * Lưu request đang chờ TRƯỚC khi gửi; retry dùng nguyên key/body kể cả mở lại app.
 */
public final class ApiClient {
    public interface Success { void accept(Object value) throws Exception; }
    public interface Failure { void accept(String message); }
    private final SharedPreferences prefs;
    private final Context context;
    private final HttpExecutor transport;
    private final SessionPreferences session;
    private final PendingRequestStore requests;

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("moneybot", Context.MODE_PRIVATE);
        transport = new HttpExecutor(this, this.context);
        session = new SessionPreferences(prefs);
        requests = new PendingRequestStore(prefs, this.context);
    }
    public String base() { return session.base(); }
    public String token() { return session.token(); }
    public String email() { return session.email(); }
    public boolean pending() { return requests.exists(); }
    public boolean isBusy() { return transport.busy; }
    public void base(String value) throws Exception {
        if (pending()) throw new Exception("Cần kiểm tra kết quả yêu cầu đang chờ trước khi đổi máy chủ.");
        URL url = new URL(value);
        if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) throw new Exception("Địa chỉ phải bắt đầu bằng http:// hoặc https://");
        prefs.edit().putString("base", value.replaceAll("/+$", "")).remove("token").remove("email").commit();
    }
    public void login(JSONObject value) throws Exception {
        if (pending() && !requests.read().optString("owner").equals(value.getString("email")))
            throw new Exception("Cần đăng nhập lại tài khoản đã gửi yêu cầu đang chờ để kiểm tra kết quả.");
        prefs.edit().putString("token", value.getString("token")).putString("email", value.getString("email")).commit();
    }
    public void clearLogin() { session.clearLogin(); }

    public void call(String method, String path, JSONObject body, Success ok, Failure error) {
        boolean mutation = !method.equals("GET") && !path.startsWith("/auth/");
        if (transport.busy) { error.accept("Đang xử lý yêu cầu trước, vui lòng chờ."); return; }
        if (mutation && pending()) { error.accept("Có yêu cầu chưa rõ kết quả. Bấm Kiểm tra yêu cầu đang chờ."); return; }
        try {
            JSONObject request = new JSONObject().put("method", method).put("path", path)
                .put("body", body == null ? JSONObject.NULL : body).put("owner", email()).put("key", UUID.randomUUID().toString());
            if (mutation) requests.save(request);
            transport.execute(request, mutation, ok, error);
        } catch (Exception e) { error.accept(e.getMessage()); }
    }

    public void upload(byte[] data, Success ok, Failure error) {
        if (transport.busy || pending()) { error.accept("Hãy hoàn tất yêu cầu đang chờ trước."); return; }
        try {
            File file = new File(context.getFilesDir(), "pending-receipt.bin");
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
            JSONObject request = new JSONObject().put("method", "POST").put("path", "/chat/android/receipt")
                .put("upload", true).put("owner", email()).put("key", UUID.randomUUID().toString());
            requests.save(request);
            transport.execute(request, true, ok, error);
        } catch (Exception e) { error.accept("Không lưu được tệp để gửi: " + e.getMessage()); }
    }

    public void retry(Success ok, Failure error) {
        if (transport.busy) { error.accept("Đang xử lý, vui lòng chờ."); return; }
        try {
            if (!pending()) { error.accept("Không có yêu cầu đang chờ."); return; }
            transport.execute(requests.read(), true, ok, error);
        } catch (Exception e) { error.accept(e.getMessage()); }
    }

    void clearPending() {
        requests.clear();
    }
}
