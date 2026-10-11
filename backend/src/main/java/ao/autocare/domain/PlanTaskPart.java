package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** Peça prevista para uma tarefa do plano, ligada ao artigo do armazém. */
@Getter
@Setter
@Entity
@Table(name = "plan_task_parts")
public class PlanTaskPart extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private PlanTask task;

    /**
      * O artigo do armazém, quando já existe lá.
      *
      * <p>Sem esta ligação não se responde à pergunta que interessa na véspera
      * de uma manutenção: «a troca é daqui a 800 km — tenho o filtro?». O nome
      * por extenso fica à mesma, porque o plano tem de se ler sem o armazém.
      */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "part_id")
    private Part part;

    @Column(name = "part_name", nullable = false, length = 200)
    private String partName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(length = 20)
    private String unit;
}
