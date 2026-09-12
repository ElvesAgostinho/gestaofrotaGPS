package ao.autocare.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import ao.autocare.app.AutoCareApp;
import ao.autocare.app.data.AuthRepository;
import ao.autocare.app.data.SessionManager;
import ao.autocare.app.data.api.dto.AuthDtos.UserDto;
import ao.autocare.app.data.ApiException;
import ao.autocare.app.ui.auth.AuthActivity;
import ao.autocare.app.ui.main.MainActivity;

/** Decide o primeiro ecrã: sessão válida -> app; senão -> autenticação. */
@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SessionManager session = AutoCareApp.from(this).session();
        if (!session.isLoggedIn()) {
            go(AuthActivity.class);
            return;
        }

        // Valida a sessão silenciosamente; se falhar, o cliente limpa os tokens.
        new AuthRepository(this).loadProfile(new AuthRepository.Callback<UserDto>() {
            @Override
            public void onSuccess(UserDto value) {
                go(MainActivity.class);
            }

            @Override
            public void onError(ApiException error) {
                if (session.isLoggedIn()) {
                    // erro de rede — deixa entrar com dados em cache
                    go(MainActivity.class);
                } else {
                    go(AuthActivity.class);
                }
            }
        });
    }

    private void go(Class<?> target) {
        startActivity(new Intent(this, target));
        finish();
    }
}
