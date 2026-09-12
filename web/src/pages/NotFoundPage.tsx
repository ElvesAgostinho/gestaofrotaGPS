import { Button, Center, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <Center py="xl">
      <Stack align="center" gap="xs">
        <Title order={1}>Página não encontrada</Title>
        <Text c="dimmed">O endereço que abriu não corresponde a nada no IMBONDEIRO OS.</Text>
        <Button component={Link} to="/" mt="md">
          Voltar ao painel
        </Button>
      </Stack>
    </Center>
  );
}
