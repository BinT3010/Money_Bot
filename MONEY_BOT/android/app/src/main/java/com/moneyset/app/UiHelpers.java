package com.moneyset.app;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.YearMonth;
import java.util.Locale;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class UiHelpers {
    static int dp(MainActivity host, int value) { return (int) (value * host.getResources().getDisplayMetrics().density); }
    static void run(MainActivity host, MainActivity.Action action) { try { action.run(); } catch (Exception e) { host.error(e.getMessage()); } }
    static LinearLayout column(MainActivity host) { LinearLayout v = new LinearLayout(host); v.setOrientation(LinearLayout.VERTICAL); return v; }
    static void baseScreen(MainActivity host) {
        host.root = host.column(); host.root.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12)); host.root.setBackgroundColor(Color.rgb(246, 247, 252));
        host.setContentView(host.root);
        TextView title = host.text(host.root, "Money Bot", 26); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.rgb(78, 55, 160));
        host.status = host.text(host.root, "", 14); host.status.setTextColor(Color.rgb(110, 70, 30));
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
        host.status.setText(message == null ? "Có lỗi, vui lòng thử lại." : message);
        if (host.api.token().isEmpty() && !host.tab.equals("Login")) host.login();
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
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            try { save.run(); dialog.dismiss(); } catch (Exception e) { Toast.makeText(host,e.getMessage(),Toast.LENGTH_LONG).show(); }
        });
    }
    static void showAlerts(MainActivity host, JSONObject overview) throws Exception {
        JSONArray rows=overview.getJSONArray("categories");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String level=row.getString("level");
            if(!level.equals("NORMAL")&&!level.equals("UNSET")) host.text(host.content,row.getString("label")+": "+host.alert(level)+" · "+host.money(row.getLong("spent")),16);
        }
    }
}
