package ao.autocare.repo;

import ao.autocare.domain.TyreReading;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TyreReadingRepository extends JpaRepository<TyreReading, String> {

    List<TyreReading> findByTyreIdOrderByMeasuredAtDesc(String tyreId);

    /**
     * As medições de vários pneus de uma vez, da mais recente para a mais
     * antiga. É daqui que sai a taxa de desgaste: sem duas medições não há
     * previsão nenhuma, só adivinhação.
     */
    @Query("select r from TyreReading r where r.tyre.id in :tyreIds order by r.measuredAt desc")
    List<TyreReading> findByTyreIds(Collection<String> tyreIds);
}
