package ao.autocare.modules.fuel;

import ao.autocare.common.ApiException;
import ao.autocare.domain.FuelCardTransaction;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Extratos dos cartões de combustível, cruzados com os abastecimentos registados. */
@Tag(name = "Combustível")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequirePermission(Permission.FUEL_MANAGE)
public class FuelCardController {

    public record TransactionView(String id, String assetId, String assetTag, String cardNumber,
                                  Instant transactedAt, BigDecimal liters, BigDecimal amount, String currency,
                                  String station, String reference, String status, String fuelRecordId) {

        static TransactionView of(FuelCardTransaction t) {
            return new TransactionView(t.getId(),
                    t.getAsset() != null ? t.getAsset().getId() : null,
                    t.getAsset() != null ? t.getAsset().getTag() : null,
                    t.getCardNumber(), t.getTransactedAt(), t.getLiters(), t.getAmount(), t.getCurrency(),
                    t.getStation(), t.getReference(), t.getStatus().name(),
                    t.getFuelRecord() != null ? t.getFuelRecord().getId() : null);
        }
    }

    private final FuelCardService service;
    private final OrgContext orgContext;

    public FuelCardController(FuelCardService service, OrgContext orgContext) {
        this.service = service;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    @Operation(summary = "Modelo do extrato do cartão de combustível")
    @GetMapping(value = "/api/v1/imports/fuel-cards/template", produces = "text/csv")
    public String template() {
        return service.template();
    }

    @Operation(summary = "Importar o extrato do cartão e cruzar com os abastecimentos",
            description = "Abre anomalias: o cartão pagou e ninguém registou; registou-se e o cartão "
                    + "não pagou. Com dryRun=true só conta, não grava.")
    @PostMapping(value = "/api/v1/imports/fuel-cards", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importar(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Nenhum ficheiro foi enviado.");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw ApiException.badRequest("Não foi possível ler o ficheiro.");
        }
        FuelCardService.Resultado r = service.importar(org(p), p.id(), content, dryRun);
        return Map.of("report", r.report(), "matched", r.matched(), "unmatched", r.unmatched(),
                "recordsWithoutCard", r.recordsWithoutCard());
    }

    @Operation(summary = "Transações dos cartões (todas, ou só as sem registo)")
    @GetMapping("/api/v1/fuel-cards/transactions")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TransactionView> list(@AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(required = false) FuelCardTransaction.Status status) {
        return service.list(org(p), status).stream().map(TransactionView::of).toList();
    }

    @Operation(summary = "Dar uma transação sem registo por tratada")
    @PostMapping("/api/v1/fuel-cards/transactions/{id}/ignore")
    public Map<String, String> ignore(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        service.ignore(org(p), p.id(), id);
        return Map.of("message", "Transação dada por tratada.");
    }
}
