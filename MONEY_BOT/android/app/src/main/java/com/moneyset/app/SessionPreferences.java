package com.moneyset.app;
import android.content.SharedPreferences;
/** Existing session keys retained for installed clients. */
final class SessionPreferences {
    private final SharedPreferences prefs;
    SessionPreferences(SharedPreferences prefs) { this.prefs = prefs; }
    String base() { return prefs.getString("base", "http://10.0.2.2:8080/api"); }
    String token() { return prefs.getString("token", ""); }
    String email() { return prefs.getString("email", ""); }
    void clearLogin() { prefs.edit().remove("token").remove("email").commit(); }
}
