-- ============================================================================
-- El perfil deja de ser un formulario: tiene cara, portada y su propio curriculum
-- ============================================================================
--
-- Hasta hoy el perfil del candidato era una columna de campos de texto y el curriculum
-- entraba UNICAMENTE al postular, atado a esa postulacion. De ahi salian dos cosas raras
-- de explicar: quien se registraba y llenaba su perfil no tenia donde poner su CV, y quien
-- postulaba a tres vacantes subia el mismo archivo tres veces.
--
-- Esta migracion trae cuatro cosas, y cada una tiene su porque:
--
--   1. FOTO. Es lo que pidio la clienta. ⚠️ Y la decision que va con ella: la foto
--      **NO viaja al panel del equipo y no la ve la IA**. El RF-41 anonimiza el curriculum
--      antes de que lo lea el modelo justamente para no sesgar por edad, sexo o aspecto, y
--      ensenarsela a quien decide desharia eso por la puerta de al lado. Se decidio el
--      05/09/2026: la foto es del candidato y la ve el candidato.
--      Si algun dia alguien quiere ensenarla en la ficha del equipo, eso necesita un RF
--      nuevo y un texto de consentimiento nuevo — no un JOIN mas.
--
--   2. PORTADA. O una del catalogo de la casa, o una suya. Nunca las dos: lo impone un
--      CHECK, porque «tiene las dos y cual gano» es una pregunta que no deberia existir.
--
--   3. EL CURRICULUM DEL PERFIL. Uno por persona, el ultimo (RF-162). Al postular se
--      reutiliza salvo que suba otro para esa vacante — y ese otro NO lo pisa: es lo que
--      pidio el usuario, y evita que mandar un CV a medida para una convocatoria le cambie
--      el perfil sin que se entere.
--
--   4. LA LECTURA DE ESE CURRICULUM, que necesita tabla propia. Ver abajo.
--
-- Los diplomas de las certificaciones siguen la misma regla que la foto: son del candidato.

-- ============================================================================
-- 1 · Lo que el perfil gana
-- ============================================================================

ALTER TABLE perfil_candidato
    ADD COLUMN foto_archivo_id    bigint REFERENCES archivo(id),
    ADD COLUMN portada_archivo_id bigint REFERENCES archivo(id),
    ADD COLUMN portada_galeria    text,
    ADD COLUMN cv_archivo_id      bigint REFERENCES archivo(id),
    ADD COLUMN cv_actualizado_en  timestamptz;

-- O la suya o la de la casa. Con las dos llenas no habria forma de saber cual pinta la
-- pantalla, y la respuesta acabaria siendo «la que el codigo mire primero».
ALTER TABLE perfil_candidato
    ADD CONSTRAINT perfil_una_sola_portada
    CHECK (portada_archivo_id IS NULL OR portada_galeria IS NULL);

COMMENT ON COLUMN perfil_candidato.foto_archivo_id IS
    'La foto del candidato. SOLO la ve el, en su portal: no viaja al DTO del panel ni al '
    'texto que lee la IA (RF-41). Decision del 05/09/2026; cambiarla necesita un RF nuevo.';
COMMENT ON COLUMN perfil_candidato.portada_galeria IS
    'Codigo de una portada del catalogo de la casa. Excluyente con portada_archivo_id.';
COMMENT ON COLUMN perfil_candidato.cv_archivo_id IS
    'Su curriculum, el ultimo (RF-162). Al postular se reutiliza copiandolo a la '
    'organizacion de la vacante: un archivo sellado con otra organizacion no se puede abrir '
    'desde el panel de esa empresa, que es lo que arreglo la V48.';

-- ============================================================================
-- 2 · El diploma de cada certificacion
-- ============================================================================
-- Que exista el titulo escrito y que exista el papel son dos cosas distintas, y hasta hoy
-- solo cabia la primera. Nullable porque casi ninguna lo va a tener: se escriben muchas mas
-- de las que se escanean.

ALTER TABLE certificacion_perfil
    ADD COLUMN archivo_id bigint REFERENCES archivo(id);

COMMENT ON COLUMN certificacion_perfil.archivo_id IS
    'El diploma escaneado, si lo subio. Como la foto: del candidato y para el candidato.';

-- ============================================================================
-- 3 · La lectura del curriculum subido al perfil
-- ============================================================================
--
-- ⚠️ POR QUE UNA TABLA NUEVA Y NO REUTILIZAR LA COLA QUE YA HAY
--
-- Toda la lectura de curriculums cuelga hoy de una POSTULACION, no de una persona:
-- `cv.postulacion_id`, `dato_cv.postulacion_id`, y la cola entera —`encolarDatosCv(id)` →
-- `situacionDe(postulacionId, ...)` → `puente.organizacionDe(postulacionId)`— no sabe
-- hablar de otra cosa. Un curriculum subido desde el perfil no tiene postulacion detras, y
-- puede que esa persona no postule nunca.
--
-- Hacer nullable el `postulacion_id` de `trabajo_ia` habria tocado la calificacion, la
-- barrera del retrato y los reintentos: tres piezas que funcionan y que nada de esto
-- necesita. Esta tabla es el camino corto, y lo unico que comparte con aquel mundo son las
-- piezas que hacen el trabajo de verdad (ExtractorTextoCv, AnonimizadorCv, AgenteDatosCv).
--
-- Los estados son los cuatro que la pantalla ya sabe pintar. SIN_CV no se guarda: es la
-- ausencia de fila.

CREATE TABLE lectura_cv_perfil (
    id           bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    persona_id   bigint  NOT NULL REFERENCES persona(id),
    archivo_id   bigint  NOT NULL REFERENCES archivo(id),
    estado       text    NOT NULL CHECK (estado IN ('EN_CURSO', 'LISTA', 'NO_LEGIBLE')),
    intentos     integer NOT NULL DEFAULT 0,
    -- Por que no salio nada, para poder contarlo sin que parezca un error del candidato.
    motivo       text,
    creado_en    timestamptz NOT NULL DEFAULT now(),
    terminado_en timestamptz
);

COMMENT ON TABLE lectura_cv_perfil IS
    'En que punto esta la lectura del curriculum que el candidato subio a SU PERFIL. No '
    'confundir con cv/dato_cv, que son de una postulacion. Sin fila = SIN_CV.';

-- Una sola lectura viva por persona. Subir un curriculum nuevo mientras el anterior se lee
-- dejaria dos corriendo y la segunda pisaria a la primera segun cual acabase antes.
--
-- ⚠️ Este indice parcial obliga a hacer flush entre cerrar la anterior y crear la nueva:
-- Hibernate ordena los INSERT antes que los UPDATE dentro de la misma transaccion, asi que
-- sin `saveAndFlush` el insert llega cuando la vieja todavia dice EN_CURSO y revienta.
CREATE UNIQUE INDEX lectura_cv_perfil_una_viva
    ON lectura_cv_perfil (persona_id) WHERE estado = 'EN_CURSO';

-- La consulta de cada visita al perfil: «la ultima lectura de esta persona».
CREATE INDEX lectura_cv_perfil_por_persona
    ON lectura_cv_perfil (persona_id, creado_en DESC);
