package ao.autocare.app;

import android.app.Application;
import ao.autocare.app.data.SessionManager;

public class AutoCareApp extends Application {

    private SessionManager sessionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        sessionManager = new SessionManager(this);
    }

    public SessionManager session() {
        return sessionManager;
    }

    public static AutoCareApp from(android.content.Context context) {
        return (AutoCareApp) context.getApplicationContext();
    }
}
