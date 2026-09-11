-- Una vacante puede calificar y avanzar sola hasta la prueba del puesto (10/09/2026).
--
-- Hasta ahora el recorrido se paraba tres veces en sitios donde nadie tenía nada que
-- decidir: el currículum se leía pero no se calificaba, la prueba del puesto entregada
-- esperaba a que alguien pulsara «calificar», y pasar del Perfil Integral a la prueba
-- era un clic que casi siempre era «sí».
--
-- Con el interruptor encendido, una postulación viaja sola desde que se envía hasta que
-- termina la prueba del puesto, y la primera vez que hace falta una persona es para
-- decidir quién va a la etapa presencial. Corregir una nota o volver a calificar a mano
-- sigue estando disponible en todo momento.
--
-- Nace APAGADO a propósito, y no es timidez: en automático cada persona que postule
-- cuesta una llamada al modelo en el momento de postular, y esa decisión es de quien
-- lleva la vacante. Ninguna vacante viva cambia de comportamiento al desplegar esto.
ALTER TABLE vacante
    ADD COLUMN calificacion_automatica boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN vacante.calificacion_automatica IS
    'Encendido: al postular se califica el currículum (si la vacante no lleva banco), al '
    'terminar la calificación la postulación pasa sola a la etapa técnica, y la prueba del '
    'puesto se califica sola al entregarse. Apagado: cada paso lo pide una persona.';

-- Y una red que hasta ahora no hacía falta.
--
-- Entrar a la etapa técnica crea lo que el candidato va a rendir. Con la prueba del
-- puesto eso no se puede duplicar: `intento_prueba` lo impide con una clave única desde
-- la V15. Con el cuestionario técnico la regla la ponía un `if` en el código, y hasta
-- ahora bastaba porque solo había un camino de entrada: una persona pulsando «confirmar».
--
-- Desde esta migración hay dos, y pueden coincidir en el mismo segundo: el retrato
-- termina y publica su aviso justo cuando quien tenía la ficha abierta pulsa el botón.
-- Los dos leen que todavía no hay examen, los dos lo crean, y el segundo en escribir se
-- queda la columna dejando el primero —con sus respuestas y sus notas— sin dueño.
--
-- Es un índice PARCIAL: la inmensa mayoría de las postulaciones no han llegado a la etapa
-- técnica y tienen la columna vacía, y en Postgres los nulos no se estorban entre sí.
CREATE UNIQUE INDEX IF NOT EXISTS postulacion_evaluacion_tecnica_unica
    ON postulacion (evaluacion_tecnica_id)
    WHERE evaluacion_tecnica_id IS NOT NULL;
