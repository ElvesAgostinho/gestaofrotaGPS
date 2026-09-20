/**
 * O primeiro acesso de quem recebeu a palavra-passe de outra pessoa.
 *
 * <p>O gestor cria a conta do motorista e escreve-lhe a palavra-passe num
 * papel. A partir do momento em que ele entra, essa palavra-passe deixa de
 * poder ser a dele: este ecrã aparece por cima de tudo e não deixa avançar
 * sem uma nova. Não é burocracia — é o que separa «a conta do Joaquim» de
 * «a conta que o escritório também sabe abrir».
 */
import { Alert, Button, Paper, PasswordInput, Stack, Text, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { IconLock } from '@tabler/icons-react';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api/client';
import { useAuth } from './AuthContext';

const AMBAR = '#FFC62F';
const PRETO = '#141416';

export function TrocarPalavraPasse() {
  const { user, refresh } = useAuth();
  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const [confirmar, setConfirmar] = useState('');

  const curta = nova.length > 0 && nova.length < 8;
  const diferentes = confirmar.length > 0 && nova !== confirmar;
  const pronto = atual.length > 0 && nova.length >= 8 && nova === confirmar;

  const gravar = useMutation({
    mutationFn: () =>
      api('/users/me/change-password', {
        method: 'POST',
        body: { currentPassword: atual, newPassword: nova },
      }),
    onSuccess: async () => {
      notifications.show({ message: 'Palavra-passe alterada. Bom trabalho.', color: 'green' });
      await refresh();
    },
    onError: (e: Error) =>
      notifications.show({ title: 'Não foi possível alterar', message: e.message, color: 'red' }),
  });

  return (
    <div
      style={{
        minHeight: '100dvh',
        background: PRETO,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 16,
      }}
    >
      <Paper radius="md" p="lg" style={{ width: '100%', maxWidth: 420 }}>
        <Stack gap="sm">
          <div
            style={{
              width: 42,
              height: 42,
              borderRadius: '50%',
              background: AMBAR,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <IconLock size={22} color={PRETO} />
          </div>
          <Title order={3}>Escolha a sua palavra-passe</Title>
          <Text size="sm" c="dimmed">
            Olá, {user?.name?.split(' ')[0] ?? ''}. A palavra-passe que recebeu foi criada pelo seu
            gestor. Escolha agora uma que só o senhor conheça — é com ela que passa a entrar.
          </Text>

          <PasswordInput
            label="Palavra-passe que recebeu"
            value={atual}
            onChange={(e) => setAtual(e.currentTarget.value)}
            size="md"
            autoComplete="current-password"
          />
          <PasswordInput
            label="Palavra-passe nova"
            description="Pelo menos 8 caracteres."
            value={nova}
            onChange={(e) => setNova(e.currentTarget.value)}
            error={curta ? 'Muito curta: precisa de 8 ou mais.' : undefined}
            size="md"
            autoComplete="new-password"
          />
          <PasswordInput
            label="Repita a palavra-passe nova"
            value={confirmar}
            onChange={(e) => setConfirmar(e.currentTarget.value)}
            error={diferentes ? 'As duas não são iguais.' : undefined}
            size="md"
            autoComplete="new-password"
          />

          <Alert variant="light" color="gray" p="xs">
            <Text size="xs">
              Se a esquecer, peça ao seu gestor — ele repõe uma nova. Ninguém, nem ele, consegue ver
              a que o senhor escolher.
            </Text>
          </Alert>

          <Button
            size="md"
            disabled={!pronto}
            loading={gravar.isPending}
            onClick={() => gravar.mutate()}
          >
            Guardar e continuar
          </Button>
        </Stack>
      </Paper>
    </div>
  );
}
