import { Button, Modal, PasswordInput, Stack, Text } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import {
  IconDeviceMobile,
  IconDownload,
  IconId,
  IconLock,
  IconBell,
  IconLogout,
  IconTruck,
} from '@tabler/icons-react';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { avisosLigados, avisosSuportados, desligarAvisos, ligarAvisos } from './avisos';
import { useHome } from './comum';
import { BotaoGrande, Cartao, Linha, Seccao } from './pecas';
import { AMBAR, CINZA, TITULO, VERDE, VERMELHO } from './tema';

/**
 * A conta do motorista.
 *
 * <p>Pouca coisa de propósito: quem é, que viatura conduz, como trocar a
 * palavra-passe e como instalar a aplicação no ecrã principal. Um motorista
 * não configura sistemas — configura-se a si.
 */
export function MPerfilPage() {
  const { user, org, signOut } = useAuth();
  const { data } = useHome();
  const [trocar, setTrocar] = useState(false);
  const [avisos, setAvisos] = useState(avisosLigados());
  const [aLigarAvisos, setALigarAvisos] = useState(false);

  const minhas = data?.myAssets ?? [];
  const instalada =
    typeof window !== 'undefined' && window.matchMedia('(display-mode: standalone)').matches;

  return (
    <Stack gap="md">
      <div>
        <Text style={{ fontFamily: TITULO, fontSize: 26, fontWeight: 700, lineHeight: 1.1 }}>
          {user?.name}
        </Text>
        <Text size="sm" style={{ color: CINZA }}>
          {org?.name}
        </Text>
      </div>

      <TrocarSenha aberto={trocar} fechar={() => setTrocar(false)} />

      <div>
        <Seccao>A minha conta</Seccao>
        <Cartao>
          <Linha
            rotulo="Identificador"
            valor={
              <span style={{ fontFamily: 'ui-monospace, monospace', letterSpacing: '0.06em' }}>
                {user?.loginId ?? user?.email ?? '—'}
              </span>
            }
          />
          <Linha rotulo="Telemóvel" valor={user?.phone ?? '—'} />
          <div style={{ marginTop: 12 }}>
            <BotaoGrande onClick={() => setTrocar(true)} icone={<IconLock size={19} />}>
              Trocar palavra-passe
            </BotaoGrande>
          </div>
          <Text size="xs" style={{ color: CINZA }} mt={8}>
            Se a esquecer, peça ao seu gestor — ele repõe uma nova. Guarde o identificador:{' '}
            <b>{user?.loginId ?? '—'}</b> é com ele que entra.
          </Text>
        </Cartao>
      </div>

      {minhas.length > 0 && (
        <div>
          <Seccao>A minha viatura</Seccao>
          <Cartao>
            {minhas.map((a) => (
              <Linha
                key={a.id}
                rotulo={a.tag}
                valor={
                  <span>
                    <IconTruck size={14} style={{ verticalAlign: -2, marginRight: 5, color: AMBAR }} />
                    {a.plate ?? a.name}
                  </span>
                }
              />
            ))}
            <Text size="xs" style={{ color: CINZA }} mt={8}>
              Quem atribui as viaturas é o gestor. Se esta não é a sua, fale com ele.
            </Text>
          </Cartao>
        </div>
      )}

      <div>
        <Seccao>Avisos no telemóvel</Seccao>
        <Cartao destaque={!avisos}>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <IconBell size={22} style={{ color: avisos ? VERDE : AMBAR, flexShrink: 0 }} />
            <Text size="sm">
              {avisos
                ? 'Este telemóvel recebe os avisos mesmo com a aplicação fechada: rota nova, ordem atribuída, inspeção por fazer.'
                : 'Ligue os avisos para saber da rota nova ou da ordem que lhe atribuírem, mesmo sem a aplicação aberta.'}
            </Text>
          </div>
          {avisosSuportados() && (
            <div style={{ marginTop: 12 }}>
              <BotaoGrande
                onClick={async () => {
                  setALigarAvisos(true);
                  if (avisos) {
                    await desligarAvisos();
                    setAvisos(false);
                    notifications.show({ message: 'Avisos desligados neste telemóvel.', color: 'gray' });
                  } else {
                    const r = await ligarAvisos();
                    setAvisos(r.ok);
                    notifications.show({
                      message: r.ok ? 'Avisos ligados neste telemóvel.' : (r.motivo ?? 'Não foi possível.'),
                      color: r.ok ? 'green' : 'red',
                    });
                  }
                  setALigarAvisos(false);
                }}
                icone={<IconBell size={19} />}
                cor={avisos ? undefined : AMBAR}
                disabled={aLigarAvisos}
              >
                {aLigarAvisos ? 'Um momento…' : avisos ? 'Desligar avisos' : 'Ligar avisos'}
              </BotaoGrande>
            </div>
          )}
        </Cartao>
      </div>

      <div>
        <Seccao>A aplicação</Seccao>
        <Cartao>
          <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start' }}>
            <IconDeviceMobile size={22} style={{ color: AMBAR, flexShrink: 0 }} />
            <Text size="sm">
              {instalada
                ? 'A aplicação está instalada neste telemóvel. Funciona sem rede: o que registar fica guardado e sobe quando houver ligação.'
                : 'Instale no ecrã principal para abrir como uma aplicação: menu do browser → «Adicionar ao ecrã principal».'}
            </Text>
          </div>
          {!instalada && (
            <div style={{ marginTop: 12 }}>
              <BotaoGrande onClick={() => instalar()} icone={<IconDownload size={19} />}>
                Instalar no telemóvel
              </BotaoGrande>
            </div>
          )}
        </Cartao>
      </div>

      <div>
        <Seccao>Sessão</Seccao>
        <Button
          fullWidth
          size="md"
          variant="outline"
          color="red"
          leftSection={<IconLogout size={18} />}
          onClick={() => {
            void desligarAvisos().finally(signOut);
          }}
          style={{ borderColor: `${VERMELHO}66`, color: VERMELHO }}
        >
          Terminar sessão
        </Button>
        <Text size="xs" style={{ color: CINZA }} mt={6}>
          Só termine a sessão se este telemóvel deixar de ser seu — entrar de novo obriga a escrever o
          identificador e a palavra-passe.
        </Text>
      </div>

      <div style={{ display: 'flex', gap: 6, alignItems: 'center', justifyContent: 'center', opacity: 0.5 }}>
        <IconId size={13} />
        <Text size="xs">IMBONDEIRO OS · aplicação do motorista</Text>
      </div>
    </Stack>
  );
}

