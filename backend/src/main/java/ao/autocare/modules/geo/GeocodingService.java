package ao.autocare.modules.geo;

import ao.autocare.domain.Location;
import ao.autocare.repo.LocationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escrever o nome de um sítio e obter o ponto no mapa.
 *
 * <p>Duas fontes, por esta ordem:
 * <ol>
 *   <li><b>Os locais da empresa</b> — filiais, obras, armazéns. Sem Internet,
 *       sem limites, e com os nomes que a empresa usa («Obra do Lobito», não
 *       «Zona industrial, Lobito»).</li>
 *   <li><b>O OpenStreetMap</b> (Nominatim), restrito a Angola. Conhece as
 *       cidades, as vilas e boa parte das ruas de Luanda. É um serviço público
 *       com regras: no máximo um pedido por segundo e identificação de quem
 *       chama. Por isso passa pelo servidor, e não pelo navegador de cada
 *       utilizador — o servidor identifica-se, guarda os resultados em cache
 *       e respeita o ritmo.</li>
 * </ol>
 *
 * <p>Sem Internet, a segunda fonte falha e a primeira continua a responder. O
 * ecrã diz que só há locais da empresa; não finge que procurou o mundo.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(12);
    private static final Duration CACHE = Duration.ofHours(24);
    /** O Nominatim público exige no máximo um pedido por segundo. */
    private static final long INTERVALO_MINIMO_MS = 1100;

    public record Resultado(String name, String detail, BigDecimal latitude, BigDecimal longitude,
                            /** EMPRESA ou MAPA. */ String source, String locationId) {}

    public record Resposta(List<Resultado> results, boolean externalAvailable) {}

    private record Cacheado(List<Resultado> resultados, Instant expira) {}

    private final LocationRepository locations;
    private final ObjectMapper json;
    private final String url;
    private final boolean enabled;
    private final Map<String, Cacheado> cache = new ConcurrentHashMap<>();
    private final Object ritmo = new Object();
    private long ultimoPedidoMs = 0;

    public GeocodingService(LocationRepository locations, ObjectMapper json,
            @Value("${autocare.geocoder.url:https://nominatim.openstreetmap.org}") String url,
            @Value("${autocare.geocoder.enabled:true}") boolean enabled) {
        this.locations = locations;
        this.json = json;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.enabled = enabled;
    }

    @Transactional(readOnly = true)
    public Resposta search(String orgId, String termo) {
        String q = termo == null ? "" : termo.trim();
        List<Resultado> out = new ArrayList<>();
        if (q.length() < 2) {
            return new Resposta(out, enabled);
        }

        // 1. A empresa primeiro.
        String chave = normalizar(q);
        for (Location l : locations.findByOrganizationIdOrderByNameAsc(orgId)) {
            if (l.getLatitude() == null || l.getLongitude() == null) {
                continue;
            }
            String alvo = normalizar(l.getName() + " " + nz(l.getCity()) + " " + nz(l.getAddress()));
            if (alvo.contains(chave)) {
                out.add(new Resultado(l.getName(),
                        junta(l.getAddress(), l.getCity(), l.getProvince()),
                        l.getLatitude(), l.getLongitude(), "EMPRESA", l.getId()));
            }
            if (out.size() >= 5) {
                break;
            }
        }

        // 2. O mapa.
        if (!enabled) {
            return new Resposta(out, false);
        }
        List<Resultado> doMapa = cacheOuPedir(chave);
        if (doMapa == null) {
            return new Resposta(out, false);
        }
        out.addAll(doMapa);
        return new Resposta(out, true);
    }

    /** Esvazia a cache. Para testes e para quando o mapa muda. */
    public void limparCache() {
        cache.clear();
    }

    /** Nulo quando o serviço externo não respondeu. */
    private List<Resultado> cacheOuPedir(String chave) {
        Cacheado c = cache.get(chave);
        if (c != null && c.expira().isAfter(Instant.now())) {
            return c.resultados();
        }
        try {
            respeitarRitmo();
            String uri = url + "/search?format=jsonv2&countrycodes=ao&limit=6&accept-language=pt&q="
                    + URLEncoder.encode(chave, StandardCharsets.UTF_8);
            HttpResponse<String> r = HttpClient.newBuilder()
                    .connectTimeout(TEMPO_LIMITE)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(HttpRequest.newBuilder()
                            .uri(URI.create(uri))
                            .timeout(TEMPO_LIMITE)
                            // A identificação que o serviço público exige.
                            .header("User-Agent", "IMBONDEIRO-OS/0.2 (gestao de frotas; contacto: suporte)")
                            .header("Accept", "application/json")
                            .GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                log.warn("Geocodificador respondeu {}", r.statusCode());
                return null;
            }
            List<Resultado> lista = new ArrayList<>();
            for (JsonNode n : json.readTree(r.body())) {
                String nome = n.path("name").asText(null);
                String completo = n.path("display_name").asText("");
                if (nome == null || nome.isBlank()) {
                    nome = completo.split(",")[0].trim();
                }
                lista.add(new Resultado(nome, completo,
                        new BigDecimal(n.path("lat").asText()),
                        new BigDecimal(n.path("lon").asText()),
                        "MAPA", null));
            }
            cache.put(chave, new Cacheado(lista, Instant.now().plus(CACHE)));
            return lista;
        } catch (Exception e) {
            log.warn("Geocodificador indisponível: {}", e.toString());
            return null;
        }
    }

    /** Nunca mais do que um pedido por segundo ao serviço público. */
    private void respeitarRitmo() throws InterruptedException {
        synchronized (ritmo) {
            long agora = System.currentTimeMillis();
            long espera = ultimoPedidoMs + INTERVALO_MINIMO_MS - agora;
            if (espera > 0) {
                Thread.sleep(espera);
            }
            ultimoPedidoMs = System.currentTimeMillis();
        }
    }

    private static String normalizar(String v) {
        return Normalizer.normalize(v, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String junta(String... partes) {
        StringBuilder b = new StringBuilder();
        for (String p : partes) {
            if (p != null && !p.isBlank()) {
                if (b.length() > 0) {
                    b.append(", ");
                }
                b.append(p.trim());
            }
        }
        return b.toString();
    }
}
