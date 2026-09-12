package ao.autocare.modules.workorder;

import ao.autocare.common.ApiException;
import ao.autocare.domain.enums.Enums.WorkOrderStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Que estados podem seguir-se a quais.
 *
 * <p>Está tudo numa tabela, e não espalhado por ifs ao longo do serviço, por
 * uma razão simples: assim é possível <b>ler</b> o percurso inteiro de uma
 * ordem sem seguir código. Quando alguém perguntar porque é que uma ordem
 * aprovada não volta a aberta, a resposta está numa linha.
 *
 * <p>Os fluxos antigos continuam válidos. Uma avaria pequena faz
 * {@code ABERTA → EM MANUTENÇÃO → CONCLUÍDA} como sempre fez; o percurso longo
 * com diagnóstico, orçamento e aprovação é para quando é preciso, não uma
 * obrigação nova imposta a toda a gente.
 */
@Component
public class WorkOrderWorkflow {

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> ALLOWED =
            new EnumMap<>(WorkOrderStatus.class);

    static {
        ALLOWED.put(WorkOrderStatus.OPEN, EnumSet.of(
                WorkOrderStatus.PLANNED, WorkOrderStatus.DIAGNOSIS, WorkOrderStatus.QUOTING,
                WorkOrderStatus.AWAITING_APPROVAL, WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.PLANNED, EnumSet.of(
                WorkOrderStatus.OPEN, WorkOrderStatus.DIAGNOSIS, WorkOrderStatus.IN_PROGRESS,
                WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.DIAGNOSIS, EnumSet.of(
                WorkOrderStatus.QUOTING, WorkOrderStatus.AWAITING_APPROVAL,
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.QUOTING, EnumSet.of(
                WorkOrderStatus.AWAITING_APPROVAL, WorkOrderStatus.APPROVED,
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.AWAITING_APPROVAL, EnumSet.of(
                WorkOrderStatus.APPROVED, WorkOrderStatus.REJECTED, WorkOrderStatus.CANCELLED));

        // Aprovada NÃO volta a aberta. Se voltasse, o valor aprovado deixava de
        // corresponder ao trabalho — e a aprovação passava a não valer nada.
        ALLOWED.put(WorkOrderStatus.APPROVED, EnumSet.of(
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.AWAITING_PARTS,
                WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.IN_PROGRESS, EnumSet.of(
                WorkOrderStatus.AWAITING_PARTS, WorkOrderStatus.TESTING,
                WorkOrderStatus.DONE, WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.AWAITING_PARTS, EnumSet.of(
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.TESTING, EnumSet.of(
                WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.DONE, WorkOrderStatus.CANCELLED));

        // Reabrir uma concluída é permitido e fica registado: o teste correu mal
        // com mais frequência do que se gostaria, e negá-lo levava as pessoas a
        // abrir uma ordem nova, perdendo a ligação ao trabalho já feito.
        ALLOWED.put(WorkOrderStatus.DONE, EnumSet.of(
                WorkOrderStatus.VERIFIED, WorkOrderStatus.CLOSED,
                WorkOrderStatus.IN_PROGRESS));

        ALLOWED.put(WorkOrderStatus.VERIFIED, EnumSet.of(WorkOrderStatus.CLOSED));

        // Rejeitada pode voltar a orçamento: pedir outra proposta é o caminho
        // normal depois de uma recusa.
        ALLOWED.put(WorkOrderStatus.REJECTED, EnumSet.of(
                WorkOrderStatus.QUOTING, WorkOrderStatus.CANCELLED));

        ALLOWED.put(WorkOrderStatus.CLOSED, EnumSet.noneOf(WorkOrderStatus.class));
        ALLOWED.put(WorkOrderStatus.CANCELLED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    public boolean canMove(WorkOrderStatus from, WorkOrderStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(WorkOrderStatus.class)).contains(to);
    }

    public Set<WorkOrderStatus> nextFrom(WorkOrderStatus from) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(WorkOrderStatus.class));
    }

    /**
     * Recusa a transição com uma mensagem que diz o que <b>é</b> possível.
     *
     * <p>"Transição inválida" obriga quem está do outro lado a adivinhar; dizer
     * para onde a ordem pode ir resolve o problema na mesma frase.
     */
    public void require(WorkOrderStatus from, WorkOrderStatus to) {
        if (from == to) {
            throw ApiException.conflict("A ordem já está em \"" + from.label() + "\".");
        }
        if (canMove(from, to)) {
            return;
        }
        Set<WorkOrderStatus> possiveis = nextFrom(from);
        if (possiveis.isEmpty()) {
            throw ApiException.conflict("Uma ordem " + from.label().toLowerCase()
                    + " já não muda de estado.");
        }
        StringBuilder lista = new StringBuilder();
        for (WorkOrderStatus s : possiveis) {
            if (lista.length() > 0) {
                lista.append(", ");
            }
            lista.append(s.label().toLowerCase());
        }
        throw ApiException.conflict("Uma ordem em \"" + from.label()
                + "\" não pode passar a \"" + to.label() + "\". "
                + "A partir daqui pode seguir para: " + lista + ".");
    }
}
