package ao.autocare.app.data.api.dto;

import com.google.gson.annotations.SerializedName;

/** DTOs de rede — espelham os corpos JSON da API AutoCare. */
public final class AuthDtos {

    private AuthDtos() {}

    public static class RegisterRequest {
        public String name;
        public String email;
        public String phone;
        public String password;
        public boolean acceptTerms;

        public RegisterRequest(String name, String email, String phone,
                               String password, boolean acceptTerms) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.password = password;
            this.acceptTerms = acceptTerms;
        }
    }

    public static class LoginRequest {
        public String identifier;
        public String password;

        public LoginRequest(String identifier, String password) {
            this.identifier = identifier;
            this.password = password;
        }
    }

    public static class RefreshRequest {
        public String refreshToken;

        public RefreshRequest(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class AuthResponse {
        public UserDto user;
        public String accessToken;
        public String refreshToken;
        public String tokenType;
        public long expiresInSeconds;
    }

    public static class TokenPair {
        public String accessToken;
        public String refreshToken;
        public String tokenType;
        public long expiresInSeconds;
    }

    public static class UserDto {
        public String id;
        public String name;
        public String email;
        public String phone;
        public String avatarUrl;
        public String locale;
        public String currency;
        public String theme;
        public boolean admin;
    }

    public static class MessageResponse {
        public String message;
    }

    public static class ApiErrorBody {
        public int statusCode;
        public String message;
        @SerializedName("path")
        public String path;
    }
}
