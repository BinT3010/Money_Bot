package com.moneyset.app;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
/** Durable pending key/upload location shared with previous app versions. */
final class PendingRequestStore {
    private final SharedPreferences prefs;
    private final Context context;
    PendingRequestStore(SharedPreferences prefs, Context context) { this.prefs = prefs; this.context = context; }
    boolean exists() { return prefs.contains("pending"); }
    JSONObject read() throws Exception { return new JSONObject(prefs.getString("pending", "{}")); }
    void save(JSONObject request) { prefs.edit().putString("pending", request.toString()).commit(); }
    void clear() { prefs.edit().remove("pending").commit(); new File(context.getFilesDir(), "pending-receipt.bin").delete(); }
}
