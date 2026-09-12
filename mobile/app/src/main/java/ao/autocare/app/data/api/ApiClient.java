package ao.autocare.app.data.api;

import ao.autocare.app.BuildConfig;
import ao.autocare.app.data.SessionManager;
import ao.autocare.app.data.api.dto.AuthDtos.RefreshRequest;
import ao.autocare.app.data.api.dto.AuthDtos.TokenPair;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Cliente HTTP único da aplicação. Injeta o token de acesso e renova-o
 * automaticamente (uma vez) quando a API responde 401.
 */
public final class ApiClient {

    private static volatile AutoCareApi api;

    private ApiClient() {}

    public static AutoCareApi get(SessionManager session) {
        if (api == null) {
            synchronized (ApiClient.class) {
                if (api == null) {
                    api = build(session);
                }
            }
        }
        return api;
    }

    private static AutoCareApi build(SessionManager session) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String token = session.getAccessToken();
                    if (token == null || original.header("Authorization") != null) {
                        return chain.proceed(original);
                    }
                    return chain.proceed(original.newBuilder()
                            .header("Authorization", "Bearer " + token)
                            .build());
                })
                .authenticator((route, response) -> {
                    if (responseCount(response) >= 2) {
                        return null; // já tentámos renovar
                    }
                    synchronized (ApiClient.class) {
                        String refresh = session.getRefreshToken();
                        if (refresh == null) return null;
                        TokenPair pair = refreshBlocking(refresh);
                        if (pair == null) {
                            session.clear();
                            return null;
                        }
                        session.updateTokens(pair.accessToken, pair.refreshToken);
                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + pair.accessToken)
                                .build();
                    }
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(AutoCareApi.class);
    }

    private static int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) {
            count++;
        }
        return count;
    }

    private static TokenPair refreshBlocking(String refreshToken) {
        try {
            Retrofit bare = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            retrofit2.Response<TokenPair> res = bare.create(AutoCareApi.class)
                    .refresh(new RefreshRequest(refreshToken))
                    .execute();
            return res.isSuccessful() ? res.body() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
