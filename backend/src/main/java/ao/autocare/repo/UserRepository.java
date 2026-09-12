package ao.autocare.repo;

import ao.autocare.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhone(String phone);

    @Query("select u from User u where lower(u.email) = lower(:id) or u.phone = :id")
    Optional<User> findByEmailOrPhone(@Param("id") String identifier);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);
}
