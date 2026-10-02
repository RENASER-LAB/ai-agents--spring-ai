-- La prueba del puesto con el editor de preguntas (01/10/2026, fase 2).
--
-- Desde la fase 1 (V66) cualquier empresa escribe, por vacante, las preguntas del Perfil
-- Integral. La etapa técnica seguía con dos instrumentos que no sirven a una empresa
-- cliente: la plantilla de prueba (cargada con guiones) y el cuestionario CAZATALENTOS (que
-- redacta la IA desde una ficha). Desde aquí la prueba técnica de cada vacante NUEVA se
-- escribe en el mismo editor, con lo que le falta a una prueba: el caso, los entregables y
-- el tiempo. Sin entregables es un cuestionario, sin que nadie lo elija.
--
-- Las vacantes que ya existen siguen con su instrumento y sus datos tal cual: nada de esta
-- migración las toca.

-- ============================================================================
-- 1 · La vacante: un tercer instrumento para su etapa técnica
-- ============================================================================
-- PRUEBA_PROPIA = la prueba escrita en el editor (version_banco con propósito
-- PRUEBA_PUESTO). Es el que reciben las vacantes nuevas: lo pone el servicio al crearlas.
-- El valor por defecto de la columna sigue siendo PLANTILLA, que es lo que son todas las que
-- ya existen y lo que reciben las filas que se insertan a mano.
ALTER TABLE vacante DROP CONSTRAINT vacante_instrumento_etapa_tecnica_check;
ALTER TABLE vacante ADD CONSTRAINT vacante_instrumento_etapa_tecnica_check
    CHECK (instrumento_etapa_tecnica IN ('PLANTILLA', 'CUESTIONARIO_TECNICO', 'PRUEBA_PROPIA'));
COMMENT ON COLUMN vacante.instrumento_etapa_tecnica IS
    'Qué se rinde en la etapa PRUEBA_PUESTO: PLANTILLA = la version_plantilla_prueba elegida · '
    'CUESTIONARIO_TECNICO = el banco CAZATALENTOS de esta vacante · PRUEBA_PROPIA = la prueba '
    'escrita en el editor (version_banco con propósito PRUEBA_PUESTO), la de toda vacante nueva.';

-- ============================================================================
-- 2 · La versión: un tercer propósito, con el caso y el tiempo
-- ============================================================================
-- Se reutilizan las tablas de la fase 1 (criterios, preguntas, opciones, guía numerada)
-- para no tener dos editores. Lo que una prueba tiene y unas preguntas no, va aquí.
ALTER TABLE version_banco DROP CONSTRAINT version_banco_proposito_check;
ALTER TABLE version_banco ADD CONSTRAINT version_banco_proposito_check
    CHECK (proposito IS NULL OR proposito IN ('PERFIL_INTEGRAL', 'CUESTIONARIO_TECNICO',
                                              'PRUEBA_PUESTO'));
COMMENT ON COLUMN version_banco.proposito IS
    'Solo en los bancos de una vacante: PERFIL_INTEGRAL (sus preguntas propias), '
    'CUESTIONARIO_TECNICO (su cuestionario CAZATALENTOS) o PRUEBA_PUESTO (su prueba técnica '
    'escrita en el editor, V67). NULL en los bancos por nivel.';

ALTER TABLE version_banco DROP CONSTRAINT version_banco_propias_por_puntos_check;
ALTER TABLE version_banco ADD CONSTRAINT version_banco_propias_por_puntos_check
    CHECK (proposito IS NULL OR proposito NOT IN ('PERFIL_INTEGRAL', 'PRUEBA_PUESTO')
           OR metodo_calificacion = 'PUNTOS');

-- El caso. El enunciado es obligatorio para publicar si hay entregables (lo exige el
-- servidor, que es quien sabe cuántos hay); sin entregables es un cuestionario y el caso es
-- opcional.
ALTER TABLE version_banco
    ADD COLUMN enunciado text CHECK (enunciado IS NULL OR char_length(enunciado) <= 10000),
    -- El enunciado en PDF o Word, como la consigna de las plantillas: el archivo y el enlace
    -- largo que se pega en el aviso PRUEBA_DISPONIBLE.
    ADD COLUMN consigna_archivo_id bigint REFERENCES archivo(id),
    ADD COLUMN url_consigna text,
    ADD COLUMN materiales text CHECK (materiales IS NULL OR char_length(materiales) <= 2000),
    ADD COLUMN herramientas_permitidas text
        CHECK (herramientas_permitidas IS NULL OR char_length(herramientas_permitidas) <= 1000),
    -- El tiempo: los mismos límites que las plantillas (cinco minutos como mínimo), sin
    -- cambio inesperado. Que la modalidad traiga su número lo exige el servidor al publicar:
    -- un borrador se escribe por partes.
    ADD COLUMN modalidad text CHECK (modalidad IS NULL OR modalidad IN ('CRONOMETRADA', 'PLAZO_ABIERTO')),
    ADD COLUMN duracion_minutos integer CHECK (duracion_minutos IS NULL OR duracion_minutos >= 5),
    ADD COLUMN plazo_dias integer CHECK (plazo_dias IS NULL OR plazo_dias >= 1);

