package ao.autocare.domain;

import ao.autocare.domain.enums.Enums;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * Uma medição feita na oficina.
 *
 * <p>É deliberadamente genérica: serve a espessura de uma pastilha, o piso de
 * um pneu, a tensão de uma bateria, a pressão de óleo e a prova de carga de um
 * gerador. Uma tabela por tipo de medição dava dez tabelas e nenhuma consulta
 * que as cruzasse.
 *
 * <p>Guardar o número, e não apenas «OK», é o que permite saber que a pastilha
 * estava a 9 mm há três meses e a 4 mm agora — e portanto quando chega ao
 * limite. Um checklist de passa/não-passa nunca responde a isso.
 */
@Getter
@Setter
@Entity
@Table(name = "work_order_measurements")
public class WorkOrderMeasurement extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    /** Agrupa no ecrã: «Travagem», «Pneus», «Motor», «Gerador». */
    @Column(name = "group_name", nullable = false, length = 60)
    private String groupName;

    @Column(nullable = false, length = 120)
    private String name;

    /** Onde foi medido: «Dianteiro esquerdo», «Fase R», «Cilindro 3». */
    @Column(length = 60)
    private String position;

    @Column(name = "value_num", precision = 18, scale = 4)
    private BigDecimal valueNum;

    /** Para o que não é número: «limpo», «com folga», «verde». */
    @Column(name = "value_text", length = 200)
    private String valueText;

    @Column(length = 20)
    private String unit;

    @Column(name = "min_value", precision = 18, scale = 4)
    private BigDecimal minValue;

    @Column(name = "max_value", precision = 18, scale = 4)
    private BigDecimal maxValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.MeasurementVerdict verdict = Enums.MeasurementVerdict.OK;

    @Column(length = 500)
    private String note;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "recorded_by_label", length = 120)
    private String recordedByLabel;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /**
     * Decide o veredicto a partir dos limites de serviço.
     *
     * <p>Sem limites não há juízo a fazer: deixa ficar o que lá estiver, em vez
     * de inventar um «OK» que ninguém verificou.
     */
    public void judge() {
        if (valueNum == null || (minValue == null && maxValue == null)) {
            return;
        }
        boolean abaixo = minValue != null && valueNum.compareTo(minValue) < 0;
        boolean acima = maxValue != null && valueNum.compareTo(maxValue) > 0;
        if (abaixo || acima) {
            verdict = Enums.MeasurementVerdict.REPLACE;
            return;
        }
        // A dez por cento do limite ainda passa, mas não passa despercebido.
        if (minValue != null
                && valueNum.compareTo(minValue.multiply(new BigDecimal("1.10"))) <= 0) {
            verdict = Enums.MeasurementVerdict.ATTENTION;
            return;
        }
        if (maxValue != null
                && valueNum.compareTo(maxValue.multiply(new BigDecimal("0.90"))) >= 0) {
            verdict = Enums.MeasurementVerdict.ATTENTION;
            return;
        }
        verdict = Enums.MeasurementVerdict.OK;
    }
}
