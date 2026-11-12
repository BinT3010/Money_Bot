package com.moneyset.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP client duy nhất. Không chứa logic tiền, phân loại hay kho tài chính riêng.
 * Lưu request đang chờ TRƯỚC khi gửi; retry dùng nguyên key/body kể cả mở lại app.
 */
public final class ApiClient {
    public interface Success { void accept(Object value) throws Exception; }
    public interface Failure { void accept(String message); }
    private final SharedPreferences prefs;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean busy;

    public ApiClient(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("moneybot", Context.MODE_PRIVATE);
    }
    public String base() { return prefs.getString("base", "http://10.0.2.2:8080/api"); }
    public String token() { return prefs.getString("token", ""); }
    public String email() { return prefs.getString("email", ""); }
    public boolean pending() { return prefs.contains("pending"); }
    public boolean isBusy() { return busy; }
    public void base(String value) throws Exception {
        if (pending()) throw new Exception("Cần kiểm tra kết quả yêu cầu đang chờ trước khi đổi máy chủ.");
        URL url = new URL(value);
        if (!url.getProtocol().equals("http") && !url.getProtocol().equals("https")) throw new Exception("Địa chỉ phải bắt đầu bằng http:// hoặc https://");
        prefs.edit().putString("base", value.replaceAll("/+$", "")).remove("token").remove("email").commit();
    }
    public void login(JSONObject value) throws Exception {
        if (pending() && !new JSONObject(prefs.getString("pending", "{}")).optString("owner").equals(value.getString("email")))
            throw new Exception("Cần đăng nhập lại tài khoản đã gửi yêu cầu đang chờ để kiểm tra kết quả.");
        prefs.edit().putString("token", value.getString("token")).putString("email", value.getString("email")).commit();
    }
    public void clearLogin() { prefs.edit().remove("token").remove("email").commit(); }

    public void call(String method, String path, JSONObject body, Success ok, Failure error) {
        boolean mutation = !method.equals("GET") && !path.startsWith("/auth/");
        if (busy) { error.accept("Đang xử lý yêu cầu trước, vui lòng chờ."); return; }
        if (mutation && pending()) { error.accept("Có yêu cầu chưa rõ kết quả. Bấm Kiểm tra yêu cầu đang chờ."); return; }
        try {
            JSONObject request = new JSONObject().put("method", method).put("path", path)
                .put("body", body == null ? JSONObject.NULL : body).put("owner", email()).put("key", UUID.randomUUID().toString());
            if (mutation) prefs.edit().putString("pending", request.toString()).commit();
            execute(request, mutation, ok, error);
        } catch (Exception e) { error.accept(e.getMessage()); }
    }

    public void upload(byte[] data, Success ok, Failure error) {
        if (busy || pending()) { error.accept("Hãy hoàn tất yêu cầu đang chờ trước."); return; }
        try {
            File file = new File(context.getFilesDir(), "pending-receipt.bin");
            try (FileOutputStream out = new FileOutputStream(file)) { out.write(data); }
            JSONObject request = new JSONObject().put("method", "POST").put("path", "/chat/android/receipt")
                .put("upload", true).put("owner", email()).put("key", UUID.randomUUID().toString());
            prefs.edit().putString("pending", request.toString()).commit();
            execute(request, true, ok, error);
        } catch (Exception e) { error.accept("Không lưu được tệp để gửi: " + e.getMessage()); }
    }

    public void retry(Success ok, Failure error) {
        if (busy) { error.accept("Đang xử lý, vui lòng chờ."); return; }
        try {
            if (!pending()) { error.accept("Không có yêu cầu đang chờ."); return; }
            execute(new JSONObject(prefs.getString("pending", "{}")), true, ok, error);
        } catch (Exception e) { error.accept(e.getMessage()); }
    }

    private void execute(JSONObject request, boolean durable, Success ok, Failure error) {
        busy = true;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(base() + request.getString("path")).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(request.optBoolean("upload") ? 180000 : 30000);
                connection.setRequestMethod(request.getString("method"));
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Idempotency-Key", request.getString("key"));
                if (!token().isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token());
                if (request.optBoolean("upload")) {
                    String boundary = "MoneyBot" + request.getString("key");
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    connection.setDoOutput(true);
                    try (OutputStream out = connection.getOutputStream(); InputStream in = new FileInputStream(new File(context.getFilesDir(), "pending-receipt.bin"))) {
                        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"receipt\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                    }
                } else if (!request.isNull("body")) {
                    byte[] data = request.getJSONObject("body").toString().getBytes(StandardCharsets.UTF_8);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    connection.setDoOutput(true);
                    try (OutputStream out = connection.getOutputStream()) { out.write(data); }
                }
                int status = connection.getResponseCode();
                InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String text;
                try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in != null) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); }
                    text = out.toString("UTF-8");
                }
                if (status >= 400) {
                    // 4xx có kết quả từ chối rõ ràng. 5xx/mất mạng giữ key để thử lại.
                    if (durable && status < 500 && status != 401) clearPending();
                    if (status == 401) clearLogin();
                    String message = "HTTP " + status;
                    try { message = new JSONObject(text).optString("message", message); } catch (Exception ignored) { }
                    throw new IOException(message);
                }
                Object result = new JSONTokener(text).nextValue();
                if (durable) clearPending();
                main.post(() -> {
                    busy = false;
                    try { ok.accept(result); } catch (Exception e) { error.accept("Không đọc được kết quả: " + e.getMessage()); }
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Không kết nối được máy chủ." : e.getMessage();
                main.post(() -> { busy = false; error.accept(message + (pending() ? "\nYêu cầu được giữ lại; bấm Kiểm tra yêu cầu đang chờ." : "")); });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }
    private void clearPending() {
        prefs.edit().remove("pending").commit();
        new File(context.getFilesDir(), "pending-receipt.bin").delete();
    }
}
