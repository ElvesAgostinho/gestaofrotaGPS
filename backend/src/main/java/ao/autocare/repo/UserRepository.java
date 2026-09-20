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

    /**
     * Quem entra pode escrever o email, o telefone ou o identificador curto.
     *
     * <p>O identificador não distingue maiúsculas: quem o escreve às seis da
     * manhã, com luvas, não tem de acertar na caixa das letras.
     */
    @Query("select u from User u where lower(u.email) = lower(:id) or u.phone = :id "
            + "or upper(u.loginId) = upper(:id)")
    Optional<User> findByEmailOrPhoneOrLoginId(@Param("id") String identifier);

    boolean existsByLoginIdIgnoreCase(String loginId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);
}
