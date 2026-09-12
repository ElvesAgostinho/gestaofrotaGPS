package ao.autocare.repo;

import ao.autocare.domain.GeofencePresence;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GeofencePresenceRepository extends JpaRepository<GeofencePresence, String> {

    /** Todas as presenças de um ativo — a única consulta feita por cada posição. */
    @Query("select p from GeofencePresence p join fetch p.geofence where p.asset.id = :assetId")
    List<GeofencePresence> findByAssetId(String assetId);

    /** Quem está dentro de uma geocerca agora. */
    @Query("""
            select p from GeofencePresence p join fetch p.asset
            where p.geofence.id = :geofenceId and p.inside = true
            """)
    List<GeofencePresence> findInside(String geofenceId);
}
