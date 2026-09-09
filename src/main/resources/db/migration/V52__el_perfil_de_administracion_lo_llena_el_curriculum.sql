-- El perfil integral de administración vuelve a tener quien lo llene (09/09/2026).
--
-- ============================================================================
-- Qué estaba roto
-- ============================================================================
-- La V50 le dio a Administrador y Asistente Administrativo un perfil integral que pesa 45, y
-- lo puso entero en el componente EVALUACION porque en el cazatalentos ese 45 **es** la prueba
-- RENASER del banco de preguntas. Pero esas dos vacantes tienen la evaluación APAGADA
-- (`aplica_evaluacion = false`, la columna que creó la V30): quien postula no recibe banco, no
-- se le crea evaluación, y ese 45 se queda sin nadie que pueda llenarlo.
--
-- El resultado no fue una nota vacía, que sería lo honesto, sino **un cero**. Al combinar,
-- `PuenteCalificacionIaImpl.recalcularNotaDeLaEtapa` descarta el CV por pesar 0 y la evaluación
-- por no existir; se queda sin ningún componente con peso y guarda `BigDecimal.ZERO`. El 08/09
-- una criba rápida escribió ese cero a once candidatos de Administrador —los once en menos de
-- veinte segundos, la tanda entera de quienes llegaron con el banco ya apagado—.
--
-- Y el cero no se queda en una columna. `grupoDe` clasifica por la nota, así que los once
-- quedaron en NO_PRIORIZADO, y el ranking ordena **primero por grupo**: los mejores currículums
-- de la convocatoria terminaron por debajo de gente de agosto con notas de 16. Sus ocho notas
-- de criterio, su retrato y sus hallazgos estaban guardados todo el tiempo. Lo único que faltaba
-- era un componente con peso donde apoyarlos.
--
-- ============================================================================
-- Qué se decide aquí, y qué no
-- ============================================================================
-- Se decide que en estas vacantes **el 45 del perfil integral lo llena el currículum**. Es la
-- única de las dos salidas que rescata a quien ya está en el proceso: la otra —encender el banco
-- y ser fiel al 45 % «RENASER» de la V50— no alcanza a nadie que ya haya postulado, porque la
-- evaluación solo se crea al postular y `reabrirEvaluacion` se niega cuando no hay ninguna.
-- Habría que pedirle a once personas que rindan un examen nuevo a mitad de proceso.
--
-- No se toca el 45/55 entre etapas: el reparto que el cliente pidió sigue igual. Lo que cambia
-- es de dónde sale ese 45 dentro del perfil integral.
--
-- ============================================================================
-- Cómo se identifica la versión, y por qué NO por su id ni por el título
-- ============================================================================
-- En producción es la 5. Aquí no se escribe ese 5, por lo mismo que lo evitó la V50: los ids de
-- `version_pesos` son de identidad y dependen del orden en que cada base los creó.
--
-- Tampoco se busca por el título de la vacante. «Administrador de sedes» empieza igual y es otra
-- cosa; los tests de integración crean vacantes llamadas «Administrador» por API.
--
-- Se busca **la contradicción misma**: una versión cuyo perfil integral pesa algo y lo pone
-- entero en el banco, usada por una vacante que no administra el banco. Eso no describe una
-- configuración sana, así que donde no exista este problema esto no toca absolutamente nada
-- —incluida cualquier base recién migrada, y las de los tests—.
--
-- ⚠️ Y con una guarda que vale más que la consulta que hicimos a mano: **si alguna vacante de
-- esa versión SÍ rinde el banco, la versión no se toca**. Su perfil integral sí tiene quien lo
-- llene, y moverle el peso al currículum le cambiaría la nota a un candidato que respondió el
-- examen. Los pesos son de la versión, no de la vacante —la clave primaria de
-- `peso_componente_perfil` es (version_pesos_id, componente)—, así que esta guarda es lo que
-- garantiza que el arreglo no se salga de donde tiene que quedarse.
CREATE TEMPORARY TABLE la_contradictoria ON COMMIT DROP AS
SELECT DISTINCT v.version_pesos_id AS id, pe.peso AS peso_etapa
  FROM vacante v
  JOIN peso_etapa pe
    ON pe.version_pesos_id = v.version_pesos_id
   AND pe.etapa_codigo = 'PERFIL_INTEGRAL'
   AND pe.peso > 0
  JOIN peso_componente_perfil cv
    ON cv.version_pesos_id = v.version_pesos_id
   AND cv.componente = 'CV'
   AND cv.peso = 0
  JOIN peso_componente_perfil ev
    ON ev.version_pesos_id = v.version_pesos_id
   AND ev.componente = 'EVALUACION'
   AND ev.peso = pe.peso
 WHERE v.aplica_evaluacion = false
   AND NOT EXISTS (SELECT 1
                     FROM vacante otra
                    WHERE otra.version_pesos_id = v.version_pesos_id
                      AND otra.aplica_evaluacion);

-- ---------------------------------------------------------------------------
-- 1. El peso pasa del banco al currículum
-- ---------------------------------------------------------------------------
-- La suma se conserva: lo que valía la evaluación pasa entero al currículum, así que los
-- componentes siguen sumando exactamente el peso de la etapa. Es la invariante que
-- `ServicioPesosImpl.validarSumas` exige al publicar, y romperla dejaría la versión sin poder
-- volver a publicarse nunca.
UPDATE peso_componente_perfil c
   SET peso = a.peso_etapa
  FROM la_contradictoria a
 WHERE c.version_pesos_id = a.id
   AND c.componente = 'CV';

