#!/usr/bin/env bash
# Prueba la V41 contra la base local SIN dejarla aplicada: todo dentro de una
# transacción que termina en ROLLBACK.
#
# Por qué así y no arrancando la app: la base local es compartida entre worktrees.
# Si Flyway estampa la V41 ahora, cada retoque posterior del archivo cambia su
# checksum y tumba el arranque de todos los demás. El rollback valida el SQL
# completo —sintaxis, FKs, semillas— y no deja rastro.
set -euo pipefail
cd "$(dirname "$0")/.."

{
  echo 'BEGIN;'
  cat src/main/resources/db/migration/V41__banco_cazatalentos.sql
  cat <<'SQL'
-- Comprobaciones sobre lo recién aplicado, antes de deshacerlo:
SELECT count(*) AS pilares_sembrados FROM dimension WHERE codigo LIKE 'PIL_%';
SELECT vp.etiqueta,
       count(pd.*) AS filas,
       sum(pd.peso) FILTER (WHERE pd.nivel_puesto_codigo = 'DIRECCION')   AS suma_dir,
       sum(pd.peso) FILTER (WHERE pd.nivel_puesto_codigo = 'SUPERVISION') AS suma_sup,
       sum(pd.peso) FILTER (WHERE pd.nivel_puesto_codigo = 'EJECUCION')   AS suma_ope
  FROM version_pesos vp
  JOIN peso_dimension pd ON pd.version_pesos_id = vp.id
 WHERE vp.etiqueta LIKE 'CAZATALENTOS%'
 GROUP BY vp.etiqueta ORDER BY vp.etiqueta;
-- El tipo nuevo pasa el CHECK de verdad: se inserta una ABIERTA con sus tres
-- declaraciones. Si el CHECK no la admitiera, esto revienta y el script sale con error.
WITH v AS (
    INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo,
                               etiqueta, estado, metodo_calificacion)
    SELECT id, 'NIVEL', 'DIRECCION', 'prueba-v41 (se deshace)', 'BORRADOR', 'CRITERIOS'
      FROM organizacion WHERE codigo = 'RENASER'
    RETURNING id
)
INSERT INTO pregunta (version_banco_id, codigo, tipo, enunciado, es_puntuable, orden,
                      peso, c3_esperado, c4_esperado, senal_de_cero)
SELECT id, 'PRB01', 'ABIERTA', '¿Prueba del CHECK?', true, 1, 1,
       'un dato', 'una incomodidad', 'una señal'
  FROM v
RETURNING codigo AS abierta_insertada;
ROLLBACK;
SQL
} | docker exec -i renaser-postgres psql -U postgres -d renaser_db -v ON_ERROR_STOP=1
