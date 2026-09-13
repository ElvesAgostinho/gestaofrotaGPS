package ao.autocare.modules.platform;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Membership;
import ao.autocare.domain.Organization;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.auth.AuthService;
import ao.autocare.modules.platform.PlatformDtos.CreateOrganizationRequest;
import ao.autocare.modules.platform.PlatformDtos.CreatedOrganization;
import ao.autocare.modules.platform.PlatformDtos.OrganizationRow;
import ao.autocare.modules.platform.PlatformDtos.OrganizationStatus;
import ao.autocare.modules.platform.PlatformDtos.OwnerPasswordReset;
import ao.autocare.modules.platform.PlatformDtos.Summary;
import ao.autocare.modules.platform.PlatformDtos.UpdateOrganizationRequest;
import ao.autocare.repo.AssetRepository;
import ao.autocare.repo.AuditLogRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que o dono do sistema faz às empresas clientes: cria-as, dá-lhes prazo,
 * suspende-as e repõe a palavra-passe do Dono quando ele a perde.
 *
 * <p>Nada aqui apaga dados. Uma empresa suspensa continua inteira; só os
 * utilizadores dela ficam travados (ver {@code LicenseInterceptor}).
 */
@Service
public class PlatformService {

    /** Sem 0/O/1/l/I: a palavra-passe temporária vai ser ditada ao telefone. */
    private static final String ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int DIAS_AVISO = 30;

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final AssetRepository assets;
    private final WorkOrderRepository workOrders;
    private final AuditLogRepository auditLogs;
    private final AuthService auth;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public PlatformService(
            OrganizationRepository organizations,
            MembershipRepository memberships,
            UserRepository users,
            AssetRepository assets,
            WorkOrderRepository workOrders,
            AuditLogRepository auditLogs,
            AuthService auth,
            AuditService audit,
            PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.users = users;
        this.assets = assets;
        this.workOrders = workOrders;
        this.auditLogs = auditLogs;
        this.auth = auth;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<OrganizationRow> list() {
        LocalDate hoje = LocalDate.now();
        return organizations.findAll().stream()
                .sorted(Comparator.comparing(Organization::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(o -> toRow(o, hoje))
                .toList();
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        LocalDate hoje = LocalDate.now();
        List<Organization> todas = organizations.findAll();
        long ativas = 0;
        long aVencer = 0;
        long vencidas = 0;
        long suspensas = 0;
        for (Organization o : todas) {
            switch (statusOf(o, hoje)) {
                case ACTIVE -> ativas++;
                case EXPIRING -> aVencer++;
                case EXPIRED -> vencidas++;
                case SUSPENDED -> suspensas++;
            }
        }
        return new Summary(todas.size(), ativas, aVencer, vencidas, suspensas,
                users.count(), assets.count());
    }

    @Transactional(readOnly = true)
    public OrganizationRow get(String id) {
        return toRow(require(id), LocalDate.now());
    }

    @Transactional
    public CreatedOrganization create(CreateOrganizationRequest req, String adminId) {
        String email = req.ownerEmail().trim().toLowerCase();
        boolean existia = users.existsByEmailIgnoreCase(email);
        String temporaria = null;
        User dono;
        if (existia) {
            dono = users.findByEmailIgnoreCase(email).orElseThrow();
        } else {
            String password;
            if (req.ownerPassword() != null && !req.ownerPassword().isBlank()) {
                password = req.ownerPassword();
            } else {
                temporaria = gerarPalavraPasse();
                password = temporaria;
            }
            dono = auth.createInvitedUser(req.ownerName(), email, password);
        }

        Organization org = auth.createOrganization(dono, req.name());
        org.setTaxId(blankToNull(req.taxId()));
        org.setCity(blankToNull(req.city()));
        org.setLicenseUntil(req.licenseUntil());
        org.setPlatformNotes(blankToNull(req.platformNotes()));
        organizations.save(org);

        audit.record(org.getId(), adminId, "platform.organization.create", "Organization",
                org.getId(), "Empresa criada pela plataforma: " + org.getName()
                        + " · dono: " + email + (existia ? " (conta já existia)" : ""));

        return new CreatedOrganization(toRow(org, LocalDate.now()), email, temporaria, existia);
    }

    @Transactional
    public OrganizationRow update(String id, UpdateOrganizationRequest req, String adminId) {
        Organization org = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            org.setName(req.name().trim());
        }
        if (Boolean.TRUE.equals(req.clearLicense())) {
            org.setLicenseUntil(null);
        } else if (req.licenseUntil() != null) {
            org.setLicenseUntil(req.licenseUntil());
        }
        if (req.platformNotes() != null) {
            org.setPlatformNotes(blankToNull(req.platformNotes()));
        }
        audit.record(org.getId(), adminId, "platform.organization.update", "Organization",
                org.getId(), "Licença até: " + (org.getLicenseUntil() != null
                        ? org.getLicenseUntil() : "sem prazo"));
        return toRow(org, LocalDate.now());
    }

    @Transactional
    public OrganizationRow suspend(String id, String reason, String adminId) {
        Organization org = require(id);
        if (!org.isSuspended()) {
            org.setSuspendedAt(Instant.now());
            org.setSuspendedReason(blankToNull(reason));
            audit.record(org.getId(), adminId, "platform.organization.suspend", "Organization",
                    org.getId(), reason);
        }
        return toRow(org, LocalDate.now());
    }

    @Transactional
    public OrganizationRow activate(String id, String adminId) {
        Organization org = require(id);
        if (org.isSuspended()) {
            org.setSuspendedAt(null);
            org.setSuspendedReason(null);
            audit.record(org.getId(), adminId, "platform.organization.activate", "Organization",
                    org.getId(), null);
        }
        return toRow(org, LocalDate.now());
    }

    /**
     * Nova palavra-passe temporária para o Dono da empresa. Serve para quando
     * ele a perde e a empresa não tem email configurado para a repor sozinha.
     */
    @Transactional
    public OwnerPasswordReset resetOwnerPassword(String id, String adminId) {
        Organization org = require(id);
        Membership dono = owner(org.getId());
        if (dono == null) {
            throw ApiException.conflict("Esta empresa não tem nenhum Dono ativo.");
        }
        User user = dono.getUser();
        String temporaria = gerarPalavraPasse();
        user.setPasswordHash(passwordEncoder.encode(temporaria));
        user.setActive(true);
        audit.record(org.getId(), adminId, "platform.owner.password_reset", "User",
                user.getId(), "Palavra-passe do Dono reposta pela plataforma: " + user.getEmail());
        return new OwnerPasswordReset(user.getEmail(), temporaria);
    }

    // -----------------------------------------------------------------------

    private Organization require(String id) {
        return organizations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));
    }

