-- Notificaciones del candidato: un solo ajuste para WhatsApp, correo y portal (24/11/2026).
-- Ver specs/Ready/notificaciones-del-candidato/spec.md.
--
-- Hasta ahora WhatsApp se activaba proceso por proceso con su tarjeta, y el correo y la campana
-- no se podían apagar. Desde aquí hay un botón «Notificaciones» en «Mis procesos» con tres
-- interruptores para toda la cuenta. Esta migración prepara cinco cosas:
--
--   1. Las preferencias de cada persona: correo, portal, WhatsApp y el mensaje flotante.
--   2. El relleno: quien ya tenía un proceso vinculado empieza con WhatsApp encendido.
--   3. El texto de consentimiento WHATSAPP_CUENTA 1.0, que vale para todos sus procesos.
--   4. Un celular es de una sola cuenta.
--   5. El anuncio del número sin ninguna postulación viva detrás.
--
-- ⚠️ Es la V92: la V90 es del seguimiento de pendientes y la V91 del video de la empresa.
--
-- ⚠️ El punto 4 FALLA si en la base hay dos personas con el mismo celular, y es a propósito: la
-- migración no decide de quién es un número. Antes de desplegar se cuentan y se resuelven a mano:
--   SELECT telefono, count(*) FROM persona WHERE telefono IS NOT NULL GROUP BY 1 HAVING count(*) > 1;


-- ============================================================================
-- 1 · Las preferencias de cada persona
-- ============================================================================
--
-- Una fila por persona, solo cuando cambia algo: sin fila vale todo por defecto (correo y portal
-- encendidos, WhatsApp apagado y el mensaje sin mostrar).
--
--   correo            los correos de sus procesos (cambio de etapa, postulación recibida,
--                     simulación, recordatorios y «más tiempo»). Los de la cuenta —crearla,
--                     recuperar la clave, el borrado— salen siempre.
--   portal            los avisos de sus procesos en la campana.
--   whatsapp_activo   el interruptor de WhatsApp: vale para los procesos de hoy y los que vengan.
--                     Lo apagan el propio candidato, la BAJA escrita al bot y el cambio de celular.
--   mensaje_vistas    cuántas veces se pintó el mensaje flotante que invita a activar WhatsApp
--                     (sale como mucho dos veces; la BAJA lo devuelve a cero).
--   mensaje_visto_en  la última vez que se pintó: vuelve a salir solo tras otra postulación.

