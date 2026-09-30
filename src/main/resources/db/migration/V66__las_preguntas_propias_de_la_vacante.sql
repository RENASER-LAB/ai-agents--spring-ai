-- Las preguntas propias de cada vacante (30/09/2026, fase 1).
--
-- Hasta hoy, para que un candidato respondiera preguntas alguien tenía que llenar un Excel
-- con los 15 formatos del método de RENASER. Desde aquí cualquier empresa escribe, desde el
-- panel y para cada vacante, las preguntas del Perfil Integral: cuatro tipos (abierta,
-- opción única, opción múltiple y escala), agrupadas en criterios con nombre que suman 100
-- puntos. Lo cerrado lo puntúa el sistema; lo abierto, la IA, pregunta a pregunta y en
-- puntos. El banco de RENASER, su método CRITERIOS, los bancos por nivel y el importador no
-- cambian: esto vive al lado.
--
-- ⚠️ La V64 y la V65 las reclama otro trabajo que va en paralelo: el hueco es deliberado
-- (ver MigracionesSinChoqueTest).

-- ============================================================================
-- 1 · El banco de la vacante tiene propósito
-- ============================================================================
-- Hasta hoy un banco con vacante_id era siempre su cuestionario técnico. Ahora una vacante
-- puede tener además sus preguntas del Perfil Integral, y cada uno su borrador y su
-- publicada. Sin el propósito, el cuestionario técnico podría tomar las preguntas del
-- Perfil Integral (y al revés): la búsqueda por vacante no sabría cuál es cuál.
ALTER TABLE version_banco ADD COLUMN proposito text
    CHECK (proposito IS NULL OR proposito IN ('PERFIL_INTEGRAL', 'CUESTIONARIO_TECNICO'));
COMMENT ON COLUMN version_banco.proposito IS
    'Solo en los bancos de una vacante: PERFIL_INTEGRAL (sus preguntas propias) o '
    'CUESTIONARIO_TECNICO (su etapa técnica). NULL en los bancos por nivel.';

UPDATE version_banco SET proposito = 'CUESTIONARIO_TECNICO' WHERE vacante_id IS NOT NULL;

-- El cuestionario técnico se sigue creando como siempre, y quien lo cree sin decir su
-- propósito (el REDACTOR de antes, un guion, una prueba) no puede dejar una fila a medias:
-- un banco de vacante sin propósito es, por historia, un cuestionario técnico.
CREATE FUNCTION version_banco_proposito_por_omision() RETURNS trigger AS $$
BEGIN
    IF NEW.vacante_id IS NOT NULL AND NEW.proposito IS NULL THEN
        NEW.proposito := 'CUESTIONARIO_TECNICO';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER version_banco_proposito_por_omision
    BEFORE INSERT OR UPDATE ON version_banco
    FOR EACH ROW EXECUTE FUNCTION version_banco_proposito_por_omision();

ALTER TABLE version_banco ADD CONSTRAINT version_banco_proposito_amarrado_check
    CHECK ((vacante_id IS NULL) = (proposito IS NULL));

-- Los índices de la V42 permitían un borrador y una publicada por vacante. Ahora es uno
-- de cada, por propósito.
DROP INDEX version_banco_borrador_por_vacante_idx;
DROP INDEX version_banco_publicada_por_vacante_idx;
CREATE UNIQUE INDEX version_banco_borrador_por_vacante_idx
    ON version_banco (vacante_id, proposito)
    WHERE vacante_id IS NOT NULL AND estado = 'BORRADOR';
CREATE UNIQUE INDEX version_banco_publicada_por_vacante_idx
    ON version_banco (vacante_id, proposito)
    WHERE vacante_id IS NOT NULL AND estado = 'PUBLICADA';

-- ============================================================================
-- 2 · El método PUNTOS, la guía de calificación y su número
-- ============================================================================
-- PUNTOS = cada pregunta vale sus puntos y todas suman 100. Lo cerrado lo cuenta el
-- sistema; lo abierto lo pone la IA entre 0 y los puntos de la pregunta.
DO $$
DECLARE restriccion text;
BEGIN
    FOR restriccion IN
        SELECT conname FROM pg_constraint
         WHERE conrelid = 'version_banco'::regclass AND contype = 'c'
           AND pg_get_constraintdef(oid) LIKE '%metodo_calificacion%'
    LOOP
        EXECUTE format('ALTER TABLE version_banco DROP CONSTRAINT %I', restriccion);
    END LOOP;
