package ao.autocare.modules.assettype;

import ao.autocare.common.ApiException;
import ao.autocare.domain.AssetSystem;
import ao.autocare.domain.AssetType;
import ao.autocare.domain.enums.Enums.AssetCategory;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.CreateAssetTypeRequest;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.SystemInput;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.UpdateAssetTypeRequest;
import ao.autocare.modules.assettype.dto.AssetTypeDtos.AssetTypeView;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.OrganizationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetTypeService {

    /** Os 8 sistemas do documento de referência (Plano de Manutenção Preventiva CAT). */
    static final List<String[]> STANDARD_SYSTEMS = List.of(
            new String[] {"ENGINE", "Motor"},
            new String[] {"HYDRAULIC", "Sistema Hidráulico"},
            new String[] {"FUEL", "Sistema de Combustível"},
            new String[] {"TRANSMISSION", "Sistema de Transmissão"},
            new String[] {"AXLES", "Eixos e Diferenciais"},
            new String[] {"ELECTRICAL", "Sistema Elétrico"},
            new String[] {"BRAKES", "Sistema de Travagem"},
            new String[] {"STRUCTURE", "Estrutura e Chassi"});

    private final AssetTypeRepository assetTypes;
    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public AssetTypeService(
            AssetTypeRepository assetTypes,
            AssetRepository assets,
            OrganizationRepository organizations,
            AuditService audit) {
        this.assetTypes = assetTypes;
        this.assets = assets;
        this.organizations = organizations;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AssetTypeView> list(String orgId) {
        return assetTypes.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(t -> AssetTypeView.of(t, assets.countByAssetTypeId(t.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetTypeView get(String orgId, String id) {
        AssetType t = load(orgId, id);
        return AssetTypeView.of(t, assets.countByAssetTypeId(id));
    }

    @Transactional
    public AssetTypeView create(String orgId, String userId, CreateAssetTypeRequest req) {
        String name = req.name().trim();
        if (assetTypes.existsByOrganizationIdAndNameIgnoreCase(orgId, name)) {
            throw ApiException.conflict("Já existe um tipo de ativo com este nome.");
        }

        AssetType t = new AssetType();
        t.setOrganization(organizations.getReferenceById(orgId));
        t.setName(name);
        t.setCategory(req.category() != null ? req.category() : AssetCategory.MACHINE);
        t.setPrimaryMeter(req.primaryMeter() != null ? req.primaryMeter() : MeterKind.HOURMETER);
        t.setSecondaryMeter(req.secondaryMeter());
        t.setIcon(req.icon());

        List<SystemInput> systems = resolveSystems(req);
        int order = 1;
        for (SystemInput s : systems) {
            AssetSystem sys = new AssetSystem();
            sys.setCode(s.code().trim().toUpperCase());
            sys.setName(s.name().trim());
            sys.setSortOrder(s.sortOrder() != null ? s.sortOrder() : order);
            t.addSystem(sys);
            order++;
        }

        assetTypes.save(t);
        audit.record(orgId, userId, "asset_type.create", "AssetType", t.getId(), t.getName());
        return AssetTypeView.of(t, 0);
    }

    @Transactional
    public AssetTypeView update(String orgId, String userId, String id, UpdateAssetTypeRequest req) {
        AssetType t = load(orgId, id);
        verificarVersao(req.version(), t.getVersion());
        if (req.name() != null && !req.name().isBlank()) {
            String name = req.name().trim();
            if (!name.equalsIgnoreCase(t.getName())
                    && assetTypes.existsByOrganizationIdAndNameIgnoreCase(orgId, name)) {
                throw ApiException.conflict("Já existe um tipo de ativo com este nome.");
            }
            t.setName(name);
        }
        if (req.category() != null) t.setCategory(req.category());
        if (req.primaryMeter() != null) t.setPrimaryMeter(req.primaryMeter());
        if (req.secondaryMeter() != null) t.setSecondaryMeter(req.secondaryMeter());
        if (req.icon() != null) t.setIcon(req.icon().isBlank() ? null : req.icon().trim());
        audit.record(orgId, userId, "asset_type.update", "AssetType", t.getId(), t.getName());
        return AssetTypeView.of(t, assets.countByAssetTypeId(id));
    }

    @Transactional
    public void delete(String orgId, String userId, String id) {
        AssetType t = load(orgId, id);
        if (assets.countByAssetTypeId(id) > 0) {
            throw ApiException.conflict(
                    "Existem ativos deste tipo. Altere-os ou arquive-os primeiro.");
        }
        assetTypes.delete(t);
        audit.record(orgId, userId, "asset_type.delete", "AssetType", id, t.getName());
    }

    // ------------------------------------------------------------------
    private List<SystemInput> resolveSystems(CreateAssetTypeRequest req) {
        if (req.systems() != null && !req.systems().isEmpty()) {
            return req.systems();
        }
        boolean useStandard = req.useStandardSystems() == null || req.useStandardSystems();
        if (!useStandard) {
            return List.of();
        }
        return STANDARD_SYSTEMS.stream()
                .map(s -> new SystemInput(s[0], s[1], null))
                .toList();
    }

    private AssetType load(String orgId, String id) {
        return assetTypes.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Tipo de ativo não encontrado."));
    }

    /**
     * A versão que o ecrã leu tem de ser a que está na base de dados.
     *
     * <p>Sem isto, o {@code @Version} só apanha colisões entre transações
     * simultâneas. O caso real — duas pessoas com a mesma ficha aberta durante
     * minutos — só se apanha comparando a versão que o ecrã devolve.
     */
    private static void verificarVersao(Long lida, long atual) {
        if (lida != null && lida != atual) {
            throw ApiException.conflict(
                    ao.autocare.common.GlobalExceptionHandler.MENSAGEM_VERSAO);
        }
    }
}
