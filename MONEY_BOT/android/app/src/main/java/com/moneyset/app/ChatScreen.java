package com.moneyset.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Extracted rendering/controller logic; financial decisions remain on the server. */
final class ChatScreen {
    static void chatAction(MainActivity host, JSONObject action) {
        UiHelpers.status(host, "Đang xử lý…");
        host.api.call("POST", "/chat/android", action, r -> { UiHelpers.status(host, ""); host.renderChat((JSONObject) r); }, host::error);
    }
    static JSONObject draftAction(MainActivity host, String kind, JSONObject draft) throws Exception {
        return new JSONObject().put("action", kind).put("draft_id", draft.getString("id")).put("version", draft.getInt("version"));
    }
    static void renderChat(MainActivity host, JSONObject state) throws Exception {
        host.chat = state; host.content.removeAllViews();
        Button form=host.button(host.content,"＋  Thêm khoản chi",()->host.transactionForm(null));
        form.setTextSize(17);form.setTextColor(Color.WHITE);form.setBackground(UiHelpers.shape(host,UiHelpers.BRAND,UiHelpers.BRAND,14));
        LinearLayout box=host.column();box.setPadding(host.dp(14),host.dp(12),host.dp(14),host.dp(12));
        box.setBackground(UiHelpers.shape(host,Color.WHITE,Color.rgb(226,232,240),16));
        LinearLayout.LayoutParams boxParams=new LinearLayout.LayoutParams(-1,-2);boxParams.topMargin=host.dp(10);host.content.addView(box,boxParams);
        TextView title=host.text(box,"Chat chi tiêu",18);title.setTypeface(null,Typeface.BOLD);
        JSONObject errors=state.optJSONObject("errors");
        JSONObject d = state.optJSONObject("draft");
        if(d!=null){
            JSONObject fields = d.getJSONObject("fields");
            String description=fields.optString("description",fields.optString("name","Khoản chi"));
            host.text(box,description,16);
            if(fields.has("amount_vnd")&&!fields.isNull("amount_vnd"))UiHelpers.moneyText(host,box,"Số tiền: ",fields.getLong("amount_vnd"),17,true);
            if(fields.has("category_id")&&!fields.isNull("category_id"))host.text(box,"Danh mục: "+host.label(fields.getString("category_id")),14);
            if(fields.has("date")&&!fields.isNull("date"))host.text(box,"Ngày chi: "+fields.getString("date"),14);
            if(!state.optBoolean("can_confirm")||errors==null||errors.length()>0)host.text(box,"Kiểm tra thông tin trước khi xác nhận.",13);
            host.button(box, "Sửa bản nháp", () -> host.draftForm(d));
            Button confirm = host.button(box, "Xác nhận lưu", () -> host.chatAction(host.draftAction("confirm", d)));
            confirm.setEnabled(state.optBoolean("can_confirm")&&errors!=null&&errors.length()==0);
            host.button(box, "Hủy bản nháp", () -> host.chatAction(host.draftAction("cancel", d)));
        } else {
            String reply=lastValidReply(state);
            host.text(box,reply==null?"Nhập một câu như “ăn trưa 45k”.":reply,14);
        }
        LinearLayout compose=host.column();compose.setOrientation(LinearLayout.HORIZONTAL);compose.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button attach=host.button(compose,"Ảnh",()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/png","image/jpeg","image/webp","application/pdf"});
            host.startActivityForResult(intent,101);
        });
        attach.setTextSize(12);attach.setMinWidth(0);attach.setMinimumWidth(0);
        LinearLayout.LayoutParams attachParams=(LinearLayout.LayoutParams)attach.getLayoutParams();attachParams.width=host.dp(58);attachParams.height=host.dp(48);attachParams.rightMargin=host.dp(6);attach.setLayoutParams(attachParams);
        EditText message=new EditText(host);message.setHint("Nhập khoản chi");message.setContentDescription("Nhập khoản chi");
        message.setSingleLine(true);message.setTextSize(15);message.setTextColor(UiHelpers.INK);message.setHintTextColor(Color.rgb(148,163,184));
        message.setPadding(host.dp(12),host.dp(8),host.dp(12),host.dp(8));message.setBackground(UiHelpers.shape(host,Color.WHITE,Color.rgb(203,213,225),11));
        LinearLayout.LayoutParams inputParams=new LinearLayout.LayoutParams(0,host.dp(48),1);compose.addView(message,inputParams);
        Button send=host.button(compose,"Gửi",()->{String value=host.value(message);if(!value.isEmpty())host.chatAction(new JSONObject().put("action","send").put("text",value));});
        send.setMinWidth(0);send.setMinimumWidth(0);LinearLayout.LayoutParams sendParams=(LinearLayout.LayoutParams)send.getLayoutParams();sendParams.width=host.dp(76);sendParams.height=host.dp(48);sendParams.leftMargin=host.dp(8);send.setLayoutParams(sendParams);
        box.addView(compose);
        if(!state.isNull("pending"))host.button(box,"Kiểm tra kết quả đang chờ",()->host.chatAction(new JSONObject().put("action","retry")));
    }

    private static String lastValidReply(JSONObject state) throws Exception {
        JSONObject errors=state.optJSONObject("errors");if(errors!=null&&errors.length()>0)return null;
        JSONArray history=state.optJSONArray("history");if(history==null)return null;
        for(int i=history.length()-1;i>=0;i--){JSONObject item=history.getJSONObject(i);if(!item.optString("role").equals("assistant"))continue;
            String text=item.optString("content").trim();
            if(text.startsWith("Đã lưu ")||text.startsWith("Đã hủy ")||text.startsWith("Đã giữ ")
                ||text.startsWith("Tháng ")||text.startsWith("Hóa đơn từ ")||text.startsWith("Bản nháp:"))return text;
            return null;
        }
        return null;
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
        EditText merchant=bill?null:host.input(box,"Cửa hàng (tùy chọn)",f.isNull("merchant")?"":f.optString("merchant"),false);
        EditText amount=host.input(box,"Số tiền VND",f.has("amount_vnd")?f.optString("amount_vnd"):"",true);
        EditText day=host.input(box,bill?"Ngày lặp 1–31":"Ngày yyyy-MM-dd",f.optString(bill?"due_day":"date"),bill);
        EditText start=bill?host.input(box,"Tháng bắt đầu yyyy-MM",f.optString("start_month"),false):null;
        Spinner cat=bill?null:host.categoryPicker(box,f.optString("category_id"));
        host.formDialog("Sửa bản nháp",box,()->{
            JSONObject changes=new JSONObject().put(bill?"name":"description",host.value(name)).put("amount_vnd",host.amount(amount));
            if(bill) changes.put("due_day",Integer.parseInt(host.value(day))).put("start_month",host.value(start));
            else changes.put("date",host.value(day)).put("category_id",host.selected(cat)).put("merchant",host.value(merchant));
            host.chatAction(host.draftAction("edit",draft).put("fields",changes));
        });
    }
}
