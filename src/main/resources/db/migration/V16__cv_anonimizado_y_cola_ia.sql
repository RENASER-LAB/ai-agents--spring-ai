-- Hito 2 · Lo que faltaba para que la IA pueda leer y calificar.
--
-- Dos cosas, y las dos son requisito, no mejora:
--
-- 1) El currículum que ve la IA. RF-41 dice que antes de que la IA lo lea hay que ocultar
--    foto, edad, sexo y estado civil, y que se guarde CUÁL de las dos versiones se envió.
--    Hasta ahora `cv` solo tenía `texto_extraido` (que nadie llenaba) y un hueco para el
--    archivo anonimizado. Se guarda el texto recortado en su propia columna: es lo que se
--    manda al modelo, y `ejecucion_ia.envio` conserva además el envío literal.
--
-- 2) Un índice para encontrar rápido los trabajos de IA atascados. El sondeo de reintentos
--    pregunta por estado + antigüedad cada minuto (Regla 3 del doc 03: si la IA falla se
--    reintenta, nunca se inventa una nota). El índice parcial que ya existía solo cubre
--    PENDIENTE; falta poder ver los EN_CURSO que se quedaron colgados.

ALTER TABLE cv ADD COLUMN texto_anonimizado text;
ALTER TABLE cv ADD COLUMN anonimizado_en timestamptz;

COMMENT ON COLUMN cv.texto_extraido IS
    'El texto completo del archivo, tal cual. Lo ve el equipo, nunca la IA (RF-42).';
COMMENT ON COLUMN cv.texto_anonimizado IS
    'La única versión que se le manda a la IA: sin foto, edad, sexo ni estado civil (RF-41).';

-- Cuándo lo tomó un trabajador. Sin esta columna no hay forma de distinguir un trabajo que
-- lleva dos segundos EN_CURSO de uno que se quedó colgado porque el proceso murió a mitad,
-- y el segundo se quedaría ahí para siempre sin que nadie lo reintentara.
ALTER TABLE trabajo_ia ADD COLUMN tomado_en timestamptz;

CREATE INDEX trabajo_ia_estado_creado_idx ON trabajo_ia (estado, creado_en);