END $$;
ALTER TABLE version_banco ADD CONSTRAINT version_banco_metodo_calificacion_check
    CHECK (metodo_calificacion IS NULL OR metodo_calificacion IN ('CRITERIOS', 'PUNTOS'));
COMMENT ON COLUMN version_banco.metodo_calificacion IS
    'NULL = motor de claves versionadas (v0.1 y v3) · CRITERIOS = conteo C1..C4 '
    '(CAZATALENTOS) · PUNTOS = preguntas propias de una vacante, que suman 100';

-- Las preguntas del Perfil Integral de una vacante se califican siempre por puntos.
ALTER TABLE version_banco ADD CONSTRAINT version_banco_propias_por_puntos_check
    CHECK (proposito IS DISTINCT FROM 'PERFIL_INTEGRAL' OR metodo_calificacion = 'PUNTOS');

-- La guía que la empresa escribe para que la IA califique lo abierto. Mismo tope que la
-- de la prueba del puesto (V46), y la misma envoltura al mandarla.
ALTER TABLE version_banco ADD COLUMN guia_calificacion text
    CHECK (guia_calificacion IS NULL OR char_length(guia_calificacion) <= 2000);

-- Cada vez que se corrigen las instrucciones de la IA de una versión publicada, este
-- número sube. Cada nota de una abierta guarda con cuál se calculó: un resultado que
-- llega calculado con una guía anterior se descarta.
ALTER TABLE version_banco ADD COLUMN version_guia integer NOT NULL DEFAULT 1
    CHECK (version_guia >= 1);

