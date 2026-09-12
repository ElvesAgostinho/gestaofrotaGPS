import { ActionIcon, Group, Modal, Select, Text, Tooltip } from '@mantine/core';
import { IconSearch } from '@tabler/icons-react';
import { useMemo, useState, type KeyboardEvent } from 'react';
import { Grelha, type Coluna } from './Grelha';
import { CampoProcura, filtrar } from './Procura';

/**
 * O «F4» de um ERP: escolher um registo numa janela com grelha e procura.
 *
 * <p>Um campo de lista serve para vinte viaturas. Com quatrocentas, a pessoa
 * quer ver a etiqueta, o nome, o tipo e a filial lado a lado, ordenar, e
 * escolher com Enter — a janela de pesquisa do Primavera. O campo continua a
 * ser um campo: escreve-se e filtra; F4 (ou a lupa) abre a janela.
 */
export function CampoConsulta<T>({
  label,
  placeholder,
  required,
  clearable,
  linhas,
  valor,
  aoMudar,
  chave,
  rotulo,
  colunas,
  camposProcura,
  tituloJanela,
  idGrelha,
}: {
  label: string;
  placeholder?: string;
  required?: boolean;
  clearable?: boolean;
  linhas: T[];
  valor: string | null;
  aoMudar: (id: string | null) => void;
  chave: (linha: T) => string;
  /** O que aparece no campo depois de escolher. */
  rotulo: (linha: T) => string;
  colunas: Coluna<T>[];
  camposProcura: (linha: T) => (string | number | null | undefined)[];
  tituloJanela: string;
  idGrelha: string;
}) {
  const [aberta, setAberta] = useState(false);
  const [procura, setProcura] = useState('');

  const filtradas = useMemo(() => filtrar(linhas, procura, camposProcura), [linhas, procura, camposProcura]);

  function escolher(linha: T) {
    aoMudar(chave(linha));
    setAberta(false);
    setProcura('');
  }

  function aoTecla(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'F4') {
      e.preventDefault();
      setAberta(true);
    }
  }

  return (
    <>
      <Group gap={4} align="flex-end" wrap="nowrap">
        <Select
          label={label}
          placeholder={placeholder}
          required={required}
          clearable={clearable}
          searchable
          data={linhas.map((l) => ({ value: chave(l), label: rotulo(l) }))}
          value={valor}
          onChange={aoMudar}
          onKeyDown={aoTecla}
          style={{ flex: 1 }}
          description={undefined}
        />
        <Tooltip label="Janela de pesquisa (F4)" withArrow>
          <ActionIcon
            variant="default"
            size="lg"
            aria-label="Pesquisar"
            onClick={() => setAberta(true)}
            style={{ marginBottom: 1 }}
          >
            <IconSearch size={16} />
          </ActionIcon>
        </Tooltip>
      </Group>

      <Modal
        opened={aberta}
        onClose={() => setAberta(false)}
        title={tituloJanela}
        size="xl"
        centered
      >
        <Group justify="space-between" mb="xs">
          <CampoProcura valor={procura} aoMudar={setProcura} placeholder="Escreva para filtrar…" largura={320} />
          <Text size="xs" c="dimmed">
            ↑ ↓ para percorrer · Enter ou duplo clique para escolher
          </Text>
        </Group>
        <Grelha
          id={idGrelha}
          linhas={filtradas}
          chave={chave}
          colunas={colunas}
          aoAbrir={escolher}
          altura="55vh"
          porPagina={50}
          vazio="Nada corresponde à procura."
        />
      </Modal>
    </>
  );
}
