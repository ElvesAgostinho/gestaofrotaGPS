package ao.autocare.modules.checklist;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.ChecklistTemplate;
import ao.autocare.domain.enums.Enums.AssetCategory;
import ao.autocare.modules.checklist.dto.ChecklistDtos.ItemView;
import ao.autocare.modules.checklist.dto.ChecklistDtos.SaveTemplateRequest;
import ao.autocare.modules.checklist.dto.ChecklistDtos.TemplateView;
import ao.autocare.modules.plan.PlanCatalog;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.ChecklistTemplateRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A inspeção diária de uma máquina: o que se verifica antes do arranque.
 *
 * <p>É a folha que fica pendurada na cabina e que o operador percorre em
 * quinze minutos. Todas as frotas sérias a têm em papel; quase nenhum sistema
 * a tem a sério — e é aí que as avarias caras começam, porque ninguém repara
 * que o nível desceu até o motor gripar.
 *
 * <p>Este serviço responde a duas perguntas: <b>esta máquina já tem uma?</b>
 * (o modelo da empresa, com os itens que ela escolheu) e, se não tem,
 * <b>qual é a que devia ter?</b> (a do catálogo, por família) — pronta a criar
 * num clique. O que se mostra é sempre dito: modelo da empresa ou sugestão.
 */
@Service
public class DailyInspectionService {

    /** O que se mostra e de onde vem. */
    public record Ficha(
            /** OWN = modelo desta empresa; SUGGESTED = do catálogo, ainda por criar. */
            String source,
            String templateId,
            String name,
            String description,
            Integer estimatedMinutes,
            List<ItemView> items) {}

    private final AssetRepository assets;
    private final ChecklistTemplateRepository templates;
    private final ChecklistTemplateService templateService;

    public DailyInspectionService(AssetRepository assets, ChecklistTemplateRepository templates,
            ChecklistTemplateService templateService) {
        this.assets = assets;
        this.templates = templates;
        this.templateService = templateService;
    }

    /** Código do catálogo para a família do ativo. */
    public static String codigoDe(Asset a) {
        AssetCategory c = a.getAssetType() != null && a.getAssetType().getCategory() != null
                ? a.getAssetType().getCategory() : AssetCategory.MACHINE;
        return switch (c) {
            case VEHICLE -> "TRUCK_HEAVY";
            case GENERATOR -> "GENERATOR";
            default -> "RETROESCAVADORA";
        };
    }

    @Transactional(readOnly = true)
    public Ficha forAsset(String orgId, String assetId) {
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
        String assetTypeId = a.getAssetType() != null ? a.getAssetType().getId() : null;

        // O modelo da própria empresa para este tipo de equipamento ganha sempre:
        // foi ela que decidiu o que se verifica nas máquinas dela.
        List<ChecklistTemplate> diarias = templates.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .filter(t -> t.isActive() && !t.getItems().isEmpty())
                .filter(t -> pareceDiaria(t.getName()))
                .toList();
        ChecklistTemplate proprio = diarias.stream()
                .filter(t -> t.getAssetType() != null && assetTypeId != null
                        && assetTypeId.equals(t.getAssetType().getId()))
                .findFirst()
                // Uma inspeção diária sem tipo de equipamento vale para toda a
                // frota: foi feita para servir todas as máquinas da casa.
                .or(() -> diarias.stream().filter(t -> t.getAssetType() == null).findFirst())
                .orElse(null);
        if (proprio != null) {
            return new Ficha("OWN", proprio.getId(), proprio.getName(), proprio.getDescription(),
                    proprio.getEstimatedMinutes(),
                    proprio.getItems().stream().map(ItemView::of).toList());
        }

        SaveTemplateRequest sugerido = PlanCatalog.dailyChecklist(codigoDe(a), assetTypeId);
        if (sugerido == null) {
            return null;
        }
        List<ItemView> itens = new java.util.ArrayList<>();
        int i = 0;
        for (var item : sugerido.items()) {
            itens.add(new ItemView("sugestao-" + i, item.text(),
                    item.verification() != null ? item.verification().name() : "VERIFY",
                    Boolean.TRUE.equals(item.critical()), i));
            i++;
        }
        return new Ficha("SUGGESTED", null, sugerido.name(), sugerido.description(),
                sugerido.estimatedMinutes(), itens);
    }

    /** Cria na empresa o modelo sugerido para este ativo. */
    @Transactional
    public TemplateView adopt(String orgId, String userId, String assetId) {
        Asset a = assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
        Ficha atual = forAsset(orgId, assetId);
        if (atual != null && "OWN".equals(atual.source())) {
            throw ApiException.conflict("Esta família já tem uma inspeção diária: «" + atual.name() + "».");
        }
        SaveTemplateRequest req = PlanCatalog.dailyChecklist(codigoDe(a),
                a.getAssetType() != null ? a.getAssetType().getId() : null);
        if (req == null) {
            throw ApiException.badRequest(
                    "Ainda não há inspeção diária de catálogo para esta família. "
                            + "Crie o modelo em Planos → Inspeções.");
        }
        return templateService.create(orgId, userId, req);
    }

    private static boolean pareceDiaria(String nome) {
        if (nome == null) {
            return false;
        }
        String n = java.text.Normalizer.normalize(nome.toLowerCase(), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return n.contains("diaria") || n.contains("arranque") || n.contains("pre-uso") || n.contains("pre uso");
    }
}
