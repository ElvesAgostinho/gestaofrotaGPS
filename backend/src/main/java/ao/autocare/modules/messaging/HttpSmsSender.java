package ao.autocare.modules.messaging;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * SMS por uma gateway HTTP genérica — a forma que quase todos os operadores e
 * revendedores de SMS em África oferecem: um URL com o número e o texto.
 *
 * <p>Configura-se com {@code SMS_GATEWAY_URL} contendo {@code {to}} e
 * {@code {text}} (ex.: {@code https://api.exemplo.com/send?key=X&to={to}&msg={text}}),
 * opcionalmente {@code SMS_GATEWAY_METHOD=POST} (o número e o texto vão então
 * no corpo como {@code to} e {@code text}) e um cabeçalho
 * {@code SMS_GATEWAY_HEADER=Authorization: Bearer xxx}. Aceite = HTTP 2xx.
 */
@Component
public class HttpSmsSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(HttpSmsSender.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(12);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TEMPO_LIMITE).build();
    private final String urlTemplate;
    private final String method;
    private final String header;

    public HttpSmsSender(@Value("${autocare.sms.gateway-url:}") String urlTemplate,
            @Value("${autocare.sms.gateway-method:GET}") String method,
            @Value("${autocare.sms.gateway-header:}") String header) {
        this.urlTemplate = urlTemplate == null ? "" : urlTemplate.trim();
        this.method = method == null || method.isBlank() ? "GET" : method.trim().toUpperCase();
        this.header = header == null ? "" : header.trim();
    }

    @Override
    public String name() {
        return "SMS";
    }

    @Override
    public boolean isConfigured() {
        return !urlTemplate.isBlank() && urlTemplate.contains("{to}") && urlTemplate.contains("{text}");
    }

    @Override
    public boolean send(String phone, String text) {
        if (!isConfigured() || phone == null || phone.isBlank()) {
            return false;
        }
        String para = phone.replaceAll("[^0-9+]", "");
        try {
            HttpRequest.Builder b;
            if ("POST".equals(method)) {
                // O URL fica sem os marcadores; o número e o texto vão no corpo do formulário.
                String url = urlTemplate.replace("{to}", "").replace("{text}", "")
                        .replaceAll("[?&][A-Za-z0-9_]+=(?=&|$)", "");
                String form = "to=" + URLEncoder.encode(para, StandardCharsets.UTF_8)
                        + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
                b = HttpRequest.newBuilder().uri(URI.create(url))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form));
            } else {
                String url = urlTemplate.replace("{to}", URLEncoder.encode(para, StandardCharsets.UTF_8))
                        .replace("{text}", URLEncoder.encode(text, StandardCharsets.UTF_8));
                b = HttpRequest.newBuilder().uri(URI.create(url)).GET();
            }
            if (header.contains(":")) {
                int i = header.indexOf(':');
                b.header(header.substring(0, i).trim(), header.substring(i + 1).trim());
            }
            HttpResponse<String> r = http.send(b.timeout(TEMPO_LIMITE).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) {
                return true;
            }
            log.warn("A gateway de SMS recusou a mensagem para {}: HTTP {}", para, r.statusCode());
            return false;
        } catch (Exception e) {
            log.warn("Falha a enviar SMS para {}: {}", para, e.toString());
            return false;
        }
    }
}
