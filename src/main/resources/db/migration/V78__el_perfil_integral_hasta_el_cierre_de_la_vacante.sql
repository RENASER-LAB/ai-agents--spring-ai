-- El Perfil Integral se responde mientras la vacante esté abierta (10/11/2026).
-- Ver specs/Ready/perfil-integral-hasta-el-cierre-de-la-vacante/spec.md, «Datos y compatibilidad».
--
-- Hasta aquí la evaluación del Perfil Integral vencía a los 14 días de postular
-- (dias_plazo_evaluacion) y el sondeo cerraba la postulación con PLAZO_VENCIDO aunque la vacante
-- siguiera recibiendo gente. Desde ahora nace sin fecha y la vence el cierre de la vacante, a mano
-- o por su fecha límite. Y «Cómo se cierra» tiene dos formas: Permanente y Fecha límite, que
-- cierra sola la vacante a las 23:59:59 de Lima del día elegido.
--
-- Ninguna columna nueva: vacante.cierra_en pasa a mandar cuando tipo_cierre = 'FECHA'. La
-- restricción de tipo_cierre se queda como está —sigue admitiendo 'PLAZAS'— para no tener que
-- reescribirla; ninguna vacante nueva la guarda y aquí se pasan a Permanente las que había.
--
-- ⚠️ Lo que esta migración NO hace, a propósito: vencer las evaluaciones que siguen abiertas en
-- vacantes ya cerradas. A esas personas se les cierra la postulación «con los avisos de siempre»
-- —el correo, la campana y, con WhatsApp v2, el resultado— y eso solo lo sabe hacer la
-- aplicación, por la máquina de estados. Lo hace el sondeo de vencimientos en su primera vuelta
-- tras desplegar (FinDelPerfilIntegral): las encuentra en una vacante CERRADA, las pasa a VENCIDA
-- con el cierre de la vacante como límite y cierra con PLAZO_VENCIDO a quien seguía esperándolas.
--
-- Tampoco reabre las postulaciones ya cerradas por PLAZO_VENCIDO: ya recibieron su correo, y su
-- historial se conserva tal cual.


-- ============================================================================
-- 1 · «Por plazas» pasa a Permanente
-- ============================================================================
-- Las plazas que tuvieran se quedan en su columna: nada las lee ya, y borrarlas sería perder el
-- dato sin ganar nada.

UPDATE vacante SET tipo_cierre = 'PERMANENTE' WHERE tipo_cierre = 'PLAZAS';


-- ============================================================================
-- 2 · Las fechas de cierre, a las 23:59:59 de Lima de su día
-- ============================================================================
-- El panel mandaba el día como su medianoche UTC (new Date('AAAA-MM-DD')), que en Lima son las
-- 19:00 del día anterior: con la fecha cerrando de verdad, la vacante se cerraría cinco horas
-- antes de lo que eligió el equipo. La medianoche UTC exacta es, por eso, ese día UTC; cualquier
-- otra hora (una fecha escrita por la API), el día de Lima en que cae. Es la misma regla que
-- aplica desde hoy el servidor al guardar (FormaDeCierre).

UPDATE vacante
   SET cierra_en = ((CASE WHEN (cierra_en AT TIME ZONE 'UTC')::time = time '00:00:00'
                          THEN (cierra_en AT TIME ZONE 'UTC')::date
                          ELSE (cierra_en AT TIME ZONE 'America/Lima')::date
                     END + time '23:59:59') AT TIME ZONE 'America/Lima')
 WHERE cierra_en IS NOT NULL;


-- ============================================================================
-- 3 · Publicadas con la fecha ya pasada (o sin fecha): a Permanente
-- ============================================================================
-- Para que no se cierren solas en el primer minuto tras desplegar: hasta hoy esa fecha no hacía
-- nada y nadie la miraba. El equipo les pone otra si la quiere. Una en borrador no se cierra sola
-- y se queda como está: al publicarla se le exige una fecha que no haya pasado.

UPDATE vacante
   SET tipo_cierre = 'PERMANENTE'
 WHERE estado = 'PUBLICADA'
   AND tipo_cierre = 'FECHA'
   AND (cierra_en IS NULL OR cierra_en < now());


-- ============================================================================
-- 4 · El Perfil Integral abierto en vacantes publicadas pierde sus 14 días
-- ============================================================================
-- Queda abierto hasta que la vacante se cierre. No se le avisa a nadie: solo gana tiempo. El
-- cuestionario técnico CAZATALENTOS (proposito = 'CUESTIONARIO_TECNICO') no cambia: sigue con
-- sus días y su cronómetro. El trigger de la V77 fecha el cambio en el contexto de WhatsApp,
-- que es lo que tiene que pasar: la fecha de entrega que contaba ya no existe.

UPDATE evaluacion e
   SET vence_en = NULL
  FROM postulacion p
  JOIN vacante v ON v.id = p.vacante_id
 WHERE p.evaluacion_id = e.id
   AND e.proposito = 'PERFIL_INTEGRAL'
   AND e.estado IN ('PENDIENTE', 'EN_CURSO')
   AND e.vence_en IS NOT NULL
   AND v.estado = 'PUBLICADA'
   AND v.eliminada_en IS NULL;


-- ============================================================================
-- 5 · Lo que dicen las columnas y el parámetro
-- ============================================================================

UPDATE parametro
   SET descripcion = 'Cuantos dias tiene el candidato para responder el cuestionario tecnico '
                     || '(CAZATALENTOS) desde que se le habilita. El Perfil Integral no lo usa: '
                     || 'dura hasta que la vacante se cierra'
 WHERE codigo = 'dias_plazo_evaluacion';

COMMENT ON COLUMN vacante.tipo_cierre IS
    'Cómo se cierra: PERMANENTE (la cierra alguien del equipo) o FECHA (se cierra sola en '
    'cierra_en). PLAZAS ya no se guarda (V78): la restricción la admite por las filas de antes.';

COMMENT ON COLUMN vacante.cierra_en IS
    'Con tipo_cierre = FECHA, cuándo se cierra sola: las 23:59:59 de Lima del día elegido (V78). '
    'El sondeo de vencimientos la cierra como mucho un minuto después, con el sistema como autor, '
    'y vence el Perfil Integral sin terminar. Con PERMANENTE no rige nada.';
