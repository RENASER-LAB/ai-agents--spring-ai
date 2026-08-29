-- El tiempo objetivo se muda de la plantilla de evaluación al banco de preguntas.
--
-- La plantilla nació para MUESTREAR el banco: cuántas preguntas de cada tipo pedirle. Ese
-- mecanismo está retirado desde el banco v3 — lo dice ServicioEvaluacionImpl.armarOrden, que
-- aplica el banco entero y no lee ni una cuota. El banco CAZATALENTOS lo confirma: es fijo,
-- todos los del mismo nivel responden las mismas preguntas en el mismo orden.
--
-- Con las cuotas muertas, el número de minutos pertenece a quien de verdad lo determina: el
-- banco. Son sus 21, 18 o 15 preguntas las que se tardan en responder, no una receta que ya no
-- elige nada. Y mientras vivía en la plantilla nadie lo veía: DIRECCION estaba en 45 minutos
-- contra los 50-60 que pide docs/CAZATALENTOS-BANCO-RENASER.md, y llevaba así desde el 17/08.
--
-- Los valores son el tope del rango de ese documento: DIR 50-60, SUP 40-45, OPE 25-35. El tope
-- y no el medio porque la clienta declara pendiente cronometrar el primer envío real y ACORTAR
-- preguntas si alguien pasa de 60 minutos (parte 10 de su documento): se empieza por arriba y
-- se recorta con datos, no al revés.

ALTER TABLE version_banco ADD COLUMN minutos_objetivo integer;

COMMENT ON COLUMN version_banco.minutos_objetivo IS
    'Cuánto se espera que dure responder este banco. NULL = usar el de la plantilla de '
    'evaluación, que es de donde salía antes de la V44.';

-- ⚠️ Solo las PUBLICADAS de tipo NIVEL. Las ARCHIVADAS se quedan en NULL A PROPÓSITO: las
-- evaluaciones ya rendidas cuelgan de bancos v3 y v0.1, y su tiempo es el que tuvieron cuando
-- las respondieron. Ponerles el número nuevo reescribiría hacia atrás lo que se le dijo a esa
-- gente. Con NULL siguen leyendo el de su plantilla, exactamente como hasta hoy.
UPDATE version_banco SET minutos_objetivo = CASE nivel_puesto_codigo
        WHEN 'DIRECCION'   THEN 60
        WHEN 'SUPERVISION' THEN 45
        WHEN 'EJECUCION'   THEN 35
    END
 WHERE tipo_banco = 'NIVEL'
   AND estado = 'PUBLICADA'
   AND nivel_puesto_codigo IN ('DIRECCION', 'SUPERVISION', 'EJECUCION');