-- ============================================================================
-- 3 · Los criterios de un banco de vacante
-- ============================================================================
-- Lo que se califica y lo que se ve como columna: nombre, qué evalúa y orden. Sus puntos
-- NO se guardan: son la suma de sus preguntas. No se reutiliza la tabla `criterio`: ya
-- mezcla los ocho del currículum con los de la rúbrica de la prueba, y cualquier lectura
-- de «los criterios del Perfil Integral» juntaría estos con los del currículum.
CREATE TABLE criterio_banco (
    id               bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version_banco_id bigint NOT NULL REFERENCES version_banco(id),
    nombre           text NOT NULL
        CHECK (btrim(nombre) <> '' AND char_length(nombre) <= 120),
    -- Llega a la IA. Opcional.
    que_evalua       text CHECK (que_evalua IS NULL OR char_length(que_evalua) <= 1000),
    orden            integer NOT NULL,
    creado_en        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX criterio_banco_version_idx ON criterio_banco (version_banco_id);

-- ============================================================================
-- 4 · La pregunta: tres tipos más, sus puntos, su criterio y qué debe tener
-- ============================================================================
ALTER TABLE pregunta DROP CONSTRAINT pregunta_tipo_check;
ALTER TABLE pregunta ADD CONSTRAINT pregunta_tipo_check CHECK (tipo IN (
    -- Banco v0.1, en uso por las evaluaciones ya hechas
    'ESTILO', 'SITUACION', 'CONDUCTUAL', 'MICROCASO', 'DILEMA', 'CONSISTENCIA',
    -- Banco v3
    'EF-4', 'SJT-R', 'SEC', 'INV', 'DE', 'CD', 'V', 'PC',
    -- Texto libre: CAZATALENTOS y las preguntas propias
    'ABIERTA',
    -- Las preguntas propias de una vacante
    'OPCION_UNICA', 'OPCION_MULTIPLE', 'ESCALA'
));

-- Entero de 0 a 100. NO se reutiliza `peso`, que es el multiplicador 0..2 del v3.
ALTER TABLE pregunta ADD COLUMN puntos integer
    CHECK (puntos IS NULL OR puntos BETWEEN 0 AND 100);
-- Obligatorio para publicar, pero un borrador puede tener preguntas sin criterio.
ALTER TABLE pregunta ADD COLUMN criterio_banco_id bigint REFERENCES criterio_banco(id);
-- «Qué debe tener una buena respuesta»: llega a la IA junto a la abierta.
ALTER TABLE pregunta ADD COLUMN que_debe_tener text
    CHECK (que_debe_tener IS NULL OR char_length(que_debe_tener) <= 1000);
CREATE INDEX pregunta_criterio_banco_idx ON pregunta (criterio_banco_id)
    WHERE criterio_banco_id IS NOT NULL;

-- ============================================================================
-- 5 · La opción tiene un orden explícito
-- ============================================================================
-- Hoy las opciones se ordenan por `letra` como texto, y con diez niveles de escala sale
-- 1, 10, 2… Los tipos nuevos se leen siempre por este número; los del v3, como estaban.
-- Sus puntos van en `puntaje` (numeric compartido con el v3): que sean enteros lo exige el
-- servidor al guardar y al publicar, no un CHECK.
ALTER TABLE opcion ADD COLUMN orden integer;

-- ============================================================================
-- 6 · De dónde salen las preguntas de una vacante
-- ============================================================================
-- NIVEL = el banco de la empresa para el nivel del puesto (como hasta hoy) · VACANTE = sus
-- preguntas propias. Las existentes siguen con el banco del nivel; `aplica_evaluacion`
-- sigue siendo el interruptor.
ALTER TABLE vacante ADD COLUMN origen_preguntas text NOT NULL DEFAULT 'NIVEL'
    CHECK (origen_preguntas IN ('NIVEL', 'VACANTE'));

-- ============================================================================
-- 7 · La evaluación de un banco de vacante no tiene plantilla
-- ============================================================================
-- La V43 exigía plantilla a toda evaluación del Perfil Integral. Las que salen de las
-- preguntas propias no la tienen: su tiempo lo dice la versión y no se reutilizan (no
-- llevan vigente_hasta). La exigencia pasa al código (ServicioEvaluacionImpl), que es el
-- que sabe de dónde sale cada evaluación.
ALTER TABLE evaluacion DROP CONSTRAINT evaluacion_plantilla_solo_del_perfil_check;

-- ============================================================================
-- 8 · La nota de una abierta: hasta 100, la de la IA aparte, y con qué guía se puso
-- ============================================================================
-- El 0..4 de la V12 pasa a 0..100. Para los métodos de siempre (NULL y CRITERIOS) el 0..4
-- se sigue exigiendo, ahora en el código al guardar (PuenteCalificacionIaImpl): la tabla
-- no sabe el método, y fuera de rango se rechaza como lo rechazaba la base.
DO $$
DECLARE restriccion text;
BEGIN
    FOR restriccion IN
        SELECT conname FROM pg_constraint
         WHERE conrelid = 'nota_respuesta'::regclass AND contype = 'c'
           AND pg_get_constraintdef(oid) LIKE '%puntaje%'
           AND pg_get_constraintdef(oid) NOT LIKE '%ajustada%'
    LOOP
        EXECUTE format('ALTER TABLE nota_respuesta DROP CONSTRAINT %I', restriccion);
    END LOOP;
END $$;
ALTER TABLE nota_respuesta ADD CONSTRAINT nota_respuesta_puntaje_check
    CHECK (puntaje BETWEEN 0 AND 100);

-- La nota que puso la IA, guardada en el primer ajuste a mano y nunca más tocada: así la
-- ficha enseña las dos. `puntaje` es la que vale.
ALTER TABLE nota_respuesta ADD COLUMN puntaje_ia numeric(5,2)
    CHECK (puntaje_ia IS NULL OR puntaje_ia BETWEEN 0 AND 100);
-- Con qué guía de la versión se calculó (version_banco.version_guia). NULL en los métodos
-- de siempre y en las que puso una persona sin IA.
ALTER TABLE nota_respuesta ADD COLUMN version_guia integer;

-- ============================================================================
-- 9 · La recalificación va por su propio carril
-- ============================================================================
-- Corregir la guía con candidatos dentro vuelve a correr SOLO al evaluador de las
-- abiertas. Si fuera por la pasada normal (FINA), al terminar dispararía el retrato y
-- movería a la gente de etapa; con un modo propio no entra en esa barrera ni en el
-- «cómo va» del retrato.
DO $$
DECLARE restriccion text;
BEGIN
    FOR restriccion IN
        SELECT conname FROM pg_constraint
         WHERE conrelid = 'trabajo_ia'::regclass AND contype = 'c'
           AND pg_get_constraintdef(oid) LIKE '%modo%'
    LOOP
        EXECUTE format('ALTER TABLE trabajo_ia DROP CONSTRAINT %I', restriccion);
    END LOOP;
END $$;
ALTER TABLE trabajo_ia ADD CONSTRAINT trabajo_ia_modo_check
    CHECK (modo IN ('RAPIDA', 'FINA', 'RECALIFICA'));

-- ============================================================================
-- 10 · El agente que propone criterios y preguntas
-- ============================================================================
-- Como el REDACTOR: no evalúa a nadie y lo que escribe no toca a ningún candidato hasta
-- que una persona lo agrega a su borrador y lo publica.
ALTER TABLE agente DROP CONSTRAINT agente_codigo_check;
ALTER TABLE agente ADD CONSTRAINT agente_codigo_check
    CHECK (codigo IN ('NECESIDAD_TALENTO', 'CAZATALENTOS', 'DATOS_CV', 'EVIDENCIA_CV',
                      'EVALUADOR', 'POTENCIAL_RIESGO', 'PRUEBA_PUESTO', 'SIMULACION',
                      'DESEMPENO', 'APRENDIZAJE', 'REDACTOR', 'EVALUADOR_TECNICO',
                      'RECOMENDADOR'));

INSERT INTO agente (codigo, nombre, descripcion, version, es_activo) VALUES
    ('RECOMENDADOR', 'Recomendador de preguntas',
     'Propone criterios y preguntas para las preguntas propias de una vacante, a partir de '
     'lo que la describe. Completa lo que le falta al borrador hasta 100 puntos. Solo '
     'propone: una persona decide qué agrega', 1, true);

-- Sin instrucción activa el ejecutor no llama al modelo, así que nace con ella. El formato
-- exacto y los puntos que faltan viajan en cada llamada.
INSERT INTO instruccion_ia (agente_codigo, version, texto, es_activa, publicada_en) VALUES
    ('RECOMENDADOR', 1,
     'Propones preguntas de selección para UNA vacante concreta, a partir de lo que la ' ||
     'describe: su título, su descripción, su propósito, sus responsabilidades y ' ||
     'requisitos, el puesto, y lo que pidió quien solicitó a la persona. NO evalúas a ' ||
     'nadie: escribes preguntas que una persona revisará antes de usarlas.' || chr(10) ||
     chr(10) ||
     'Reglas:' || chr(10) ||
     '- Agrupa las preguntas en criterios con nombre: cada criterio es algo que se ' ||
     'califica (un conocimiento, una habilidad, una forma de trabajar) y dice qué evalúa.' ||
     chr(10) ||
     '- Mezcla tipos cuando tenga sentido: una abierta pide un caso real y concreto; una ' ||
     'cerrada comprueba un conocimiento con una respuesta clara.' || chr(10) ||
     '- Las preguntas hablan del trabajo de esta vacante, con sus palabras. Nada genérico.' ||
     chr(10) ||
     '- En cada abierta di qué debe tener una buena respuesta: el dato, el paso o la ' ||
     'decisión que distingue a quien lo hizo de quien lo leyó.' || chr(10) ||
     '- Nunca preguntes por estado civil, hijos, salud, embarazo, religión, política, ' ||
     'sindicato, edad ni origen étnico.' || chr(10) ||
     '- Escribe claro y directo, sin jerga de recursos humanos.',
     true, now());

-- ============================================================================
-- 11 · La propuesta del RECOMENDADOR
-- ============================================================================
-- Lo que la IA propone se guarda aparte y NO toca el borrador: la persona agrega lo que
-- quiera. `puntos_que_faltan` es lo que faltaba al pedirla, y es lo que la propuesta tiene
-- que sumar exactamente.
CREATE TABLE propuesta_preguntas (
    id                bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    organizacion_id   bigint NOT NULL REFERENCES organizacion(id),
    vacante_id        bigint NOT NULL REFERENCES vacante(id),
    indicacion        text CHECK (indicacion IS NULL OR char_length(indicacion) <= 1000),
    puntos_que_faltan integer NOT NULL CHECK (puntos_que_faltan BETWEEN 1 AND 100),
    estado            text NOT NULL DEFAULT 'PEDIDA'
        CHECK (estado IN ('PEDIDA', 'LISTA', 'FALLIDA')),
    contenido         jsonb,
    -- Por qué falló, dicho para quien la pidió.
    motivo_fallo      text,
    pedida_por_usuario_id bigint REFERENCES usuario(id),
    creado_en         timestamptz NOT NULL DEFAULT now(),
    terminada_en      timestamptz
);
CREATE INDEX propuesta_preguntas_vacante_idx ON propuesta_preguntas (vacante_id, id);

-- ============================================================================
-- 12 · La instrucción del EVALUADOR vale para los tres métodos
-- ============================================================================
-- La v1 habla solo de la escala 0–4. Con el método PUNTOS cada abierta tiene su propio
-- máximo, que viaja en el formato de cada llamada. Si la instrucción activa todavía habla
-- solo del 0–4 (nadie la reescribió desde el panel), se publica otra que valga para los
-- tres. Si alguien ya la reescribió, se respeta la suya.
DO $$
DECLARE
    activa_id    bigint;
    activa_texto text;
    siguiente    integer;
BEGIN
    SELECT id, texto INTO activa_id, activa_texto FROM instruccion_ia
     WHERE agente_codigo = 'EVALUADOR' AND es_activa;
    IF activa_id IS NULL OR activa_texto NOT LIKE '%de 0 a 4%' THEN
        RETURN;
    END IF;
    SELECT coalesce(max(version), 0) + 1 INTO siguiente FROM instruccion_ia
     WHERE agente_codigo = 'EVALUADOR';
    -- Una sola activa por agente (índice parcial de la V11): primero se apaga.
    UPDATE instruccion_ia SET es_activa = false WHERE id = activa_id;
    INSERT INTO instruccion_ia (agente_codigo, version, texto, es_activa, publicada_en)
    VALUES ('EVALUADOR', siguiente,
        'Calificas respuestas abiertas de una evaluación de selección. La escala la fija ' ||
        'el formato de cada llamada: a veces es de 0 a 4, a veces va de 0 a los puntos ' ||
        'máximos de cada respuesta, y a veces no pones un número sino que declaras qué ' ||
        'criterios aparecen. Usa siempre la que te pida el formato.' || chr(10) ||
        'Como guía, de menos a más:' || chr(10) ||
        '· Lo mínimo: no da un caso, responde en abstracto o evade.' || chr(10) ||
        '· Cuenta un caso pero fue pasivo, sin contribución propia clara.' || chr(10) ||
        '· Hubo acción clara, pero poca medición, evidencia o aprendizaje.' || chr(10) ||
        '· Actuó por iniciativa, con criterio y resultado verificable.' || chr(10) ||
        '· Lo máximo: anticipó, priorizó, comunicó, actuó, midió y convirtió el ' ||
        'aprendizaje en sistema.' || chr(10) ||
        'Cuando la escala es de puntos, reparte en proporción a los puntos máximos de esa ' ||
        'respuesta, y si la respuesta trae lo que debe tener una buena respuesta, mídela ' ||
        'contra eso.' || chr(10) ||
        'Reglas que no puedes romper:' || chr(10) ||
        '- Cita la parte concreta de la respuesta en que te basas. Sin evidencia citada la ' ||
        'nota no se guarda.' || chr(10) ||
        '- Lenguaje impecable sin un solo caso verificable baja la confianza, no sube la ' ||
        'nota.' || chr(10) ||
        '- Si no puedes calificar con lo que hay, dilo. Nunca pongas cero por falta de ' ||
        'información.',
        true, now());
END $$;
