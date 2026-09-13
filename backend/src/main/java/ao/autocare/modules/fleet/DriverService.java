package ao.autocare.modules.fleet;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Driver;
import ao.autocare.domain.DriverAssignment;
import ao.autocare.domain.Location;
import ao.autocare.domain.enums.Enums.DriverStatus;
import ao.autocare.domain.enums.Enums.LocationKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.fleet.dto.FleetDtos.AssignDriverRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.AssignmentView;
import ao.autocare.modules.fleet.dto.FleetDtos.DriverSummary;
import ao.autocare.modules.fleet.dto.FleetDtos.DriverView;
import ao.autocare.modules.fleet.dto.FleetDtos.EndAssignmentRequest;
import ao.autocare.modules.fleet.dto.FleetDtos.SaveDriverRequest;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.DriverAssignmentRepository;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.LocationRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motoristas e quem conduz o quê.
 *
 * <p>O que distingue este módulo de uma lista de nomes é a <b>atribuição no
 * tempo</b>. É ela que permite responder, meses depois, a quem conduzia o
 * camião no dia em que houve o excesso de velocidade, ou em que o depósito
 * levou trinta litros a mais do que cabia. Sem isso, o resto do sistema pode
 * medir tudo e não imputar nada a ninguém.
 */
@Service
public class DriverService {

    /** Antecedência com que uma carta a caducar passa a contar como problema. */
    private static final int LICENSE_WARNING_DAYS = 30;

    private final DriverRepository drivers;
    private final DriverAssignmentRepository assignments;
    private final AssetRepository assets;
    private final LocationRepository locations;
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public DriverService(
            DriverRepository drivers,
            DriverAssignmentRepository assignments,
            AssetRepository assets,
            LocationRepository locations,
            UserRepository users,
            OrganizationRepository organizations,
            AuditService audit) {
        this.drivers = drivers;
        this.assignments = assignments;
        this.assets = assets;
        this.locations = locations;
        this.users = users;
        this.organizations = organizations;
        this.audit = audit;
    }

    // ==== Consulta =========================================================
    @Transactional(readOnly = true)
    public PagedResponse<DriverView> list(String orgId, String search, Pageable pageable) {
        Page<Driver> page = search != null && !search.isBlank()
                ? drivers.search(orgId, search.trim(), pageable)
                : drivers.findByOrganizationIdOrderByNameAsc(orgId, pageable);
        return PagedResponse.of(page.map(d -> DriverView.of(d, assetsOf(d.getId()))));
    }

    @Transactional(readOnly = true)
    public DriverView get(String orgId, String driverId) {
        Driver d = require(orgId, driverId);
        return DriverView.of(d, assetsOf(d.getId()));
    }

    @Transactional(readOnly = true)
    public DriverSummary summary(String orgId) {
        LocalDate limite = LocalDate.now().plusDays(LICENSE_WARNING_DAYS);
        List<Driver> aCaducar = drivers.licensesExpiringBy(orgId, limite);
        long caducadas = aCaducar.stream().filter(Driver::isLicenseExpired).count();

        long semAtivo = drivers.findByOrganizationIdAndStatusOrderByNameAsc(
                        orgId, DriverStatus.ACTIVE).stream()
                .filter(d -> assignments.openForDriver(d.getId()).isEmpty())
                .count();

        return new DriverSummary(
                drivers.countByOrganizationIdAndStatus(orgId, DriverStatus.ACTIVE),
                drivers.countByOrganizationIdAndStatus(orgId, DriverStatus.SUSPENDED),
                drivers.countByOrganizationIdAndStatus(orgId, DriverStatus.INACTIVE),
                caducadas, aCaducar.size() - caducadas, semAtivo);
    }

    /** Cartas caducadas ou a caducar, para os avisos e para o ecrã. */
    @Transactional(readOnly = true)
    public List<DriverView> licensesNeedingAttention(String orgId) {
        return drivers.licensesExpiringBy(orgId, LocalDate.now().plusDays(LICENSE_WARNING_DAYS))
                .stream()
                .map(d -> DriverView.of(d, List.of()))
                .toList();
    }

    // ==== Escrita ==========================================================
    @Transactional
    public DriverView create(String orgId, String userId, SaveDriverRequest req) {
        if (req.employeeNumber() != null && !req.employeeNumber().isBlank()
                && drivers.existsByOrganizationIdAndEmployeeNumber(
                        orgId, req.employeeNumber().trim())) {
            throw ApiException.conflict(
                    "Já existe um motorista com o número de funcionário "
                            + req.employeeNumber().trim() + ".");
        }
        Driver d = new Driver();
        d.setOrganization(organizations.getReferenceById(orgId));
        apply(orgId, d, req, true);
        drivers.save(d);

        audit.record(orgId, userId, "driver.create", "Driver", d.getId(),
                d.getName() + (d.getEmployeeNumber() != null
                        ? " · nº " + d.getEmployeeNumber() : ""));
        return DriverView.of(d, List.of());
    }

