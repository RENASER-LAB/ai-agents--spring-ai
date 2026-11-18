-- Las pruebas incompletas, dentro del ranking y con «Darle más tiempo» (18/11/2026).
-- Ver specs/Ready/incompletas-en-el-ranking-y-mas-tiempo/spec.md.
--
-- Quien deja vencer la prueba del puesto con algo sin responder —o no la abre antes de su
-- fecha— vuelve a la tabla como «No la completó», y el equipo puede darle más tiempo desde su
-- ficha: una fecha nueva, un motivo obligatorio y, si se marca, un aviso por correo, campana y
-- WhatsApp. Esta migración prepara tres cosas:
--
--   1. Quién, cuándo y por qué se le dio más tiempo, en la propia prueba.
--   2. El correo PRUEBA_MAS_TIEMPO, en todas las organizaciones.
--   3. El aviso por WhatsApp de ese tipo, que no cuelga de ninguna transición.
--
-- Lo demás no toca el esquema: «No la completó» se calcula al leer (la marca no_completada de
-- la V67, o la fecha pasada sin abrirla), y el recordatorio previo al plazo ya se deduplica por
-- turno y fecha (V70).
--
-- ⚠️ Es la V89: la V85 está en main y la V86, la V87 y la V88 las reservan otras specs (el
-- brochure, la convocatoria y el video). Los huecos V81–V83 no se usan: Flyway no aplica fuera
-- de orden.


-- ============================================================================
-- 1 · Quién, cuándo y por qué se le dio más tiempo
-- ============================================================================
--
-- Lo último que se le dio, no un historial: el historial entero (fecha anterior, nueva, motivo
-- y quién) queda en `auditoria` con la acción dar_mas_tiempo_prueba. Estas columnas existen
-- para dos lectores que no pueden ir a la auditoría cada vez:
--
--   - la ficha, que dice «más tiempo dado el 18 nov por Ana: {motivo}»;
--   - el recordatorio previo al plazo, que con esta marca alcanza también a quien ya la había
--     empezado (antes solo miraba las pruebas sin abrir).
--
-- Dar más tiempo además borra la marca no_completada y la entrega: la prueba vuelve a estar
-- abierta, con sus respuestas y sus archivos. El CHECK de la V67 (no_completada exige
-- entregado_en) se cumple igual, porque se apagan las dos a la vez.

ALTER TABLE intento_prueba
    ADD COLUMN mas_tiempo_en timestamptz,
    ADD COLUMN mas_tiempo_por_usuario_id bigint REFERENCES usuario(id),
    ADD COLUMN mas_tiempo_motivo text;

ALTER TABLE intento_prueba ADD CONSTRAINT intento_prueba_mas_tiempo_con_motivo_check
    CHECK ((mas_tiempo_en IS NULL) = (mas_tiempo_motivo IS NULL));

COMMENT ON COLUMN intento_prueba.mas_tiempo_en IS
    'La última vez que el equipo le dio más tiempo (V89). Vacía si nunca se le dio. Es la marca '
    'de reapertura del recordatorio previo al plazo.';
COMMENT ON COLUMN intento_prueba.mas_tiempo_por_usuario_id IS
    'Quién le dio más tiempo la última vez (V89).';
COMMENT ON COLUMN intento_prueba.mas_tiempo_motivo IS
    'Por qué se le dio más tiempo la última vez (V89). Obligatorio con mas_tiempo_en.';


-- ============================================================================
-- 2 · El correo PRUEBA_MAS_TIEMPO
-- ============================================================================
--
-- En TODAS las organizaciones, como los recordatorios de la V70: ServicioCorreo.enviar no cae a
-- la plataforma cuando a una empresa le falta un texto, y un texto que no existe no se puede
-- editar desde el panel. Las empresas que se den de alta después lo copian de la plataforma.
--
-- Sin enlace con token: {{enlace}} es la dirección de su proceso, que pide su correo y su
-- contraseña (15/10/2026). {{vence}} es la fecha nueva en hora de Lima («jue 20 nov, 18:00»).

INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa, creado_en)
SELECT o.id, 'PRUEBA_MAS_TIEMPO', 1,
       'Tienes más tiempo para tu prueba de {{vacante}}',
       'Hola {{nombre}}:' || chr(10) || chr(10) ||
       'Tienes más tiempo para la prueba de {{vacante}}, hasta el {{vence}} (hora de Lima). '
           || 'Entra a Mis procesos para continuarla.' || chr(10) || chr(10) ||
       'Lo que ya habías respondido y subido sigue ahí.' || chr(10) || chr(10) ||
       'Entra desde aquí:' || chr(10) ||
       '{{enlace}}' || chr(10) || chr(10) ||
       'O entra en el portal con tu correo y tu contraseña y abre «Mis procesos».' || chr(10) || chr(10) ||
       'Si tienes cualquier duda, responde a este correo.' || chr(10) || chr(10) ||
       'Equipo de Talento — Renaser',
       true, now()
  FROM organizacion o
 WHERE NOT EXISTS (SELECT 1 FROM plantilla_correo pc
                    WHERE pc.organizacion_id = o.id AND pc.codigo = 'PRUEBA_MAS_TIEMPO');


-- ============================================================================
-- 3 · El aviso por WhatsApp sin transición
-- ============================================================================
--
-- Dar más tiempo no es una transición de estado: el turno sigue siendo el mismo. Por eso el
-- aviso PRUEBA_MAS_TIEMPO no se liga a una transición sino a la prueba y a la fecha nueva, y
-- la base impide repetirlo: dar más tiempo dos veces con la misma fecha deja un solo aviso.
--
-- Los dos tipos de siempre siguen exigiendo su transición y nada más; la clave única
-- (transicion_estado_id, tipo) de la V73 no se toca y no mira las filas sin transición.

ALTER TABLE aviso_whatsapp
    ADD COLUMN intento_prueba_id bigint REFERENCES intento_prueba(id),
    ADD COLUMN plazo_en timestamptz;

ALTER TABLE aviso_whatsapp ALTER COLUMN transicion_estado_id DROP NOT NULL;

ALTER TABLE aviso_whatsapp DROP CONSTRAINT aviso_whatsapp_tipo_check;
ALTER TABLE aviso_whatsapp ADD CONSTRAINT aviso_whatsapp_tipo_check
    CHECK (tipo IN ('PRUEBA_DISPONIBLE', 'POSTULACION_AVANZA', 'PRUEBA_MAS_TIEMPO'));

ALTER TABLE aviso_whatsapp ADD CONSTRAINT aviso_whatsapp_de_que_cuelga_check
    CHECK (CASE WHEN tipo = 'PRUEBA_MAS_TIEMPO'
                THEN transicion_estado_id IS NULL AND intento_prueba_id IS NOT NULL
                     AND plazo_en IS NOT NULL
                ELSE transicion_estado_id IS NOT NULL AND intento_prueba_id IS NULL
                     AND plazo_en IS NULL
           END);

CREATE UNIQUE INDEX aviso_whatsapp_mas_tiempo_una_vez
    ON aviso_whatsapp (intento_prueba_id, plazo_en)
    WHERE tipo = 'PRUEBA_MAS_TIEMPO';

COMMENT ON TABLE aviso_whatsapp IS
    'Los avisos «te toca» que EX manda al bot de WhatsApp (V73). Uno por transición y tipo; '
    'el de más tiempo (V89), uno por prueba y fecha nueva. El id es el avisoId, la clave de '
    'idempotencia del bot.';
COMMENT ON COLUMN aviso_whatsapp.intento_prueba_id IS
    'La prueba a la que se le dio más tiempo. Solo en PRUEBA_MAS_TIEMPO (V89).';
COMMENT ON COLUMN aviso_whatsapp.plazo_en IS
    'La fecha nueva que se avisó. Solo en PRUEBA_MAS_TIEMPO (V89).';


-- ============================================================================
-- 4 · El aviso nuevo de la campana
-- ============================================================================
-- Sin tabla nueva: `aviso_portal` guarda el tipo como texto (V56).
COMMENT ON COLUMN aviso_portal.tipo IS
    'Qué clase de noticia es. De la vacante: REMUNERACION_ACTUALIZADA (V54), VACANTE_ACTUALIZADA '
    '(V58), VACANTE_ELIMINADA (V60). De las reseñas (V63): RESENA_PUBLICADA, RESENA_EDITADA, '
    'REPORTE_RESENA_RESUELTO, RESPUESTA_RESENA_OCULTADA. De las etapas (V70), el mismo código que '
    'el correo que la acompaña: PRUEBA_DISPONIBLE, POSTULACION_AVANZA, POSTULACION_NO_CONTINUA, '
    'POSTULACION_CERRADA, RETIRO_CONFIRMADO. Los recordatorios (V70): RECORDATORIO_EVALUACION y '
    'RECORDATORIO_PRUEBA. Y el más tiempo para la prueba (V89): PRUEBA_MAS_TIEMPO. El texto de '
    'lo que pasó va en titulo y cuerpo.';
