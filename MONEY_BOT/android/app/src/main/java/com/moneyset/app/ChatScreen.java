package com.moneyset.app;

import android.content.Intent;
import android.graphics.Color;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class ChatScreen {
    static void chatAction(MainActivity host, JSONObject action) {
        host.status.setText("Đang xử lý…");
        host.api.call("POST", "/chat/android", action, r -> { host.status.setText(""); host.renderChat((JSONObject) r); }, host::error);
    }
    static JSONObject draftAction(MainActivity host, String kind, JSONObject draft) throws Exception {
        return new JSONObject().put("action", kind).put("draft_id", draft.getString("id")).put("version", draft.getInt("version"));
    }
    static void renderChat(MainActivity host, JSONObject state) throws Exception {
        host.chat = state; host.content.removeAllViews();
        host.text(host.content, "Trợ lý chi tiêu", 22);
        host.text(host.content, "Ghi khoản chi · Hỏi tổng chi · Tạo hóa đơn · Xem đến hạn", 14);
        JSONArray history = state.getJSONArray("history");
        for (int i=Math.max(0, history.length()-20); i<history.length(); i++) {
            JSONObject item = history.getJSONObject(i);
            TextView line = host.text(host.content, (item.getString("role").equals("user") ? "Bạn: " : "Bot: ") + item.getString("content"), 16);
            if (item.getString("role").equals("user")) line.setTextColor(Color.rgb(78,55,160));
        }
        if (state.has("ocr_text")) host.text(host.content, "Nội dung OCR (cần kiểm tra):\n" + state.getString("ocr_text"), 14);
        JSONObject d = state.optJSONObject("draft");
        if (d != null) {
            host.text(host.content, "Bản nháp — chưa lưu", 20);
            JSONObject fields = d.getJSONObject("fields");
            if(fields.has("description"))host.text(host.content,fields.getString("description"),18);
            if(fields.has("name"))host.text(host.content,fields.getString("name"),18);
            if(fields.has("amount_vnd"))host.text(host.content,"Số tiền: "+host.money(fields.getLong("amount_vnd")),18);
            if(fields.has("category_id"))host.text(host.content,"Danh mục: "+host.label(fields.getString("category_id")),16);
            if(fields.has("date"))host.text(host.content,"Ngày chi: "+fields.getString("date"),16);
            if(fields.has("due_day"))host.text(host.content,"Ngày lặp: "+fields.getInt("due_day"),16);
            if(fields.has("start_month"))host.text(host.content,"Bắt đầu: "+fields.getString("start_month"),16);
            if(fields.has("month"))host.text(host.content,"Tháng: "+fields.getString("month"),16);
            JSONObject errors = state.getJSONObject("errors");
            for (java.util.Iterator<String> it=errors.keys(); it.hasNext();) host.text(host.content, errors.getString(it.next()), 15);
            host.button(host.content, "Sửa bản nháp", () -> host.draftForm(d));
            Button confirm = host.button(host.content, "Xác nhận lưu", () -> host.chatAction(host.draftAction("confirm", d)));
            confirm.setEnabled(state.optBoolean("can_confirm"));
            host.button(host.content, "Hủy bản nháp", () -> host.chatAction(host.draftAction("cancel", d)));
        }
        JSONObject overview = state.optJSONObject("overview");
        if (overview != null) host.showAlerts(overview);
        EditText message = host.input(host.content, "Nhập câu tiếng Việt", "", false);
        host.button(host.content, "Gửi", () -> { String t=host.value(message); if (!t.isEmpty()) host.chatAction(new JSONObject().put("action","send").put("text",t)); });
        host.button(host.content, "Nhập bằng giọng nói", () -> {
            Intent voice=new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            voice.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            voice.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE,"vi-VN");
            try { host.startActivityForResult(voice,102); } catch(android.content.ActivityNotFoundException e) { host.error("Thiết bị chưa có dịch vụ nhận giọng nói; hãy nhập bằng bàn phím."); }
        });
        host.button(host.content, "Chọn ảnh / PDF hóa đơn", () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*"); intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png","image/jpeg","image/webp","application/pdf"});
            host.startActivityForResult(intent, 101);
        });
        if (!state.isNull("pending")) host.button(host.content, "Kiểm tra kết quả lưu Chat", () -> host.chatAction(new JSONObject().put("action", "retry")));
    }
    static void draftForm(MainActivity host, JSONObject draft) throws Exception {
        JSONObject f=draft.getJSONObject("fields"); LinearLayout box=host.column();
        if(draft.getString("intent").equals("query_spending")){
            EditText queryMonth=host.input(box,"Tháng yyyy-MM",f.optString("month"),false);
            host.formDialog("Chọn tháng cần xem",box,()->host.chatAction(host.draftAction("edit",draft).put("fields",new JSONObject().put("month",host.value(queryMonth)))));
            return;
        }
        boolean bill=draft.getString("intent").equals("create_recurring_bill");
        EditText name=host.input(box,bill?"Tên hóa đơn":"Nội dung",f.optString(bill?"name":"description"),false);
        EditText amount=host.input(box,"Số tiền VND",f.has("amount_vnd")?f.optString("amount_vnd"):"",true);
        EditText day=host.input(box,bill?"Ngày lặp 1–31":"Ngày yyyy-MM-dd",f.optString(bill?"due_day":"date"),bill);
        EditText start=bill?host.input(box,"Tháng bắt đầu yyyy-MM",f.optString("start_month"),false):null;
        Spinner cat=bill?null:host.categoryPicker(box,f.optString("category_id"));
        host.formDialog("Sửa bản nháp",box,()->{
            JSONObject changes=new JSONObject().put(bill?"name":"description",host.value(name)).put("amount_vnd",host.amount(amount));
            if(bill) changes.put("due_day",Integer.parseInt(host.value(day))).put("start_month",host.value(start));
            else changes.put("date",host.value(day)).put("category_id",host.selected(cat));
            host.chatAction(host.draftAction("edit",draft).put("fields",changes));
        });
    }
}
