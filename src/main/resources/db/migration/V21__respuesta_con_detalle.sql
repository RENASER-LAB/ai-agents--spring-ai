-- Las respuestas de los formatos del banco v3.
--
-- Hasta aquí una respuesta era una opción elegida (`opcion_id`) o un texto. Al banco v0.1 le
-- bastaba. Los formatos del v3 no caben ahí:
--
--   SJT-R   una calificación de 1 a 5 por CADA opción
--   EF-4    dos opciones: la más parecida a uno y la menos
--   INV/DE  un conjunto de elementos marcados
--   SEC     los cinco pasos, ordenados
--   CD      un valor por cada campo del caso
--
-- ⚠️ **Esto es deuda técnica, decidida a sabiendas.** Lo correcto sería una tabla de detalle
-- —`respuesta_detalle(respuesta_id, opcion_id, valor, orden)`—, y así hay que dejarlo. Se
-- eligió jsonb para avanzar más rápido, y el precio es concreto:
--
--   · La base ya no puede comprobar que una opción exista ni que sea de esa pregunta. Esa
--     comprobación pasa a ser código, en ServicioEvaluacionImpl.validarDetalle, y si alguien
--     mete una respuesta por otra vía, entra sin que nadie la mire.
--   · Un informe que cruce respuestas ya no sale con SQL normal.
--   · Nada impide guardar un detalle con la forma de otro formato.
--
-- Ver docs/AVANCE-BANCO-V3-2026-08-19.md, sección «La deuda del jsonb».

-- Nullable a propósito: los ítems V se siguen respondiendo con texto, y las respuestas que ya
-- existan no tienen detalle. No se quita ni opcion_id ni texto.
ALTER TABLE respuesta ADD COLUMN detalle jsonb;

COMMENT ON COLUMN respuesta.detalle IS
    'Respuesta de los formatos v3 que no caben en una sola opción. Su forma depende de '
    'pregunta.tipo y la valida el código al guardar, no la base. Pendiente: pasar a tabla.';
