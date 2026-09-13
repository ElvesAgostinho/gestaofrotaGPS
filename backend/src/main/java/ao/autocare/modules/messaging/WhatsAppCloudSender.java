package ao.autocare.modules.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WhatsApp pela API oficial da Meta (WhatsApp Business Cloud API).
 *
 * <p>A Meta só deixa uma empresa iniciar conversa com uma <b>mensagem de
 * modelo</b> aprovada; texto livre só nas 24 h depois de o utilizador
 * escrever. Por isso o normal é configurar um modelo com um parâmetro
 * ({@code WHATSAPP_TEMPLATE}, com «{{1}}» = o texto do aviso). Sem modelo
 * envia-se texto livre — funciona só dentro da janela das 24 h, e diz-se isso
 * no ecrã em vez de fingir.
 */
@Component
public class WhatsAppCloudSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudSender.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(12);

    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TEMPO_LIMITE).build();
    private final String apiUrl;
    private final String token;
    private final String phoneId;
    private final String template;
    private final String templateLang;

    public WhatsAppCloudSender(ObjectMapper json,
            @Value("${autocare.whatsapp.api-url:https://graph.facebook.com/v20.0}") String apiUrl,
            @Value("${autocare.whatsapp.token:}") String token,
            @Value("${autocare.whatsapp.phone-id:}") String phoneId,
            @Value("${autocare.whatsapp.template:}") String template,
            @Value("${autocare.whatsapp.template-lang:pt_PT}") String templateLang) {
        this.json = json;
        this.apiUrl = apiUrl == null ? "" : apiUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token.trim();
        this.phoneId = phoneId == null ? "" : phoneId.trim();
        this.template = template == null ? "" : template.trim();
        this.templateLang = templateLang == null || templateLang.isBlank() ? "pt_PT" : templateLang.trim();
    }

    @Override
    public String name() {
        return "WhatsApp";
    }

    @Override
    public boolean isConfigured() {
        return !token.isBlank() && !phoneId.isBlank();
    }

    public boolean usesTemplate() {
        return !template.isBlank();
    }

    @Override
    public boolean send(String phone, String text) {
        if (!isConfigured() || phone == null || phone.isBlank()) {
            return false;
        }
        String para = phone.replaceAll("[^0-9]", "");
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("messaging_product", "whatsapp");
        corpo.put("to", para);
        if (usesTemplate()) {
            corpo.put("type", "template");
            corpo.put("template", Map.of(
                    "name", template,
                    "language", Map.of("code", templateLang),
                    "components", List.of(Map.of(
                            "type", "body",
                            "parameters", List.of(Map.of("type", "text", "text", text))))));
        } else {
            corpo.put("type", "text");
            corpo.put("text", Map.of("preview_url", false, "body", text));
        }
        try {
            HttpRequest pedido = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/" + phoneId + "/messages"))
                    .timeout(TEMPO_LIMITE)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpo), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> r = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) {
                return true;
            }
            String corpoResposta = r.body() == null ? "" : r.body();
            log.warn("WhatsApp recusou a mensagem para {}: HTTP {} {}", para, r.statusCode(),
                    corpoResposta.substring(0, Math.min(200, corpoResposta.length())));
            return false;
        } catch (Exception e) {
            log.warn("Falha a enviar WhatsApp para {}: {}", para, e.toString());
            return false;
        }
    }
}
