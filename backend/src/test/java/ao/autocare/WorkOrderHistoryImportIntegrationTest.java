package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * O cliente novo traz anos de ordens numa folha. Entram como história —
 * concluídas, com data, contador e custo — e o «custo por viatura» passa a
 * ter passado desde o primeiro dia.
 */
class WorkOrderHistoryImportIntegrationTest extends AbstractIntegrationTest {

    private String bearer;
    private String camiao;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect) throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    private JsonNode upload(String csv, boolean dryRun) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "historico.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        MvcResult r = mvc.perform(multipart("/api/v1/imports/work-orders").file(file)
                        .param("dryRun", String.valueOf(dryRun)).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("historico@teste.ao", "Transportes Histórico").bearer();
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Camião", "primaryMeter", "ODOMETER"), 201)
                .get("id").asText();
        camiao = send(post("/api/v1/assets"), Map.of("tag", "CAM-101", "name", "Camião", "assetTypeId", tipo,
                "plate", "LD-11-22-AA", "initialMeterValue", 150_000), 201).get("id").asText();
    }

    @Test
    void oHistoricoEntraComoOrdensConcluidasEAtualizaOContador() throws Exception {
        String csv = """
                ativo;numero;data;tipo;titulo;detalhe;contador;oficina;mao de obra;pecas;custo total;horas paradas
                CAM-101;OS-2024-031;12/03/2024;corretiva;Substituição de embraiagem;Disco e prato novos;154200;Oficina Central;85000;420000;505000;16
                LD-11-22-AA;;30/06/2024;preventiva;Revisão dos 160 000 km;Óleo e filtros;160100;;45.000,00;120.000,00;;6
                CAM-999;;01/01/2024;corretiva;Não existe;;;;;;;
                CAM-101;;31/12/2099;corretiva;No futuro;;;;;;;
                """;
        // Ensaio seco: diz o que vai acontecer sem gravar.
        JsonNode ensaio = upload(csv, true);
        assertThat(ensaio.get("dryRun").asBoolean()).isTrue();
        assertThat(ensaio.get("created").asInt()).isEqualTo(2);
        assertThat(ensaio.get("errors")).hasSize(2);
        assertThat(ensaio.get("errors").get(0).get("message").asText()).contains("CAM-999");
        assertThat(ensaio.get("errors").get(1).get("message").asText()).contains("futuro");
        assertThat(send(get("/api/v1/work-orders?size=50"), null, 200).get("content")).isEmpty();

        JsonNode real = upload(csv, false);
        assertThat(real.get("created").asInt()).isEqualTo(2);

        JsonNode ordens = send(get("/api/v1/work-orders?status=DONE&size=50"), null, 200).get("content");
        assertThat(ordens).hasSize(2);
        JsonNode embraiagem = null;
        for (JsonNode o : ordens) {
            if (o.get("number").asText().equals("OS-2024-031")) embraiagem = o;
        }
        assertThat(embraiagem).isNotNull();
        JsonNode detalhe = send(get("/api/v1/work-orders/" + embraiagem.get("id").asText()), null, 200);
        assertThat(detalhe.get("status").asText()).isEqualTo("DONE");
        assertThat(detalhe.get("type").asText()).isEqualTo("CORRECTIVE");
        assertThat(detalhe.get("totalCost").asDouble()).isEqualTo(505_000.0);
        assertThat(detalhe.get("openedAt").asText()).startsWith("2024-03-12");

        // A revisão sem número recebeu um do ano da ordem, e o total somou-se das partes.
        JsonNode revisao = ordens.get(0).get("number").asText().equals("OS-2024-031") ? ordens.get(1) : ordens.get(0);
        assertThat(revisao.get("number").asText()).startsWith("OM-2024-");
        assertThat(send(get("/api/v1/work-orders/" + revisao.get("id").asText()), null, 200)
                .get("totalCost").asDouble()).isEqualTo(165_000.0);

        // O contador da ficha anda para o valor mais alto do histórico.
        JsonNode ativo = send(get("/api/v1/assets/" + camiao), null, 200);
        assertThat(ativo.get("meters").get(0).get("currentValue").asDouble()).isEqualTo(160_100.0);

        // O mesmo número não entra duas vezes.
        JsonNode outraVez = upload(csv, false);
        assertThat(outraVez.get("errors").toString()).contains("OS-2024-031");

        // E o custo por viatura já conta com o histórico.
        String csvCustos = mvc.perform(get("/api/v1/reports/maintenance-by-asset.csv").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csvCustos).contains("CAM-101");
    }

    @Test
    void oModeloDescarrega() throws Exception {
        MvcResult r = mvc.perform(get("/api/v1/imports/work-orders/template").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentAsString(StandardCharsets.UTF_8)).startsWith("ativo;numero;data");
    }
}