    @Transactional
    public DriverView update(String orgId, String userId, String driverId, SaveDriverRequest req) {
        Driver d = require(orgId, driverId);
        verificarVersao(req.version(), d.getVersion());
        apply(orgId, d, req, false);
        audit.record(orgId, userId, "driver.update", "Driver", d.getId(), d.getName());
        return DriverView.of(d, assetsOf(d.getId()));
    }

    @Transactional
    public void delete(String orgId, String userId, String driverId) {
        Driver d = require(orgId, driverId);
        // Apagar apagaria também o histórico de quem conduzia o quê — e é esse
        // histórico que responde por infrações e consumos já registados.
        // Quem sai da empresa fica INATIVO; o passado tem de continuar legível.
        if (!assignments.findByDriverIdOrderByStartedAtDesc(driverId).isEmpty()) {
            throw ApiException.conflict(
                    "Este motorista já conduziu ativos e o histórico não pode ser apagado. "
                            + "Marque-o como inativo em vez de o eliminar.");
        }
        String nome = d.getName();
        drivers.delete(d);
        audit.record(orgId, userId, "driver.delete", "Driver", driverId, nome);
    }

    private void apply(String orgId, Driver d, SaveDriverRequest req, boolean creating) {
        if (creating || req.name() != null) {
            d.setName(req.name().trim());
        }
        d.setEmployeeNumber(trim(req.employeeNumber()));
        d.setPhone(trim(req.phone()));
        d.setEmail(trim(req.email()));
        d.setNationalId(trim(req.nationalId()));
        d.setBirthDate(req.birthDate());
        d.setHiredAt(req.hiredAt());
        d.setLicenseNumber(trim(req.licenseNumber()));
        d.setLicenseCategories(trim(req.licenseCategories()));
        d.setLicenseIssuedAt(req.licenseIssuedAt());
        d.setLicenseExpiresAt(req.licenseExpiresAt());
        d.setLicenseCountry(trim(req.licenseCountry()));
        d.setCardNumber(trim(req.cardNumber()));
        d.setCardExpiresAt(req.cardExpiresAt());
        d.setMedicalExpiresAt(req.medicalExpiresAt());
        d.setNotes(trim(req.notes()));
        if (req.status() != null) {
            d.setStatus(req.status());
        }
        if (req.userId() != null) {
            d.setUser(req.userId().isBlank() ? null
                    : users.findById(req.userId()).orElseThrow(
                            () -> ApiException.notFound("Utilizador não encontrado.")));
        }
        if (req.branchId() != null) {
            d.setBranch(req.branchId().isBlank() ? null : requireBranch(orgId, req.branchId()));
        }
        if (d.getLicenseIssuedAt() != null && d.getLicenseExpiresAt() != null
                && !d.getLicenseExpiresAt().isAfter(d.getLicenseIssuedAt())) {
            throw ApiException.badRequest(
                    "A validade da carta não pode ser anterior à data de emissão.");
        }
    }

    // ==== Atribuições ======================================================
    /**
     * Põe um motorista ao volante de um ativo.
     *
     * <p>Recusa quem tem a carta caducada. A regra é simples de contornar do
     * lado de fora — uma pessoa pode sempre pegar nas chaves — mas o sistema
     * não pode ser o sítio onde isso fica registado como normal: se acontecer
     * um acidente, a empresa responde por ter deixado.
     */
    @Transactional
    public AssignmentView assign(String orgId, String userId, AssignDriverRequest req) {
        Driver driver = require(orgId, req.driverId());
        Asset asset = assets.findByIdAndOrganizationId(req.assetId(), orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));

        if (driver.getStatus() != DriverStatus.ACTIVE) {
            throw ApiException.conflict("O motorista " + driver.getName() + " está "
                    + DriverView.statusLabel(driver.getStatus()).toLowerCase()
                    + " e não pode ser atribuído a um ativo.");
        }
        if (driver.isLicenseExpired()) {
            throw ApiException.conflict("A carta de condução de " + driver.getName()
                    + " caducou em " + driver.getLicenseExpiresAt()
                    + ". Atualize o documento antes de o pôr a conduzir.");
        }

        Instant inicio = req.startedAt() != null ? req.startedAt() : Instant.now();
        boolean titular = req.primaryDriver() == null || req.primaryDriver();

