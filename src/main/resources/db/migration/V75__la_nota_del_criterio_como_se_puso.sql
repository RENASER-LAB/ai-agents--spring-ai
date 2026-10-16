-- ============================================================================
-- V75 · La nota de cada criterio de la simulación y la validación, tal como se puso
-- (specs/pesos-con-candidatos-y-fin-del-prestamo.md, AC-03; hallazgo QA-01).
--
-- Esas notas van sobre el peso del criterio para el nivel del puesto (un 7 sobre 15), y al
-- cambiar la versión de pesos de una vacante con candidatos se llevan a la escala nueva
-- (4,67 sobre 10). Hasta aquí se reescribía `puntaje` y se perdía la de partida: ir y volver
-- a la versión de antes dejaba un 7,01 por el redondeo, y un criterio que pasaba por peso 0
-- se quedaba en 0 para siempre.
--
-- Desde aquí la nota que puso el facilitador y el máximo que tenía entonces no se tocan, y
-- `puntaje` se calcula siempre desde ellos: volver a la misma versión devuelve la misma
-- nota, pase por donde pase.
-- ============================================================================

ALTER TABLE nota_criterio
    ADD COLUMN puntaje_original numeric(5,2),
    ADD COLUMN maximo_original  numeric(5,2);

COMMENT ON COLUMN nota_criterio.puntaje_original IS
    'La nota tal como la puso quien califica, en la escala del máximo de entonces (V75). '
    'Solo la usan los criterios que valen su peso_criterio (simulación y validación): al '
    'cambiar los pesos de la vacante, puntaje se recalcula desde aquí y esta no cambia. '
    'Vacía en las filas de antes: el primer cambio de pesos la toma de puntaje.';

COMMENT ON COLUMN nota_criterio.maximo_original IS
    'Sobre cuánto se puso puntaje_original: el peso del criterio en la versión de la vacante '
    'de entonces (V75). Vacío si ese criterio no tenía peso para el nivel.';

-- Las que ya existen están en la escala de la versión que su vacante tiene ahora: hasta
-- la V74 la versión no podía cambiar con candidatos dentro.
UPDATE nota_criterio nc
   SET puntaje_original = nc.puntaje,
       maximo_original = (
           SELECT pc.peso
             FROM postulacion po
             JOIN vacante v ON v.id = po.vacante_id
             JOIN puesto pu ON pu.id = v.puesto_id
             JOIN peso_criterio pc ON pc.version_pesos_id = v.version_pesos_id
                                  AND pc.nivel_puesto_codigo = pu.nivel_puesto_codigo
                                  AND pc.criterio_id = nc.criterio_id
            WHERE po.id = nc.postulacion_id)
  FROM criterio c
 WHERE c.id = nc.criterio_id
   AND c.etapa_codigo IN ('SIMULACION', 'VALIDACION')
   AND c.version_plantilla_prueba_id IS NULL
   AND c.puntos IS NULL;
