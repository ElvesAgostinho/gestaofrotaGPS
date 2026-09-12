package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ao.autocare.domain.Asset;
import ao.autocare.domain.AssetCriticality;
import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.AssetSystem;
import ao.autocare.domain.AssetType;
import ao.autocare.domain.Location;
import ao.autocare.domain.MeterReading;
import ao.autocare.domain.Organization;
import ao.autocare.domain.enums.Enums.AssetCategory;
import ao.autocare.domain.enums.Enums.CriticalityLevel;
import ao.autocare.domain.enums.Enums.MeterKind;
import ao.autocare.repo.AssetCriticalityRepository;
import ao.autocare.repo.AssetMeterRepository;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.AssetTypeRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.MeterReadingRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faz o papel de {@code ddl-auto=validate} para as entidades do núcleo CMMS (V3)
 * e confirma que o registo passa a criar uma organização + membro.
 */
class CmmsCoreSmokeTest extends AbstractIntegrationTest {

    @Autowired OrganizationRepository organizations;
    @Autowired MembershipRepository memberships;
    @Autowired UserRepository users;
    @Autowired LocationRepository locations;
    @Autowired AssetTypeRepository assetTypes;
    @Autowired AssetRepository assets;
    @Autowired AssetMeterRepository assetMeters;
    @Autowired MeterReadingRepository meterReadings;
    @Autowired AssetCriticalityRepository criticalities;
    @Autowired ao.autocare.repo.ChecklistTemplateRepository checklistTemplates;
    @Autowired ao.autocare.repo.MaintenancePlanRepository maintenancePlans;
    @Autowired ao.autocare.repo.AssetPlanRepository assetPlans;
    @Autowired ao.autocare.repo.AssetPlanTaskRepository assetPlanTasks;
    @Autowired ao.autocare.config.CmmsDemoSeeder demoSeeder;

