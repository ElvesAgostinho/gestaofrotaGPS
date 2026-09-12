package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Importação em massa de ativos e peças a partir de folhas de cálculo. */
class ImportIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode send(MockHttpServletRequestBuilder req, Object body, int expect)
            throws Exception {
        if (bearer != null) {
            req = req.header("Authorization", bearer);
        }
        if (body != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        MvcResult r = mvc.perform(req).andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    /** Envia um CSV para o endpoint indicado e devolve o relatório. */
    private JsonNode upload(String path, String csv, boolean dryRun, int expect) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dados.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        MvcResult r = mvc.perform(multipart(path)
                        .file(file)
                        .param("dryRun", String.valueOf(dryRun))
                        .header("Authorization", bearer))
                .andExpect(status().is(expect)).andReturn();
        return json.readTree(r.getResponse().getContentAsByteArray());
    }

    private JsonNode assets() throws Exception {
        return send(get("/api/v1/assets?size=100"), null, 200).get("content");
    }

    // ---- ativos ---------------------------------------------------------
    @Test
    void importsAFleetCreatingTypesAndLocationsAlongTheWay() throws Exception {
        bearer = register("im1@teste.ao").bearer();

        String csv = """
                tag;nome;tipo;local;fabricante;modelo;ano;horimetro
                RE-001;Retroescavadora 1;Retroescavadora;Obra Luanda Sul;Volvo;BL71B;2023;1250
                RE-002;Retroescavadora 2;Retroescavadora;Obra Luanda Sul;Volvo;BL71B;2022;3100
                GER-001;Gerador 150 kVA;Gerador;Parque de Máquinas;Volvo;C15;2021;3400
                """;

        JsonNode rel = upload("/api/v1/imports/assets", csv, false, 200);
        assertThat(rel.get("created").asInt()).isEqualTo(3);
        assertThat(rel.get("updated").asInt()).isZero();
        assertThat(rel.get("skipped").asInt()).isZero();
        assertThat(rel.get("errors")).isEmpty();

        // Os tipos e locais que não existiam foram criados e são reportados.
        JsonNode criados = rel.get("createdReferences");
        assertThat(criados).hasSize(4);
        assertThat(criados.toString()).contains("Tipo de ativo: Retroescavadora")
                .contains("Local: Obra Luanda Sul");

        assertThat(assets()).hasSize(3);
        // Os tipos repetidos não geraram duplicados.
        assertThat(send(get("/api/v1/asset-types"), null, 200)).hasSize(2);
        assertThat(send(get("/api/v1/locations"), null, 200)).hasSize(2);
    }

    @Test
    void aDryRunReportsWhatWouldHappenWithoutWritingAnything() throws Exception {
        bearer = register("im2@teste.ao").bearer();

        String csv = """
                tag;nome;tipo
                RE-001;Retro;Retroescavadora
                ;Sem etiqueta;Retroescavadora
                RE-003;Outra;Retroescavadora
                """;

        JsonNode ensaio = upload("/api/v1/imports/assets", csv, true, 200);
        assertThat(ensaio.get("dryRun").asBoolean()).isTrue();
        assertThat(ensaio.get("totalRows").asInt()).isEqualTo(3);
        assertThat(ensaio.get("created").asInt()).isEqualTo(2);
        assertThat(ensaio.get("skipped").asInt()).isEqualTo(1);

        // Nada foi gravado: nem ativos, nem o tipo que teria sido criado.
        assertThat(assets()).isEmpty();
        assertThat(send(get("/api/v1/asset-types"), null, 200)).isEmpty();
    }

    @Test
    void oneBadLineDoesNotStopTheGoodOnes() throws Exception {
        bearer = register("im3@teste.ao").bearer();

        String csv = """
                tag;nome;tipo;ano
                RE-001;Retro 1;Retroescavadora;2023
                RE-002;;Retroescavadora;2022
                RE-003;Retro 3;Retroescavadora;dois mil e vinte
                RE-004;Retro 4;Retroescavadora;2020
                """;

        JsonNode rel = upload("/api/v1/imports/assets", csv, false, 200);
        assertThat(rel.get("created").asInt()).isEqualTo(2);
        assertThat(rel.get("skipped").asInt()).isEqualTo(2);

        JsonNode erros = rel.get("errors");
        assertThat(erros).hasSize(2);
        // O número da linha é o do ficheiro, contando o cabeçalho.
        assertThat(erros.get(0).get("line").asInt()).isEqualTo(3);
        assertThat(erros.get(0).get("value").asText()).isEqualTo("RE-002");
        assertThat(erros.get(0).get("message").asText()).contains("nome");
        assertThat(erros.get(1).get("line").asInt()).isEqualTo(4);
        assertThat(erros.get(1).get("message").asText()).contains("numérico");

        assertThat(assets()).hasSize(2);
    }

    @Test
    void reimportingUpdatesInsteadOfDuplicating() throws Exception {
        bearer = register("im4@teste.ao").bearer();

        upload("/api/v1/imports/assets", """
                tag;nome;tipo;modelo
                RE-001;Retroescavadora;Retroescavadora;BL71B
                """, false, 200);

        // Segunda folha: corrige o nome e acrescenta o fabricante, sem modelo.
        JsonNode rel = upload("/api/v1/imports/assets", """
                tag;nome;tipo;fabricante
                RE-001;Retroescavadora BL71B;Retroescavadora;Volvo
                """, false, 200);

        assertThat(rel.get("created").asInt()).isZero();
        assertThat(rel.get("updated").asInt()).isEqualTo(1);
        assertThat(assets()).hasSize(1);

        String id = assets().get(0).get("id").asText();
        JsonNode ativo = send(get("/api/v1/assets/" + id), null, 200);
        assertThat(ativo.get("name").asText()).isEqualTo("Retroescavadora BL71B");
        assertThat(ativo.get("manufacturer").asText()).isEqualTo("Volvo");
        // Coluna ausente é falta de informação, não ordem para apagar.
        assertThat(ativo.get("model").asText()).isEqualTo("BL71B");
    }

    @Test
    void acceptsCommaFilesAndBothDecimalConventions() throws Exception {
        bearer = register("im5@teste.ao").bearer();

        JsonNode rel = upload("/api/v1/imports/assets", """
                tag,nome,tipo,horimetro
                RE-001,Retro 1,Retroescavadora,"1.250,50"
                RE-002,Retro 2,Retroescavadora,3100.75
                """, false, 200);

        assertThat(rel.get("created").asInt()).isEqualTo(2);
        assertThat(rel.get("errors")).isEmpty();
        assertThat(assets()).hasSize(2);
    }

    @Test
    void guessesTheCategoryFromTheTypeName() throws Exception {
        bearer = register("im6@teste.ao").bearer();

        upload("/api/v1/imports/assets", """
                tag;nome;tipo
                GER-001;Gerador 150 kVA;Gerador
                CAM-001;Camião basculante;Camião
                RE-001;Retroescavadora;Retroescavadora
                """, false, 200);

        Map<String, String> categorias = new HashMap<>();
        for (JsonNode tipo : send(get("/api/v1/asset-types"), null, 200)) {
            categorias.put(tipo.get("name").asText(), tipo.get("category").asText());
        }
        assertThat(categorias).containsEntry("Gerador", "GENERATOR");
        assertThat(categorias).containsEntry("Camião", "VEHICLE");
        assertThat(categorias).containsEntry("Retroescavadora", "MACHINE");
    }

    // ---- peças ----------------------------------------------------------
    @Test
    void importsPartsAndUpdatesOnReimport() throws Exception {
        bearer = register("im7@teste.ao").bearer();

        JsonNode rel = upload("/api/v1/imports/parts", """
                nome;numero de peca;sistema;unidade;stock minimo;custo medio;moeda
                Filtro de óleo do motor;1R-0716;ENGINE;un;10;8500;AOA
                Óleo hidráulico 20L;HYD-20L;HYDRAULIC;un;5;42000;AOA
                """, false, 200);

        assertThat(rel.get("created").asInt()).isEqualTo(2);
        assertThat(send(get("/api/v1/parts"), null, 200)).hasSize(2);

        JsonNode segunda = upload("/api/v1/imports/parts", """
                nome;stock minimo
                Filtro de óleo do motor;25
                """, false, 200);
        assertThat(segunda.get("updated").asInt()).isEqualTo(1);
        assertThat(send(get("/api/v1/parts"), null, 200)).hasSize(2);
    }

    @Test
    void reportsUnknownPartCategoriesPerLine() throws Exception {
        bearer = register("im8@teste.ao").bearer();

        JsonNode rel = upload("/api/v1/imports/parts", """
                nome;categoria
                Filtro de ar;FILTER
                Peça estranha;CATEGORIA_INVENTADA
                """, false, 200);

        assertThat(rel.get("created").asInt()).isEqualTo(1);
        assertThat(rel.get("errors")).hasSize(1);
        assertThat(rel.get("errors").get(0).get("message").asText())
                .contains("Categoria de peça desconhecida");
    }

    // ---- modelos e permissões -------------------------------------------
    @Test
    void offersTemplatesThatTheImporterItselfAccepts() throws Exception {
        bearer = register("im9@teste.ao").bearer();

        MvcResult r = mvc.perform(get("/api/v1/imports/assets/template")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        String modelo = r.getResponse().getContentAsString();
        assertThat(modelo).contains("tag;nome;tipo");

        // O modelo tem de passar pelo próprio importador sem erros.
        JsonNode rel = upload("/api/v1/imports/assets", modelo, false, 200);
        assertThat(rel.get("errors")).isEmpty();
        assertThat(rel.get("created").asInt()).isEqualTo(2);

        MvcResult p = mvc.perform(get("/api/v1/imports/parts/template")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn();
        JsonNode relPecas = upload("/api/v1/imports/parts",
                p.getResponse().getContentAsString(), false, 200);
        assertThat(relPecas.get("errors")).isEmpty();
    }

    @Test
    void refusesEmptyFilesAndNonManagers() throws Exception {
        bearer = register("im10@teste.ao").bearer();

        MockMultipartFile vazio = new MockMultipartFile(
                "file", "vazio.csv", "text/csv", new byte[0]);
        mvc.perform(multipart("/api/v1/imports/assets").file(vazio)
                        .header("Authorization", bearer))
                .andExpect(status().isBadRequest());

        // Um técnico não importa frotas.
        Map<String, Object> convite = new HashMap<>();
        convite.put("email", "tecimp@teste.ao");
        convite.put("role", "TECHNICIAN");
        String token = send(post("/api/v1/team/invitations"), convite, 201).get("token").asText();
        MvcResult r = mvc.perform(post("/api/v1/invitations/" + token + "/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("name", "Ana", "password", "palavraForte1"))))
                .andExpect(status().isOk()).andReturn();
        bearer = "Bearer " + json.readTree(r.getResponse().getContentAsByteArray())
                .get("accessToken").asText();

        upload("/api/v1/imports/assets", "tag;nome;tipo\nX-1;X;Máquina\n", false, 403);
    }
}
