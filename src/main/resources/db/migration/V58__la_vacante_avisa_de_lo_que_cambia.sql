-- Lo que cambia en una vacante se cuenta por la campana, y solo por la campana (19/09/2026).
--
-- Hasta hoy el panel podía corregir una convocatoria publicada —el horario, la ubicación,
-- los requisitos— y quien estaba postulando no se enteraba de nada: el texto cambiaba bajo
-- sus pies. Lo único que avisaba era el sueldo, y avisaba por dos sitios a la vez: un correo
-- y un aviso en el portal.
--
-- Esta migración acompaña a las dos decisiones de esa entrega:
--
--   1. Aparece un tipo de aviso nuevo, VACANTE_ACTUALIZADA: uno solo por guardado, con todo
--      lo que cambió escrito dentro. No hace falta tabla nueva —`aviso_portal` ya guarda el
--      tipo como texto y el cuerpo ya armado (V56)—, pero sí hace falta que el comentario de
--      la columna deje de decir que solo hay uno, porque es lo que se lee al abrir la base.
--
--   2. El correo del cambio de sueldo deja de mandarse. La campana se queda quieta hasta que
--      la persona entra y la ve; el correo se pierde —cae en promociones, llega a una
--      dirección que el cargador de currículums inventó— y mandar los dos hacía que el panel
--      prometiera una entrega que nadie podía confirmar.

-- ============ El tipo de aviso nuevo ============

COMMENT ON COLUMN aviso_portal.tipo IS
    'Qué clase de noticia es. REMUNERACION_ACTUALIZADA (V55) es el cambio de sueldo hecho '
    'desde la tarjeta del detalle; VACANTE_ACTUALIZADA (V58) es el guardado del formulario '
    'de la vacante, con todo lo que cambió —sueldo incluido— en un solo aviso. El portal usa '
    'el tipo para el icono y para agrupar; el texto de lo que pasó va en titulo y cuerpo.';

COMMENT ON TABLE aviso_portal IS
    'La campana del portal del candidato: lo que pasó mientras no estaba, con su estado de '
    'leído. Desde la V58 es el ÚNICO canal de las noticias de la vacante: lo que cambia en '
    'una convocatoria no sale por correo.';

-- ============ El correo del sueldo se retira, y no se borra ============
--
-- Se desactiva en lugar de borrarse, y las dos mitades importan:
--
--   · Desactivarlo es lo que impide que vuelva a salir por descuido. `ServicioCorreo` busca
--     siempre la versión ACTIVA de un código; sin ninguna activa, no manda nada y lo anota.
--     Es la red debajo del código, que ya no lo llama.
--
--   · Borrarlo sería perder lo dicho. Cada fila de `correo_enviado` guarda el código y la
--     versión del texto con el que salió, y esos correos existen: si un candidato pregunta
--     meses después por qué se le dijo que el sueldo bajaba, la respuesta tiene que seguir
--     estando. Las filas se quedan enteras, con su asunto y su cuerpo.
--
-- El panel tampoco lo ofrece ya para editar (ver TextosDeCorreoRetirados): un texto que no
-- se manda, puesto en la pantalla de configuración, hace trabajar en balde.
UPDATE plantilla_correo
   SET es_activa = false
 WHERE codigo = 'REMUNERACION_ACTUALIZADA'
   AND es_activa;

COMMENT ON COLUMN plantilla_correo.es_activa IS
    'Cuál de las versiones de este texto es la que sale hoy. Falsa en todas las versiones de '
    'un código significa que ese aviso ya no se manda por correo: es lo que pasa desde la '
    'V58 con REMUNERACION_ACTUALIZADA, cuyas filas se conservan para explicar los correos '
    'que sí salieron.';