-- ============================================================================
-- 3 · La parte calificada de cada criterio
-- ============================================================================
-- En la prueba, las abiertas y los entregables no llevan puntos: la IA (o una persona)
-- califica el criterio entero. Cada criterio tiene una parte automática —la suma de sus
-- cerradas, que no se guarda— y una parte calificada: sus puntos y quién la califica.
-- NULL o 0 = sin parte calificada. En el Perfil Integral (fase 1) no se usan.
ALTER TABLE criterio_banco
    ADD COLUMN puntos_calificados integer
        CHECK (puntos_calificados IS NULL OR puntos_calificados BETWEEN 0 AND 100),
    ADD COLUMN calificador text CHECK (calificador IS NULL OR calificador IN ('IA', 'PERSONA'));

-- ============================================================================
-- 4 · Los entregables de la prueba nueva
-- ============================================================================
-- Se reutiliza entregable_requerido —y con ella toda la maquinaria de subir, reemplazar y
-- leer entregas—: hasta hoy colgaba siempre de una plantilla, ahora puede colgar de una
-- versión del editor. Una de las dos, nunca las dos.
ALTER TABLE entregable_requerido ALTER COLUMN version_plantilla_prueba_id DROP NOT NULL;
ALTER TABLE entregable_requerido
    ADD COLUMN version_banco_id bigint REFERENCES version_banco(id),
    -- «Qué debe tener una buena entrega»: llega a la IA y a quien califica, nunca al portal.
    ADD COLUMN que_debe_tener text CHECK (que_debe_tener IS NULL OR char_length(que_debe_tener) <= 1000);
ALTER TABLE entregable_requerido ADD CONSTRAINT entregable_requerido_de_una_version_check
    CHECK (num_nonnulls(version_plantilla_prueba_id, version_banco_id) = 1);
CREATE INDEX entregable_requerido_version_banco_idx ON entregable_requerido (version_banco_id)
    WHERE version_banco_id IS NOT NULL;

-- Qué entregables mira cada criterio. Un entregable puede estar en varios criterios, y
-- todo entregable tiene que estar en al menos uno (lo exige el servidor al publicar).
CREATE TABLE criterio_banco_entregable (
    id                      bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    criterio_banco_id       bigint NOT NULL REFERENCES criterio_banco(id),
    entregable_requerido_id bigint NOT NULL REFERENCES entregable_requerido(id),
    creado_en               timestamptz NOT NULL DEFAULT now(),
    UNIQUE (criterio_banco_id, entregable_requerido_id)
);
CREATE INDEX criterio_banco_entregable_entregable_idx
    ON criterio_banco_entregable (entregable_requerido_id);

-- ============================================================================
-- 5 · El intento: de una plantilla o del editor, y «no completada»
-- ============================================================================
-- La rendición reutiliza el intento (reloj, entregas, cierre por plazo). Deja de exigir una
-- plantilla: cuelga de una de las dos. El cambio inesperado no se sortea para las nuevas.
ALTER TABLE intento_prueba ALTER COLUMN version_plantilla_prueba_id DROP NOT NULL;
ALTER TABLE intento_prueba
    ADD COLUMN version_banco_id bigint REFERENCES version_banco(id),
    -- Venció con algo sin responder o sin un entregable obligatorio: se cierra sin
    -- entregar. No se califica, no gasta IA, no sale en el ranking y la postulación no
    -- cambia de etapa sola. `entregado_en` guarda cuándo se cerró, para que el barrido no
    -- lo vuelva a mirar.
    ADD COLUMN no_completada boolean NOT NULL DEFAULT false;
ALTER TABLE intento_prueba ADD CONSTRAINT intento_prueba_de_una_version_check
    CHECK (num_nonnulls(version_plantilla_prueba_id, version_banco_id) = 1);
ALTER TABLE intento_prueba ADD CONSTRAINT intento_prueba_no_completada_cerrada_check
    CHECK (NOT no_completada OR entregado_en IS NOT NULL);

