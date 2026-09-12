package ao.autocare.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import ao.autocare.app.data.api.dto.AuthDtos.UserDto;

/**
 * Guarda os tokens e o utilizador da sessão de forma cifrada
 * (EncryptedSharedPreferences). Os dados de localização e credenciais são
 * tratados como informação sensível (secção 45/73).
 */
public class SessionManager {

    private static final String FILE = "autocare_session";
    private static final String K_ACCESS = "access_token";
    private static final String K_REFRESH = "refresh_token";
    private static final String K_USER_ID = "user_id";
    private static final String K_USER_NAME = "user_name";
    private static final String K_USER_EMAIL = "user_email";
    private static final String K_USER_PHONE = "user_phone";
    private static final String K_ONBOARDED = "onboarded";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        SharedPreferences p;
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            p = EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            // Recurso de último recurso caso o keystore falhe no dispositivo.
            p = context.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE);
        }
        this.prefs = p;
    }

    public boolean isLoggedIn() {
        return getAccessToken() != null && getRefreshToken() != null;
    }

    public String getAccessToken() {
        return prefs.getString(K_ACCESS, null);
    }

    public String getRefreshToken() {
        return prefs.getString(K_REFRESH, null);
    }

    public void updateTokens(String access, String refresh) {
        prefs.edit().putString(K_ACCESS, access).putString(K_REFRESH, refresh).apply();
    }

    public void saveSession(String access, String refresh, UserDto user) {
        SharedPreferences.Editor e = prefs.edit()
                .putString(K_ACCESS, access)
                .putString(K_REFRESH, refresh);
        if (user != null) {
            e.putString(K_USER_ID, user.id)
                    .putString(K_USER_NAME, user.name)
                    .putString(K_USER_EMAIL, user.email)
                    .putString(K_USER_PHONE, user.phone);
        }
        e.apply();
    }

    public void cacheUser(UserDto user) {
        if (user == null) return;
        prefs.edit()
                .putString(K_USER_ID, user.id)
                .putString(K_USER_NAME, user.name)
                .putString(K_USER_EMAIL, user.email)
                .putString(K_USER_PHONE, user.phone)
                .apply();
    }

    public String getUserName() {
        return prefs.getString(K_USER_NAME, "");
    }

    public String getUserEmail() {
        return prefs.getString(K_USER_EMAIL, null);
    }

    public String getUserPhone() {
        return prefs.getString(K_USER_PHONE, null);
    }

    public boolean hasSeenOnboarding() {
        return prefs.getBoolean(K_ONBOARDED, false);
    }

    public void setSeenOnboarding() {
        prefs.edit().putBoolean(K_ONBOARDED, true).apply();
    }

    public void clear() {
        boolean onboarded = hasSeenOnboarding();
        prefs.edit().clear().putBoolean(K_ONBOARDED, onboarded).apply();
    }
}
