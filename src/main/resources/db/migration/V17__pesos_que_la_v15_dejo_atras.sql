-- Los pesos que la V15 se dejó atrás.
--
-- La V15 creó la versión de pesos «v3 hito 3» y movió TODAS las vacantes a ella, pero solo
-- sembró `peso_etapa` y `peso_componente_perfil`. Se quedaron sin copiar:
--
--   peso_criterio   los ocho criterios del currículum, por nivel de puesto (24 filas en v2)
--   peso_dimension  las dimensiones del banco, por nivel                  (36 filas en v2)
--
-- La consecuencia se ve en cuanto la IA califica a alguien: sin `peso_criterio` no hay con
-- qué ponderar los ocho criterios, la nota del currículum sale nula, el componente CV se
-- ignora entero y la nota de la etapa del Perfil Integral queda en 0 aunque los ocho
-- criterios tengan puntaje. El candidato aparece con todo calificado y un cero global.
--
-- Se copian de la v2 tal cual: la V15 solo repartió de otra forma el peso ENTRE etapas,
-- no cambió lo que vale cada criterio dentro del currículum ni cada dimensión dentro del
-- banco. Copiar es lo correcto; inventar números nuevos, no.

INSERT INTO peso_criterio (version_pesos_id, nivel_puesto_codigo, criterio_id, peso)
SELECT destino.id, pc.nivel_puesto_codigo, pc.criterio_id, pc.peso
FROM peso_criterio pc
JOIN version_pesos origen  ON origen.id = pc.version_pesos_id AND origen.etiqueta = 'v2 hito 2'
JOIN version_pesos destino ON destino.organizacion_id = origen.organizacion_id
                          AND destino.etiqueta = 'v3 hito 3'
WHERE NOT EXISTS (
    SELECT 1 FROM peso_criterio ya
    WHERE ya.version_pesos_id = destino.id
      AND ya.nivel_puesto_codigo = pc.nivel_puesto_codigo
      AND ya.criterio_id = pc.criterio_id
);

INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT destino.id, pd.nivel_puesto_codigo, pd.dimension_codigo, pd.peso
FROM peso_dimension pd
JOIN version_pesos origen  ON origen.id = pd.version_pesos_id AND origen.etiqueta = 'v2 hito 2'
JOIN version_pesos destino ON destino.organizacion_id = origen.organizacion_id
                          AND destino.etiqueta = 'v3 hito 3'
WHERE NOT EXISTS (
    SELECT 1 FROM peso_dimension ya
    WHERE ya.version_pesos_id = destino.id
      AND ya.nivel_puesto_codigo = pd.nivel_puesto_codigo
      AND ya.dimension_codigo = pd.dimension_codigo
);

-- Las notas de etapa que se calcularon con la v3 incompleta valen 0 y son mentira: se
-- calcularon sin el currículum. Se borran para que se recalculen en la próxima
-- calificación, en vez de dejar un cero que nadie sabría de dónde salió.
--
-- Solo las que están en cero: una nota distinta de cero se calculó con datos y no se toca.
-- El histórico real no se pierde porque estas nunca fueron reales.
DELETE FROM nota_etapa
WHERE etapa_codigo = 'PERFIL_INTEGRAL'
  AND puntaje = 0
  AND version_pesos_id IN (SELECT id FROM version_pesos WHERE etiqueta = 'v3 hito 3');
