import {
  Alert,
  Anchor,
  Button,
  Card,
  Center,
  Divider,
  Group,
  PasswordInput,
  Stack,
  Text,
  TextInput,
  Title,
} from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconAlertCircle } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { login, registerAccount } from '../api/client';
import { useConfigPublica } from '../lib/marca';
import { useAuth } from '../auth/AuthContext';

export function LoginPage() {
  const { refresh } = useAuth();
  const [mode, setMode] = useState<'entrar' | 'criar'>('entrar');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Em produção o registo livre está fechado: as empresas são criadas pelo
  // fornecedor. Até a resposta chegar não se mostra a opção — aparecer e
  // desaparecer é pior do que aparecer um instante depois.
  const [registoAberto, setRegistoAberto] = useState<boolean>(false);
  const cfg = useConfigPublica();
  const marca = cfg?.brand ?? null;

  useEffect(() => {
    setRegistoAberto(cfg?.registrationOpen === true);
  }, [cfg]);

  const form = useForm({
    initialValues: { name: '', identifier: '', password: '', organizationName: '' },
    validate: {
      identifier: (v) => (v.trim().length < 3 ? 'Indique o seu email ou telefone.' : null),
      password: (v) =>
        v.length < 8 ? 'A palavra-passe tem de ter pelo menos 8 caracteres.' : null,
      name: (v) => (mode === 'criar' && v.trim().length < 2 ? 'Indique o seu nome.' : null),
    },
  });

  const submit = form.onSubmit(async (values) => {
    setError(null);
    setBusy(true);
    try {
      if (mode === 'entrar') {
        await login(values.identifier.trim(), values.password);
      } else {
        await registerAccount({
          name: values.name.trim(),
          email: values.identifier.trim(),
          password: values.password,
          organizationName: values.organizationName.trim() || undefined,
        });
      }
      await refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível continuar.');
    } finally {
      setBusy(false);
    }
  });

  return (
    <Center mih="100vh" bg="var(--mantine-color-gray-0)" p="md">
      <Stack w={420} gap="lg">
        <Stack gap={4} align="center">
          {/* Chapa de identificação, como a de um equipamento: é a mesma
              marca que está no cabeçalho depois de entrar. */}
          {marca ? (
            /* Marca branca: o cliente entrou pelo domínio dele e vê a marca dele. */
            <Stack gap={6} align="center">
              {marca.logoUrl && (
                <img src={marca.logoUrl} alt={marca.name} style={{ maxHeight: 64, maxWidth: 220, objectFit: 'contain' }} />
              )}
              <Title
                order={1}
                style={{
                  fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                  fontSize: 28,
                  letterSpacing: '0.04em',
                  color: marca.color ?? '#18181b',
                  margin: 0,
                  textAlign: 'center',
                }}
              >
                {marca.name}
              </Title>
            </Stack>
          ) : (
            <div
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                border: '2px solid var(--erp-dourado)',
                background: '#000',
                padding: '4px 13px',
              }}
            >
              <Title
                order={1}
                style={{
                  fontFamily: '"Barlow Condensed", Barlow, sans-serif',
                  fontSize: 30,
                  letterSpacing: '0.06em',
                  color: '#fff',
                  margin: 0,
                  whiteSpace: 'nowrap',
                }}
              >
                IMBONDEIRO<span style={{ color: 'var(--erp-dourado)' }}> OS</span>
              </Title>
            </div>
          )}
          <Text c="dimmed" size="sm" ta="center">
            {marca ? 'Gestão de frota e manutenção' : 'Gestão de frota, manutenção e rastreamento'}
          </Text>
        </Stack>

        <Card p="lg" radius="md">
          <form onSubmit={submit}>
            <Stack gap="md">
              <Title order={2} size="h4">
                {mode === 'entrar' ? 'Entrar' : 'Criar conta e empresa'}
              </Title>

              {error && (
                <Alert color="red" icon={<IconAlertCircle size={16} />} variant="light">
                  {error}
                </Alert>
              )}

              {mode === 'criar' && (
                <TextInput
                  label="O seu nome"
                  placeholder="Marco Silva"
                  {...form.getInputProps('name')}
                />
              )}

              <TextInput
                label={mode === 'entrar' ? 'Email ou telefone' : 'Email'}
                placeholder="nome@empresa.ao"
                autoComplete="username"
                {...form.getInputProps('identifier')}
              />

              <PasswordInput
                label="Palavra-passe"
                autoComplete={mode === 'entrar' ? 'current-password' : 'new-password'}
                {...form.getInputProps('password')}
              />

              {mode === 'criar' && (
                <TextInput
                  label="Nome da empresa"
                  description="Pode mudar depois."
                  placeholder="Construções Kwanza, Lda."
                  {...form.getInputProps('organizationName')}
                />
              )}

              <Button type="submit" loading={busy} fullWidth mt="xs">
                {mode === 'entrar' ? 'Entrar' : 'Criar conta'}
              </Button>

              {registoAberto && (
                <>
                  <Divider />

                  <Group justify="center" gap={6}>
                    <Text size="sm" c="dimmed">
                      {mode === 'entrar' ? 'Ainda não tem conta?' : 'Já tem conta?'}
                    </Text>
                    <Anchor
                      size="sm"
                      onClick={() => {
                        setMode(mode === 'entrar' ? 'criar' : 'entrar');
                        setError(null);
                      }}
                    >
                      {mode === 'entrar' ? 'Criar empresa' : 'Entrar'}
                    </Anchor>
                  </Group>
                </>
              )}
            </Stack>
          </form>
        </Card>

        <Text size="xs" c="dimmed" ta="center">
          Foi convidado por uma empresa? Abra o link que recebeu para entrar na equipa.
          {!registoAberto && ' As contas das empresas são criadas pelo fornecedor do sistema.'}
        </Text>
      </Stack>
    </Center>
  );
}
