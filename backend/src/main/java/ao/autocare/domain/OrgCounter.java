package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;

/** Contador sequencial por organização (ex.: número das Ordens de Manutenção). */
@Getter
@Setter
@Entity
@Table(name = "org_counters")
@IdClass(OrgCounter.Key.class)
public class OrgCounter {

    @Id
    @Column(name = "organization_id", length = 36)
    private String organizationId;

    @Id
    @Column(name = "counter_key", length = 40)
    private String counterKey;

    @Column(name = "counter_value", nullable = false)
    private long counterValue = 0;

    public static class Key implements Serializable {
        private String organizationId;
        private String counterKey;

        public Key() {}

        public Key(String organizationId, String counterKey) {
            this.organizationId = organizationId;
            this.counterKey = counterKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return java.util.Objects.equals(organizationId, k.organizationId)
                    && java.util.Objects.equals(counterKey, k.counterKey);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(organizationId, counterKey);
        }
    }
}
