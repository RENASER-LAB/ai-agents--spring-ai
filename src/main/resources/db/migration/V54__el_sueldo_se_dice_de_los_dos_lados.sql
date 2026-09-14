-- El sueldo deja de ser una frase y pasa a ser un contrato entre los dos (14/09/2026).
--
-- Hasta hoy la vacante decía el dinero en `compensacion_publica`: un texto libre donde
-- cabía «S/ 3500», «a convenir», «según experiencia» o nada. Y el candidato decía el suyo
-- en su perfil, opcional, donde casi nadie lo llenaba y —cuando lo hacía— no tenía nada
-- que ver con la vacante concreta a la que postulaba.
--
-- Las dos mitades del mismo dato vivían separadas y ninguna comprometía a nadie.
--
-- Desde esta migración el sueldo es un número, y viene con una regla de reciprocidad:
--
--   - La vacante declara su remuneración: OCULTA, FIJA (un monto) o RANGO (min–max).
--   - Si la vacante la enseña, quien postula está OBLIGADO a decir cuánto quiere ganar.
--   - Si la vacante la calla, el candidato tampoco tiene que decir nada.
--
-- Es un trato simétrico y se decidió así: pedirle el número a quien postula mientras la
-- empresa esconde el suyo es el desequilibrio que esta migración viene a romper.

-- ============ La remuneración de la vacante ============

ALTER TABLE vacante
    ADD COLUMN remuneracion_tipo    text NOT NULL DEFAULT 'OCULTA',
    ADD COLUMN remuneracion_min     numeric(12,2),
    ADD COLUMN remuneracion_max     numeric(12,2),
    ADD COLUMN remuneracion_moneda  text,
    ADD COLUMN remuneracion_actualizada_en timestamptz;

-- Las tres formas posibles, y la base las hace cumplir: el código puede tener un fallo,
-- una carga masiva puede saltárselo, y una vacante FIJA sin monto sería una vacante que
-- promete un sueldo que nadie escribió.
--
-- FIJA guarda el monto en `min` y deja `max` vacío. Podría haber una columna `monto`
-- aparte, pero entonces comparar «¿cuánto paga esta vacante?» exigiría mirar dos sitios
-- distintos según el tipo, y toda consulta de rangos tendría que hacer un COALESCE.
ALTER TABLE vacante
    ADD CONSTRAINT vacante_remuneracion_coherente CHECK (
        (remuneracion_tipo = 'OCULTA'
            AND remuneracion_min IS NULL
            AND remuneracion_max IS NULL
            AND remuneracion_moneda IS NULL)
     OR (remuneracion_tipo = 'FIJA'
            AND remuneracion_min IS NOT NULL AND remuneracion_min > 0
            AND remuneracion_max IS NULL
            AND remuneracion_moneda IS NOT NULL)
     OR (remuneracion_tipo = 'RANGO'
            AND remuneracion_min IS NOT NULL AND remuneracion_min > 0
            AND remuneracion_max IS NOT NULL AND remuneracion_max >= remuneracion_min
            AND remuneracion_moneda IS NOT NULL)
    );

COMMENT ON COLUMN vacante.remuneracion_tipo IS
    'OCULTA (no se publica sueldo y tampoco se le exige al candidato), FIJA (un monto, en '
    'remuneracion_min) o RANGO (remuneracion_min a remuneracion_max). Es lo que decide si '
    'declarar pretensión al postular es obligatorio.';

COMMENT ON COLUMN vacante.remuneracion_actualizada_en IS
    'Cuándo se cambió el sueldo por última vez. Vacío: nunca se tocó desde que se creó. El '
    'portal lo usa para pintar «actualizado el …» sobre el monto nuevo.';

-- `compensacion_publica` se jubila.
--
-- No se borra: son datos que alguien escribió y la auditoría de una vacante vieja debe
-- poder explicarse. Pero deja de viajar en el contrato público y deja de pintarse. Tener
-- dos sitios donde decir el sueldo —uno estructurado y otro en prosa— es tener dos sitios
-- donde contradecirse, y el contrato de esta migración necesita un número comparable, no
-- una frase.
COMMENT ON COLUMN vacante.compensacion_publica IS
    'RETIRADA (V54). El sueldo vive en remuneracion_tipo/min/max/moneda. Se conserva por '
    'las vacantes creadas antes; ninguna pantalla la lee ni la escribe.';

