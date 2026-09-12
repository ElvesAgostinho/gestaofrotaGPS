package ao.autocare.repo;

import ao.autocare.domain.Driver;
import ao.autocare.domain.enums.Enums.DriverStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DriverRepository extends JpaRepository<Driver, String> {

    Optional<Driver> findByIdAndOrganizationId(String id, String organizationId);

    Page<Driver> findByOrganizationIdOrderByNameAsc(String organizationId, Pageable pageable);

    List<Driver> findByOrganizationIdOrderByNameAsc(String organizationId);

    List<Driver> findByOrganizationIdAndStatusOrderByNameAsc(
            String organizationId, DriverStatus status);

    boolean existsByOrganizationIdAndEmployeeNumber(String organizationId, String employeeNumber);

    long countByOrganizationIdAndStatus(String organizationId, DriverStatus status);

    /** Cartas de conducao a caducar ate uma data -- alimenta os avisos. */
    @Query("""
            select d from Driver d
            where d.organization.id = :organizationId
              and d.status = ao.autocare.domain.enums.Enums$DriverStatus.ACTIVE
              and d.licenseExpiresAt is not null
              and d.licenseExpiresAt <= :limite
            order by d.licenseExpiresAt asc
            """)
    List<Driver> licensesExpiringBy(String organizationId, LocalDate limite);

    @Query("""
            select d from Driver d
            where d.organization.id = :organizationId
              and (lower(d.name) like lower(concat('%', :termo, '%'))
                or lower(coalesce(d.employeeNumber, '')) like lower(concat('%', :termo, '%'))
                or lower(coalesce(d.licenseNumber, '')) like lower(concat('%', :termo, '%')))
            order by d.name asc
            """)
    Page<Driver> search(String organizationId, String termo, Pageable pageable);
}
