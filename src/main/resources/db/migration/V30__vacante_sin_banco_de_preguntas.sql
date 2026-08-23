-- Una vacante puede prescindir del banco de preguntas (22/08/2026).
--
-- Es V30 y no V29: Ricardo tomó ese número el mismo día para el aviso de la prueba
-- («el aviso de la prueba lleva su enunciado»). Dos migraciones con el mismo número y
-- Flyway no arranca.
--
-- Lo pidió Renaser para la vacante de Administrador: su cuestionario técnico ES la
-- evaluación completa. Se carga como prueba del puesto, la califica el agente
-- PRUEBA_PUESTO, y la etapa del Perfil Integral se queda sin nada que preguntar.
--
-- Con el interruptor apagado, quien postula no recibe evaluación: su postulación va
-- directa a PERFIL_POR_CONFIRMAR, donde el equipo decide a quién invitar a la prueba.
-- El peso de la etapa saltada no se reparte aquí: eso lo dice la versión de pesos que
-- la vacante tenga asignada, como siempre (RF-114).
ALTER TABLE vacante
    ADD COLUMN aplica_evaluacion boolean NOT NULL DEFAULT true;
