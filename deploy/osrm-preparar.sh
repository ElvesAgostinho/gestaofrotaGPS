#!/bin/sh
# Prepara o mapa de Angola para o motor de rotas (OSRM).
#
# Corre uma vez, no contentor «osrm-preparar»: descarrega o mapa do
# OpenStreetMap (Geofabrik), extrai a rede rodoviária e prepara os índices.
# Demora alguns minutos e precisa de ~2 GB de memória durante a preparação.
# Depois disso o motor «osrm» arranca em segundos e responde sem Internet.
#
# Sem este passo o motor não tem mapa: fica de pé mas responde «NoRoute», e o
# teste nas Configurações reprova-o de propósito.

set -eu

cd /data
if [ -f angola-latest.osrm.mldgr ] || [ -f angola-latest.osrm.partition ]; then
    echo "Mapa já preparado; nada a fazer. Apague /data para refazer."
    exit 0
fi

if [ ! -f angola-latest.osm.pbf ]; then
    echo "A descarregar o mapa de Angola…"
    wget -q --show-progress -O angola-latest.osm.pbf \
        https://download.geofabrik.de/africa/angola-latest.osm.pbf
fi

echo "A extrair a rede rodoviária (perfil: automóvel)…"
osrm-extract -p /opt/car.lua angola-latest.osm.pbf

echo "A preparar os índices…"
osrm-partition angola-latest.osrm
osrm-customize angola-latest.osrm

echo "Pronto. O motor pode arrancar."
