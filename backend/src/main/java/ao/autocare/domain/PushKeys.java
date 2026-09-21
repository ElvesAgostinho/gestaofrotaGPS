package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * As chaves VAPID desta instalação.
 *
 * <p>O protocolo de notificações do browser exige um par de chaves que
 * identifique o servidor que envia. Podiam vir de uma configuração, mas então
 * alguém teria de as gerar antes de a aplicação funcionar — e não funcionaria
 * até lá. Geram-se à primeira utilização e ficam aqui: uma linha, para sempre.
 */
@Entity
@Table(name = "push_keys")
@Getter
@Setter
public class PushKeys extends BaseEntity {

    @Column(name = "public_key", nullable = false, length = 200)
    private String publicKey;

    @Column(name = "private_key", nullable = false, length = 200)
    private String privateKey;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
