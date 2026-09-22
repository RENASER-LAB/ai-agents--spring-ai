-- Una vacante que no debió existir se retira, sin perder lo que pasó dentro (21/09/2026).
--
-- No es archivar y no se parece. Archivar guarda una convocatoria que terminó bien: su
-- proceso se sigue consultando en «Archivadas» y vuelve cuando haga falta. Eliminar es para
-- la que se creó por error —el puesto equivocado, la empresa equivocada, duplicada—: deja de
-- consultarse en el panel y en el portal, sus postulantes en carrera se cierran y se les
-- avisa, y su solicitud de talento vuelve a estar libre para respaldar la vacante correcta.
--
-- ⚠️ **Y aun así no se borra ni una fila.** Lo que se guarda aquí es una FECHA, igual que
-- con el archivo:
--
--   · Las postulaciones, sus transiciones, sus notas, sus currículums y los correos que ya
--     salieron siguen enteros. Quien pregunte dentro de un año por qué se le cerró su
--     proceso tiene que poder encontrar la respuesta.
--
--   · El borrado de datos personales de la ley 29733 sigue alcanzando esas postulaciones:
--     el borrado lógico de la vacante no las esconde de ese barrido, que mira a la persona
--     y no a la convocatoria.
--
--   · No hay vuelta desde el panel, a propósito. Quitar la fecha a mano en la base la
--     devuelve —es lo que hará soporte si alguien se equivoca—, pero un botón de restaurar
--     convertiría una acción excepcional en una reversible, y entonces nadie leería el modal.

ALTER TABLE vacante
    ADD COLUMN eliminada_en TIMESTAMPTZ;

COMMENT ON COLUMN vacante.eliminada_en IS
    'Cuándo se eliminó esta vacante por borrado lógico. Vacío = existe, que es como quedan '
    'TODAS las vacantes anteriores a la V60. Con fecha, la vacante deja de salir en el panel '
    '(lista habitual, Archivadas y su contador), en el tablón, en «Mis procesos», en los '
    'rankings, en las exportaciones, en la selección de sesiones de simulación y en los '
    'procesos automáticos; consultarla por id contesta 404. Sus filas NO se borran: quién la '
    'eliminó, cuándo y por qué están en «auditoria», acción eliminar_vacante (V60).';

-- ============ Los índices de las dos listas del panel ============
--
-- La lista de todos los días ya no es «sin archivar»: es «sin archivar y sin eliminar», y el
-- índice parcial de la V59 se quedó corto —seguiría cargando con las eliminadas, que son
-- justo las que ninguna consulta quiere—. Se sustituye en vez de añadir otro al lado: dos
-- índices parciales sobre la misma columna se pagan los dos al escribir y solo se usa uno.
DROP INDEX IF EXISTS ix_vacante_sin_archivar;

CREATE INDEX ix_vacante_lista_habitual
    ON vacante (organizacion_id, creado_en DESC)
 WHERE archivada_en IS NULL AND eliminada_en IS NULL;

-- Y el de Archivadas, que tiene el mismo problema: una eliminada que estaba archivada sale
-- también de esa lista y de su contador «Archivadas (N)».
CREATE INDEX ix_vacante_archivadas_vivas
    ON vacante (organizacion_id, archivada_en DESC)
 WHERE archivada_en IS NOT NULL AND eliminada_en IS NULL;

-- ============ Se elimina en cualquier estado, y la base no opina ============
--
-- Sin CHECK a propósito, al revés que el archivo. Archivar solo tiene sentido sobre una
-- CERRADA —guardar lo que terminó—, pero una vacante mal creada se elimina esté donde esté:
-- en BORRADOR (la más común: nadie la vio), PUBLICADA (hay gente dentro y por eso se les
-- cierra y se les avisa), CERRADA o incluso archivada. Quién puede hacerlo es el permiso
-- nuevo, y cuándo no hace falta preguntarlo.
--
-- El CHECK de la V59 se queda tal cual: una archivada sigue teniendo que estar CERRADA,
-- eliminada o no.

