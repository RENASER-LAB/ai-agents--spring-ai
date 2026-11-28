-- Cada vacante con sus etapas (25/11/2026). Ver specs/Ready/etapas-por-vacante/spec.md.
--
-- Hasta aquí toda vacante recorría las mismas cinco etapas en el mismo orden. Desde esta
-- migración cada una guarda SU recorrido: la lista ordenada de las etapas que tiene. Talento lo
-- elige en la tuerca hasta que llega el primer postulante.
--
-- Dos piezas:
--
--   etapa           el catálogo gana la explicación de la ⓘ (obligatoria) y dos marcas: cuál va
--                   siempre primera y cuáles forman el recorrido con el que nace una vacante.
--   vacante_etapa   el recorrido de cada vacante, en orden.
--
-- Las vacantes que ya existen quedan con las cinco etapas en el orden de siempre: nadie que esté
-- en proceso nota un cambio. Las postulaciones, el historial y las notas no se tocan.


-- ============================================================================
-- 1 · El catálogo: la explicación y las marcas
-- ============================================================================

ALTER TABLE etapa
    ADD COLUMN descripcion       text,
    ADD COLUMN fija_primera      boolean NOT NULL DEFAULT false,
    ADD COLUMN en_predeterminado boolean NOT NULL DEFAULT false;

-- Los textos de la ⓘ, tal cual los aprobó el usuario (decisión 4 de la spec). Una etapa que no
-- fuera de las cinco se queda con su nombre, para que el NOT NULL de abajo no tumbe la migración.
UPDATE etapa SET descripcion = CASE codigo
    WHEN 'PERFIL_INTEGRAL' THEN 'La persona sube su CV y responde unas preguntas. El sistema lo revisa y le pone una nota para ver si encaja con el puesto.'
    WHEN 'PRUEBA_PUESTO'   THEN 'La persona resuelve una tarea real del puesto, con tiempo límite. Se califica sola y tú la revisas antes de que avance.'
    WHEN 'SIMULACION'      THEN 'La persona elige un día y hace una práctica del trabajo con el equipo. Tú la observas y le pones nota.'
    WHEN 'VALIDACION'      THEN 'La persona trabaja unos días a prueba en el área. El jefe del área dice si lo hizo bien.'
    WHEN 'DECISION'        THEN 'Se juntan todas las notas y el sistema sugiere contratar, pedir algo más o descartar. Una persona toma la decisión final.'
    ELSE nombre
END;

UPDATE etapa SET
    fija_primera      = (codigo = 'PERFIL_INTEGRAL'),
    en_predeterminado = (codigo IN ('PERFIL_INTEGRAL', 'PRUEBA_PUESTO', 'DECISION'));

-- Una etapa nueva no se registra sin su explicación: es lo que Talento lee en la ⓘ.
ALTER TABLE etapa ALTER COLUMN descripcion SET NOT NULL;
ALTER TABLE etapa ADD CONSTRAINT etapa_descripcion_no_vacia CHECK (btrim(descripcion) <> '');

COMMENT ON COLUMN etapa.descripcion IS
    'La explicación corta de la etapa que Talento lee en la ⓘ de «Etapas de esta vacante» (V95). '
    'Obligatoria: una etapa nueva no se registra sin ella.';
COMMENT ON COLUMN etapa.fija_primera IS
    'La etapa va siempre primera en todo recorrido y no se puede quitar ni mover (V95). Solo el '
    'Perfil Integral.';
COMMENT ON COLUMN etapa.en_predeterminado IS
    'La etapa forma parte del recorrido con el que nace una vacante nueva, en el orden de '
    'etapa.orden (V95).';


-- ============================================================================
-- 2 · El recorrido de cada vacante
-- ============================================================================

CREATE TABLE vacante_etapa (
    vacante_id   bigint      NOT NULL REFERENCES vacante(id) ON DELETE CASCADE,
    etapa_codigo text        NOT NULL REFERENCES etapa(codigo),
    orden        integer     NOT NULL CHECK (orden > 0),
    creado_en    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (vacante_id, etapa_codigo),
    CONSTRAINT vacante_etapa_orden_unico UNIQUE (vacante_id, orden)
);

COMMENT ON TABLE vacante_etapa IS
    'El recorrido de cada vacante: sus etapas en orden (V95). La etapa siguiente de una '
    'postulación sale de aquí. Solo cambia mientras la vacante no tiene postulaciones.';

-- La etapa que va siempre primera no puede quedar en otro sitio. La validación completa —que
-- empiece por ella, sin repetidas y con códigos que existan— vive en el servicio; esto es el
-- último muro, por si alguien escribe la tabla a mano.
CREATE FUNCTION vacante_etapa_fija_primera() RETURNS trigger AS $$
BEGIN
    IF NEW.orden <> 1 AND EXISTS (
        SELECT 1 FROM etapa e WHERE e.codigo = NEW.etapa_codigo AND e.fija_primera
    ) THEN
        RAISE EXCEPTION 'La etapa % va siempre primera en el recorrido de una vacante: no puede ir en el lugar %',
            NEW.etapa_codigo, NEW.orden
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER vacante_etapa_fija_primera
    BEFORE INSERT OR UPDATE ON vacante_etapa
    FOR EACH ROW EXECUTE FUNCTION vacante_etapa_fija_primera();


-- ============================================================================
-- 3 · Las vacantes que ya existen: las cinco, en el orden de siempre
-- ============================================================================

INSERT INTO vacante_etapa (vacante_id, etapa_codigo, orden)
SELECT v.id, e.codigo, e.orden
FROM vacante v
CROSS JOIN etapa e;
