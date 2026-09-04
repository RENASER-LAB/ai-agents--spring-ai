-- Las dos versiones del cazatalentos se quedaron sin `peso_etapa`.
--
-- La V41 les sembró sus pesos de pilar —que es lo que el cliente detalló hoja por hoja— y
-- ahí paró. Ninguna migración posterior les dio el reparto ENTRE etapas, y sin él una vacante
-- apuntada a cualquiera de las dos no tiene con qué mezclar la nota del Perfil Integral con
-- la de la prueba. Se vio en la vacante de Administración: el ranking enseñaba las dos notas
-- y el ponderado vacío.
--
-- ⚠️ Lo que NO se veía, y era lo grave: la Puntuación Global de la Decisión suma las etapas
-- recorriendo estos mismos pesos. Con la tabla vacía el bucle no itera, la nota se queda en
-- cero y el código concluye que no falta ninguna etapa, así que proponía ROJO con un cero
-- sobre candidatos que tenían todas sus notas. Eso se arregló aparte, en el servicio; esto
-- es la otra mitad: darles los pesos que les faltaban.
--
-- ============================================================================
-- De dónde salen 45 y 55
-- ============================================================================
-- Los dice el cliente, textualmente y cuatro veces —en `CAZATALENTOS-sistema-de-filtro.md`
-- y en la hoja «Cálculo» de los tres libros de DIR, SUP y OPE—:
--
--     Índice combinado = (Índice RENASER × 0.45) + (Índice técnico × 0.55)
--     «La técnica pesa más porque mide si sabe hacer el trabajo. La RENASER decide si vale
--      la pena enseñárselo.»
--
-- Su prueba RENASER se guarda en la etapa PERFIL_INTEGRAL y su prueba técnica en
-- PRUEBA_PUESTO, así que el reparto se traduce directo.
--
-- ⚠️ **No se reescala el 40/30 del embudo normal, y es a propósito.** La V15 hizo eso para
-- la v3 —57.14/42.86— cuando la simulación y la validación aún no existían, y sería el
-- precedente cómodo. Pero apunta al revés: en el embudo genérico el perfil pesa MÁS que la
-- prueba, y aquí el cliente pidió lo contrario y escribió por qué. Entre un precedente
-- nuestro y una instrucción suya sobre su propio instrumento, manda la suya.
--
-- ⚠️ **Solo dos etapas, y suman 100.** El material del cazatalentos describe un embudo de
-- dos fases y no menciona la Simulación ni la Validación práctica en ningún punto. Darles
-- peso aquí sería inventarle al cliente una etapa que no pidió, y además dejaría a todo el
-- mundo sin nota global hasta rendir algo que su proceso no contempla.
INSERT INTO peso_etapa (version_pesos_id, etapa_codigo, peso)
SELECT vp.id, e.codigo, e.peso::numeric
  FROM version_pesos vp, (VALUES
    ('PERFIL_INTEGRAL', 45),
    ('PRUEBA_PUESTO',   55)
  ) AS e(codigo, peso)
 WHERE vp.etiqueta IN ('CAZATALENTOS · MICRO', 'CAZATALENTOS · MEDIA/GRANDE')
   AND NOT EXISTS (
     SELECT 1 FROM peso_etapa ya
      WHERE ya.version_pesos_id = vp.id AND ya.etapa_codigo = e.codigo);

-- ============================================================================
-- El reparto dentro del Perfil Integral
-- ============================================================================
-- Hace falta porque al publicar se comprueba que los componentes sumen exactamente lo que
-- vale la etapa; sin estas filas la versión no pasaría su propia validación.
--
-- Todo el peso va a EVALUACION porque en el cazatalentos el Perfil Integral **es** la prueba
-- RENASER: lo calcula `CalificacionCriterios` como índice de los pilares del banco. No hay
-- currículum que ponderar —el CV no se puntúa en este instrumento— ni módulo psicométrico,
-- así que los dos van a cero. Un CV en 12, copiado del embudo normal, prometería una nota
-- que aquí nadie calcula.
INSERT INTO peso_componente_perfil (version_pesos_id, componente, peso)
SELECT vp.id, c.componente, c.peso::numeric
  FROM version_pesos vp, (VALUES
    ('CV',            0),
    ('PSICOMETRICO',  0),
    ('EVALUACION',   45)
  ) AS c(componente, peso)
 WHERE vp.etiqueta IN ('CAZATALENTOS · MICRO', 'CAZATALENTOS · MEDIA/GRANDE')
   AND NOT EXISTS (
     SELECT 1 FROM peso_componente_perfil ya
      WHERE ya.version_pesos_id = vp.id AND ya.componente = c.componente);

-- ============================================================================
-- Y se publican
-- ============================================================================
-- Nacieron en BORRADOR «hasta que el banco se publique» (V41). El banco ya está, y en
-- borrador no se les puede asignar una vacante: la ficha exige una versión publicada. Con
-- sus pesos completos ya son utilizables, y publicada además queda inmutable, que es la
-- garantía que permite reconstruir una decisión vieja (RF-139).
UPDATE version_pesos
   SET estado = 'PUBLICADA',
       publicada_en = now()
 WHERE etiqueta IN ('CAZATALENTOS · MICRO', 'CAZATALENTOS · MEDIA/GRANDE')
   AND estado = 'BORRADOR';
