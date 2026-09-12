package ao.autocare.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ao.autocare.modules.importer.Csv;
import org.junit.jupiter.api.Test;

/** Leitura de CSV: os casos que aparecem mesmo em ficheiros de escritório. */
class CsvTest {

    @Test
    void readsSemicolonFilesFromPortugueseExcel() {
        Csv.Sheet sheet = Csv.parse("""
                tag;nome;modelo
                RE-001;Retroescavadora;BL71B
                GER-001;Gerador;C15
                """);

        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0).get("tag")).isEqualTo("RE-001");
        assertThat(sheet.rows().get(0).get("modelo")).isEqualTo("BL71B");
        assertThat(sheet.rows().get(1).get("nome")).isEqualTo("Gerador");
    }

    @Test
    void readsCommaFilesToo() {
        Csv.Sheet sheet = Csv.parse("""
                tag,nome
                CAM-001,Camião
                """);
        assertThat(sheet.rows()).hasSize(1);
        assertThat(sheet.rows().get(0).get("nome")).isEqualTo("Camião");
    }

    @Test
    void aCommaInsideAQuotedFieldIsNotASeparator() {
        Csv.Sheet sheet = Csv.parse("""
                tag,observacoes
                RE-001,"Comprada em 2023, com pá frontal"
                """);
        assertThat(sheet.rows().get(0).get("observacoes"))
                .isEqualTo("Comprada em 2023, com pá frontal");
    }

    @Test
    void handlesDoubledQuotesAndLineBreaksInsideFields() {
        Csv.Sheet sheet = Csv.parse(
                "tag;observacoes\nRE-001;\"Máquina dita \"\"a velha\"\";\nsegunda linha\"\n");

        assertThat(sheet.rows()).hasSize(1);
        assertThat(sheet.rows().get(0).get("observacoes"))
                .isEqualTo("Máquina dita \"a velha\";\nsegunda linha");
    }

    @Test
    void headersAreMatchedWithoutCaringAboutAccentsCaseOrSpaces() {
        Csv.Sheet sheet = Csv.parse("""
                Número de Série;Ano de Fabrico;RESPONSÁVEL
                CAT0416;2023;João Silva
                """);

        Csv.Row row = sheet.rows().get(0);
        assertThat(row.get("numero de serie")).isEqualTo("CAT0416");
        assertThat(row.get("NUMERO_DE_SERIE")).isEqualTo("CAT0416");
        assertThat(row.get("ano_de_fabrico")).isEqualTo("2023");
        assertThat(row.get("responsavel")).isEqualTo("João Silva");
    }

    @Test
    void differentSpellingsOfTheSameColumnAreAcceptedAsSynonyms() {
        // Normalizar resolve acentos e maiúsculas, não sinónimos: "Nº Série"
        // reduz-se a "noserie" e nunca coincidiria com "numeroserie".
        Csv.Sheet folhaCurta = Csv.parse("Nº Série\nCAT0416\n");
        Csv.Sheet folhaLonga = Csv.parse("numero_serie\nCAT0416\n");
        Csv.Sheet folhaInglesa = Csv.parse("serial\nCAT0416\n");

        String[] nomes = {"no serie", "numero de serie", "numero serie", "serial"};
        assertThat(folhaCurta.rows().get(0).getAny(nomes)).isEqualTo("CAT0416");
        assertThat(folhaLonga.rows().get(0).getAny(nomes)).isEqualTo("CAT0416");
        assertThat(folhaInglesa.rows().get(0).getAny(nomes)).isEqualTo("CAT0416");

        assertThat(folhaCurta.rows().get(0).getAny("inexistente", "outra")).isNull();
    }

    @Test
    void skipsTheExcelByteOrderMarkAndBlankLines() {
        Csv.Sheet sheet = Csv.parse("﻿tag;nome\n\nRE-001;Retro\n\n\nGER-001;Gerador\n");

        assertThat(sheet.headers()).containsExactly("tag", "nome");
        assertThat(sheet.rows()).hasSize(2);
        assertThat(sheet.rows().get(0).get("tag")).isEqualTo("RE-001");
    }

    @Test
    void keepsTheOriginalLineNumberSoErrorsCanBeFound() {
        Csv.Sheet sheet = Csv.parse("""
                tag;nome
                RE-001;Retro
                GER-001;Gerador
                """);
        // A linha 1 é o cabeçalho: os dados começam na 2.
        assertThat(sheet.rows().get(0).lineNumber()).isEqualTo(2);
        assertThat(sheet.rows().get(1).lineNumber()).isEqualTo(3);
    }

    @Test
    void missingColumnsComeBackAsNullInsteadOfBreaking() {
        Csv.Sheet sheet = Csv.parse("""
                tag;nome;modelo
                RE-001;Retro
                """);
        assertThat(sheet.rows().get(0).get("modelo")).isNull();
        assertThat(sheet.rows().get(0).get("coluna_que_nao_existe")).isNull();
    }

    @Test
    void blankValuesCountAsAbsent() {
        Csv.Sheet sheet = Csv.parse("""
                tag;nome;modelo
                RE-001;Retro;
                """);
        assertThat(sheet.rows().get(0).get("modelo")).isNull();
    }

    @Test
    void refusesAnEmptyFile() {
        assertThatThrownBy(() -> Csv.parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vazio");
        assertThatThrownBy(() -> Csv.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
