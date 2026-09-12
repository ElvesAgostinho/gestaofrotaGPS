package ao.autocare.repo;

import ao.autocare.domain.AssetDocument;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AssetDocumentRepository extends JpaRepository<AssetDocument, String> {

    List<AssetDocument> findByAssetIdOrderByKindAscTitleAsc(String assetId);

    Optional<AssetDocument> findByIdAndOrganizationId(String id, String organizationId);

    /**
     * Documentos da empresa que caducam até uma data, do mais urgente para o
     * menos. Inclui os já caducados — são os que mais importam.
     */
    @Query("select d from AssetDocument d join fetch d.asset a "
            + "where d.organization.id = :organizationId and d.expiresAt is not null "
            + "and d.expiresAt < :until and a.archived = false "
            + "order by d.expiresAt asc")
    List<AssetDocument> findExpiringUntil(String organizationId, Instant until);

    /** Todos os que têm validade, em toda a plataforma — usado pelo agendador. */
    @Query("select d from AssetDocument d join fetch d.asset a join fetch d.organization "
            + "where d.expiresAt is not null and a.archived = false")
    List<AssetDocument> findAllWithExpiry();

    long countByAssetId(String assetId);
}
