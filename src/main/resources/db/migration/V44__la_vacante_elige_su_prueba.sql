-- La prueba técnica, ciclo 2: la vacante elige qué se rinde en su etapa técnica, y el
-- candidato puede rendir el cuestionario CAZATALENTOS.
-- Ver docs/CAZATALENTOS-PRUEBA-TECNICA.md y docs/DISENO-PRUEBA-TECNICA-FICHA-Y-REDACTOR.md.
--
-- El ciclo 1 dejó el cuestionario escrito, aprobado y sin nadie que pudiera contestarlo.
-- Lo que faltaba no era el motor —el portal de la evaluación y la calificación por
-- criterios ya sirven— sino tres cosas que la base no permitía: que la vacante DIGA cuál
-- de los dos instrumentos usa, que una postulación tenga dos exámenes, y que un examen
-- exista sin plantilla de evaluación.
--
-- La regla, confirmada con la clienta: en la etapa «Prueba del puesto» se rinde UNO de los
-- dos, nunca los dos. O la prueba del puesto de siempre —enunciado, cronómetro,
-- entregables— o el cuestionario técnico de la vacante. Lo que se elija es lo que el
-- candidato encuentra, y de ahí sale la nota de esa etapa.

-- ============================================================================
-- 1 · Qué rinde esta vacante en su etapa técnica
-- ============================================================================
-- Se DECLARA, no se deduce de si hay un cuestionario publicado. Un dueño puede preparar
-- el cuestionario «por si acaso» y eso no puede cambiar en silencio lo que rinde el
-- candidato; y la puerta de publicar necesita poder decir con una frase qué falta.
--
-- El valor por defecto es PLANTILLA y no es una preferencia: es lo que hacen hoy TODAS
-- las vacantes que ya existen, y con RF-73 («obligatoria para todo puesto») fue lo único
-- posible hasta esta migración. Sin el DEFAULT, las vacantes vivas quedarían en un estado
-- que ningún código sabe servir.
ALTER TABLE vacante
    ADD COLUMN instrumento_etapa_tecnica text NOT NULL DEFAULT 'PLANTILLA',
    ADD CONSTRAINT vacante_instrumento_etapa_tecnica_check
        CHECK (instrumento_etapa_tecnica IN ('PLANTILLA', 'CUESTIONARIO_TECNICO'));

COMMENT ON COLUMN vacante.instrumento_etapa_tecnica IS
    'Qué se rinde en la etapa PRUEBA_PUESTO: PLANTILLA = la version_plantilla_prueba '
    'elegida · CUESTIONARIO_TECNICO = el banco de tipo VACANTE publicado de esta vacante.';

-- Cuánto tiempo tiene el candidato, decidido en la vacante y no en el instrumento.
--
-- NULL = el del instrumento elegido: `version_plantilla_prueba.duracion_minutos` para la
-- prueba de siempre, y para el cuestionario técnico el del banco. Así las vacantes que ya
-- existen no cambian de comportamiento y quien no quiera pensarlo no tiene que rellenar
-- nada para publicar.
--
-- ⚠️ Solo la etapa técnica. Los minutos del banco del perfil integral NO se tocan aquí:
-- viajan con el banco desde su Excel (V43).
ALTER TABLE vacante ADD COLUMN minutos_etapa_tecnica integer
    CHECK (minutos_etapa_tecnica IS NULL OR minutos_etapa_tecnica > 0);

COMMENT ON COLUMN vacante.minutos_etapa_tecnica IS
    'Minutos que tiene el candidato en la etapa técnica. NULL = los del instrumento elegido.';

-- ============================================================================
-- 2 · Una postulación puede tener dos exámenes
-- ============================================================================
-- `postulacion.evaluacion_id` es de la etapa 1 y sigue siéndolo. El cuestionario técnico
-- es un segundo examen de la MISMA postulación, en otra etapa, contra otro banco.
--
-- Columna aparte y no `postulacion_id` dentro de `evaluacion`: una evaluación de la etapa 1
-- es **de la persona** y se reutiliza entre postulaciones mientras esté vigente
-- (`reutiliza_de_evaluacion_id`, `vigente_hasta`). La técnica es de la vacante y no se
-- reutiliza jamás — meterlas en la misma relación obligaría a explicar por qué una tiene
-- dueño y la otra no.
ALTER TABLE postulacion
    ADD COLUMN evaluacion_tecnica_id bigint REFERENCES evaluacion(id);

