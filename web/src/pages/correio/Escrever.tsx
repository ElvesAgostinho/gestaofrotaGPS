import {
  ActionIcon,
  Button,
  FileButton,
  Group,
  Modal,
  Stack,
  Text,
  Textarea,
  TextInput,
} from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconPaperclip, IconSend, IconTrash } from '@tabler/icons-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { tokens } from '../../api/client';
import { tamanhoLegivel } from './tipos';
/**
 * Escrever uma mensagem.
 *
 * <p>Os ficheiros vão no próprio envio, como num formulário. Carregá-los
 * primeiro para um sítio e só depois enviar deixaria ficheiros órfãos sempre
 * que alguém desistisse a meio — e alguém desiste sempre.
 *
 * <p>O limite de 15 MB é verificado aqui e no servidor. Aqui para a pessoa
 * saber antes de esperar pelo envio; lá porque o ecrã nunca é a última
 * defesa.
 */
const LIMITE = 15 * 1024 * 1024;
export function Escrever({
  aberto,
  aoFechar,
  paraInicial,
  assuntoInicial,
  corpoInicial,
}: {
  aberto: boolean;
  aoFechar: () => void;
  paraInicial?: string;
  assuntoInicial?: string;
  corpoInicial?: string;
}) {
  const qc = useQueryClient();
  const [para, setPara] = useState(paraInicial ?? '');
  const [cc, setCc] = useState('');
  const [assunto, setAssunto] = useState(assuntoInicial ?? '');
  const [corpo, setCorpo] = useState(corpoInicial ?? '');
  const [ficheiros, setFicheiros] = useState<File[]>([]);
  const total = ficheiros.reduce((soma, f) => soma + f.size, 0);
  const demasiado = total > LIMITE;
  const enviar = useMutation({
    mutationFn: async () => {
      const dados = new FormData();
      dados.append('para', para.trim());
      if (cc.trim()) {
        dados.append('cc', cc.trim());
      }
      dados.append('assunto', assunto.trim());
      // O corpo vai em HTML: o que se escreve em linhas tem de chegar em linhas.
      dados.append('corpo', corpo.replace(/\n/g, '<br/>'));
      ficheiros.forEach((f) => dados.append('anexos', f));
      // FormData não passa pelo cliente comum, que serializa tudo em JSON.
      const r = await fetch('/api/v1/mailbox/send', {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokens.access ?? ''}` },
        body: dados,
      });
      if (!r.ok) {
        const erro = (await r.json().catch(() => null)) as { message?: string } | null;
        throw new Error(erro?.message ?? 'Não foi possível enviar.');
      }
    },
    onSuccess: () => {
      notifications.show({ color: 'green', message: 'Mensagem enviada.' });
      void qc.invalidateQueries({ queryKey: ['correio'] });
      setPara('');
      setCc('');
      setAssunto('');
      setCorpo('');
      setFicheiros([]);
      aoFechar();
    },
    onError: (e: Error) =>
      notifications.show({ color: 'red', title: 'O envio falhou', message: e.message }),
  });
  const podeEnviar = para.trim().length > 3 && assunto.trim().length > 0 && !demasiado;
  return (
    <Modal opened={aberto} onClose={aoFechar} title="Nova mensagem" size="lg">
      <Stack gap="sm">
        <TextInput
          label="Para"
          placeholder="vendas@fornecedor.ao, outro@empresa.ao"
          value={para}
          onChange={(e) => setPara(e.currentTarget.value)}
          required
        />
        <TextInput
          label="Cc"
          placeholder="opcional"
          value={cc}
          onChange={(e) => setCc(e.currentTarget.value)}
        />
        <TextInput
          label="Assunto"
          value={assunto}
          onChange={(e) => setAssunto(e.currentTarget.value)}
          required
        />
        <Textarea
          label="Mensagem"
          autosize
          minRows={8}
          maxRows={18}
          value={corpo}
          onChange={(e) => setCorpo(e.currentTarget.value)}
        />
        {ficheiros.length > 0 && (
          <Stack gap={4}>
            {ficheiros.map((f, i) => (
              <Group key={`${f.name}-${i}`} gap={8} wrap="nowrap">
                <IconPaperclip size={14} />
                <Text size="sm" style={{ flex: 1 }}>
                  {f.name}
                </Text>
                <Text size="xs" c="dimmed">
                  {tamanhoLegivel(f.size)}
                </Text>
                <ActionIcon
                  variant="subtle"
                  color="red"
                  aria-label="Retirar anexo"
                  onClick={() => setFicheiros((lista) => lista.filter((_, j) => j !== i))}
                >
                  <IconTrash size={14} />
                </ActionIcon>
              </Group>
            ))}
            <Text size="xs" c={demasiado ? 'red.7' : 'dimmed'}>
              {tamanhoLegivel(total)} no total
              {demasiado && ' — acima de 15 MB a maior parte dos servidores recusa'}
            </Text>
          </Stack>
        )}
        <Group justify="space-between">
          <FileButton multiple onChange={(novos) => setFicheiros((l) => [...l, ...novos])}>
            {(props) => (
              <Button {...props} variant="default" leftSection={<IconPaperclip size={15} />}>
                Anexar
              </Button>
            )}
          </FileButton>
          <Button
            leftSection={<IconSend size={15} />}
            loading={enviar.isPending}
            disabled={!podeEnviar}
            onClick={() => enviar.mutate()}
          >
            Enviar
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
