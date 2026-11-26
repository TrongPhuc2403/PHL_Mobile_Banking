package vn.edu.tdtu.phlbanking.data.local;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_FILE = "phl_secure_session";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_ROLE = "user_role";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_CODE = "identity_code";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context
            .getApplicationContext()
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE);
    }

    public void saveSession(
        String accessToken,
        String refreshToken,
        String role,
        String username,
        String fullName,
        String identityCode
    ) {
        prefs
            .edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_ROLE, role)
            .putString(KEY_USERNAME, username)
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_CODE, identityCode)
            .apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "");
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getFullName() {
        return prefs.getString(KEY_FULL_NAME, "");
    }

    public String getIdentityCode() {
        return prefs.getString(KEY_CODE, "");
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.trim().isEmpty();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }
}
