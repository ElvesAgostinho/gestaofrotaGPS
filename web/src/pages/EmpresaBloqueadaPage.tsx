import { Alert, Button, Card, Center, Stack, Text, Title } from '@mantine/core';
import { IconLock } from '@tabler/icons-react';
import { useAuth } from '../auth/AuthContext';

/**
 * A empresa está suspensa pela plataforma ou a licença venceu. Os dados
 * estão intactos; só o trabalho está parado até o fornecedor resolver.
 */
export function EmpresaBloqueadaPage({ motivo }: { motivo: string }) {
  const { org, user, signOut } = useAuth();
  return (
    <Center mih="100vh" bg="var(--mantine-color-gray-0)" p="md">
      <Card p="xl" radius="md" w={520}>
        <Stack gap="md" align="center">
          <IconLock size={40} style={{ color: '#B08D3C' }} />
          <Title order={2} ta="center">
            {org?.name ?? 'A sua empresa'} está sem acesso
          </Title>
          <Alert color="yellow" variant="light" w="100%">
            {motivo}
          </Alert>
          <Text size="sm" c="dimmed" ta="center">
            Os dados da empresa estão guardados e voltam a estar disponíveis assim que a
            situação for regularizada. Sessão de {user?.email}.
          </Text>
          <Button variant="default" onClick={signOut}>
            Terminar sessão
          </Button>
        </Stack>
      </Card>
    </Center>
  );
}
