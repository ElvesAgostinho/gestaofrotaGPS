package ao.autocare.modules.catalog;

import ao.autocare.domain.Plan;
import ao.autocare.repo.PlanRepository;
import ao.autocare.repo.VehicleBrandRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dados de referência lidos pelo cliente: planos de assinatura e marcas/modelos
 * comuns em Angola (a lista não limita o cadastro).
 */
@Tag(name = "Catálogo")
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final PlanRepository plans;
    private final VehicleBrandRepository brands;

    public CatalogController(PlanRepository plans, VehicleBrandRepository brands) {
        this.plans = plans;
        this.brands = brands;
    }

    @Operation(summary = "Planos de assinatura disponíveis")
    @GetMapping("/plans")
    public List<PlanView> plans() {
        return plans.findByActiveTrueOrderByPriceMonthlyAsc().stream().map(PlanView::from).toList();
    }

    @Operation(summary = "Marcas e modelos de referência")
    @GetMapping("/brands")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> brands() {
        return brands.findAllByOrderByNameAsc().stream()
                .map(b -> Map.<String, Object>of(
                        "id", b.getId(),
                        "name", b.getName(),
                        "models", b.getModels().stream().map(m -> Map.of(
                                "id", m.getId(), "name", m.getName())).toList()))
                .toList();
    }

    public record PlanView(
            String code, String name, int maxVehicles, boolean hasGps,
            java.math.BigDecimal priceMonthly, String currency, List<String> features) {

        static PlanView from(Plan p) {
            return new PlanView(p.getCode().name(), p.getName(), p.getMaxVehicles(),
                    p.isHasGps(), p.getPriceMonthly(), p.getCurrency(), p.getFeatures());
        }
    }
}
