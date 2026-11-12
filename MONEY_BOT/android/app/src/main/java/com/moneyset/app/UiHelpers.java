package com.moneyset.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.YearMonth;
import java.util.Locale;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class UiHelpers {
    static final int EXPENSE_RED = Color.rgb(185, 28, 28);
    static final int FUNDS_GREEN = Color.rgb(21, 128, 61);
    static final int NEUTRAL = Color.rgb(71, 85, 105);

    static void warningStyle(MainActivity host, TextView view) {
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.rgb(254, 242, 242));
        box.setStroke(host.dp(1), Color.rgb(252, 165, 165));
        box.setCornerRadius(host.dp(8));
        view.setBackground(box); view.setTextColor(EXPENSE_RED);
        view.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.setMargins(0, host.dp(6), 0, host.dp(6)); view.setLayoutParams(layout);
        view.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    static TextView warning(MainActivity host, LinearLayout parent, String message, int size) {
        TextView view = text(host, parent, message, size); warningStyle(host, view);
        view.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
        return view;
    }
    static void status(MainActivity host, String message) {
        host.status.setBackground(null); host.status.setTextColor(NEUTRAL);
        host.status.setPadding(0, host.dp(6), 0, host.dp(6));
        host.status.setText(message);
        host.status.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }
    static void warningStatus(MainActivity host, String message) {
        status(host, message); warningStyle(host, host.status);
    }
    static CharSequence moneyLabel(MainActivity host, String prefix, long amount, String suffix, boolean expense) {
        String value = money(host, amount);
        SpannableString label = new SpannableString(prefix + value + suffix);
        int color = expense || amount < 0 ? EXPENSE_RED : amount > 0 ? FUNDS_GREEN : NEUTRAL;
        label.setSpan(new ForegroundColorSpan(color), prefix.length(), prefix.length() + value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return label;
    }
    static TextView moneyText(MainActivity host, LinearLayout parent, String prefix, long amount, int size, boolean expense) {
        TextView view = text(host, parent, "", size);
        view.setText(moneyLabel(host, prefix, amount, "", expense));
        return view;
    }

    static int dp(MainActivity host, int value) { return (int) (value * host.getResources().getDisplayMetrics().density); }
    static void run(MainActivity host, MainActivity.Action action) { try { action.run(); } catch (Exception e) { host.error(e.getMessage()); } }
    static LinearLayout column(MainActivity host) { LinearLayout v = new LinearLayout(host); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static void baseScreen(MainActivity host) {
        host.root = host.column(); host.root.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12)); host.root.setBackgroundColor(Color.rgb(246, 247, 252));
        host.setContentView(host.root);
        TextView title = host.text(host.root, "Money Bot", 26); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.rgb(78, 55, 160));
        host.status = host.text(host.root, "", 14); status(host, "");
    }
    static TextView text(MainActivity host, LinearLayout parent, String value, int size) {
        TextView v = new TextView(host); v.setText(value); v.setTextSize(size); v.setPadding(0, host.dp(6), 0, host.dp(6)); parent.addView(v); return v;
    }
    static Button button(MainActivity host, LinearLayout parent, String label, MainActivity.Action action) {
        Button b = new Button(host); b.setText(label); b.setAllCaps(false); parent.addView(b); b.setOnClickListener(v -> host.run(action)); return b;
    }
    static EditText input(MainActivity host, LinearLayout parent, String label, String value, boolean numeric) {
        host.text(parent, label, 14);
        EditText edit = new EditText(host); edit.setText(value); edit.setHint(label); edit.setContentDescription(label);
        edit.setSingleLine(true); edit.setInputType(numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
        parent.addView(edit); return edit;
    }
    static void scroll(MainActivity host) {
        ScrollView sc = new ScrollView(host); host.content = host.column(); sc.setFillViewport(true); sc.addView(host.content);
        host.root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    }
    static String value(MainActivity host, EditText e) { return e.getText().toString().trim(); }
    static long amount(MainActivity host, EditText e) { return Long.parseLong(host.value(e)); }
    static String money(MainActivity host, long n) { return String.format(Locale.forLanguageTag("vi-VN"), "%,d đ", n); }
    static String label(MainActivity host, String cid) { for (int i=0;i<host.categories.length;i++) if (host.categories[i].equals(cid)) return host.labels[i]; return "Chưa chọn"; }
    static String alert(MainActivity host, String code) {
        switch (code) { case "WARNING": return "Sắp đạt hạn mức"; case "REACHED": return "Đã đạt 100% hạn mức";
            case "OVER": return "Đã vượt hạn mức"; case "NORMAL": return "Trong hạn mức"; default: return "Chưa đặt hạn mức"; }
    }
    static void error(MainActivity host, String message) {
        if (host.isFinishing() || host.isDestroyed()) return;
        if (host.api.token().isEmpty() && !host.tab.equals("Login")) host.login();
        warningStatus(host, message == null ? "Có lỗi, vui lòng thử lại." : message);
    }
    static void monthPicker(MainActivity host) {
        EditText e = host.input(host.content, "Tháng (yyyy-MM)", host.month, false);
        host.button(host.content, "Xem tháng", () -> { host.month = YearMonth.parse(host.value(e)).toString(); host.load(); });
    }
    static Spinner categoryPicker(MainActivity host, LinearLayout box, String selected) {
        host.text(box, "Danh mục", 14); Spinner spinner = new Spinner(host);
        String[] choices = new String[7]; choices[0] = "— Hãy chọn —"; System.arraycopy(host.labels,0,choices,1,6);
        spinner.setAdapter(new ArrayAdapter<>(host, android.R.layout.simple_spinner_dropdown_item, choices));
        for (int i=0;i<host.categories.length;i++) if (host.categories[i].equals(selected)) spinner.setSelection(i+1);
        box.addView(spinner); return spinner;
    }
    static String selected(MainActivity host, Spinner spinner) throws Exception {
        if (spinner.getSelectedItemPosition() == 0) throw new Exception("Hãy chọn danh mục.");
        return host.categories[spinner.getSelectedItemPosition()-1];
    }
    static void formDialog(MainActivity host, String title, LinearLayout box, MainActivity.Action save) {
        ScrollView view = new ScrollView(host); view.addView(box); box.setPadding(host.dp(20),host.dp(4),host.dp(20),host.dp(4));
        AlertDialog dialog = new AlertDialog.Builder(host).setTitle(title).setView(view).setNegativeButton("Đóng",null).setPositiveButton("Áp dụng",null).create();
        TextView validation = warning(host, box, "", 14);
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            validation.setVisibility(View.GONE);
            try { save.run(); dialog.dismiss(); } catch (Exception e) { validation.setText(e instanceof NumberFormatException ? "Nhập số tiền và các trường số hợp lệ." : e.getMessage() == null ? "Kiểm tra dữ liệu đã nhập." : e.getMessage()); validation.setVisibility(View.VISIBLE); }
        });
    }
    static void showAlerts(MainActivity host, JSONObject overview) throws Exception {
        JSONArray rows=overview.getJSONArray("categories");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String level=row.getString("level");
            if(!level.equals("NORMAL")&&!level.equals("UNSET")) warning(host,host.content,row.optString("message",row.getString("label")+": "+host.alert(level)+" · "+host.money(row.getLong("spent"))),16);
        }
    }
}
