import { Alert, Button, Card, Center, FileInput, Group, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { IconCircleCheck, IconCircleX, IconShieldCheck } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fmtDateTime } from '../../lib/format';

interface Verificacao {
  found: boolean;
  code?: string;
  organization?: string | null;
  kindLabel?: string | null;
  reference?: string | null;
  issuedAt?: string | null;
  issuedBy?: string | null;
  sizeBytes?: number;
  fileMatches?: boolean | null;
}

/**
 * Verificação pública de um documento pelo código do rodapé — sem conta.
 * Quem recebe a ordem, a guia ou o histórico em papel confirma aqui que foi
 * emitido por esta empresa, quando, por quem; e, carregando o PDF, que o
 * ficheiro é exatamente o que saiu do sistema.
 */
export function VerificarPage() {
  const { code: daRota } = useParams();
  const [code, setCode] = useState(daRota ?? '');
  const [resultado, setResultado] = useState<Verificacao | null>(null);
  const [ficheiro, setFicheiro] = useState<File | null>(null);
  const [aVerificar, setAVerificar] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const verificar = async (f: File | null) => {
    const c = code.trim().toUpperCase();
    if (c.length < 12) return;
    setAVerificar(true);
    setErro(null);
    try {
      let r: Response;
      if (f) {
        const form = new FormData();
        form.append('file', f);
        r = await fetch(`/api/v1/public/verify/${encodeURIComponent(c)}`, { method: 'POST', body: form });
      } else {
        r = await fetch(`/api/v1/public/verify/${encodeURIComponent(c)}`);
      }
      if (!r.ok) throw new Error('Não foi possível verificar agora. Tente de novo.');
      setResultado((await r.json()) as Verificacao);
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível verificar.');
    } finally {
      setAVerificar(false);
    }
  };

  useEffect(() => {
    if (daRota) void verificar(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [daRota]);

  return (
    <Center mih="100vh" bg="var(--mantine-color-gray-0)" p="md">
      <Stack w={520} gap="md">
        <Group gap="xs" justify="center">
          <IconShieldCheck size={26} style={{ color: '#B08D3C' }} />
          <Title order={2}>Verificar um documento</Title>
        </Group>
        <Text size="sm" c="dimmed" ta="center">
          Escreva o código de verificação que está no rodapé do documento (ordem de manutenção, guia de transporte,
          histórico). Para confirmar que o ficheiro não foi alterado, carregue também o PDF.
        </Text>
        <Text size="xs" c="dimmed" ta="center" fs="italic">
          Verify a document: enter the verification code printed in its footer; upload the PDF to confirm it was not altered.
        </Text>
        <Card p="lg" radius="md">
          <Stack gap="sm">
            <TextInput
              label="Código de verificação"
              placeholder="XXXX-XXXX-XXXX"
              value={code}
              onChange={(e) => setCode(e.currentTarget.value)}
              onKeyDown={(e) => e.key === 'Enter' && void verificar(ficheiro)}
              styles={{ input: { fontFamily: 'monospace', letterSpacing: '0.1em', textTransform: 'uppercase' } }}
              data-autofocus
            />
            <FileInput label="PDF recebido (opcional)" placeholder="Escolher o ficheiro…" accept="application/pdf" value={ficheiro} onChange={setFicheiro} />
            <Button loading={aVerificar} onClick={() => void verificar(ficheiro)} disabled={code.trim().length < 12}>
              Verificar
            </Button>
            {erro && (
              <Alert color="red" variant="light">
                {erro}
              </Alert>
            )}
            {resultado && !resultado.found && (
              <Alert color="red" variant="light" icon={<IconCircleX size={18} />}>
                <b>Este código não existe.</b> O documento não foi emitido por este sistema, ou o código está mal
                escrito.
              </Alert>
            )}
            {resultado && resultado.found && (
              <Stack gap="xs">
                <Alert color="green" variant="light" icon={<IconCircleCheck size={18} />}>
                  <b>Documento emitido por {resultado.organization}.</b>{' '}
                  <Text span size="xs" c="dimmed">
                    (Document issued by this company.)
                  </Text>
                </Alert>
                <Table>
                  <Table.Tbody>
                    <Table.Tr>
                      <Table.Td c="dimmed">Documento</Table.Td>
                      <Table.Td>
                        {resultado.kindLabel} {resultado.reference && <b>{resultado.reference}</b>}
                      </Table.Td>
                    </Table.Tr>
                    <Table.Tr>
                      <Table.Td c="dimmed">Emitido em</Table.Td>
                      <Table.Td>{fmtDateTime(resultado.issuedAt)}</Table.Td>
                    </Table.Tr>
                    <Table.Tr>
                      <Table.Td c="dimmed">Emitido por</Table.Td>
                      <Table.Td>{resultado.issuedBy ?? '—'}</Table.Td>
                    </Table.Tr>
                    <Table.Tr>
                      <Table.Td c="dimmed">Código</Table.Td>
                      <Table.Td style={{ fontFamily: 'monospace' }}>{resultado.code}</Table.Td>
                    </Table.Tr>
                  </Table.Tbody>
                </Table>
                {resultado.fileMatches === true && (
                  <Alert color="green" variant="light" icon={<IconCircleCheck size={18} />}>
                    O ficheiro carregado é <b>exatamente</b> o que foi emitido. Não foi alterado.
                  </Alert>
                )}
                {resultado.fileMatches === false && (
                  <Alert color="red" variant="light" icon={<IconCircleX size={18} />}>
                    <b>O ficheiro carregado não corresponde ao emitido.</b> Foi alterado, ou é outra impressão do mesmo
                    documento (cada impressão tem o seu código).
                  </Alert>
                )}
              </Stack>
            )}
          </Stack>
        </Card>
        <Text size="xs" c="dimmed" ta="center">
          <Link to="/">IMBONDEIRO OS</Link> · Gestão de frota e manutenção
        </Text>
      </Stack>
    </Center>
  );
}