-- ============ Lo que pide el candidato, por postulación ============

-- Un monto único y no un rango, y es deliberado.
--
-- El perfil del candidato sigue guardando su banda (pretension_min–max): esa es su
-- expectativa general, y es la que prellena el formulario. Pero al postular a una vacante
-- concreta se le pide UN número, porque es lo único que se puede poner de frente contra el
-- presupuesto de la vacante y contestar «entra o no entra». Rango contra rango no contesta
-- eso: contesta «se solapan», que no sirve para decidir.
ALTER TABLE postulacion
    ADD COLUMN pretension_monto  numeric(12,2),
    ADD COLUMN pretension_moneda text,
    ADD COLUMN pretension_declarada_en timestamptz;

ALTER TABLE postulacion
    ADD CONSTRAINT postulacion_pretension_coherente CHECK (
        (pretension_monto IS NULL AND pretension_moneda IS NULL
            AND pretension_declarada_en IS NULL)
     OR (pretension_monto IS NOT NULL AND pretension_monto > 0
            AND pretension_moneda IS NOT NULL
            AND pretension_declarada_en IS NOT NULL)
    );

COMMENT ON COLUMN postulacion.pretension_monto IS
    'Cuánto dijo que quiere ganar al postular a ESTA vacante, confirmando o corrigiendo lo '
    'que tiene en su perfil. Vacío = la vacante tenía el sueldo oculto y no se le exigió. '
    'Tratamiento igual que la pretensión del perfil: solo con el permiso ver_pretension.';

-- ⚠️ Vacío NO significa «no quiso decirlo».
--
-- Las postulaciones anteriores a esta migración tienen el campo vacío porque cuando se
-- hicieron nadie lo pedía, y las posteriores a vacantes OCULTAS lo tienen vacío porque la
-- regla no lo exige. El panel tiene que decir cuál de las dos cosas es —«la vacante no
-- mostraba sueldo»— y no dejar un guion que se lea como un candidato esquivo.
--
-- Y por lo mismo, encender la remuneración de una vacante que YA tiene postulaciones no
-- vuelve atrás a pedírsela a nadie: el contrato se juzga con las reglas que había el día
-- que cada uno postuló.

-- ============ El correo de que el sueldo cambió ============

-- Un texto por empresa, como todos: cada organización puede reescribirlo, y una vacante
-- concreta puede sustituirlo por otro con plantilla_correo_vacante (V31).
--
-- `{{antes}}` y `{{ahora}}` llegan ya formateados desde el servicio («S/ 3 000 a 4 000»):
-- armar la frase del dinero en SQL obligaría a repetir el formato de moneda en la
-- plantilla de cada empresa, y la primera que lo tradujera mal lo mandaría mal para siempre.
INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa)
SELECT o.id, 'REMUNERACION_ACTUALIZADA',
       1,
       'Cambió la remuneración de {{vacante}}',
       'Hola {{nombre}}:' || E'\n\n'
       || 'La empresa actualizó la remuneración de «{{vacante}}», el puesto al que postulaste.'
       || E'\n\n'
       || 'Antes: {{antes}}' || E'\n'
       || 'Ahora: {{ahora}}' || E'\n\n'
       || 'Tu postulación sigue su curso con normalidad y no tienes que hacer nada. Te lo '
       || 'contamos porque el sueldo es parte de lo que aceptaste al postular, y cambiarlo '
       || 'sin avisarte no sería justo.' || E'\n\n'
       || 'Entra a tu portal para verlo: {{enlace}}' || E'\n\n'
       || 'Equipo de Talento · Renaser'
       , true
FROM organizacion o
WHERE NOT EXISTS (
    SELECT 1 FROM plantilla_correo p
    WHERE p.organizacion_id = o.id AND p.codigo = 'REMUNERACION_ACTUALIZADA');
