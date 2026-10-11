package ao.autocare.modules.mailbox;

import ao.autocare.domain.Driver;
import ao.autocare.domain.Membership;
import ao.autocare.domain.Supplier;
import ao.autocare.domain.SupplierContact;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.MembershipRepository;
import ao.autocare.repo.SupplierContactRepository;
import ao.autocare.repo.SupplierRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem é o endereço do outro lado.
 *
 * <p>É isto que separa uma caixa de correio dentro do sistema de uma caixa de
 * correio qualquer. Um webmail mostra «geral@autopecas.co.ao»; aqui mostra
 * «Auto Peças de Luanda · fornecedor», com um caminho para a ficha dele. E
 * porque sabe quem é, consegue separar o correio dos fornecedores de tudo o
 * resto — que é a diferença entre procurar uma mensagem durante cinco minutos
 * e abri-la logo.
 *
 * <p>A correspondência é feita por endereço, em minúsculas e sem espaços, e
 * passa por quatro sítios: os fornecedores, as pessoas dentro de cada
 * fornecedor, os motoristas e a equipa. Um endereço que não esteja em lado
 * nenhum fica sem etiqueta — e isso também é informação: é correio de alguém
 * que ainda não está no sistema.
 */
@Service
public class EntityDirectory {

    /** A quem pertence um endereço de email. */
    public record Entidade(
            String tipo,
            String id,
            String nome,
            /** O endereço por onde se fala com ela — é por aqui que se filtra. */
            String email,
            /** O papel dentro da entidade, quando é uma pessoa. */
            String detalhe,
            /** O caminho da ficha no sistema. */
            String link) {

        public static final String FORNECEDOR = "SUPPLIER";
        public static final String CONTACTO = "SUPPLIER_CONTACT";
        public static final String MOTORISTA = "DRIVER";
        public static final String EQUIPA = "TEAM";
    }

    private final SupplierRepository suppliers;
    private final SupplierContactRepository contacts;
    private final DriverRepository drivers;
    private final MembershipRepository memberships;

    public EntityDirectory(
            SupplierRepository suppliers,
            SupplierContactRepository contacts,
            DriverRepository drivers,
            MembershipRepository memberships) {
        this.suppliers = suppliers;
        this.contacts = contacts;
        this.drivers = drivers;
        this.memberships = memberships;
    }

    /**
     * O índice de endereços da empresa.
     *
     * <p>Construído de uma vez e usado para uma página inteira de mensagens:
     * perguntar à base de dados por cada mensagem daria cinquenta consultas
     * para mostrar cinquenta linhas.
     */
    @Transactional(readOnly = true)
    public Map<String, Entidade> indice(String orgId) {
        Map<String, Entidade> porEndereco = new HashMap<>();

        for (Supplier s : suppliers.findByOrganizationIdOrderByNameAsc(orgId)) {
            juntar(porEndereco, s.getEmail(), e -> new Entidade(
                    Entidade.FORNECEDOR, s.getId(), s.getName(), e,
                    s.getKind() != null ? s.getKind().label() : "Fornecedor",
                    "/fornecedores/" + s.getId()));

            for (SupplierContact c : contacts.findBySupplierIdOrderByPrimaryContactDescNameAsc(s.getId())) {
                juntar(porEndereco, c.getEmail(), e -> new Entidade(
                        Entidade.CONTACTO, s.getId(),
                        c.getName() + " · " + s.getName(), e,
                        c.getRole().label(),
                        "/fornecedores/" + s.getId()));
            }
        }

        for (Driver d : drivers.findByOrganizationIdOrderByNameAsc(orgId)) {
            juntar(porEndereco, d.getEmail(), e -> new Entidade(
                    Entidade.MOTORISTA, d.getId(), d.getName(), e, "Motorista",
                    "/motoristas/" + d.getId()));
        }

        for (Membership m : memberships.findTeam(orgId)) {
            juntar(porEndereco, m.getUser().getEmail(), e -> new Entidade(
                    Entidade.EQUIPA, m.getUser().getId(), m.getUser().getName(), e,
                    m.getRole() != null ? m.getRole().name() : "Equipa",
                    "/equipa"));
        }

        return porEndereco;
    }

    /**
     * Junta sem substituir.
     *
     * <p>O primeiro a reclamar um endereço fica com ele. A ordem é deliberada:
     * os fornecedores entram antes das pessoas deles, e se o email do
     * fornecedor for o mesmo do comercial, quem se mostra é a empresa — que é
     * o que interessa a quem está a ler o correio de compras.
     */
    private static void juntar(
            Map<String, Entidade> mapa, String email, java.util.function.Function<String, Entidade> cria) {
        String chave = normalizar(email);
        if (chave != null) {
            mapa.computeIfAbsent(chave, cria);
        }
    }

    /** Minúsculas, sem espaços e sem o nome à frente: «Nome <a@b.ao>» → «a@b.ao». */
    public static String normalizar(String email) {
        if (email == null) {
            return null;
        }
        String v = email.trim();
        int abre = v.indexOf('<');
        int fecha = v.indexOf('>');
        if (abre >= 0 && fecha > abre) {
            v = v.substring(abre + 1, fecha).trim();
        }
        v = v.toLowerCase(Locale.ROOT);
        return v.isEmpty() || !v.contains("@") ? null : v;
    }

    /** As entidades que têm email, para a lista de filtros do ecrã. */
    @Transactional(readOnly = true)
    public List<Entidade> comEmail(String orgId) {
        List<Entidade> todas = new ArrayList<>(indice(orgId).values());
        todas.sort((a, b) -> {
            int porTipo = ordem(a.tipo()) - ordem(b.tipo());
            return porTipo != 0 ? porTipo : a.nome().compareToIgnoreCase(b.nome());
        });
        return todas;
    }

    private static int ordem(String tipo) {
        return switch (tipo) {
            case Entidade.FORNECEDOR -> 0;
            case Entidade.CONTACTO -> 1;
            case Entidade.MOTORISTA -> 2;
            default -> 3;
        };
    }
}
