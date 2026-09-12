package ao.autocare;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class PartStockIntegrationTest extends AbstractIntegrationTest {

    private String bearer;

    private JsonNode postJson(String url, Object body, int expect) throws Exception {
        MvcResult r = mvc.perform(post(url).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(expect)).andReturn();
        byte[] c = r.getResponse().getContentAsByteArray();
        return c.length == 0 ? json.nullNode() : json.readTree(c);
    }

    @Test
    void createsPartAndWarehouseThenReceivesAndConsumesStock() throws Exception {
        bearer = register("part1@teste.ao").bearer();

        String partId = postJson("/api/v1/parts", Map.of(
                "name", "Filtro de óleo", "partNumber", "1R-0716", "systemCode", "ENGINE",
                "category", "FILTER", "unit", "un", "minQuantity", 4), 201).get("id").asText();
        String whId = postJson("/api/v1/warehouses", Map.of("name", "Armazém de Peças"), 201)
                .get("id").asText();

        // entrada de 10 unidades a 12.000 Kz
        JsonNode in = postJson("/api/v1/stock/movements", Map.of(
                "partId", partId, "warehouseId", whId, "type", "IN",
                "quantity", 10, "unitCost", 12000, "reference", "Fatura 123"), 201);
        org.assertj.core.api.Assertions.assertThat(in.get("balanceAfter").asInt()).isEqualTo(10);

        mvc.perform(get("/api/v1/parts/" + partId).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(10))
                .andExpect(jsonPath("$.averageCost").value(12000.0))
                .andExpect(jsonPath("$.lowStock").value(false))
                .andExpect(jsonPath("$.stock", hasSize(1)));

        // saída de 8 -> fica abaixo do mínimo (4)
        postJson("/api/v1/stock/movements", Map.of(
                "partId", partId, "warehouseId", whId, "type", "OUT_OTHER", "quantity", 8), 201);
        mvc.perform(get("/api/v1/parts/" + partId).header("Authorization", bearer))
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.lowStock").value(true));

        mvc.perform(get("/api/v1/parts/low-stock").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Filtro de óleo"));
    }

    @Test
    void blocksNegativeStock() throws Exception {
        bearer = register("part2@teste.ao").bearer();
        String partId = postJson("/api/v1/parts", Map.of("name", "Correia"), 201).get("id").asText();
        String whId = postJson("/api/v1/warehouses", Map.of("name", "Central"), 201).get("id").asText();

        postJson("/api/v1/stock/movements", Map.of(
                "partId", partId, "warehouseId", whId, "type", "OUT_OTHER", "quantity", 5), 409);
    }

    @Test
    void transfersStockBetweenWarehouses() throws Exception {
        bearer = register("part3@teste.ao").bearer();
        String partId = postJson("/api/v1/parts", Map.of("name", "Vedante"), 201).get("id").asText();
        String a = postJson("/api/v1/warehouses", Map.of("name", "Obra A"), 201).get("id").asText();
        String b = postJson("/api/v1/warehouses", Map.of("name", "Obra B"), 201).get("id").asText();

        postJson("/api/v1/stock/movements", Map.of(
                "partId", partId, "warehouseId", a, "type", "IN", "quantity", 20), 201);
        JsonNode moves = postJson("/api/v1/stock/transfers", Map.of(
                "partId", partId, "fromWarehouseId", a, "toWarehouseId", b, "quantity", 7), 201);
        org.assertj.core.api.Assertions.assertThat(moves).hasSize(2);

        JsonNode part = json.readTree(mvc.perform(get("/api/v1/parts/" + partId).header("Authorization", bearer))
                .andReturn().getResponse().getContentAsByteArray());
        org.assertj.core.api.Assertions.assertThat(part.get("totalQuantity").asInt()).isEqualTo(20);
        // 13 em A, 7 em B
        var byWh = new java.util.HashMap<String, Integer>();
        part.get("stock").forEach(s -> byWh.put(s.get("warehouseName").asText(), s.get("quantity").asInt()));
        org.assertj.core.api.Assertions.assertThat(byWh).containsEntry("Obra A", 13).containsEntry("Obra B", 7);
    }

    @Test
    void rejectsDuplicatePartNumber() throws Exception {
        bearer = register("part4@teste.ao").bearer();
        postJson("/api/v1/parts", Map.of("name", "Filtro A", "partNumber", "F-1"), 201);
        postJson("/api/v1/parts", Map.of("name", "Filtro B", "partNumber", "f-1"), 409);
    }

    @Test
    void isolatedBetweenOrganizations() throws Exception {
        bearer = register("partA@teste.ao", "Empresa A").bearer();
        postJson("/api/v1/parts", Map.of("name", "Peça da A"), 201);
        String other = register("partB@teste.ao", "Empresa B").bearer();
        mvc.perform(get("/api/v1/parts").header("Authorization", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
}
