package ao.autocare.modules.workorder;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Supplier;
import ao.autocare.domain.enums.Enums.SupplierKind;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.SupplierRepository;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oficinas e fornecedores de peças.
 *
 * <p>O NIF não é um campo opcional de formulário. Sem ele, a factura da oficina
 * não se reconcilia com a contabilidade da empresa — e o custo de manutenção
 * fica registado num sistema enquanto a despesa vive noutro.
 */
@Service
public class SupplierService {

    private final SupplierRepository suppliers;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public SupplierService(
            SupplierRepository suppliers,
            OrganizationRepository organizations,
            AuditService audit) {
        this.suppliers = suppliers;
        this.organizations = organizations;
        this.audit = audit;
    }

    public record SaveSupplierRequest(
            @NotBlank(message = "Indique o nome do fornecedor.")
            @Size(max = 200) String name,
            @Size(max = 40) String taxId,
            SupplierKind kind,
            @Size(max = 40) String phone,
            @Email(message = "O email indicado não é válido.")
            @Size(max = 190) String email,
            @Size(max = 400) String address,
            @Size(max = 120) String city,
            @Size(max = 160) String contactPerson,
            @Size(max = 200) String paymentTerms,
            Integer defaultWarrantyMonths,
            @Size(max = 2000) String notes,
            Boolean active) {}

    public record SupplierView(
            String id,
            String name,
            String taxId,
            SupplierKind kind,
            String kindLabel,
            String phone,
            String email,
            String address,
            String city,
            String contactPerson,
            String paymentTerms,
            Integer defaultWarrantyMonths,
            String notes,
            boolean active) {

        public static SupplierView of(Supplier s) {
            return new SupplierView(
                    s.getId(), s.getName(), s.getTaxId(), s.getKind(), s.getKind().label(),
                    s.getPhone(), s.getEmail(), s.getAddress(), s.getCity(),
                    s.getContactPerson(), s.getPaymentTerms(), s.getDefaultWarrantyMonths(),
                    s.getNotes(), s.isActive());
        }
    }

    @Transactional(readOnly = true)
    public List<SupplierView> list(String orgId) {
        return suppliers.findByOrganizationIdOrderByNameAsc(orgId).stream()
                .map(SupplierView::of).toList();
    }

    @Transactional
    public SupplierView create(String orgId, String userId, SaveSupplierRequest req) {
        if (req.taxId() != null && !req.taxId().isBlank()
                && suppliers.existsByOrganizationIdAndTaxId(orgId, req.taxId().trim())) {
            throw ApiException.conflict(
                    "Já existe um fornecedor com o NIF " + req.taxId().trim() + ".");
        }
        Supplier s = new Supplier();
        s.setOrganization(organizations.getReferenceById(orgId));
        apply(s, req);
        suppliers.save(s);

        audit.record(orgId, userId, "supplier.create", "Supplier", s.getId(),
                s.getName() + (s.getTaxId() != null ? " · NIF " + s.getTaxId() : ""));
        return SupplierView.of(s);
    }

    @Transactional
    public SupplierView update(String orgId, String userId, String id, SaveSupplierRequest req) {
        Supplier s = suppliers.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Fornecedor não encontrado."));
        apply(s, req);
        audit.record(orgId, userId, "supplier.update", "Supplier", s.getId(), s.getName());
        return SupplierView.of(s);
    }

    private void apply(Supplier s, SaveSupplierRequest req) {
        s.setName(req.name().trim());
        s.setTaxId(trim(req.taxId()));
        s.setPhone(trim(req.phone()));
        s.setEmail(trim(req.email()));
        s.setAddress(trim(req.address()));
        s.setCity(trim(req.city()));
        s.setContactPerson(trim(req.contactPerson()));
        s.setPaymentTerms(trim(req.paymentTerms()));
        s.setDefaultWarrantyMonths(req.defaultWarrantyMonths());
        s.setNotes(trim(req.notes()));
        if (req.kind() != null) {
            s.setKind(req.kind());
        }
        if (req.active() != null) {
            s.setActive(req.active());
        }
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
