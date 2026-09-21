package ao.autocare;

import static org.assertj.core.api.Assertions.assertThat;

import ao.autocare.modules.plan.PlanCatalog;
import ao.autocare.modules.plan.VehicleTaxonomy;
import ao.autocare.modules.plan.VehicleTaxonomy.Identificacao;
import org.junit.jupiter.api.Test;

/**
 * O sistema a reconhecer o que cada viatura é.
 *
 * <p>Um Corolla, um Hilux, uma Hiace e um Actros não são «viaturas»: são
 * quatro máquinas diferentes, com sistemas diferentes e avarias diferentes.
 * Quem gere uma frota sabe isso de cor; o sistema tem de saber também — e
 * tem de saber pelo que a empresa escreve na ficha, que é a marca, o modelo e
 * o ano.
 *
 * <p>O que este teste protege é sobretudo o que <b>não</b> pode acontecer: um
 * «Ligeiro de passageiros» classificado como autocarro por causa da palavra
 * «passageiros», ou um Coaster tratado como Toyota de escritório.
 */
class TaxonomiaViaturasTest {

    private static String familia(String categoria, String tipo, String marca, String modelo) {
        return VehicleTaxonomy.classificar(
                new Identificacao(categoria, tipo, marca, modelo, null)).familia();
    }

    @Test
    void reconheceOsCarrosQueAndamNasEstradasDeAngola() {
        // Pick-ups de obra
        assertThat(familia("VEHICLE", "Viatura de serviço", "Toyota", "Hilux 2.4 GD-6")).isEqualTo("PICKUP");
        assertThat(familia("VEHICLE", null, "Ford", "Ranger XLT")).isEqualTo("PICKUP");
        assertThat(familia("VEHICLE", null, "Isuzu", "D-Max")).isEqualTo("PICKUP");
        assertThat(familia("VEHICLE", null, "Nissan", "Navara NP300")).isEqualTo("PICKUP");

        // Jipes: os chefes de obra e as direções
        assertThat(familia("VEHICLE", null, "Toyota", "Land Cruiser Prado")).isEqualTo("SUV");
        assertThat(familia("VEHICLE", null, "Mitsubishi", "Pajero Sport")).isEqualTo("SUV");
        assertThat(familia("VEHICLE", null, "Land Rover", "Discovery 4")).isEqualTo("SUV");

        // Carros de escritório e de reuniões
        assertThat(familia("VEHICLE", null, "Toyota", "Corolla 1.8")).isEqualTo("SEDAN");
        assertThat(familia("VEHICLE", null, "Mercedes-Benz", "Classe E 220d")).isEqualTo("SEDAN");
        assertThat(familia("VEHICLE", null, "BMW", "Série 5 520d")).isEqualTo("SEDAN");
        assertThat(familia("VEHICLE", null, "Volkswagen", "Passat")).isEqualTo("SEDAN");

        // Carrinhas
        assertThat(familia("VEHICLE", null, "Toyota", "Hiace")).isEqualTo("VAN");
        assertThat(familia("VEHICLE", null, "Mercedes-Benz", "Sprinter 519")).isEqualTo("VAN");
        assertThat(familia("VEHICLE", null, "Ford", "Transit")).isEqualTo("VAN");

        // Pesados
        assertThat(familia("VEHICLE", null, "Mercedes-Benz", "Actros 3340")).isEqualTo("TRUCK_HEAVY");
        assertThat(familia("VEHICLE", null, "Volvo", "FH 460")).isEqualTo("TRUCK_HEAVY");
        assertThat(familia("VEHICLE", null, "Howo", "A7 6x4")).isEqualTo("TRUCK_HEAVY");

        // Autocarros — incluindo os que têm nome de carro pequeno
        assertThat(familia("VEHICLE", null, "Yutong", "ZK6119")).isEqualTo("BUS");
        assertThat(familia("VEHICLE", null, "Toyota", "Coaster")).isEqualTo("BUS");
        assertThat(familia("VEHICLE", null, "Marcopolo", "Paradiso 1200")).isEqualTo("BUS");

        // Máquinas, empilhadoras e geradores continuam onde estavam
        assertThat(familia("MACHINE", null, "Volvo", "BL71B")).isEqualTo("RETROESCAVADORA");
        assertThat(familia("MACHINE", null, "JCB", "3CX")).isEqualTo("RETROESCAVADORA");
        assertThat(familia("MACHINE", null, "Linde", "H30D empilhadora")).isEqualTo("FORKLIFT");
        assertThat(familia("GENERATOR", null, "Cummins", "C250 D5")).isEqualTo("GENERATOR");
    }

