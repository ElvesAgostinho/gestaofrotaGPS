package ao.autocare.app.data;

import android.content.Context;
import ao.autocare.app.AutoCareApp;
import ao.autocare.app.data.api.ApiClient;
import ao.autocare.app.data.api.AutoCareApi;
import ao.autocare.app.data.api.dto.AuthDtos.ApiErrorBody;
import ao.autocare.app.data.api.dto.AuthDtos.AuthResponse;
import ao.autocare.app.data.api.dto.AuthDtos.LoginRequest;
import ao.autocare.app.data.api.dto.AuthDtos.RefreshRequest;
import ao.autocare.app.data.api.dto.AuthDtos.RegisterRequest;
import ao.autocare.app.data.api.dto.AuthDtos.UserDto;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Response;

/**
 * Camada de dados da autenticação. Executa as chamadas fora da thread principal
 * e devolve o resultado através de um callback simples.
 */
public class AuthRepository {

    public interface Callback<T> {
        void onSuccess(T value);

        void onError(ApiException error);
    }

    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final Gson GSON = new Gson();

    private final AutoCareApi api;
    private final SessionManager session;
    private final android.os.Handler main =
            new android.os.Handler(android.os.Looper.getMainLooper());

    public AuthRepository(Context context) {
        this.session = AutoCareApp.from(context).session();
        this.api = ApiClient.get(session);
    }

    public SessionManager session() {
        return session;
    }

    public void register(RegisterRequest body, Callback<UserDto> cb) {
        run(() -> {
            Response<AuthResponse> res = api.register(body).execute();
            if (res.isSuccessful() && res.body() != null) {
                AuthResponse a = res.body();
                session.saveSession(a.accessToken, a.refreshToken, a.user);
                return a.user;
            }
            throw toException(res);
        }, cb);
    }

    public void login(String identifier, String password, Callback<UserDto> cb) {
        run(() -> {
            Response<AuthResponse> res = api.login(new LoginRequest(identifier, password)).execute();
            if (res.isSuccessful() && res.body() != null) {
                AuthResponse a = res.body();
                session.saveSession(a.accessToken, a.refreshToken, a.user);
                return a.user;
            }
            throw toException(res);
        }, cb);
    }

    public void loadProfile(Callback<UserDto> cb) {
        run(() -> {
            Response<UserDto> res = api.me().execute();
            if (res.isSuccessful() && res.body() != null) {
                session.cacheUser(res.body());
                return res.body();
            }
            throw toException(res);
        }, cb);
    }

    public void logout(Runnable done) {
        run(() -> {
            String refresh = session.getRefreshToken();
            if (refresh != null) {
                try {
                    api.logout(new RefreshRequest(refresh)).execute();
                } catch (Exception ignored) {
                    // limpamos localmente na mesma
                }
            }
            session.clear();
            return null;
        }, new Callback<Object>() {
            @Override
            public void onSuccess(Object value) {
                done.run();
            }

            @Override
            public void onError(ApiException error) {
                done.run();
            }
        });
    }

    // ------------------------------------------------------------------
    private interface Work<T> {
        T run() throws Exception;
    }

    private <T> void run(Work<T> work, Callback<T> cb) {
        IO.execute(() -> {
            try {
                T value = work.run();
                main.post(() -> cb.onSuccess(value));
            } catch (ApiException e) {
                main.post(() -> cb.onError(e));
            } catch (IOException e) {
                main.post(() -> cb.onError(new ApiException(
                        "Sem ligação ao servidor. Verifique a internet.", 0)));
            } catch (Exception e) {
                main.post(() -> cb.onError(new ApiException(
                        "Não foi possível concluir a operação. Tente novamente.", -1)));
            }
        });
    }

    private ApiException toException(Response<?> res) {
        String message = "Não foi possível concluir a operação. Tente novamente.";
        try {
            if (res.errorBody() != null) {
                ApiErrorBody body = GSON.fromJson(res.errorBody().charStream(), ApiErrorBody.class);
                if (body != null && body.message != null && !body.message.isBlank()) {
                    message = body.message;
                }
            }
        } catch (Exception ignored) {
            // usa a mensagem por omissão
        }
        return new ApiException(message, res.code());
    }
}
