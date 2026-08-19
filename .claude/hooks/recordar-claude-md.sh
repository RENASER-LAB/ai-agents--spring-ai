#!/usr/bin/env bash
# Avisa cuando hay codigo o migraciones sin commitear pero CLAUDE.MD sigue igual.
#
# Por que existe: CLAUDE.MD es lo primero que lee una sesion nueva. Si miente, la
# sesion trabaja sobre una idea falsa del proyecto. Ya paso: decia «hito 1
# implementado, hitos 2 y 3 no» cuando el embudo ya estaba completo.
#
# No bloquea nada. Solo recuerda, y como mucho una vez cada 30 minutos.

set -uo pipefail

RAIZ="$(git rev-parse --show-toplevel 2>/dev/null)" || exit 0
cd "$RAIZ" || exit 0

CAMBIOS=$(git status --porcelain -- \
    'src/main/java' 'src/main/resources/db/migration' 2>/dev/null | wc -l)
[ "$CAMBIOS" -eq 0 ] && exit 0

# Si CLAUDE.MD ya se toco, no hay nada que recordar.
git status --porcelain -- CLAUDE.MD 2>/dev/null | grep -q . && exit 0

# Un aviso cada media hora basta: el hook se dispara en cada turno.
MARCA="$(git rev-parse --git-dir)/recordatorio-claude-md"
AHORA=$(date +%s)
if [ -f "$MARCA" ]; then
    ULTIMO=$(cat "$MARCA" 2>/dev/null || echo 0)
    [ $((AHORA - ULTIMO)) -lt 1800 ] && exit 0
fi
echo "$AHORA" > "$MARCA"

printf '{"systemMessage":"%s"}\n' \
    "CLAUDE.MD sin tocar y hay $CAMBIOS archivo(s) de codigo o migracion sin commitear. Si la funcionalidad ya esta terminada, actualiza la seccion «Donde estamos»."
