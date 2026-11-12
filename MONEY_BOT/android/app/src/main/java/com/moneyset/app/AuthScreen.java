package com.moneyset.app;

import android.text.InputType;
import android.widget.*;
import org.json.JSONObject;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class AuthScreen {
    static void login(MainActivity host) {
        host.tab = "Login"; host.baseScreen(); host.scroll();
        host.text(host.content, "Đăng nhập để dùng dữ liệu riêng của bạn", 19);
        EditText server = host.input(host.content, "Địa chỉ API", host.api.base(), false);
        host.text(host.content, "Emulator: http://10.0.2.2:8080/api\nMáy thật: dùng IP LAN của máy chạy Python.", 14);
        EditText email = host.input(host.content, "Email", "", false);
        EditText password = host.input(host.content, "Mật khẩu", "", false);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        for (String operation : new String[]{"login", "register"}) {
            host.button(host.content, operation.equals("login") ? "Đăng nhập" : "Tạo tài khoản", () -> {
                if (!host.value(server).equals(host.api.base())) host.api.base(host.value(server));
                host.status.setText("Đang kết nối…");
                host.api.call("POST", "/auth/" + operation, new JSONObject().put("email", host.value(email)).put("password", host.value(password)),
                    result -> { host.api.login((JSONObject) result); host.tab = "Chat"; host.shell(); }, host::error);
            });
        }
    }
    static void account(MainActivity host) {
        host.text(host.content,host.api.email(),20);host.text(host.content,host.api.base(),14);
        host.button(host.content,"Đổi mật khẩu",()->{
            LinearLayout box=host.column();EditText old=host.input(box,"Mật khẩu hiện tại","",false),next=host.input(box,"Mật khẩu mới","",false);
            old.setInputType(129);next.setInputType(129);
            host.formDialog("Đổi mật khẩu",box,()->host.api.call("POST","/auth/change-password",new JSONObject().put("old_password",host.value(old)).put("new_password",host.value(next)),r->{host.api.clearLogin();host.login();},host::error));
        });
        host.button(host.content,"Đăng xuất",()->{
            if(host.api.pending())throw new Exception("Kiểm tra yêu cầu đang chờ trước khi đăng xuất.");
            host.api.call("POST","/auth/logout",new JSONObject(),r->{host.api.clearLogin();host.login();},host::error);
        });
        host.text(host.content,"Dữ liệu được lưu tại máy chủ Python. Nhắc hạn hiển thị khi mở/làm mới ứng dụng; không phải thông báo nền khi app đã đóng.",16);
    }
}