    private Membership owner(String organizationId) {
        return memberships.findTeam(organizationId).stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER && !m.isSuspended())
                .findFirst()
                .orElse(null);
    }

    static OrganizationStatus statusOf(Organization o, LocalDate hoje) {
        if (o.isSuspended()) {
            return OrganizationStatus.SUSPENDED;
        }
        if (o.isLicenseExpired(hoje)) {
            return OrganizationStatus.EXPIRED;
        }
        if (o.getLicenseUntil() != null && !o.getLicenseUntil().isAfter(hoje.plusDays(DIAS_AVISO))) {
            return OrganizationStatus.EXPIRING;
        }
        return OrganizationStatus.ACTIVE;
    }

    private OrganizationRow toRow(Organization o, LocalDate hoje) {
        List<Membership> equipa = memberships.findTeam(o.getId());
        Membership dono = equipa.stream()
                .filter(m -> m.getRole() == MembershipRole.OWNER && !m.isSuspended())
                .findFirst()
                .orElse(null);
        return new OrganizationRow(
                o.getId(), o.getName(), o.getTaxId(), o.getCity(), o.getCreatedAt(),
                statusOf(o, hoje), o.getLicenseUntil(), o.getSuspendedAt(), o.getSuspendedReason(),
                o.getPlatformNotes(),
                dono != null ? dono.getUser().getName() : null,
                dono != null ? dono.getUser().getEmail() : null,
                equipa.stream().filter(m -> !m.isSuspended()).count(),
                assets.countByOrganizationId(o.getId()),
                workOrders.countByOrganizationId(o.getId()),
                auditLogs.lastActivity(o.getId()));
    }

    private String gerarPalavraPasse() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
