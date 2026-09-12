/**
 * Procura de lista.
 *
 * <p>Três decisões que separam uma procura útil de uma procura irritante:
 *
 * <p><b>Ignora acentos.</b> Quem escreve depressa escreve «camiao» e «orcamento».
 * Uma procura que exige o acento certo é uma procura que não encontra nada e
 * faz o utilizador desistir dela.
 *
 * <p><b>Todas as palavras, em qualquer ordem.</b> «volvo luanda» encontra o
 * camião Volvo da Filial de Luanda mesmo que as palavras estejam em colunas
 * diferentes e por outra ordem. É como as pessoas procuram.
 *
 * <p><b>Filtra no browser.</b> As listas trazem até algumas centenas de linhas;
 * filtrar aqui responde enquanto se escreve, em vez de ir ao servidor a cada
 * tecla — o que numa ligação móvel angolana daria uma procura que anda atrás
 * de quem escreve.
 */
import { CloseButton, TextInput } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';

/** Tira acentos e põe em minúsculas, para comparar como as pessoas escrevem. */
function normalizar(v: unknown): string {
  return String(v ?? '')
    .normalize('NFD')
    // Marcas diacríticas: o que sobra depois de separar a letra do acento.
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase();
}

/**
 * Filtra uma lista pelo texto escrito.
 *
 * <p>`campos` devolve o que, em cada linha, é procurável. Valores nulos são
 * ignorados, por isso pode passar campos opcionais sem os tratar antes.
 */
export function filtrar<T>(
  linhas: T[],
  termo: string,
  campos: (linha: T) => (string | number | null | undefined)[],
): T[] {
  const palavras = normalizar(termo).split(/\s+/).filter(Boolean);
  if (palavras.length === 0) {
    return linhas;
  }
  return linhas.filter((linha) => {
    const texto = normalizar(campos(linha).filter((c) => c != null).join(' '));
    return palavras.every((p) => texto.includes(p));
  });
}

export function CampoProcura({
  valor,
  aoMudar,
  placeholder = 'Procurar…',
  largura = 210,
}: {
  valor: string;
  aoMudar: (v: string) => void;
  placeholder?: string;
  largura?: number;
}) {
  return (
    <TextInput
      className="campo-procura"
      size="xs"
      placeholder={placeholder}
      leftSection={<IconSearch size={12} />}
      rightSection={
        valor ? (
          <CloseButton size="xs" onClick={() => aoMudar('')} aria-label="Limpar procura" />
        ) : null
      }
      value={valor}
      onChange={(e) => aoMudar(e.currentTarget.value)}
      style={{ marginLeft: 'auto', width: largura }}
    />
  );
}