-- ============ Un cierre más de postulación: «la vacante ya no existe» ============
--
-- Cerrar por eliminación NO es un CIERRE_MANUAL. Los dos dejan la postulación en CERRADA,
-- pero la pregunta que se contesta meses después es distinta: «¿por qué se cerró?» tiene que
-- poder responder «porque la convocatoria se retiró», y no «porque alguien lo decidió». Sin
-- este código, las dos cosas se contarían juntas en cualquier informe de motivos de cierre.
--
-- Es una transición HECHA POR UNA PERSONA, no por el sistema: hay alguien que decidió
-- eliminar la vacante, escribió su motivo y quedó anotado.
ALTER TABLE postulacion
    DROP CONSTRAINT IF EXISTS postulacion_motivo_cierre_check;

ALTER TABLE postulacion
    ADD CONSTRAINT postulacion_motivo_cierre_check CHECK (
        motivo_cierre IN ('REQUISITO_OBJETIVO', 'BARRERA_CRITICA', 'DECISION_ROJA',
                          'DECISION_PERSONA', 'PASA_A_RESERVA',
                          'INACTIVIDAD', 'CIERRE_MANUAL', 'RETIRO_CANDIDATO',
                          'PLAZO_VENCIDO', 'BORRADO_DATOS', 'VACANTE_ELIMINADA')
    );

COMMENT ON COLUMN postulacion.motivo_cierre IS
    'De qué clase fue el cierre, cuando la postulación terminó. VACANTE_ELIMINADA (V60) es '
    'el cierre que provoca retirar la vacante entera: la persona no hizo nada y no tiene '
    'nada que hacer, y por eso su aviso se lo dice con esas palabras.';

-- ============ El aviso que le llega a quien estaba dentro ============
--
-- Sin tabla nueva: `aviso_portal` guarda el tipo como texto y el cuerpo ya armado (V56). Lo
-- que sí cambia es el comentario, que es lo que se lee al abrir la base.
--
-- ⚠️ Este aviso NO enlaza al proceso, y es la única diferencia con los dos anteriores: el
-- proceso al que llevaría ya no se puede abrir. Un aviso que lleva a un 404 hace creer al
-- candidato que se rompió algo suyo.
COMMENT ON COLUMN aviso_portal.tipo IS
    'Qué clase de noticia es. REMUNERACION_ACTUALIZADA (V55) es el cambio de sueldo hecho '
    'desde la tarjeta del detalle; VACANTE_ACTUALIZADA (V58) es el guardado del formulario '
    'de la vacante, con todo lo que cambió —sueldo incluido— en un solo aviso; '
    'VACANTE_ELIMINADA (V60) es que la empresa retiró la vacante y la postulación quedó '
    'cerrada, y es el único que no enlaza a ninguna parte. El portal usa el tipo para el '
    'icono y para agrupar; el texto de lo que pasó va en titulo y cuerpo.';

-- ============ El permiso ============
--
-- Permiso propio y no `cerrar_vacante`, al revés que el archivo. Archivar es el paso
-- siguiente de cerrar y reparte el mismo poder; eliminar cierra las postulaciones de otros y
-- retira la convocatoria de todas las pantallas. Que quien pueda dar por terminada una
-- convocatoria pueda además hacerla desaparecer no se sigue de nada, y el día que alguien
-- reparta `cerrar_vacante` a un rol nuevo no estaría regalando esto sin saberlo.
INSERT INTO permiso (codigo, etiqueta, grupo, orden) VALUES
    ('eliminar_vacante', 'Eliminar una vacante', 'VACANTES', 9);

-- A Talento y Dirección, con alcance TODO, en TODAS las organizaciones que ya existen —no
-- solo en la de la plataforma—: una empresa dada de alta el mes pasado tiene sus propios
-- roles, y sin esta fila su equipo de Talento vería el permiso en la matriz sin tenerlo.
--
-- Las empresas FUTURAS no hacen falta aquí: al darlas de alta se les copia entera la matriz
-- permiso-alcance de la plataforma (ver ServicioPlataformaImpl#sembrar), y la plataforma ya
-- lo tiene por esta misma sentencia.
INSERT INTO rol_permiso (rol_id, permiso_id, alcance)
SELECT r.id, p.id, 'TODO'
FROM rol r
JOIN permiso p ON p.codigo = 'eliminar_vacante'
WHERE r.codigo IN ('TALENTO', 'DIRECCION')
  AND NOT EXISTS (SELECT 1 FROM rol_permiso ya
                  WHERE ya.rol_id = r.id AND ya.permiso_id = p.id);
