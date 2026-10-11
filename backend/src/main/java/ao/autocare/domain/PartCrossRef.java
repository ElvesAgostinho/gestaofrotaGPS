package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * O mesmo artigo com outro número.
 *
 * <p>Um filtro tem o número do fabricante do camião (o original), o do
 * fabricante do filtro e os dos equivalentes de três outras marcas. Quem está
 * ao balcão tem uma caixa na mão com <i>um</i> desses números e precisa de
 * saber se é o artigo certo. Sem esta tabela, a resposta é «procura pelo nome
 * e espera que alguém se lembre» — e é assim que se compra duas vezes a mesma
 * peça com dois nomes diferentes, ou se recusa um equivalente que servia.
 *
 * <p>A relação diz-se, não se adivinha: um número que <b>substitui</b> outro
 * não é a mesma coisa que um equivalente, e montar o antigo onde o fabricante
 * já mandou o novo é um defeito à espera de acontecer.
 */
@Getter
@Setter
@Entity
@Table(name = "part_cross_refs")
public class PartCrossRef extends TimestampedEntity {

    public enum Kind {
        /** O número do fabricante do equipamento. */
        OEM,
        /** Outra marca, serve igual. */
        EQUIVALENT,
        /** Este número substitui o que está na referência (é o mais recente). */
        SUPERSEDES,
        /** Este número foi substituído: o fabricante já manda outro. */
        SUPERSEDED_BY
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "part_id", nullable = false)
    private Part part;

    @Column(nullable = false, length = 80)
    private String number;

    @Column(length = 80)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind = Kind.EQUIVALENT;

    @Column(length = 300)
    private String note;
}