CREATE TABLE preferencia_aviso (
    persona_id          bigint PRIMARY KEY REFERENCES persona(id) ON DELETE CASCADE,
    correo              boolean NOT NULL DEFAULT true,
    portal              boolean NOT NULL DEFAULT true,
    whatsapp_activo     boolean NOT NULL DEFAULT false,
    mensaje_vistas      smallint NOT NULL DEFAULT 0
        CONSTRAINT preferencia_aviso_mensaje_vistas_check CHECK (mensaje_vistas >= 0),
    mensaje_visto_en    timestamptz,
    actualizado_en      timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE preferencia_aviso IS
    'Cómo quiere cada candidato enterarse de sus procesos (V92): correo, campana del portal y '
    'WhatsApp, más el contador del mensaje flotante. Sin fila, todo por defecto.';
COMMENT ON COLUMN preferencia_aviso.whatsapp_activo IS
    'El interruptor de WhatsApp de la cuenta: vale para todos sus procesos, los de hoy y los que '
    'vengan. Lo apagan el candidato, la BAJA y el cambio de celular.';


-- ============================================================================
-- 2 · El relleno
-- ============================================================================
--
-- Los vínculos que ya existían proceso por proceso se respetan tal cual. Quien tiene al menos uno
-- ACTIVO empieza con el interruptor encendido: ya escribió al bot y recibe sus novedades.

INSERT INTO preferencia_aviso (persona_id, whatsapp_activo)
SELECT DISTINCT v.persona_id, true
  FROM vinculo_whatsapp v
 WHERE v.estado = 'ACTIVO'
ON CONFLICT (persona_id) DO NOTHING;


-- ============================================================================
-- 3 · El consentimiento WHATSAPP_CUENTA 1.0
-- ============================================================================
--
-- El de WHATSAPP 1.0 (V73) era de UN proceso y se firmaba al pulsar «Escribir por WhatsApp»; no se
-- toca, y quien lo firmó queda ligado a él. Este vale para toda la cuenta y se firma al encender el
-- interruptor, una vez por persona y versión. Es literalmente lo que la ventana pinta bajo el
-- interruptor: lo firmado tiene que ser lo leído. Solo para la plataforma, como el de WhatsApp.

ALTER TABLE texto_consentimiento DROP CONSTRAINT IF EXISTS texto_consentimiento_tipo_check;
ALTER TABLE texto_consentimiento ADD CONSTRAINT texto_consentimiento_tipo_check
    CHECK (tipo IN ('PLATAFORMA', 'PROCESO', 'FUTUROS_CONTACTOS', 'WHATSAPP', 'WHATSAPP_CUENTA'));

COMMENT ON COLUMN texto_consentimiento.tipo IS
    'PLATAFORMA = lo que se acepta con Renaser al crear la cuenta; PROCESO = lo que se acepta '
    'con LA EMPRESA de la vacante al postular; FUTUROS_CONTACTOS = el permiso opcional para '
    'avisar de otras vacantes; WHATSAPP = recibir por WhatsApp las novedades de un proceso (V73); '
    'WHATSAPP_CUENTA = recibir por WhatsApp las novedades de todos sus procesos, que se firma al '
    'encender el interruptor de «Notificaciones» (V92). Todos de la plataforma.';

INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
SELECT o.id, 'WHATSAPP_CUENTA', '1.0', t.texto, encode(digest(t.texto, 'sha256'), 'hex'), now()
FROM organizacion o,
     (VALUES ('Al activarlo aceptas recibir por WhatsApp las novedades de tus procesos. Escribe BAJA cuando quieras dejarlo.')) AS t(texto)
WHERE o.es_plataforma
  AND NOT EXISTS (
    SELECT 1 FROM texto_consentimiento ya
     WHERE ya.organizacion_id = o.id AND ya.tipo = 'WHATSAPP_CUENTA');


-- ============================================================================
-- 4 · Un celular, una cuenta
-- ============================================================================
--
-- Con WhatsApp por cuenta, un número compartido mezclaría los procesos de dos personas en el mismo
-- chat. El portal responde 409 TELEFONO_EN_USO antes de llegar aquí; el índice es la red debajo,
-- para dos altas a la vez con el mismo número. Los nulos no cuentan: las cuentas anteriores a la
-- V73 y las borradas no tienen celular.

CREATE UNIQUE INDEX persona_telefono_unico
    ON persona (telefono)
    WHERE telefono IS NOT NULL;


-- ============================================================================
-- 5 · El anuncio sin postulación
-- ============================================================================
--
-- Quien enciende WhatsApp con un número sin comprobar y sin ningún proceso vivo no tiene nada que
-- anunciar, pero tiene que poder escribir desde él para comprobarlo. Se anuncia el número con una
-- referencia opaca y sin postulación detrás; cuando escribe, /vinculos lo cierra como un proceso
-- terminado y contesta «no tienes procesos activos», como siempre. Un vínculo así nunca llega a
-- ACTIVO: no hay de qué contarle novedades.

ALTER TABLE vinculo_whatsapp ALTER COLUMN postulacion_id DROP NOT NULL;
ALTER TABLE vinculo_whatsapp ADD CONSTRAINT vinculo_whatsapp_activo_con_postulacion_check
    CHECK (estado <> 'ACTIVO' OR postulacion_id IS NOT NULL);

COMMENT ON COLUMN vinculo_whatsapp.postulacion_id IS
    'La postulación vinculada. Nula solo en el anuncio de un número sin procesos vivos (V92), que '
    'nunca pasa a ACTIVO.';
