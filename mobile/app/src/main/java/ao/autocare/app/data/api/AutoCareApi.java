package ao.autocare.app.data.api;

import ao.autocare.app.data.api.dto.AuthDtos.AuthResponse;
import ao.autocare.app.data.api.dto.AuthDtos.LoginRequest;
import ao.autocare.app.data.api.dto.AuthDtos.MessageResponse;
import ao.autocare.app.data.api.dto.AuthDtos.RefreshRequest;
import ao.autocare.app.data.api.dto.AuthDtos.RegisterRequest;
import ao.autocare.app.data.api.dto.AuthDtos.TokenPair;
import ao.autocare.app.data.api.dto.AuthDtos.UserDto;
import ao.autocare.app.data.api.dto.ConfigDtos.AppConfig;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AutoCareApi {

    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @POST("auth/refresh")
    Call<TokenPair> refresh(@Body RefreshRequest body);

    @POST("auth/logout")
    Call<MessageResponse> logout(@Body RefreshRequest body);

    @GET("auth/me")
    Call<UserDto> me();

    @GET("config")
    Call<AppConfig> config();
}
