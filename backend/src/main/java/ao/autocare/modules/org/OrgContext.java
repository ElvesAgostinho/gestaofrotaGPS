package ao.autocare.modules.org;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Organization;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.security.AuthPrincipal;
import org.springframework.stereotype.Component;

/**
 * Resolve a organização do pedido atual. Todos os módulos CMMS operam sempre
 * no contexto de uma organização (multi-tenant).
 */
@Component
public class OrgContext {

    private final OrganizationRepository organizations;

    public OrgContext(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /** Id da organização do utilizador autenticado, ou erro amigável se não tiver. */
    public String requireOrganizationId(AuthPrincipal principal) {
        if (principal == null || principal.organizationId() == null) {
            throw ApiException.forbidden(
                    "A sua conta ainda não está associada a nenhuma empresa.");
        }
        return principal.organizationId();
    }

    public Organization require(AuthPrincipal principal) {
        String id = requireOrganizationId(principal);
        return organizations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));
    }
}