    @Test
    void registerCreatesCompanyOrganizationAndOwnerMembership() throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Ana Gestora",
                                "email", "ana@empresa.ao",
                                "password", "palavraForte1",
                                "acceptTerms", true,
                                "organizationName", "Transportes Benguela, Lda."))))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = json.readTree(res.getResponse().getContentAsString());
        String userId = body.get("user").get("id").asText();

        var membership = memberships.findFirstByUserIdOrderByCreatedAtAsc(userId).orElseThrow();
        assertThat(membership.getRole().name()).isEqualTo("OWNER");

        // .getId() num proxy lazy não força inicialização — sem sessão aberta no teste
        var org = organizations.findById(membership.getOrganization().getId()).orElseThrow();
        assertThat(org.getName()).isEqualTo("Transportes Benguela, Lda.");
        assertThat(org.getType().name()).isEqualTo("COMPANY");
    }

    @Test
    void registerWithoutCompanyNameUsesUserName() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", "Carlos Silva",
                                "email", "carlos@teste.ao",
                                "password", "palavraForte1",
                                "acceptTerms", true))))
                .andExpect(status().isCreated());

        var user = users.findByEmailIgnoreCase("carlos@teste.ao").orElseThrow();
        var membership = memberships.findFirstByUserIdOrderByCreatedAtAsc(user.getId()).orElseThrow();
        var org = organizations.findById(membership.getOrganization().getId()).orElseThrow();
        assertThat(org.getName()).isEqualTo("Carlos Silva");
    }

    @Test
    @Transactional
    void cmmsCoreEntitiesPersistAndReload() {
        Organization org = new Organization();
        org.setName("Oficina Central");
        organizations.save(org);

        Location yard = new Location();
        yard.setOrganization(org);
        yard.setName("Parque de Máquinas");
        locations.save(yard);

        AssetType type = new AssetType();
        type.setOrganization(org);
        type.setName("Retroescavadora");
        type.setCategory(AssetCategory.MACHINE);
        type.setPrimaryMeter(MeterKind.HOURMETER);
        AssetSystem engine = new AssetSystem();
        engine.setCode("ENGINE");
        engine.setName("Motor");
        engine.setSortOrder(1);
        type.addSystem(engine);
        assetTypes.save(type);

        Asset asset = new Asset();
        asset.setOrganization(org);
        asset.setAssetType(type);
        asset.setLocation(yard);
        asset.setTag("RE-001");
        asset.setName("Retroescavadora");
        asset.setManufacturer("Volvo");
        asset.setModel("BL71B");
        asset.setSerialNumber("VCE0BL71C00012345");
        asset.setModelYear(2023);
        asset.setResponsibleLabel("Departamento de Manutenção");
        assets.save(asset);

        AssetMeter meter = new AssetMeter();
        meter.setAsset(asset);
        meter.setKind(MeterKind.HOURMETER);
        meter.setUnit("h");
        meter.setCurrentValue(new BigDecimal("1250.00"));
        meter.setLastReadingAt(Instant.now());
        assetMeters.save(meter);

        MeterReading reading = new MeterReading();
        reading.setMeter(meter);
        reading.setValue(new BigDecimal("1250.00"));
        reading.setReadingAt(Instant.now());
        meterReadings.save(reading);

        AssetCriticality crit = new AssetCriticality();
        crit.setAsset(asset);
        crit.setProductionImpact(5);
        crit.setSafetyImpact(5);
        crit.setFinancialImpact(5);
        crit.setOverall(AssetCriticality.computeOverall(5, 5, 5));
        criticalities.save(crit);

        Asset reloaded = assets.findByIdAndOrganizationId(asset.getId(), org.getId()).orElseThrow();
        assertThat(reloaded.getModel()).isEqualTo("BL71B");
        assertThat(reloaded.getAssetType().getSystems()).hasSize(1);
        assertThat(assetMeters.findByAssetIdAndKind(asset.getId(), MeterKind.HOURMETER))
                .get().extracting(AssetMeter::getCurrentValue)
                .isEqualTo(new BigDecimal("1250.00"));
        assertThat(criticalities.findByAssetId(asset.getId()))
                .get().extracting(AssetCriticality::getOverall)
                .isEqualTo(CriticalityLevel.CRITICAL);
    }

    @Test
    @Transactional
    void demoSeederCreatesBackhoeLoaderFromReferenceDocument() {
        Organization org = new Organization();
        org.setName("Construções Kwanza, Lda.");
        organizations.save(org);
        var user = users.save(newDemoUser());

        demoSeeder.seed(org, user);

        Asset re001 = assets.findByOrganizationId(org.getId()).stream()
                .filter(a -> a.getTag().equals("RE-001"))
                .findFirst().orElseThrow();
        assertThat(re001.getName()).isEqualTo("Retroescavadora");
        assertThat(re001.getModel()).isEqualTo("BL71B");
        assertThat(re001.getSerialNumber()).isEqualTo("VCE0BL71C00012345");
        assertThat(re001.getModelYear()).isEqualTo(2023);
        assertThat(re001.getResponsibleLabel()).isEqualTo("Departamento de Manutenção");

        assertThat(criticalities.findByAssetId(re001.getId()))
                .get().extracting(c -> c.getOverall().name()).isEqualTo("CRITICAL");
        assertThat(assetMeters.findByAssetIdAndKind(re001.getId(), MeterKind.HOURMETER)).isPresent();
        // frota de demonstração: retroescavadora + gerador + camião
        assertThat(assets.findByOrganizationId(org.getId())).hasSize(3);

        // Checklist de inspeção diária (11 itens) e plano de fabricante (lubrificação 50h + 8 sistemas × 4)
        var checklist = checklistTemplates.findByOrganizationIdOrderByNameAsc(org.getId()).get(0);
        assertThat(checklist.getName()).contains("Inspeção diária");
        assertThat(checklist.getItems()).hasSize(11);
        assertThat(checklist.getEstimatedMinutes()).isEqualTo(15);

        var plan = maintenancePlans.findByOrganizationIdOrderByNameAsc(org.getId()).get(0);
        assertThat(plan.getTasks()).hasSize(1 + 8 * 4); // lubrificação + 32 tarefas por horas

        var assigned = assetPlans.findByAssetId(re001.getId());
        assertThat(assigned).hasSize(1);
        assertThat(assetPlanTasks.findByAssetPlanId(assigned.get(0).getId())).hasSize(33);
    }

    private ao.autocare.domain.User newDemoUser() {
        var u = new ao.autocare.domain.User();
        u.setName("João Manuel");
        u.setEmail("seed-demo@teste.ao");
        u.setPasswordHash("x");
        return u;
    }

    @Test
    void criticalityFormulaMatchesReferenceDocument() {
        // 5/5/5 estrelas -> CRÍTICA (como no documento Volvo)
        assertThat(AssetCriticality.computeOverall(5, 5, 5)).isEqualTo(CriticalityLevel.CRITICAL);
        assertThat(AssetCriticality.computeOverall(4, 2, 1)).isEqualTo(CriticalityLevel.HIGH);
        assertThat(AssetCriticality.computeOverall(3, 1, 2)).isEqualTo(CriticalityLevel.MEDIUM);
        assertThat(AssetCriticality.computeOverall(1, 1, 1)).isEqualTo(CriticalityLevel.LOW);
        assertThat(AssetCriticality.computeOverall(2, 1, 2)).isEqualTo(CriticalityLevel.LOW);
    }
}
