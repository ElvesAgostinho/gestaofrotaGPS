#!/bin/sh
# Cópias de segurança automáticas: base de dados todos os dias, ficheiros
# carregados todos os dias, e apaga as mais antigas do que BACKUP_KEEP_DAYS.
#
# Corre dentro do contentor «backup» do docker-compose, que tem o cliente do
# PostgreSQL e acesso ao volume dos ficheiros. Nunca escreve por cima de uma
# cópia do mesmo dia: o nome leva a data e a hora.
#
# O que é preciso salvaguardar são DUAS coisas: a base de dados e os ficheiros.
# Só uma delas não chega — uma ficha de ativo sem as fotografias e sem a
# apólice não repõe nada.

set -eu

DESTINO="${BACKUP_DIR:-/backups}"
MANTER="${BACKUP_KEEP_DAYS:-30}"
HORA="${BACKUP_HOUR:-02}"

mkdir -p "$DESTINO"

fazer_copia() {
    carimbo="$(date +%Y-%m-%d_%H%M)"
    echo "[$(date '+%F %T')] a copiar a base de dados…"
    # -Fc: formato próprio do PostgreSQL, comprimido, restaurável com pg_restore
    # tabela a tabela se for preciso.
    PGPASSWORD="$DATABASE_PASSWORD" pg_dump -h "${DATABASE_HOST:-db}" -U "$DATABASE_USER" -d "$DATABASE_NAME" -Fc \
        > "$DESTINO/bd-$carimbo.dump.tmp"
    mv "$DESTINO/bd-$carimbo.dump.tmp" "$DESTINO/bd-$carimbo.dump"

    echo "[$(date '+%F %T')] a copiar os ficheiros…"
    tar czf "$DESTINO/ficheiros-$carimbo.tar.gz.tmp" -C /files .
    mv "$DESTINO/ficheiros-$carimbo.tar.gz.tmp" "$DESTINO/ficheiros-$carimbo.tar.gz"

    # O Traccar (quando corre ao lado): a base dele tem os aparelhos e o historico.
    if [ -d /traccar ]; then
        echo "[$(date '+%F %T')] a copiar o Traccar…"
        tar czf "$DESTINO/traccar-$carimbo.tar.gz.tmp" -C /traccar .
        mv "$DESTINO/traccar-$carimbo.tar.gz.tmp" "$DESTINO/traccar-$carimbo.tar.gz"
    fi

    # Para fora do servidor: uma copia no mesmo disco morre com o disco.
    # BACKUP_REMOTE e um remoto do rclone (s3:bucket/pasta, b2:..., drive:...).
    if [ -n "${BACKUP_REMOTE:-}" ]; then
        if ! command -v rclone >/dev/null 2>&1; then
            echo "[$(date '+%F %T')] a instalar o rclone…"
            apk add --no-cache rclone >/dev/null 2>&1 || true
        fi
        if command -v rclone >/dev/null 2>&1; then
            echo "[$(date '+%F %T')] a enviar para $BACKUP_REMOTE…"
            rclone copy "$DESTINO" "$BACKUP_REMOTE" --include "*-$carimbo.*"                 && echo "[$(date '+%F %T')] enviado."                 || echo "[$(date '+%F %T')] AVISO: o envio para fora falhou; a copia local ficou."
        else
            echo "[$(date '+%F %T')] AVISO: sem rclone; a copia ficou so no servidor."
        fi
    fi

    echo "[$(date '+%F %T')] a apagar cópias com mais de $MANTER dias…"
    find "$DESTINO" -name 'bd-*.dump' -mtime +"$MANTER" -delete
    find "$DESTINO" -name 'ficheiros-*.tar.gz' -mtime +"$MANTER" -delete
    find "$DESTINO" -name 'traccar-*.tar.gz' -mtime +"$MANTER" -delete

    echo "[$(date '+%F %T')] feito: $(ls -1 "$DESTINO" | wc -l) ficheiros em $DESTINO"
}

# Uma cópia logo ao arrancar: a primeira instalação fica protegida no mesmo dia.
fazer_copia

# Depois, uma vez por dia à hora indicada.
while true; do
    agora="$(date +%H)"
    if [ "$agora" = "$HORA" ]; then
        fazer_copia
        # dorme mais de uma hora para não repetir dentro da mesma hora
        sleep 3900
    else
        sleep 600
    fi
done