-- El barrido del cronómetro pregunta por esta columna cada minuto: sin índice, cada vuelta
-- recorre la tabla de postulaciones entera.
CREATE INDEX postulacion_evaluacion_tecnica_idx ON postulacion (evaluacion_tecnica_id)
    WHERE evaluacion_tecnica_id IS NOT NULL;

COMMENT ON COLUMN postulacion.evaluacion_tecnica_id IS
    'El examen de la etapa técnica cuando la vacante usa el cuestionario CAZATALENTOS. '
    'NULL con la prueba del puesto de siempre, que va por intento_prueba.';

-- ============================================================================
-- 3 · Un examen dice para qué es, y puede no tener plantilla de evaluación
-- ============================================================================
-- La plantilla dice cuánto dura y cada cuánto caduca un examen del banco por nivel. Un
-- cuestionario técnico no tiene ninguna: nace para una vacante, dura lo que diga esa
-- vacante y no se reutiliza, así que no hay nada que esa fila pudiera decir de él.
--
-- ⚠️ El NOT NULL se relaja, pero **no se pierde**: se restringe a donde importaba. Sin el
-- CHECK de abajo, un examen del perfil integral podría nacer sin plantilla —el fallo que
-- ese NOT NULL frenaba desde la V12— y nadie se enteraría hasta que un candidato abriera
-- su examen. Por eso `proposito` se gana su sitio: es lo que permite conservar la
-- invariante vieja en su mitad del mundo.
ALTER TABLE evaluacion
    ADD COLUMN proposito text NOT NULL DEFAULT 'PERFIL_INTEGRAL',
    ADD CONSTRAINT evaluacion_proposito_check
        CHECK (proposito IN ('PERFIL_INTEGRAL', 'CUESTIONARIO_TECNICO'));

ALTER TABLE evaluacion ALTER COLUMN plantilla_evaluacion_id DROP NOT NULL;

ALTER TABLE evaluacion ADD CONSTRAINT evaluacion_plantilla_solo_del_perfil_check
    CHECK (proposito = 'CUESTIONARIO_TECNICO' OR plantilla_evaluacion_id IS NOT NULL);

COMMENT ON COLUMN evaluacion.proposito IS
    'PERFIL_INTEGRAL = el examen del banco por nivel (etapa 1) · CUESTIONARIO_TECNICO = el '
    'cuestionario de una vacante (etapa 2). Decide de qué columna de postulacion cuelga y '
    'qué barrido de vencimientos lo cierra.';

COMMENT ON COLUMN evaluacion.plantilla_evaluacion_id IS
    'La plantilla del banco por nivel (etapa 1). NULL solo en el cuestionario técnico de una '
    'vacante, que no tiene plantilla: su tiempo lo dice la vacante.';

-- Los minutos con los que nació este examen, congelados.
--
-- ⚠️ **Se resuelven al crear, no al pintar.** `evaluacion` no sabe de qué vacante viene, y
-- resolverlos al leer movería el reloj de alguien que ya está respondiendo si el dueño edita
-- la vacante a mitad de tanda. Es la misma razón por la que `intento_prueba` guarda su
-- `vence_en` en vez de recalcularlo. NULL = lo que diga el instrumento, como hasta hoy.
ALTER TABLE evaluacion ADD COLUMN minutos_objetivo integer
    CHECK (minutos_objetivo IS NULL OR minutos_objetivo > 0);

COMMENT ON COLUMN evaluacion.minutos_objetivo IS
    'Los minutos que le tocaron a este examen, congelados al crearlo. NULL = los del '
    'instrumento (la plantilla o el banco).';

