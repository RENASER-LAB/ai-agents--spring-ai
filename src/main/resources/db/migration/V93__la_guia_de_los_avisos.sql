-- La guía de los avisos: cuándo la vio cada candidato (25/11/2026).
-- Ver specs/Ready/notificaciones-del-candidato/spec.md y su cambio de diseño del 25/11/2026.
--
-- Los ajustes de avisos dejan de ser un botón en «Mis procesos» y pasan a una tuerca dentro de la
-- campana. Para que se sepa dónde están, la primera vez que alguien entra en «Mis procesos» con al
-- menos una postulación sale una guía corta de tres pasos sobre la campana. Sale UNA vez por
-- cuenta: saltarla, «Ahora no», «Activar WhatsApp», «Entendido» o Esc la dan por vista.
--
-- Se guarda en el servidor, y no en el navegador, para que en otro dispositivo no se repita.
--
-- Va en la misma fila de preferencias de la V92: es de la persona, como sus canales, y sin fila
-- vale lo de siempre (todo por defecto y la guía sin ver). La V92 ya está aplicada, así que esto
-- va en una migración aparte.

ALTER TABLE preferencia_aviso
    ADD COLUMN guia_avisos_vista_en timestamptz;

COMMENT ON COLUMN preferencia_aviso.guia_avisos_vista_en IS
    'Cuándo dio por vista la guía de los avisos que sale la primera vez en «Mis procesos» (V93). '
    'Nula: todavía no la vio. Se marca una sola vez y no se vuelve a tocar.';
