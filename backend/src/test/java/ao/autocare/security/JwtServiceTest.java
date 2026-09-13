package ao.autocare.security;

import static org.assertj.core.api.Assertions.assertThat;

import ao.autocare.config.AutoCareProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwt;

    @BeforeEach
    void setUp() {
        var props = new AutoCareProperties(
                new AutoCareProperties.App("AutoCare", "tagline", "http://localhost:5173"),
                new AutoCareProperties.Security(new AutoCareProperties.Security.Jwt(
                        "segredo-de-acesso-com-mais-de-32-bytes-1234567890",
                        "segredo-de-refresh-com-mais-de-32-bytes-1234567890",
                        Duration.ofMinutes(15),
                        Duration.ofDays(30))),
                new AutoCareProperties.Cors("*"),
                new AutoCareProperties.Seed(false),
                new AutoCareProperties.Registration(true),
                new AutoCareProperties.Admin(null, null, null));
        jwt = new JwtService(props);
    }

    @Test
    void accessTokenRoundTrip() {
        String token = jwt.generateAccessToken("user-1");
        assertThat(jwt.parseAccessSubject(token)).isEqualTo("user-1");
    }

    @Test
    void refreshTokenRoundTrip() {
        String token = jwt.generateRefreshToken("user-2");
        assertThat(jwt.parseRefreshSubject(token)).isEqualTo("user-2");
    }

    @Test
    void accessTokenIsNotAcceptedAsRefresh() {
        String access = jwt.generateAccessToken("user-3");
        assertThat(jwt.parseRefreshSubject(access)).isNull();
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccess() {
        String refresh = jwt.generateRefreshToken("user-4");
        assertThat(jwt.parseAccessSubject(refresh)).isNull();
    }

    @Test
    void garbageTokenReturnsNull() {
        assertThat(jwt.parseAccessSubject("nao-e-um-token")).isNull();
    }

    @Test
    void tokenSignedWithWrongSecretIsRejected() {
        var otherProps = new AutoCareProperties(
                new AutoCareProperties.App("x", "y", "http://localhost:5173"),
                new AutoCareProperties.Security(new AutoCareProperties.Security.Jwt(
                        "OUTRO-segredo-de-acesso-com-mais-de-32-bytes-000",
                        "OUTRO-segredo-de-refresh-com-mais-de-32-bytes-00",
                        Duration.ofMinutes(15), Duration.ofDays(30))),
                new AutoCareProperties.Cors("*"),
                new AutoCareProperties.Seed(false),
                new AutoCareProperties.Registration(true),
                new AutoCareProperties.Admin(null, null, null));
        String foreign = new JwtService(otherProps).generateAccessToken("intruso");
        assertThat(jwt.parseAccessSubject(foreign)).isNull();
    }
}
