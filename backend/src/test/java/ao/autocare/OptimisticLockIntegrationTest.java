package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Duas pessoas com a mesma ficha aberta.
 *
 * <p>É o cenário que os sistemas «simples» ignoram: o segundo a gravar
 * escrevia por cima do primeiro e recebia «guardado». Aqui o segundo recebe
 * um 409 que diz o que fazer, e o trabalho do primeiro fica.
 */
class OptimisticLockIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        req = req.header("Authorization", bearer);
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    @BeforeEach
    void setUp() throws Exception {
        bearer = register("versao@teste.ao").bearer();
    }

    @Test
    void aFichaTrazAVersaoEElaSobeACadaGravacao() throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        JsonNode criado = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-1", "name", "Camião", "assetTypeId", typeId), 201);
        String id = criado.get("id").asText();

        JsonNode lido = send(get("/api/v1/assets/" + id), null, 200);
        long v0 = lido.get("version").asLong();

        JsonNode depois = send(patch("/api/v1/assets/" + id), Map.of("name", "Camião Volvo"), 200);
        assertThat(depois.get("version").asLong()).isGreaterThan(v0);
    }

    @Test
    void oSegundoAGravarPerdeEFicaASaber() throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        String id = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-2", "name", "Camião", "assetTypeId", typeId), 201)
                .get("id").asText();

        // Os dois gestores abrem a ficha ao mesmo tempo: leem a mesma versão.
        long versaoDaAna = send(get("/api/v1/assets/" + id), null, 200).get("version").asLong();
        long versaoDoRui = versaoDaAna;

        // A Ana grava primeiro.
        Map<String, Object> deAna = new HashMap<>();
        deAna.put("name", "Camião Volvo FH");
        deAna.put("version", versaoDaAna);
        send(patch("/api/v1/assets/" + id), deAna, 200);

        // O Rui grava a seguir, com a ficha que leu antes da Ana gravar.
        Map<String, Object> deRui = new HashMap<>();
        deRui.put("name", "Camião Scania");
        deRui.put("version", versaoDoRui);
        JsonNode erro = send(patch("/api/v1/assets/" + id), deRui, 409);

        // A mensagem diz o que fazer, não «conflito».
        assertThat(erro.get("message").asText()).contains("alterado por outra pessoa");
        assertThat(erro.get("message").asText()).contains("Recarregue");

        // E o trabalho da Ana ficou.
        assertThat(send(get("/api/v1/assets/" + id), null, 200).get("name").asText())
                .isEqualTo("Camião Volvo FH");
    }

    @Test
    void quemRecarregaConsegueGravar() throws Exception {
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        String id = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-3", "name", "Camião", "assetTypeId", typeId), 201)
                .get("id").asText();

        long antiga = send(get("/api/v1/assets/" + id), null, 200).get("version").asLong();
        send(patch("/api/v1/assets/" + id), Map.of("name", "Primeira alteração"), 200);

        Map<String, Object> velho = new HashMap<>();
        velho.put("name", "Tentativa com a ficha velha");
        velho.put("version", antiga);
        send(patch("/api/v1/assets/" + id), velho, 409);

        // Recarrega, e agora passa.
        long atual = send(get("/api/v1/assets/" + id), null, 200).get("version").asLong();
        Map<String, Object> novo = new HashMap<>();
        novo.put("name", "Depois de recarregar");
        novo.put("version", atual);
        send(patch("/api/v1/assets/" + id), novo, 200);
    }

    @Test
    void semVersaoNoPedidoContinuaAGravar() throws Exception {
        // Um cliente antigo (ou um script) que não manda a versão não parte:
        // a verificação só corre quando há versão para verificar.
        String typeId = send(post("/api/v1/asset-types"), Map.of("name", "Camião"), 201)
                .get("id").asText();
        String id = send(post("/api/v1/assets"),
                Map.of("tag", "CAM-4", "name", "Camião", "assetTypeId", typeId), 201)
                .get("id").asText();
        send(patch("/api/v1/assets/" + id), Map.of("name", "A"), 200);
        send(patch("/api/v1/assets/" + id), Map.of("name", "B"), 200);
    }

    @Test
    void aMesmaRegraValeParaLocaisRotasMotoristasETipos() throws Exception {
        // Tipo de ativo
        String tipo = send(post("/api/v1/asset-types"), Map.of("name", "Gerador"), 201)
                .get("id").asText();
        long vTipo = send(get("/api/v1/asset-types/" + tipo), null, 200).get("version").asLong();
        send(patch("/api/v1/asset-types/" + tipo), Map.of("name", "Gerador diesel"), 200);
        Map<String, Object> velho = new HashMap<>();
        velho.put("name", "Gerador a gás");
        velho.put("version", vTipo);
        send(patch("/api/v1/asset-types/" + tipo), velho, 409);

        // Local
        String local = send(post("/api/v1/locations"), Map.of("name", "Filial", "kind", "BRANCH"), 201)
                .get("id").asText();
        long vLocal = send(get("/api/v1/locations/" + local), null, 200).get("version").asLong();
        send(patch("/api/v1/locations/" + local), Map.of("name", "Filial de Luanda"), 200);
        velho = new HashMap<>();
        velho.put("name", "Filial de Benguela");
        velho.put("version", vLocal);
        send(patch("/api/v1/locations/" + local), velho, 409);

        // Rota
        Map<String, Object> rota = new HashMap<>();
        rota.put("name", "Luanda - Lobito");
        rota.put("originLabel", "Luanda");
        rota.put("destinationLabel", "Lobito");
        String rotaId = send(post("/api/v1/routes"), rota, 201).get("id").asText();
        long vRota = send(get("/api/v1/routes/" + rotaId), null, 200).get("version").asLong();
        rota.put("name", "Luanda - Lobito (norte)");
        send(put("/api/v1/routes/" + rotaId), rota, 200);
        rota.put("name", "Luanda - Lobito (sul)");
        rota.put("version", vRota);
        send(put("/api/v1/routes/" + rotaId), rota, 409);

        // Motorista
        Map<String, Object> motorista = new HashMap<>();
        motorista.put("name", "José");
        String motoristaId = send(post("/api/v1/drivers"), motorista, 201).get("id").asText();
        long vMot = send(get("/api/v1/drivers/" + motoristaId), null, 200).get("version").asLong();
        motorista.put("name", "José Manuel");
        send(put("/api/v1/drivers/" + motoristaId), motorista, 200);
        motorista.put("name", "José Maria");
        motorista.put("version", vMot);
        send(put("/api/v1/drivers/" + motoristaId), motorista, 409);
    }
}
