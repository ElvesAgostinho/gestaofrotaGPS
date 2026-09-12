package ao.autocare.repo;

import ao.autocare.domain.VehicleBrand;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleBrandRepository extends JpaRepository<VehicleBrand, String> {

    Optional<VehicleBrand> findByName(String name);

    List<VehicleBrand> findAllByOrderByNameAsc();
}
