package ao.autocare.repo;

import ao.autocare.domain.Asset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, String> {

    Page<Asset> findByOrganizationIdAndArchived(String organizationId, boolean archived, Pageable pageable);

    /** Procura por etiqueta, nome, matrícula, fabricante, modelo ou número de série — no servidor, para frotas grandes. */
    @org.springframework.data.jpa.repository.Query("""
            select a from Asset a
            where a.organization.id = :organizationId and a.archived = :archived
              and (lower(a.tag) like :q or lower(a.name) like :q or lower(coalesce(a.plate, '')) like :q
                   or lower(coalesce(a.manufacturer, '')) like :q or lower(coalesce(a.model, '')) like :q
                   or lower(coalesce(a.serialNumber, '')) like :q)
            """)
    Page<Asset> search(String organizationId, boolean archived, String q, Pageable pageable);

    List<Asset> findByOrganizationId(String organizationId);

    Optional<Asset> findByIdAndOrganizationId(String id, String organizationId);

    boolean existsByOrganizationIdAndTagIgnoreCase(String organizationId, String tag);

    long countByOrganizationId(String organizationId);

    long countByAssetTypeId(String assetTypeId);

    long countByLocationId(String locationId);
}
