-- ============================================================================
-- El plazo para responder la evaluacion deja de estar escrito en el codigo
-- ============================================================================
--
-- Estaba como constante en ServicioEvaluacionImpl (DIAS_DE_PLAZO = 14). Cambiarlo
-- obligaba a recompilar y desplegar, y es justo la clase de numero que se mueve: una
-- convocatoria que se manda un viernes no da los mismos dias utiles que una de un lunes,
-- y si la invitacion sale dos dias despues de reabrir, el plazo real se acorta dos dias.
--
-- Va como parametro por la misma razon que los umbrales de prioridad: Renaser tiene que
-- poder corregirlo desde el panel sin que nadie toque el codigo.
--
-- El valor de 14 es el que ya estaba en el codigo: esta migracion no cambia el
-- comportamiento de nadie, solo lo hace editable.

INSERT INTO parametro (organizacion_id, codigo, valor, tipo, descripcion)
SELECT o.id, 'dias_plazo_evaluacion', '14', 'ENTERO',
       'Cuantos dias tiene el candidato para responder su evaluacion desde que se le habilita'
FROM organizacion o
ON CONFLICT (organizacion_id, codigo) DO NOTHING;
