-- Auditoria do bloqueio, correções críticas.
--
-- Três problemas de fundo que esta migração resolve:
--
-- 1. O PROTOCOLO do aparelho não era modelado. "engineStop" não existe em todos
--    os rastreadores: depende do protocolo do fabricante. Sem saber quais os
--    comandos suportados, o sistema enviava às cegas.
--
-- 2. A CONFIRMAÇÃO era um clique humano. `CONFIRMED` só existia se alguém
--    carregasse num botão a dizer que sim — sem prova nenhuma vinda do aparelho.
--    Agora regista-se DE ONDE veio a confirmação.
--
-- 3. Não se distinguia "entregue ao aparelho" de "em fila porque está offline".
--    O Traccar responde 200 num caso e 202 no outro, e a diferença importa: uma
--    viatura pode receber o corte horas depois, quando já está noutro sítio.

-- Protocolo e comandos suportados, sincronizados a partir do Traccar.
ALTER TABLE gps_devices ADD COLUMN protocol VARCHAR(60);
-- Lista JSON dos tipos de comando que ESTE aparelho aceita, tal como o Traccar
-- os declara. Guardada em TEXT para ser portável entre H2 e PostgreSQL.
ALTER TABLE gps_devices ADD COLUMN supported_commands TEXT;
ALTER TABLE gps_devices ADD COLUMN commands_synced_at TIMESTAMP;
-- Identificador do aparelho no Traccar, para não o procurar a cada comando.
ALTER TABLE gps_devices ADD COLUMN provider_device_id VARCHAR(40);

-- De onde veio a confirmação: DEVICE_ATTRIBUTE (o aparelho reportou o estado),
-- TRACCAR_EVENT (resultado do comando) ou MANUAL (alguém afirmou, sem prova).
ALTER TABLE device_commands ADD COLUMN confirmation_source VARCHAR(20);
-- O Traccar aceitou mas o aparelho estava offline: fica em fila do lado dele.
ALTER TABLE device_commands ADD COLUMN provider_queued BOOLEAN NOT NULL DEFAULT FALSE;
-- Última vez que se foi perguntar ao Traccar se já há resposta.
ALTER TABLE device_commands ADD COLUMN last_checked_at TIMESTAMP;
-- Estado do bloqueio antes e depois, para a auditoria se poder reconstituir.
ALTER TABLE device_commands ADD COLUMN previous_lock_state VARCHAR(20);
ALTER TABLE device_commands ADD COLUMN resulting_lock_state VARCHAR(20);
-- Categoria do motivo (furto, incumprimento, manutenção...), além do texto livre.
ALTER TABLE device_commands ADD COLUMN reason_category VARCHAR(30);

CREATE INDEX ix_device_commands_awaiting ON device_commands(status, last_checked_at);
