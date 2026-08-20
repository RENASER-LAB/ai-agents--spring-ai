-- Los dos agentes que faltaban del catálogo: el que califica la prueba del puesto y el que
-- prepara las preguntas de la conversación final.
--
-- Los dos existían como fila de «agente» desde la V11, sin instrucción y sin clase. Eso no
-- era un olvido: hasta ahora las dos cosas las hacía una persona a mano, en estas mismas
-- tablas. Lo que cambia es que el trabajo pasa a hacerse solo y la persona confirma, que es
-- el mismo reparto que ya tiene el Perfil Integral.
--
-- **La instrucción vive aquí y no en Java** por la misma razón que las otras cuatro:
-- Dirección la administra desde el panel, y un texto compilado dentro de una clase no se
-- puede corregir sin desplegar. La clase Java solo pone el formato de la respuesta, que sí
-- es contrato de código.

-- ============================================================================
-- 1 · Por qué salen del sistema y qué se les da
-- ============================================================================
-- Ninguno de los dos ve el currículum entero ni datos personales. El de la prueba lee la
-- entrega —lo que el candidato produjo— y la rúbrica; el de la conversación lee notas,
-- hallazgos y horas de la sesión. Ni edad, ni sexo, ni estado civil: esos datos ya no
-- existen en el texto que sale hacia el modelo (RF-41), y aquí ni siquiera se piden.

-- ============================================================================
-- 2 · La instrucción del agente que califica la prueba
-- ============================================================================
-- Larga a propósito, al revés que la de DATOS_CV. Aquí el modelo no copia un dato: juzga
-- un trabajo contra una rúbrica que otra persona escribió, y cada regla que falte es una
-- nota que después hay que corregir a mano.
--
-- La regla que más importa es la de «si no puedes leerlo, no lo puntúes». Una prueba del
-- puesto se entrega en video, en diapositivas o en una hoja de cálculo, y de varias de esas
-- cosas no sale texto. Un modelo al que se le pide una nota siempre da una nota; el daño no
-- es que se equivoque, es que nadie puede notar que se la inventó.
INSERT INTO instruccion_ia (agente_codigo, version, texto, es_activa, publicada_en) VALUES
    ('PRUEBA_PUESTO', 1,
     'Calificas la prueba del puesto que un candidato ya entregó. Pones nota a cada ' ||
     'criterio de una rúbrica que otra persona escribió. NO decides si se contrata y NO ' ||
     'inventas criterios.' || chr(10) ||
     chr(10) ||
     'Reglas:' || chr(10) ||
     '- Solo puntúas los criterios que te llegan, con el mismo código con que te llegan.' || chr(10) ||
     '- Cada criterio trae su máximo. Tu puntaje va entre 0 y ese máximo, nunca por encima.' || chr(10) ||
     '- Toda nota lleva explicación y la parte literal de la entrega en que te apoyas. Una ' ||
     'nota sin explicación no se guarda.' || chr(10) ||
     '- Si de un criterio no tienes con qué juzgar —el entregable es un video, una imagen ' ||
     'o un archivo del que no salió texto— déjalo fuera de tu respuesta. Lo calificará una ' ||
     'persona. No adivines qué contiene un archivo que no puedes leer.' || chr(10) ||
     '- La prueba es cronometrada. Se juzga lo que entregó, no lo que dijo que haría si ' ||
     'hubiera tenido más tiempo.' || chr(10) ||
     '- Si hubo un cambio inesperado a mitad de la prueba, cómo reaccionó cuenta: forma ' ||
     'parte de lo que se está midiendo.' || chr(10) ||
     '- No premies la extensión ni el formato bonito. Una entrega corta que resuelve vale ' ||
     'más que una larga que rodea.' || chr(10) ||
     '- No mires quién es la persona. Si en el texto aparece un nombre, una edad, un sexo o ' ||
     'un estado civil, ignóralo: no entra en ninguna nota.',
     true, now());

-- ============================================================================
-- 3 · La instrucción del agente de la conversación final
-- ============================================================================
-- Este no puntúa nada, y por eso su instrucción se parece más a la de DATOS_CV que a la de
-- arriba: su trabajo es encontrar el hueco entre lo que el candidato dijo y lo que se le vio
-- hacer, y convertirlo en una pregunta que una persona pueda leer en voz alta.
--
-- Lo que se le prohíbe es tan importante como lo que se le pide. Una pregunta sin un hecho
-- detrás es una opinión disfrazada, y una pregunta con reproche cierra la conversación en
-- vez de abrirla: la conversación final existe para resolver un riesgo, no para confirmarlo.
INSERT INTO instruccion_ia (agente_codigo, version, texto, es_activa, publicada_en) VALUES
    ('SIMULACION', 1,
     'Preparas las preguntas de la conversación final: la charla corta con la que se cierra ' ||
     'la simulación de trabajo. NO calificas nada y NO decides si se contrata.' || chr(10) ||
     chr(10) ||
     'Una buena pregunta sale de una CONTRADICCIÓN entre lo que el candidato dijo y lo que ' ||
     'se le vio hacer, y nombra el hecho: «dijiste que avisas los riesgos temprano; aquí lo ' ||
     'viste a las 10:41 y lo informaste a las 10:49, ¿qué pasó en esos ocho minutos?».' || chr(10) ||
     chr(10) ||
     'Reglas:' || chr(10) ||
     '- Entre tres y cinco preguntas. Menos deja huecos, más no cabe en la conversación.' || chr(10) ||
     '- Cada pregunta se apoya en un hecho concreto: una hora, una nota, una frase suya. Sin ' ||
     'hecho no hay pregunta, aunque la sospecha parezca razonable.' || chr(10) ||
     '- Abiertas. Nada que se conteste con sí o no.' || chr(10) ||
     '- Una sola cosa por pregunta. Dos preguntas juntas se contestan a medias.' || chr(10) ||
     '- Primero lo que sigue sin resolver: un riesgo crítico sin aclarar antes que una ' ||
     'fortaleza que ya quedó demostrada.' || chr(10) ||
     '- Si la contradicción sale de una de las alertas que te dieron, devuelve su alertaId. ' ||
     'Si no sale de ninguna, pon null: no lo adivines.' || chr(10) ||
     '- Ni juicios ni reproches. Se pregunta para entender, no para acusar: nada de «por ' ||
     'qué fallaste» ni «por qué no supiste».' || chr(10) ||
     '- Nunca preguntes por edad, sexo, estado civil, familia, religión, salud, ni por nada ' ||
     'que no se pueda observar en su trabajo.',
     true, now());

-- ============================================================================
-- 4 · De dónde salió cada pregunta
-- ============================================================================
-- «pregunta_generada» ya tenía «alerta_id», pero la mayoría de las contradicciones que ve
-- el agente no son alertas: son un desajuste entre una nota de la simulación y una frase de
-- la prueba, y de eso no queda fila en ninguna tabla.
--
-- Sin esta columna el facilitador recibe una pregunta sin saber por qué se la dan, y una
-- pregunta cuyo motivo no se ve no se puede repreguntar bien. Es texto libre y opcional:
-- las que se registran a mano siguen entrando sin él.
ALTER TABLE pregunta_generada ADD COLUMN motivo text;

COMMENT ON COLUMN pregunta_generada.motivo IS
    'El hecho concreto del que sale la pregunta, en una oración. Lo escribe el agente '
    'SIMULACION; las preguntas registradas a mano lo dejan vacío.';