-- ============================================================================
-- 6 · Las respuestas: a las preguntas del editor, también las cerradas
-- ============================================================================
-- Hasta hoy una respuesta de la prueba era siempre texto a una pregunta del catálogo
-- global. Las de la prueba nueva apuntan a una pregunta de su versión, y las cerradas
-- guardan la opción marcada (opción única y escala) o las marcadas (opción múltiple, en
-- `detalle.marcadas`, la misma forma que la evaluación).
ALTER TABLE respuesta_prueba ALTER COLUMN pregunta_prueba_id DROP NOT NULL;
ALTER TABLE respuesta_prueba ALTER COLUMN texto DROP NOT NULL;
ALTER TABLE respuesta_prueba
    ADD COLUMN pregunta_id bigint REFERENCES pregunta(id),
    ADD COLUMN opcion_id bigint REFERENCES opcion(id),
    ADD COLUMN detalle jsonb;
ALTER TABLE respuesta_prueba ADD CONSTRAINT respuesta_prueba_de_una_pregunta_check
    CHECK (num_nonnulls(pregunta_prueba_id, pregunta_id) = 1);
CREATE UNIQUE INDEX respuesta_prueba_pregunta_unica_idx
    ON respuesta_prueba (intento_prueba_id, pregunta_id) WHERE pregunta_id IS NOT NULL;

-- ============================================================================
-- 7 · La nota de la parte calificada de cada criterio
-- ============================================================================
-- Por el id del criterio de ESA versión, nunca por código ni por nombre: con criterios por
-- vacante hay muchos que se llaman igual. No se usa nota_criterio, cuya FK apunta a la
-- tabla `criterio` y que mezcla las tres etapas.
--
-- Solo se guarda la parte calificada: la automática (las cerradas) se calcula al leer con
-- los puntos que tenga la versión, así que cambiar una clave mal puesta mueve la nota de
-- todos a la vez sin reescribir nada.
CREATE TABLE nota_criterio_prueba (
    id                        bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    intento_prueba_id         bigint NOT NULL REFERENCES intento_prueba(id),
    criterio_banco_id         bigint NOT NULL REFERENCES criterio_banco(id),
    -- La que vale, de 0 a la parte calificada del criterio.
    puntaje                   numeric(6,2) NOT NULL CHECK (puntaje BETWEEN 0 AND 100),
    -- La que puso la IA, guardada en el primer ajuste a mano y nunca más tocada.
    puntaje_ia                numeric(6,2) CHECK (puntaje_ia IS NULL OR puntaje_ia BETWEEN 0 AND 100),
    explicacion               text NOT NULL,
    evidencia                 text,
    -- IA = la puso el agente (aunque después se ajustara) · PERSONA = la puso una persona.
    origen                    text NOT NULL CHECK (origen IN ('IA', 'PERSONA')),
    confianza                 numeric(5,2),
    ejecucion_ia_id           bigint REFERENCES ejecucion_ia(id),
    -- Con qué guía de la versión se calculó (version_banco.version_guia). NULL si la puso
    -- una persona.
    version_guia              integer,
    calificada_por_usuario_id bigint REFERENCES usuario(id),
    ajustada_por_usuario_id   bigint REFERENCES usuario(id),
    motivo_ajuste             text,
    ajustada_en               timestamptz,
    creado_en                 timestamptz NOT NULL DEFAULT now(),
    UNIQUE (intento_prueba_id, criterio_banco_id),
    CHECK (ajustada_por_usuario_id IS NULL
           OR (motivo_ajuste IS NOT NULL AND btrim(motivo_ajuste) <> ''))
);
CREATE INDEX nota_criterio_prueba_criterio_idx ON nota_criterio_prueba (criterio_banco_id);

-- ============================================================================
-- 8 · Las recomendaciones llevan propósito
-- ============================================================================
-- Hasta hoy había una sola propuesta viva por vacante. Ahora una vacante puede pedir
-- recomendaciones para sus preguntas del Perfil Integral y para su prueba técnica, y las
-- dos no se pueden pisar.
ALTER TABLE propuesta_preguntas
    ADD COLUMN proposito text NOT NULL DEFAULT 'PERFIL_INTEGRAL'
        CHECK (proposito IN ('PERFIL_INTEGRAL', 'PRUEBA_PUESTO'));
DROP INDEX propuesta_preguntas_vacante_idx;
CREATE INDEX propuesta_preguntas_vacante_idx ON propuesta_preguntas (vacante_id, proposito, id);
