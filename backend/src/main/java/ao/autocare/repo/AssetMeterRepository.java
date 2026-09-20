package ao.autocare.repo;

import ao.autocare.domain.AssetMeter;
import ao.autocare.domain.enums.Enums.MeterKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetMeterRepository extends JpaRepository<AssetMeter, String> {

    List<AssetMeter> findByAssetId(String assetId);

    Optional<AssetMeter> findByAssetIdAndKind(String assetId, MeterKind kind);

    /** O contador principal de cada ativo da empresa: id do ativo e tipo de contador. */
    @org.springframework.data.jpa.repository.Query(
            "select m.asset.id, m.kind from AssetMeter m "
                    + "where m.asset.organization.id = :orgId and m.primary = true")
    List<Object[]> primaryKindsForOrg(String orgId);
}