UPDATE peso_componente_perfil c
   SET peso = 0
  FROM la_contradictoria a
 WHERE c.version_pesos_id = a.id
   AND c.componente = 'EVALUACION';

-- ---------------------------------------------------------------------------
-- 2. Las notas que se escribieron en cero se rehacen desde lo que ya está guardado
-- ---------------------------------------------------------------------------
-- La misma cuenta que hace `notaCurriculum`: los puntajes por criterio ponderados con
-- `peso_criterio` del nivel del puesto, normalizando por los pesos que de verdad tienen nota.
-- No se llama al modelo: los ocho criterios ya estaban calificados antes de que la nota se
-- redondeara a cero.
--
-- ⚠️ Tres filtros, y ninguno sobra:
--   * `puntaje = 0` — un cero es lo único que este fallo pudo escribir aquí, y no rehacemos
--     ninguna nota que alguien pueda haber usado para decidir.
--   * `ne.version_pesos_id` en la versión corregida — las notas viejas conservan la versión con
--     la que se calcularon (RF-139) y no se recalculan hacia atrás.
--   * `v.version_pesos_id` en la versión corregida — la vacante tiene que seguir apuntando ahí.
CREATE TEMPORARY TABLE recalculadas ON COMMIT DROP AS
SELECT ne.postulacion_id,
       round(sum(nc.puntaje * pc.peso) / sum(pc.peso), 2) AS nota
  FROM nota_etapa ne
  JOIN postulacion p  ON p.id  = ne.postulacion_id
  JOIN vacante     v  ON v.id  = p.vacante_id
  JOIN puesto      pu ON pu.id = v.puesto_id
  JOIN nota_criterio nc ON nc.postulacion_id = p.id
  JOIN criterio      c  ON c.id = nc.criterio_id
                       AND c.etapa_codigo = 'PERFIL_INTEGRAL'
                       AND c.version_plantilla_prueba_id IS NULL
  JOIN peso_criterio pc ON pc.criterio_id         = nc.criterio_id
                       AND pc.version_pesos_id    = v.version_pesos_id
                       AND pc.nivel_puesto_codigo = pu.nivel_puesto_codigo
 WHERE ne.etapa_codigo = 'PERFIL_INTEGRAL'
   AND ne.puntaje = 0
   AND ne.version_pesos_id IN (SELECT id FROM la_contradictoria)
   AND v.version_pesos_id  IN (SELECT id FROM la_contradictoria)
 GROUP BY ne.postulacion_id
HAVING sum(pc.peso) > 0;

UPDATE nota_etapa ne
   SET puntaje = r.nota,
       calculada_en = now()
  FROM recalculadas r
 WHERE ne.postulacion_id = r.postulacion_id
   AND ne.etapa_codigo = 'PERFIL_INTEGRAL';

-- ---------------------------------------------------------------------------
-- 3. Y su grupo de prioridad, que es lo que de verdad los tenía enterrados
-- ---------------------------------------------------------------------------
-- Sin este paso el arreglo no se ve: `ServicioPerfilIntegralPanelImpl` ordena el ranking
-- **primero por grupo** y solo después por nota, así que once notas nuevas en el grupo
-- equivocado siguen saliendo al fondo de la lista.
--
-- La regla es la de `grupoDe`, repetida aquí porque no hay forma de llamarla desde SQL:
-- ALTA si llega al umbral y no tiene ningún riesgo crítico; si no, PRIORIZADO por nota o por
-- potencial alto; si no, NO_PRIORIZADO. Los umbrales se leen de `parametro` por organización,
-- con el mismo valor por defecto que el código pasa a `parametros.entero(...)` cuando la fila
-- no existe — 80 y 65. Escribirlos aquí a secas sería inventar una regla paralela.
UPDATE postulacion p
   SET grupo_prioridad = CASE
         WHEN r.nota >= umbrales.alta AND NOT umbrales.tiene_riesgo THEN 'ALTA'
         WHEN r.nota >= umbrales.priorizado
           OR coalesce(umbrales.potencial, 0) >= umbrales.alta        THEN 'POTENCIAL_CON_RIESGO'
         ELSE 'NO_PRIORIZADO'
       END
  FROM recalculadas r
  JOIN LATERAL (
        SELECT
          coalesce((SELECT pa.valor::numeric FROM parametro pa
                     WHERE pa.organizacion_id = po.organizacion_id
                       AND pa.codigo = 'umbral_grupo_alta'), 80)       AS alta,
          coalesce((SELECT pa.valor::numeric FROM parametro pa
                     WHERE pa.organizacion_id = po.organizacion_id
                       AND pa.codigo = 'umbral_grupo_priorizado'), 65) AS priorizado,
          pt.potencial                                                 AS potencial,
          EXISTS (SELECT 1 FROM hallazgo_perfil h
                   WHERE h.perfil_talento_id = pt.id
                     AND h.tipo = 'RIESGO_CRITICO')                    AS tiene_riesgo
          FROM postulacion po
          LEFT JOIN perfil_talento pt ON pt.postulacion_id = po.id
         WHERE po.id = r.postulacion_id
       ) umbrales ON true
 WHERE p.id = r.postulacion_id;
