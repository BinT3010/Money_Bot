package com.moneyset.app;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Chạy client Java thực vào backend Python trên localhost:18080 (scripts/TEST.ps1). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class IntegrationTest {
    private Context context;
    private ApiClient api;
    private interface Call { void invoke(ApiClient.Success ok, ApiClient.Failure fail); }
    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("moneybot",Context.MODE_PRIVATE).edit().clear().commit();
        api = new ApiClient(context);
        api.base("http://127.0.0.1:18080/api");
    }
    private Object await(Call call) throws Exception {
        AtomicReference<Object> result=new AtomicReference<>();
        AtomicReference<String> error=new AtomicReference<>();
        call.invoke(result::set,error::set);
        long deadline=System.currentTimeMillis()+30000;
        while(result.get()==null&&error.get()==null&&System.currentTimeMillis()<deadline){
            Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);
        }
        if(error.get()!=null)throw new Exception(error.get());
        assertNotNull("HTTP request timed out",result.get());return result.get();
    }
    private JSONObject request(String method,String path,JSONObject body) throws Exception {
        return (JSONObject)await((ok,fail)->api.call(method,path,body,ok,fail));
    }
    private void register() throws Exception {
        JSONObject body=new JSONObject().put("email","test-"+UUID.randomUUID()+"@moneybot.test").put("password","Password123");
        api.login(request("POST","/auth/register",body));
    }
    @Test public void javaClientToPythonChatConfirmStatsAndRestart() throws Exception {
        register();
        JSONObject state=request("POST","/chat/android",new JSONObject().put("action","send").put("text","ăn trưa 45k"));
        assertTrue(state.getBoolean("can_confirm"));
        JSONObject draft=state.getJSONObject("draft");
        JSONObject confirm=new JSONObject().put("action","confirm").put("draft_id",draft.getString("id")).put("version",draft.getInt("version"));
        assertTrue(request("POST","/chat/android",confirm).isNull("draft"));
        request("POST","/chat/android",confirm);
        String month=state.getJSONObject("overview").getString("month");
        assertEquals(45000,request("GET","/summary?month="+month,null).getLong("total"));
        api=new ApiClient(context);
        assertEquals(1,request("GET","/summary?month="+month,null).getInt("count"));
        assertFalse(api.pending());
    }
    @Test public void persistedRetryKeepsSameKeyAndCannotChangeAccount() throws Exception {
        MockWebServer server=new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"message\":\"response lost\"}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"ok\":true}"));
        server.start();
        try {
            api.base(server.url("/api").toString());
            api.login(new JSONObject().put("email","duy@test.vn").put("token","dummy"));
            try { request("POST","/test",new JSONObject().put("amount",45000));fail("Expected network failure"); } catch(Exception expected) { assertTrue(api.pending()); }
            api=new ApiClient(context);
            try { api.login(new JSONObject().put("email","other@test.vn").put("token","other"));fail("Must reject cross-account replay"); } catch(Exception expected) { assertTrue(api.pending()); }
            JSONObject result=(JSONObject)await((ok,fail)->api.retry(ok,fail));
            assertTrue(result.getBoolean("ok"));assertFalse(api.pending());
            okhttp3.mockwebserver.RecordedRequest first=server.takeRequest(),second=server.takeRequest();
            assertEquals(first.getHeader("Idempotency-Key"),second.getHeader("Idempotency-Key"));
            assertEquals(first.getBody().readUtf8(),second.getBody().readUtf8());
        }finally{server.shutdown();}
    }
    @Test public void loginScreenAndAuthenticatedChatRender() throws Exception {
        MainActivity login=Robolectric.buildActivity(MainActivity.class).setup().get();
        assertTrue(contains(login.getWindow().getDecorView(),"Tạo tài khoản"));
        login.finish();register();
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        long deadline=System.currentTimeMillis()+15000;
        while(!contains(activity.getWindow().getDecorView(),"Trợ lý chi tiêu")&&System.currentTimeMillis()<deadline){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(20);}
        assertTrue(contains(activity.getWindow().getDecorView(),"Trợ lý chi tiêu"));
        assertTrue(contains(activity.getWindow().getDecorView(),"Chọn ảnh / PDF hóa đơn"));
        activity.finish();
    }
    private boolean contains(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),text))return true;}
        return false;
    }

    private View find(View view,String text,boolean description){
        if(description && text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(!description && view instanceof TextView && text.equals(((TextView)view).getText().toString()))return view;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View r=find(g.getChildAt(i),text,description);if(r!=null)return r;}}
        return null;
    }
    private void idleActivity(MainActivity activity) throws Exception {
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("api");field.setAccessible(true);
        ApiClient client=(ApiClient)field.get(activity);
        long end=System.currentTimeMillis()+30000;
        do{Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(30);}while(client.isBusy()&&System.currentTimeMillis()<end);
        assertFalse("Activity request timed out",client.isBusy());
    }
    @Test public void actualScreenChatConfirmationBudgetAndBills() throws Exception {
        register();MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();idleActivity(a);
        View root=a.getWindow().getDecorView();
        ((android.widget.EditText)find(root,"Nhập câu tiếng Việt",true)).setText("ăn trưa 45k");
        find(root,"Gửi",false).performClick();idleActivity(a);
        assertTrue(find(root,"Xác nhận lưu",false).isEnabled());
        find(root,"Xác nhận lưu",false).performClick();idleActivity(a);
        assertTrue(contains(root,"Đã lưu khoản chi"));
        find(root,"Thống kê",false).performClick();idleActivity(a);
        assertTrue(contains(root,"Đã chi:"));
        assertTrue(contains(root,"1 giao dịch"));
        find(root,"Ngân sách",false).performClick();idleActivity(a);
        ((android.widget.EditText)find(root,"Ngân sách tổng VND",true)).setText("100000");
        ((android.widget.EditText)find(root,"Ăn uống",true)).setText("50000");
        find(root,"Xác nhận lưu ngân sách",false).performClick();idleActivity(a);
        assertEquals("100000",((TextView)find(root,"Ngân sách tổng VND",true)).getText().toString());
        find(root,"Hóa đơn",false).performClick();idleActivity(a);
        assertTrue(contains(root,"Các kỳ hóa đơn"));
        find(root,"Tạo lịch hóa đơn",false).performClick();
        AlertDialogCheck.assertShown();
        android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        View form=dialog.getWindow().getDecorView();
        ((android.widget.EditText)find(form,"Tên hóa đơn",true)).setText("Internet test");
        ((android.widget.EditText)find(form,"Số tiền VND",true)).setText("50000");
        ((android.widget.EditText)find(form,"Ngày lặp 1–31",true)).setText("12");
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();idleActivity(a);
        assertTrue(contains(root,"Internet test"));
        find(root,"Đánh dấu đã trả / Liên kết khoản chi",false).performClick();idleActivity(a);
        dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();idleActivity(a);
        assertTrue(contains(root,"Đã trả"));
        a.finish();
    }
    @Test public void javaMultipartImageReachesRealOcr() throws Exception {
        register();
        // Test JVM làm việc ở android/app; mẫu dùng cùng bản đóng gói source.
        java.io.File file=new java.io.File("../../examples/hoa_don_mau.png");
        if(!file.exists())file=new java.io.File("../examples/hoa_don_mau.png");
        byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());
        JSONObject result=(JSONObject)await((ok,fail)->api.upload(bytes,ok,fail));
        assertEquals(45000,result.getJSONObject("draft").getJSONObject("fields").getLong("amount_vnd"));
        assertEquals(0,result.getJSONObject("overview").getInt("count"));
    }
    private static final class AlertDialogCheck {
        static void assertShown(){assertNotNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog());}
    }
    @Test public void activityRecreationPreservesAccountTabAndSelectedMonth() throws Exception {
        api.login(new JSONObject().put("email", "restore@test.vn").put("token", "dummy"));
        android.os.Bundle saved = new android.os.Bundle();
        saved.putString("tab", "Tài khoản"); saved.putString("month", "2026-09");
        org.robolectric.android.controller.ActivityController<MainActivity> first =
            Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible();
        android.os.Bundle next = new android.os.Bundle();
        first.saveInstanceState(next).pause().stop().destroy();
        org.robolectric.android.controller.ActivityController<MainActivity> second =
            Robolectric.buildActivity(MainActivity.class).create(next).start().resume().visible();
        assertEquals("Tài khoản", second.get().tab);
        assertEquals("2026-09", second.get().month);
        assertEquals("restore@test.vn", second.get().api.email());
        second.pause().stop().destroy();
    }
    @Test public void pendingUploadSurvivesStoreRecreation() throws Exception {
        android.content.SharedPreferences prefs = context.getSharedPreferences("moneybot", Context.MODE_PRIVATE);
        PendingRequestStore first = new PendingRequestStore(prefs, context);
        java.io.File upload = new java.io.File(context.getFilesDir(), "pending-receipt.bin");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(upload)) { out.write(new byte[]{1,2,3}); }
        first.save(new JSONObject().put("key", "stable-key").put("owner", "owner@test.vn").put("upload", true));
        PendingRequestStore restored = new PendingRequestStore(prefs, context);
        assertTrue(restored.exists());
        assertEquals("stable-key", restored.read().getString("key"));
        assertEquals(3L, upload.length());
        restored.clear();
        assertFalse(restored.exists()); assertFalse(upload.exists());
    }
}
