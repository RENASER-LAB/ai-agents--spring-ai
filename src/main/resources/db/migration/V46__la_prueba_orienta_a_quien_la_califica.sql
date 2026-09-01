-- Cada prueba puede decirle al agente que la califica qué mirar. Sin quitarle el sitio a la
-- rúbrica.
--
-- QUÉ PASABA. El texto con el que se le habla al modelo vive en `instruccion_ia`, versionado
-- y editable desde el panel, pero es UNO para todo el mundo: esa tabla no tiene
-- `organizacion_id`, así que hay una sola instrucción activa por agente y la comparten todas
-- las empresas. La instrucción de PRUEBA_PUESTO habla del oficio de calificar —cita la
-- evidencia, no supongas lo que no dijo— y eso está bien, porque es lo único que vale para
-- todas: no puede decir qué distingue un buen tablero de control de uno malo en el rubro de
-- ESTA empresa, ni qué error de esta prueba concreta es el que de verdad descarta.
--
-- Quien sabe eso es quien escribió la prueba. Hasta hoy solo podía escribirlo en el enunciado
-- —que lo lee el candidato, no el modelo— o en la descripción de cada criterio, una frase
-- corta que se ve en la rúbrica del panel.
--
-- QUÉ CAMBIA. La versión de la plantilla gana un texto propio que se le da al agente antes de
-- calificar. Vive en la VERSIÓN y no en la organización ni en la vacante por la misma razón
-- que la rúbrica: es parte del instrumento, se congela al publicar, y viaja con la prueba
-- cuando una empresa se lleva una copia de la plataforma (CopiadorDeInstrumentos). Una prueba
-- publicada califica igual hoy que dentro de un año, y eso incluye con qué guía se calificó.

-- ============================================================================
-- 1 · La guía
-- ============================================================================
ALTER TABLE version_plantilla_prueba ADD COLUMN guia_calificacion text;

COMMENT ON COLUMN version_plantilla_prueba.guia_calificacion IS
    'Guía de calificación que esta prueba le da al agente PRUEBA_PUESTO: qué mirar, qué pesa '
    'en este oficio, qué error descarta. ORIENTA, NO SUSTITUYE. La rúbrica sigue siendo la '
    'fuente del 100 y el agente sigue devolviendo una nota POR CRITERIO, nunca una global: '
    'un texto que pida «califica sobre 100» no tiene dónde escribirse, porque las notas se '
    'guardan en nota_criterio por código de criterio. Tampoco cambia los puntos máximos, ni '
    'qué criterios son de agente y cuáles de persona, ni el formato JSON de la respuesta.';

-- ============================================================================
-- 2 · El tope de longitud, en la base y no solo en el DTO
-- ============================================================================
-- ⚠️ Este texto lo escribe una persona y acaba dentro del mensaje `system` de un modelo que
-- pone notas que alimentan el ranking. El límite del contrato REST (@Size en CrearVersion)
-- cubre el panel, pero no es el único camino a esta columna: CopiadorDeInstrumentosImpl
-- escribe la fila copiada directo por el repositorio y nunca ve el DTO, y las plantillas de
-- las convocatorias reales se han cargado más de una vez por SQL. Por esos caminos, esto es
-- lo único que hay.
--
-- Dos mil caracteres son unas trescientas palabras: de sobra para decir qué mirar en una
-- prueba, y poco para esconder dentro otra instrucción larga que compita con la de verdad.
ALTER TABLE version_plantilla_prueba
    ADD CONSTRAINT version_plantilla_prueba_guia_calificacion_check
        CHECK (guia_calificacion IS NULL OR length(guia_calificacion) <= 2000);

-- ============================================================================
-- 3 · El enunciado que se sube como archivo
-- ============================================================================
-- No hay columna nueva: `url_consigna` existe desde la V29 y es exactamente esto. Lo que
-- faltaba era poder llenarla sin entrar a la base — hasta hoy solo se llenaba por SQL o al
-- clonar una prueba de la plataforma— y eso es código, no esquema.
--
-- Se deja dicho aquí porque el comentario de la V29 habla de «el PDF del enunciado» sin
-- decir de dónde sale, y a partir de ahora sale de una subida del panel.
COMMENT ON COLUMN version_plantilla_prueba.url_consigna IS
    'El enunciado de la prueba en PDF o Word, para el enlace del correo PRUEBA_DISPONIBLE. '
    'Se sube desde el panel mientras la versión está en BORRADOR. Es el ENUNCIADO y nada '
    'más: quien lo suba sigue teniendo que definir preguntas, entregables y rúbrica, porque '
    'de ese archivo no sale ninguna nota. La validación de publicarVersion no lo mira.';
