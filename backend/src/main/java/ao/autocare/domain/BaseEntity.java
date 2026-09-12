package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Identificador UUID gerado pela aplicação (portável entre H2 e PostgreSQL). */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(length = 36, updatable = false, nullable = false)
    private String id;

    /**
     * Atribuído na gravação, e não na construção.
     *
     * <p>Já se tentou o contrário, para as entidades novas saírem com id na
     * mesma resposta. Partiu a aceitação de convites: com o id preenchido, o
     * Hibernate deixa de ver uma entidade nova como transitória, passa a
     * tratá-la como destacada e recusa-se a gravá-la em cascata. Quando é
     * preciso o id de um filho acabado de criar, a resposta é gravar (flush),
     * não antecipar o id.
     */
    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !getClass().equals(o.getClass())) return false;
        return id != null && id.equals(((BaseEntity) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