-- ============================================================================
-- 4 · Quién califica el cuestionario técnico
-- ============================================================================
-- Un agente propio, y **no reutilizar el código EVALUADOR**, por tres motivos verificados
-- en el código de la cola. Los tres fallan en silencio, que es lo que los vuelve caros:
--
--   1. `RegistroTrabajosIa.crearSiHaceFalta` deduplica buscando el ÚLTIMO trabajo de
--      `(postulacion, agente, modo)`. Con dos trabajos EVALUADOR en una misma postulación,
--      encolar el del perfil integral encontraría el técnico ya TERMINADO y no correría.
--   2. `ColaCalificacionIaImpl.A_LA_VEZ` incluye al EVALUADOR: la barrera que espera a que
--      todos acaben para armar el retrato contaría mal, en las dos direcciones.
--   3. `seSalta` mira si la evaluación está entregada leyendo `postulacion.evaluacion_id`.
--      En una vacante con la evaluación del banco apagada (V30) —combinación legal y
--      probablemente la más común con el cuestionario técnico— se tragaría el trabajo.
--
-- Un código distinto los resuelve por construcción: carril de deduplicación propio, fuera
-- de la barrera y fuera de ese atajo. El catálogo es cerrado, así que rehacer la
-- restricción (patrón de V19 y V42).
ALTER TABLE agente DROP CONSTRAINT agente_codigo_check;
ALTER TABLE agente ADD CONSTRAINT agente_codigo_check
    CHECK (codigo IN ('NECESIDAD_TALENTO', 'CAZATALENTOS', 'DATOS_CV', 'EVIDENCIA_CV',
                      'EVALUADOR', 'POTENCIAL_RIESGO', 'PRUEBA_PUESTO', 'SIMULACION',
                      'DESEMPENO', 'APRENDIZAJE', 'REDACTOR', 'EVALUADOR_TECNICO'));

INSERT INTO agente (codigo, nombre, descripcion, version, es_activo) VALUES
    ('EVALUADOR_TECNICO', 'Evaluador del cuestionario técnico',
     'Califica las respuestas del cuestionario técnico de una vacante contando los cuatro '
     'criterios del método, igual que el evaluador del banco. Va por su propio carril para '
     'no pisar la calificación del perfil integral', 1, true);

-- Sin instrucción activa el ejecutor se niega a llamar al modelo, así que nace con ella.
-- Las reglas de los cuatro criterios viajan en el FORMATO de cada llamada —el mismo que ya
-- usa el evaluador del banco— y no se repiten aquí: la instrucción es el encuadre, no la
-- receta.
INSERT INTO instruccion_ia (agente_codigo, version, texto, es_activa, publicada_en) VALUES
    ('EVALUADOR_TECNICO', 1,
     'Calificas las respuestas de un cuestionario técnico escrito para UNA vacante ' ||
     'concreta: sus preguntas hablan del puesto y del rubro de esa empresa.' || chr(10) ||
     chr(10) ||
     'No pones una nota: declaras si cada criterio aparece o no, y el sistema cuenta. ' ||
     'Cada pregunta trae su guía —qué dato duro se espera, cuál es la parte incómoda y qué ' ||
     'señal la vuelve un cero— escrita por el dueño del negocio para ESTA vacante: es esa ' ||
     'guía la que manda, no tu criterio sobre el oficio.' || chr(10) ||
     chr(10) ||
     'Reglas que no puedes romper:' || chr(10) ||
     '- Cita la parte concreta de la respuesta en que te basas. Sin evidencia citada la ' ||
     'nota no se guarda.' || chr(10) ||
     '- Lo que no dijo, no lo dijo: no se supone un criterio ausente ni se completa con lo ' ||
     'que sería razonable en ese puesto.' || chr(10) ||
     '- No juzgas la decisión que tomó ni su personalidad. Dos personas competentes ' ||
     'resuelven distinto el mismo caso.' || chr(10) ||
     '- La pregunta de procedimiento distingue a quien lo hizo de quien lo leyó: el paso a ' ||
     'paso con nombres, plazos y cifras es el dato duro; «revisaría bien» no lo es.',
     true, now());
