package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.modules.plan.MeterReadingWatch;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * O contador que ninguém lê.
 *
 * <p>Num gerador ou numa máquina sem GPS, o plano por horas só avança quando
 * alguém escreve a leitura. Se ninguém escreve, o contador fica parado, a
 * tarefa das 250 h nunca vence — e o sistema fica calado enquanto a máquina
 * trabalha. Estes testes provam que deixou de ficar calado.
 */
class MeterWatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MeterReadingWatch watch;

    @Autowired
    private JdbcTemplate jdbc;

    private String criarMaquina(Session s, String tag) throws Exception {
        MvcResult tipo = mvc.perform(post("/api/v1/asset-types")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Gerador de teste",
                                "primaryMeter", "HOURMETER"))))
                .andExpect(status().isCreated())
                .andReturn();
        String tipoId = json.readTree(tipo.getResponse().getContentAsString()).get("id").asText();

        MvcResult a = mvc.perform(post("/api/v1/assets")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "tag", tag,
                                "name", "Gerador Cummins",
                                "assetTypeId", tipoId,
                                "initialMeterValue", 1200))))
                .andExpect(status().isCreated())
                .andReturn();
        return json.readTree(a.getResponse().getContentAsString()).get("id").asText();
    }

    /** Empurra a última leitura para trás no tempo, como se ninguém lesse há semanas. */
    private void envelhecerLeitura(String assetId, int dias) {
        jdbc.update("UPDATE asset_meters SET last_reading_at = ? WHERE asset_id = ?",
                java.sql.Timestamp.from(Instant.now().minus(dias, ChronoUnit.DAYS)), assetId);
    }

    private JsonNode avisos(Session s) throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/notifications?size=50")
                        .header("Authorization", s.bearer()))
                .andExpect(status().isOk())
                .andReturn();
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Um contador lido há pouco não gera aviso nenhum")
    void recentMeterIsQuiet() throws Exception {
        Session s = register("recente@teste.ao");
        String id = criarMaquina(s, "GER-REC");
        envelhecerLeitura(id, 3);

        watch.notifyStaleMeters();

        JsonNode body = avisos(s);
        assertThat(body.toString()).doesNotContain("Contador por ler");
    }

    @Test
    @DisplayName("Passadas duas semanas sem leitura, avisa — e diz o que isso implica")
    void staleMeterWarns() throws Exception {
        Session s = register("parado@teste.ao");
        String id = criarMaquina(s, "GER-PAR");
        envelhecerLeitura(id, 20);

        watch.notifyStaleMeters();

        String texto = avisos(s).toString();
        assertThat(texto).contains("Contador por ler");
        assertThat(texto).contains("GER-PAR");
        // A mensagem tem de dizer a consequência, não só o facto.
        assertThat(texto).contains("plano por horas não avança");
    }

    @Test
    @DisplayName("Passado muito tempo, o aviso passa a crítico e explica o risco")
    void veryStaleMeterIsCritical() throws Exception {
        Session s = register("abandonado@teste.ao");
        String id = criarMaquina(s, "GER-ABD");
        envelhecerLeitura(id, 90);

        watch.notifyStaleMeters();

        String texto = avisos(s).toString();
        assertThat(texto).contains("CRITICAL");
        assertThat(texto).contains("parado no tempo");
    }

    @Test
    @DisplayName("Com aparelho GPS instalado não avisa: o contador anda sozinho")
    void assetWithGpsIsSkipped() throws Exception {
        Session s = register("comgps@teste.ao");
        String id = criarMaquina(s, "GER-GPS");
        envelhecerLeitura(id, 60);

        mvc.perform(post("/api/v1/gps-devices")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "externalId", "860000000000001",
                                "assetId", id))))
                .andExpect(status().isCreated());

        watch.notifyStaleMeters();

        assertThat(avisos(s).toString()).doesNotContain("GER-GPS");
    }

    @Test
    @DisplayName("Uma leitura nova resolve o aviso em vez de o deixar pendurado")
    void readingResolvesTheWarning() throws Exception {
        Session s = register("resolvido@teste.ao");
        String id = criarMaquina(s, "GER-RES");
        envelhecerLeitura(id, 30);
        watch.notifyStaleMeters();
        assertThat(avisos(s).toString()).contains("GER-RES");

        // O operador lê o contador.
        mvc.perform(post("/api/v1/assets/" + id + "/meters/HOURMETER/readings")
                        .header("Authorization", s.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("value", 1450))))
                .andExpect(status().is2xxSuccessful());

        watch.notifyStaleMeters();

        // O aviso deixa de estar por resolver: é isso que impede a lista de
        // avisos de se encher de coisas já tratadas, até ninguém a ler.
        JsonNode body = avisos(s);
        boolean pendente = false;
        for (JsonNode n : body.has("content") ? body.get("content") : body) {
            if (n.path("title").asText().contains("GER-RES")
                    && !n.path("resolvedAt").isTextual()) {
                pendente = true;
            }
        }
        assertThat(pendente).isFalse();
    }
}
