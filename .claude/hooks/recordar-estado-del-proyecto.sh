#!/usr/bin/env bash
# Avisa cuando hay codigo o migraciones sin commitear pero docs/ESTADO-DEL-PROYECTO.md sigue igual.
#
# Por que existe: el estado del proyecto es lo primero que lee una sesion nueva
# (CLAUDE.MD solo apunta a el). Si miente, la sesion trabaja sobre una idea falsa
# del proyecto. Ya paso: decia «hito 1 implementado, hitos 2 y 3 no» cuando el
# embudo ya estaba completo.
#
# Hasta el 06/09/2026 miraba CLAUDE.MD, que era donde vivia el estado. Desde la
# reescritura, CLAUDE.MD no guarda estado y no se toca al terminar una funcionalidad:
# lo que cambia se escribe en docs/ESTADO-DEL-PROYECTO.md.
#
# No bloquea nada. Solo recuerda, y como mucho una vez cada 30 minutos.

set -uo pipefail

RAIZ="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$RAIZ" || exit 0

CAMBIOS=$(git status --porcelain -- \
    'src/main/java' 'src/main/resources/db/migration' 2>/dev/null | wc -l)
[ "$CAMBIOS" -eq 0 ] && exit 0

# Si el estado ya se toco, no hay nada que recordar.
git status --porcelain -- docs/ESTADO-DEL-PROYECTO.md 2>/dev/null | grep -q . && exit 0

# Un aviso cada media hora basta: el hook se dispara en cada turno.
MARCA="$(git rev-parse --git-dir)/recordatorio-estado-del-proyecto"
AHORA=$(date +%s)
if [ -f "$MARCA" ]; then
    ULTIMO=$(cat "$MARCA" 2>/dev/null || echo 0)
    [ $((AHORA - ULTIMO)) -lt 1800 ] && exit 0
fi
echo "$AHORA" > "$MARCA"

printf '{"systemMessage":"%s"}\n' \
    "docs/ESTADO-DEL-PROYECTO.md sin tocar y hay $CAMBIOS archivo(s) de codigo o migracion sin commitear. Si la funcionalidad ya esta terminada, actualiza ahi «Donde estamos» y el historial (no CLAUDE.MD)."
