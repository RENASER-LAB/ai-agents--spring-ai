-- El brochure de la empresa, armado solo (spec brochure-de-la-empresa, 17/11/2026).
--
-- La 4a (V85) ya guarda lo que la empresa cuenta de sí misma. Esta migración solo añade lo que
-- el brochure en PDF necesita y todavía no existía: la misión, la visión y la plantilla y el
-- color elegidos. El PDF no se guarda en ningún sitio: se arma al pedirlo a partir de lo
-- guardado, así que no hay tablas nuevas ni archivos nuevos en el almacén.
--
-- No hace falta un permiso nuevo: misión, visión, plantilla y color se editan con el mismo
-- `editar_presentacion_empresa` de la V85.
--
-- Las empresas que existen quedan sin misión ni visión, con «Clásica» y «Azul».

-- Misión y visión: opcionales, de 1 a 400 caracteres. Vacío es NULL, nunca una cadena vacía,
-- como en la V85.
--
-- La plantilla y el color son un catálogo fijo en el código (PlantillaDelBrochure y
-- ColorDelBrochure), sin tabla, como las portadas del perfil. El CHECK es la red de abajo: una
-- plantilla nueva pide ampliarlo, pero no tocar a las empresas que ya eligieron.
ALTER TABLE organizacion
    ADD COLUMN mision             text
        CHECK (mision IS NULL OR char_length(mision) BETWEEN 1 AND 400),
    ADD COLUMN vision             text
        CHECK (vision IS NULL OR char_length(vision) BETWEEN 1 AND 400),
    ADD COLUMN plantilla_brochure text NOT NULL DEFAULT 'CLASICA'
        CHECK (plantilla_brochure IN ('CLASICA', 'PORTADA', 'COLUMNAS')),
    ADD COLUMN color_brochure     text NOT NULL DEFAULT 'AZUL'
        CHECK (color_brochure IN ('AZUL', 'VERDE', 'ROJO', 'NOCHE', 'PETROLEO', 'CIRUELA'));

COMMENT ON COLUMN organizacion.plantilla_brochure IS
    'La plantilla del brochure en PDF: CLASICA, PORTADA o COLUMNAS. El catálogo vive en el código.';
COMMENT ON COLUMN organizacion.color_brochure IS
    'El color del brochure, de un catálogo fijo de seis. Todos llevan texto blanco encima.';