/** O pedido de instalação que o browser guardou, quando existe. */
function instalar() {
  const evento = (window as unknown as { __instalar?: { prompt: () => void } }).__instalar;
  if (evento) {
    evento.prompt();
  } else {
    notifications.show({
      title: 'Instalar',
      message:
        'Abra o menu do browser e escolha «Adicionar ao ecrã principal». No iPhone: botão de partilha → «Adicionar ao ecrã principal».',
      color: 'yellow',
    });
  }
}

function TrocarSenha({ aberto, fechar }: { aberto: boolean; fechar: () => void }) {
  const [atual, setAtual] = useState('');
  const [nova, setNova] = useState('');
  const pronto = atual.length > 0 && nova.length >= 8;

  const gravar = useMutation({
    mutationFn: () =>
      api('/users/me/change-password', {
        method: 'POST',
        body: { currentPassword: atual, newPassword: nova },
      }),
    onSuccess: () => {
      notifications.show({ message: 'Palavra-passe alterada.', color: 'green' });
      setAtual('');
      setNova('');
      fechar();
    },
    onError: (e: Error) => notifications.show({ message: e.message, color: 'red' }),
  });

  return (
    <Modal opened={aberto} onClose={fechar} title="Trocar palavra-passe" centered>
      <Stack gap="sm">
        <PasswordInput label="Palavra-passe actual" value={atual} onChange={(e) => setAtual(e.currentTarget.value)} size="md" />
        <PasswordInput
          label="Palavra-passe nova"
          description="Pelo menos 8 caracteres."
          value={nova}
          onChange={(e) => setNova(e.currentTarget.value)}
          size="md"
        />
        <Button size="md" disabled={!pronto} loading={gravar.isPending} onClick={() => gravar.mutate()}>
          Guardar
        </Button>
      </Stack>
    </Modal>
  );
}
