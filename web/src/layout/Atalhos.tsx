import { Kbd, Modal, Table, Text } from '@mantine/core';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * Atalhos de teclado, como num ERP.
 *
 * <p>Quem passa oito horas num sistema não larga o teclado: F2 abre o «novo»
 * do ecrã onde está, Ctrl+S guarda o formulário aberto, «/» salta para a
 * procura, Esc fecha. Os atalhos encontram os botões pelo que eles são — o
 * botão principal da barra, o «Guardar» do diálogo aberto — e não por cada
 * ecrã os declarar, por isso valem em todos os ecrãs sem os tocar.
 *
 * <p>Nunca disparam dentro de um campo de texto: F2 num campo continua a ser
 * F2 do navegador, e «/» escreve uma barra.
 */
export const ATALHOS: { tecla: string; faz: string }[] = [
  { tecla: 'F2', faz: 'Novo registo no ecrã atual' },
  { tecla: 'Ctrl + S', faz: 'Guardar o formulário aberto' },
  { tecla: '/', faz: 'Ir para a procura' },
  { tecla: 'Esc', faz: 'Fechar o diálogo' },
  { tecla: '↑ ↓', faz: 'Percorrer a grelha' },
  { tecla: 'Enter', faz: 'Abrir o registo selecionado na grelha' },
  { tecla: 'Home / End', faz: 'Primeiro / último registo da página' },
  { tecla: 'PgUp / PgDn', faz: 'Página anterior / seguinte da grelha' },
  { tecla: 'Alt + 1 … 9', faz: 'Saltar para o 1.º … 9.º ecrã do menu' },
  { tecla: 'F1', faz: 'Esta lista' },
];

/** Botões de gravar, pelo nome — o que eles dizem é o que fazem. */
const GRAVAR = /^(guardar|criar|registar|convidar|emitir|aplicar|lançar|usar este ponto)$/i;

function dentroDeCampo(alvo: EventTarget | null) {
  const el = alvo as HTMLElement | null;
  if (!el) return false;
  return !!el.closest('input, textarea, select, [contenteditable="true"]');
}

function clicar(botao: HTMLElement | null): boolean {
  if (!botao || (botao as HTMLButtonElement).disabled) return false;
  botao.click();
  return true;
}

export function Atalhos() {
  const [ajuda, setAjuda] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    function aoTecla(e: KeyboardEvent) {
      // F1: a lista. Vale sempre, mesmo dentro de um campo.
      if (e.key === 'F1') {
        e.preventDefault();
        setAjuda((a) => !a);
        return;
      }

      const dialogo = document.querySelector<HTMLElement>('[role="dialog"]');

      // Ctrl+S: o botão de gravar do diálogo aberto. Vale dentro de campos —
      // é exatamente aí que a pessoa está quando quer gravar.
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
        e.preventDefault();
        if (!dialogo) return;
        const botoes = [...dialogo.querySelectorAll<HTMLButtonElement>('button')];
        const alvo = botoes.reverse().find((b) => GRAVAR.test(b.innerText.trim()));
        clicar(alvo ?? null);
        return;
      }

      if (dentroDeCampo(e.target)) return;

      // F2: o botão principal da barra de ferramentas do ecrã.
      if (e.key === 'F2') {
        e.preventDefault();
        if (dialogo) return;
        const principal = document.querySelector<HTMLElement>('main [data-atalho="novo"]');
        clicar(principal);
        return;
      }

      // «/»: a procura do ecrã.
      if (e.key === '/') {
        const procura = document.querySelector<HTMLInputElement>('main .campo-procura input, main input[placeholder*="rocur"]');
        if (procura) {
          e.preventDefault();
          procura.focus();
          procura.select();
        }
        return;
      }

      // Alt+1..9: o n-ésimo ecrã do menu.
      if (e.altKey && /^[1-9]$/.test(e.key)) {
        const ligacoes = [...document.querySelectorAll<HTMLAnchorElement>('.mantine-AppShell-navbar a[href]')];
        const alvo = ligacoes[Number(e.key) - 1];
        if (alvo) {
          e.preventDefault();
          navigate(alvo.getAttribute('href')!);
        }
      }
    }
    window.addEventListener('keydown', aoTecla);
    return () => window.removeEventListener('keydown', aoTecla);
  }, [navigate]);

  return (
    <Modal opened={ajuda} onClose={() => setAjuda(false)} title="Atalhos de teclado" centered>
      <Text size="sm" c="dimmed" mb="sm">
        Os atalhos não disparam dentro de um campo de texto, exceto Ctrl+S e F1.
      </Text>
      <Table>
        <Table.Tbody>
          {ATALHOS.map((a) => (
            <Table.Tr key={a.tecla}>
              <Table.Td style={{ width: 140 }}>
                <Kbd>{a.tecla}</Kbd>
              </Table.Td>
              <Table.Td>{a.faz}</Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Modal>
  );
}