    @Test
    void oModeloManda_eNaoAMarcaNemOTipoEscritoAPressa() {
        // A mesma marca, três famílias diferentes: é o modelo que decide.
        assertThat(familia("VEHICLE", "Viatura", "Toyota", "Corolla")).isEqualTo("SEDAN");
        assertThat(familia("VEHICLE", "Viatura", "Toyota", "Hilux")).isEqualTo("PICKUP");
        assertThat(familia("VEHICLE", "Viatura", "Toyota", "Coaster")).isEqualTo("BUS");

        // E o modelo ganha ao tipo escrito à pressa pela empresa.
        assertThat(familia("VEHICLE", "camião", "Toyota", "Land Cruiser")).isEqualTo("SUV");
    }

    @Test
    void naoConfundeUmLigeiroDePassageirosComUmAutocarro() {
        assertThat(familia("VEHICLE", "Ligeiro de passageiros", null, null)).isEqualTo("LIGHT_VEHICLE");
        assertThat(familia("VEHICLE", "Autocarro de passageiros", null, null)).isEqualTo("BUS");
        assertThat(familia("VEHICLE", "Transporte de passageiros", null, null)).isEqualTo("BUS");
    }

    @Test
    void quandoNaoReconheceNaoInventa() {
        var r = VehicleTaxonomy.classificar(
                new Identificacao("VEHICLE", "Viatura de serviço", "Marca X", "Modelo Y", 2020));
        assertThat(r.familia()).isEqualTo("LIGHT_VEHICLE");
        assertThat(r.origem()).isEqualTo("CATEGORIA");
        assertThat(r.explicacao()).contains("escreva o modelo");
    }

    @Test
    void aIdadeApertaOQueEnvelheceComOTempo() {
        int agora = java.time.Year.now().getValue();
        assertThat(VehicleTaxonomy.factorDeIdade(agora - 2)).isEqualTo(1);
        assertThat(VehicleTaxonomy.factorDeIdade(agora - 12)).isEqualTo(0.75);
        assertThat(VehicleTaxonomy.factorDeIdade(agora - 25)).isEqualTo(0.6);
        assertThat(VehicleTaxonomy.factorDeIdade(null)).isEqualTo(1);

        assertThat(VehicleTaxonomy.notaDeIdade(agora - 2)).isNull();
        assertThat(VehicleTaxonomy.notaDeIdade(agora - 12))
                .contains("12 anos")
                .contains("25 %")
                .containsIgnoringCase("travagem");
    }

    @Test
    void cadaFamiliaDeLigeiroTemOPlanoQueLhePertence() {
        var jipe = PlanCatalog.build("SUV", null).tasks();
        assertThat(jipe.toString()).contains("transferência").contains("foles");

        var pickup = PlanCatalog.build("PICKUP", null).tasks();
        assertThat(pickup).hasSizeGreaterThan(jipe.size());
        assertThat(pickup.toString()).contains("Caixa de carga").contains("Molas e amortecedores");

        var carrinha = PlanCatalog.build("VAN", null).tasks();
        assertThat(carrinha.toString()).contains("Portas laterais").contains("regulador de travagem");
        // Uma carrinha não tem tração às quatro rodas por omissão.
        assertThat(carrinha).noneMatch(t -> "Tração às quatro rodas".equals(t.systemName()));

        var sedan = PlanCatalog.build("SEDAN", null).tasks();
        assertThat(sedan).noneMatch(t -> "Tração às quatro rodas".equals(t.systemName()));

        // E cada uma com a sua inspeção diária.
        assertThat(PlanCatalog.dailyChecklist("SUV", null).items().toString()).contains("Foles");
        assertThat(PlanCatalog.dailyChecklist("VAN", null).items().toString()).contains("Cintos");
        assertThat(PlanCatalog.dailyChecklist("SEDAN", null)).isNotNull();
    }
}
