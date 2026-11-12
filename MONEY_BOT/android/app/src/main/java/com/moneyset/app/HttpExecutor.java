package com.moneyset.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial HTTP execution; ApiClient owns the durable request lifecycle. */
final class HttpExecutor {
    private final ApiClient client;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    volatile boolean busy;
    HttpExecutor(ApiClient client, Context context) { this.client = client; this.context = context; }
    void execute(JSONObject request, boolean durable, ApiClient.Success ok, ApiClient.Failure error) {
        busy = true;
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(client.base() + request.getString("path")).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(request.optBoolean("upload") ? 180000 : 30000);
                connection.setRequestMethod(request.getString("method"));
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Idempotency-Key", request.getString("key"));
                if (!client.token().isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + client.token());
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
                    if (durable && status < 500 && status != 401) client.clearPending();
                    if (status == 401) client.clearLogin();
                    String message = "HTTP " + status;
                    try { message = new JSONObject(text).optString("message", message); } catch (Exception ignored) { }
                    throw new IOException(message);
                }
                Object result = new JSONTokener(text).nextValue();
                if (durable) client.clearPending();
                main.post(() -> {
                    busy = false;
                    try { ok.accept(result); } catch (Exception e) { error.accept("Không đọc được kết quả: " + e.getMessage()); }
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Không kết nối được máy chủ." : e.getMessage();
                main.post(() -> { busy = false; error.accept(message + (client.pending() ? "\nYêu cầu được giữ lại; bấm Kiểm tra yêu cầu đang chờ." : "")); });
            } finally { if (connection != null) connection.disconnect(); }
        });
    }
}
