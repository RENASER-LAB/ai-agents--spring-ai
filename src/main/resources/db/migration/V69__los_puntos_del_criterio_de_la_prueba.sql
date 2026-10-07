-- Los puntos del criterio de la prueba técnica (revisión del 06/10/2026).
--
-- Hasta aquí, quien armaba la prueba escribía la PARTE CALIFICADA de cada criterio (lo que
-- califican la IA o una persona) y el criterio valía sus cerradas más esa parte. Desde aquí
-- escribe lo que VALE EL CRITERIO entero, y la parte calificada se deduce:
--
--     parte calificada = puntos del criterio − lo que suman sus cerradas
--
-- Por eso el total se guarda: si luego cambian los puntos de una cerrada, se añade o se
-- quita una cerrada, o una pregunta cambia de criterio, el criterio sigue valiendo lo mismo
-- y lo que se mueve es su parte calificada. Si las cerradas pasan del total, la parte
-- calificada queda en 0 y el servidor lo dice como falta al publicar.
--
-- La calificación no cambia: lo que recibe la IA, la red de seguridad, el ajuste a mano y la
-- recalificación leen la misma parte calificada de siempre, solo que deducida.
--
-- ⚠️ Las versiones publicadas (o archivadas) NO se tocan: quedan con el total vacío y siguen
-- leyendo `puntos_calificados` tal cual, así que sus notas no cambian (AC-22). Solo se
-- convierten los borradores, que ningún candidato rinde.

ALTER TABLE criterio_banco
    ADD COLUMN puntos_del_criterio integer
        CHECK (puntos_del_criterio IS NULL OR puntos_del_criterio >= 0);

COMMENT ON COLUMN criterio_banco.puntos_del_criterio IS
    'Solo en la prueba técnica (V69): lo que vale el criterio entero. Su parte calificada es '
    'este total menos lo que suman sus cerradas. NULL = de antes de la V69 (una versión '
    'publicada), que lee puntos_calificados tal cual; en el Perfil Integral no se usa.';

-- Los borradores abiertos: el total es lo que valían, sus cerradas más su parte calificada.
UPDATE criterio_banco c
   SET puntos_del_criterio = COALESCE(c.puntos_calificados, 0)
       + COALESCE((SELECT SUM(p.puntos)
                     FROM pregunta p
                    WHERE p.criterio_banco_id = c.id
                      AND p.tipo <> 'ABIERTA'), 0)
  FROM version_banco v
 WHERE c.version_banco_id = v.id
   AND v.proposito = 'PRUEBA_PUESTO'
   AND v.estado = 'BORRADOR';