        // Já conduz este ativo? Repetir a atribuição criaria duas linhas abertas
        // para a mesma pessoa e o mesmo ativo, e o histórico deixava de fechar.
        boolean jaAtribuido = assignments.openForAsset(asset.getId()).stream()
                .anyMatch(a -> a.getDriver().getId().equals(driver.getId()));
        if (jaAtribuido) {
            throw ApiException.conflict(
                    driver.getName() + " já está atribuído a " + asset.getTag() + ".");
        }

        // Um ativo tem um titular de cada vez. O anterior fecha no instante em
        // que o novo começa — sem intervalo por explicar entre os dois.
        if (titular) {
            for (DriverAssignment aberta : assignments.openForAsset(asset.getId())) {
                if (aberta.isPrimaryDriver()) {
                    aberta.setEndedAt(inicio);
                    aberta.setNotes(append(aberta.getNotes(),
                            "Substituído por " + driver.getName() + "."));
                }
            }
        }

        DriverAssignment a = new DriverAssignment();
        a.setOrganization(organizations.getReferenceById(orgId));
        a.setDriver(driver);
        a.setAsset(asset);
        a.setStartedAt(inicio);
        a.setPrimaryDriver(titular);
        a.setNotes(trim(req.notes()));
        a.setCreatedBy(userId);
        assignments.save(a);

        audit.record(orgId, userId, "driver.assign", "Asset", asset.getId(),
                driver.getName() + " → " + asset.getTag()
                        + (titular ? " (titular)" : " (secundário)"));
        return AssignmentView.of(a);
    }

    @Transactional
    public AssignmentView endAssignment(
            String orgId, String userId, String assignmentId, EndAssignmentRequest req) {

        DriverAssignment a = assignments.findByIdAndOrganizationId(assignmentId, orgId)
                .orElseThrow(() -> ApiException.notFound("Atribuição não encontrada."));
        if (!a.isOpen()) {
            throw ApiException.conflict("Esta atribuição já foi encerrada.");
        }
        Instant fim = req != null && req.endedAt() != null ? req.endedAt() : Instant.now();
        if (fim.isBefore(a.getStartedAt())) {
            throw ApiException.badRequest(
                    "A data de fim não pode ser anterior à de início da atribuição.");
        }
        a.setEndedAt(fim);
        if (req != null && req.notes() != null) {
            a.setNotes(append(a.getNotes(), req.notes().trim()));
        }
        audit.record(orgId, userId, "driver.unassign", "Asset", a.getAsset().getId(),
                a.getDriver().getName() + " deixou " + a.getAsset().getTag());
        return AssignmentView.of(a);
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> historyForAsset(String orgId, String assetId) {
        assets.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> ApiException.notFound("Ativo não encontrado."));
        return assignments.findByAssetIdOrderByStartedAtDesc(assetId).stream()
                .map(AssignmentView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> historyForDriver(String orgId, String driverId) {
        require(orgId, driverId);
        return assignments.findByDriverIdOrderByStartedAtDesc(driverId).stream()
                .map(AssignmentView::of).toList();
    }

    /**
     * Quem conduzia este ativo neste instante — a pergunta que todo o resto do
     * sistema faz para imputar uma viagem, uma infração ou um abastecimento.
     *
     * <p>Devolve vazio quando não há atribuição a cobrir o momento, e é assim
     * que deve ser: inventar um responsável a partir de "quem conduz hoje"
     * poria o nome errado num processo disciplinar.
     */
    @Transactional(readOnly = true)
    public Optional<Driver> driverAt(String assetId, Instant moment) {
        if (assetId == null || moment == null) {
            return Optional.empty();
        }
        return assignments.coveringAt(assetId, moment).stream().findFirst()
                .map(DriverAssignment::getDriver);
    }

    // ==== Auxiliares =======================================================
    private List<AssignmentView> assetsOf(String driverId) {
        return assignments.openForDriver(driverId).stream().map(AssignmentView::of).toList();
    }

    private Driver require(String orgId, String id) {
        return drivers.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Motorista não encontrado."));
    }

    private Location requireBranch(String orgId, String branchId) {
        Location l = locations.findByIdAndOrganizationId(branchId, orgId)
                .orElseThrow(() -> ApiException.notFound("Filial não encontrada."));
        if (l.getKind() != LocationKind.BRANCH) {
            throw ApiException.badRequest(
                    "O local \"" + l.getName() + "\" não é uma filial.");
        }
        return l;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String append(String existing, String extra) {
        if (extra == null || extra.isBlank()) {
            return existing;
        }
        return existing == null || existing.isBlank() ? extra : existing + " · " + extra;
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
