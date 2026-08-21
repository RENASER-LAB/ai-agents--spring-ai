-- Las opciones que el banco v3 nunca llegó a tener, y cuatro enunciados que no lo eran.
--
-- QUÉ PASABA. De los 190 ítems del banco v3, 28 llegaron a producción sin una sola fila
-- en la tabla opcion: los 13 INV, los 6 DE y 9 de los 10 PC. Un INV que dice «marca lo
-- que haces siempre al iniciar tu jornada» no traía nada que marcar; un DE pedía
-- encontrar el error entre ocho afirmaciones que no estaban; un PC pedía elegir sin
-- opciones. Como la pantalla no deja entregar la evaluación con preguntas sin responder,
-- el candidato se quedaba atascado y no había manera de terminar.
--
-- POR QUÉ PASÓ. El contenido sí está en el PDF del cliente, pero maquetado distinto de
-- lo que el importador sabía leer: los INV llevan su lista corrida y separada por «·»,
-- los DE la llevan en una tabla de números en círculo, y los PC dicen sus alternativas
-- dentro de la propia frase («Sí / No», «(nunca / una vez / algunas veces)»). El script
-- comprobaba cuántos ítems había y cuánto sumaban, pero nunca que cada ítem trajera algo
-- que enseñar, así que el hueco pasó las cuatro comprobaciones sin ruido.
--
-- Ya está arreglado en scripts/importar-banco-v3.py, que además tiene una quinta
-- comprobación: si un ítem que se responde eligiendo se queda sin opciones, no genera
-- nada y dice cuál. Esta migración carga lo que faltaba en las bases ya cargadas.
--
-- SE PUEDE REPETIR SIN MIEDO. Los INSERT no duplican (llave única de pregunta y letra) y
-- los UPDATE solo tocan el enunciado que sigue roto. No borra ninguna respuesta: al
-- escribirla no había ni una guardada sobre estos 28 ítems.

-- ============================================================================
-- 1 · Las opciones que faltaban
-- ============================================================================
-- es_distractor marca el elemento inventado, que el candidato no distingue del real:
-- en INV son los que el documento señala con ⚑, y en DE los cuatro marcados con ✘.
-- Ni INV ni DE ni PC esconden número, así que valor y puntaje quedan nulos: en INV y DE
-- la nota sale de cuántos reales acertó y cuántos inventados se tragó, y un PC no suma.
--
-- Lo que NO se carga: el «Ninguno formal» con el que varias listas de INV terminan. El
-- puntaje del formato es reales marcados ÷ reales totales, así que meterlo como real
-- subiría el divisor de todos y nadie podría sacar el máximo; y meterlo como inventado
-- castigaría a quien dice la verdad. Quien no hace nada de la lista lo dice no marcando
-- nada, que da exactamente el mismo cero.
INSERT INTO opcion (pregunta_id, letra, texto, es_distractor)
SELECT p.id, v.letra, v.texto, v.es_distractor
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES
    ('D02', 'a', 'Evaluación 90°', false),
    ('D02', 'b', 'Evaluación 180°', false),
    ('D02', 'c', 'Evaluación 360°', false),
    ('D02', 'd', 'Escala gráfica de calificación', false),
    ('D02', 'e', 'Administración por objetivos (APO)', false),
    ('D02', 'f', 'Matriz 9-box', false),
    ('D02', 'g', 'Método de incidentes críticos', false),
    ('D02', 'h', 'Assessment center', false),
    ('D02', 'i', 'Evaluación por competencias conductuales', false),
    ('D02', 'j', 'Autoevaluación con validación de jefe', false),
    ('D02', 'k', 'Matriz de rendimiento cruzado 4Q', true),
    ('D02', 'l', 'Escala Bramwell de desempeño', true),
    ('D02', 'm', 'Índice ponderado ISO 30414-B', true),
    ('D03', 'a', 'Perfil de puesto', false),
    ('D03', 'b', 'Manual de organización y funciones', false),
    ('D03', 'c', 'Plan de trabajo anual', false),
    ('D03', 'd', 'Acta de reunión con acuerdos y responsables', false),
    ('D03', 'e', 'Memorando de llamada de atención', false),
    ('D03', 'f', 'Plan de mejora individual con plazo', false),
    ('D03', 'g', 'Informe de gestión mensual', false),
    ('D03', 'h', 'Matriz RACI', false),
    ('D03', 'i', 'Reglamento interno de trabajo', false),
    ('D03', 'j', 'Presupuesto de área', false),
    ('D03', 'k', 'Manual de procedimientos', false),
    ('D03', 'l', 'Ficha de trazabilidad funcional DS-118', true),
    ('D03', 'm', 'Acta de brecha operativa ISO 9004-B', true),
    ('D03', 'n', 'Matriz Kepner-Segal de asignación', true),
    ('D13', 'a', 'ERP (SAP / Oracle / Odoo)', false),
    ('D13', 'b', 'CRM (HubSpot / Salesforce / Zoho)', false),
    ('D13', 'c', 'Gestor de proyectos (Asana / Trello / Monday / Jira)', false),
    ('D13', 'd', 'Power BI o Looker', false),
    ('D13', 'e', 'Hojas de cálculo con fórmulas y tableros propios', false),
    ('D13', 'f', 'Sistema de control de asistencia', false),
    ('D13', 'g', 'Software de gestión de obra (S10 / MS Project / Primavera)', false),
    ('D13', 'h', 'Sistema de tickets o mesa de ayuda', false),
    ('D13', 'i', 'Software de planilla o RRHH', false),
    ('D13', 'j', 'Plataforma de trazabilidad SIGEP-4', true),
    ('D13', 'k', 'Módulo de control operativo Vantrix', true),
    ('D13', 'l', 'Suite de indicadores OKR-Tegra', true),
    ('D16', 'a', 'Supervisar se centra en la persona y su ejecución; controlar, en el proceso y su resultado', false),
    ('D16', 'b', 'Controlar exige un estándar previo contra el cual comparar', false),
    ('D16', 'c', 'El control puede delegarse a un sistema; la supervisión requiere criterio humano', false),
    ('D16', 'd', 'Un buen control detecta el desvío antes de que afecte el resultado', false),
    ('D16', 'e', 'Supervisar y controlar son sinónimos en la práctica', true),
    ('D16', 'f', 'Controlar significa estar presente físicamente', true),
    ('D16', 'g', 'Si el resultado salió bien, el control fue bueno', true),
    ('D16', 'h', 'El control solo aplica a áreas operativas', true),
    ('D17', 'a', 'Mi control dependía de su reporte, no de un dato propio', false),
    ('D17', 'b', 'Mis indicadores medían actividad, no resultado', false),
    ('D17', 'c', 'No había punto de control intermedio dentro del mes', false),
    ('D17', 'd', 'El estándar de "bien" nunca se definió con un número', false),
    ('D17', 'e', 'La persona no fue transparente', true),
    ('D17', 'f', 'Faltó exigirle más', true),
    ('D17', 'g', 'El equipo no estaba comprometido', true),
    ('D17', 'h', 'El plazo de un mes era demasiado corto', true),
    ('D28', 'a', 'Cifra objetivo', false),
    ('D28', 'b', 'Fecha límite', false),
    ('D28', 'c', 'Responsable nombrado', false),
    ('D28', 'd', 'Estándar de calidad esperado', false),
    ('D28', 'e', 'Recursos disponibles', false),
    ('D28', 'f', 'Punto de control intermedio', false),
    ('D28', 'g', 'Cómo se va a medir', false),
    ('D28', 'h', 'Contexto de por qué importa', false),
    ('D28', 'i', 'A quién acudir si se traba', false),
    ('D28', 'j', 'Ficha de conformidad previa', true),
    ('D28', 'k', 'Matriz de holgura del entregable', true),
    ('D28', 'l', 'Índice de compromiso declarado', true),
    ('D30', 'a', 'Misión del puesto', false),
    ('D30', 'b', 'Funciones principales', false),
    ('D30', 'c', 'Indicadores del puesto', false),
    ('D30', 'd', 'Competencias conductuales requeridas', false),
    ('D30', 'e', 'Formación mínima', false),
    ('D30', 'f', 'Experiencia mínima verificable', false),
    ('D30', 'g', 'Relaciones internas y externas', false),
    ('D30', 'h', 'Nivel de autonomía y decisión', false),
    ('D30', 'i', 'Banda salarial', false),
    ('D30', 'j', 'Plan de reemplazo o sucesión', false),
    ('D30', 'k', 'Índice de criticidad funcional', true),
    ('D30', 'l', 'Coeficiente de carga MOF-3', true),
    ('D30', 'm', 'Perfil de exposición operativa', true),
    ('D37', 'a', 'Objetivo con cifra', false),
    ('D37', 'b', 'Línea base', false),
    ('D37', 'c', 'Hitos con fecha', false),
    ('D37', 'd', 'Responsable por hito', false),
    ('D37', 'e', 'Presupuesto asignado', false),
    ('D37', 'f', 'Riesgos identificados', false),
    ('D37', 'g', 'Plan de contingencia', false),
    ('D37', 'h', 'Indicadores de avance', false),
    ('D37', 'i', 'Punto de control periódico', false),
    ('D37', 'j', 'Recursos requeridos', false),
    ('D37', 'k', 'Dependencias entre áreas', false),
    ('D37', 'l', 'Matriz de holgura crítica ZR', true),
    ('D37', 'm', 'Índice de madurez del entregable', true),
    ('D37', 'n', 'Ficha de alineación estratégica AE-2', true),
    ('D52', 'a', 'Sí', false),
    ('D52', 'b', 'No', false),
    ('D66', 'a', 'Que fui exigente pero justo', false),
    ('D66', 'b', 'Que fui duro y no le gustó en el momento', false),
    ('D66', 'c', 'Que lo apoyé', false),
    ('D66', 'd', 'Que no se dio cuenta de que estaba siendo evaluado', false),
    ('D70', 'a', 'Sí', false),
    ('D70', 'b', 'No', false),
    ('D79', 'a', 'La muestra es demasiado pequeña para concluir', false),
    ('D79', 'b', 'Las muestras tienen distinto tamaño y no son comparables', false),
    ('D79', 'c', 'No se sabe si son los mismos clientes en ambos meses', false),
    ('D79', 'd', 'No hay evidencia de que la política haya causado la mejora', false),
    ('D79', 'e', '4.6 no es una nota alta', true),
    ('D79', 'f', 'La encuesta debió ser anónima', true),
    ('D79', 'g', 'Falta indicar quién aplicó la encuesta', true),
    ('D79', 'h', 'Debió medirse trimestralmente por norma', true),
    ('D81', 'a', 'Trata como iguales tareas de dificultad y duración distintas', false),
    ('D81', 'b', 'No dice nada sobre la calidad de lo terminado', false),
    ('D81', 'c', 'No considera el resultado que esas tareas debían producir', false),
    ('D81', 'd', 'Se puede inflar fragmentando una tarea en varias', false),
    ('D81', 'e', 'No incluye las horas trabajadas', true),
    ('D81', 'f', 'Debería compararse con el promedio del sector', true),
    ('D81', 'g', 'No distingue al personal nuevo del antiguo', true),
    ('D81', 'h', 'Debería medirse mensualmente y no semanalmente', true),
    ('D85', 'a', 'Revisar mis indicadores', false),
    ('D85', 'b', 'Revisar la agenda del día antes de empezar', false),
    ('D85', 'c', 'Un contacto directo con cada reporte clave', false),
    ('D85', 'd', 'Cerrar el día dejando definidas las prioridades de mañana', false),
    ('D85', 'e', 'Actividad física', false),
    ('D85', 'f', 'Leer algo de mi rubro', false),
    ('D85', 'g', 'Registrar las decisiones tomadas', false),
    ('D85', 'h', 'Revisar el correo a horas fijas y no todo el día', false),
    ('D85', 'i', 'Un bloque sin interrupciones para trabajo profundo', false),
    ('D85', 'j', 'Contacto con al menos un cliente', false),
    ('D85', 'k', 'Revisión de holgura operativa', true),
    ('D85', 'l', 'Cierre de ciclo documental diario', true),
    ('D85', 'm', 'Chequeo de índice de carga', true),
    ('C02', 'a', 'Asistencia del personal', false),
    ('C02', 'b', 'Estado de equipos o herramientas', false),
    ('C02', 'c', 'Avance del día anterior contra lo programado', false),
    ('C02', 'd', 'Materiales o insumos disponibles', false),
    ('C02', 'e', 'Pendientes heredados del turno anterior', false),
    ('C02', 'f', 'Condiciones de seguridad del área', false),
    ('C02', 'g', 'Programación del día por persona', false),
    ('C02', 'h', 'Incidencias registradas', false),
    ('C02', 'i', 'Entrega formal de turno', false),
    ('C02', 'j', 'Ficha de holgura de turno', true),
    ('C02', 'k', 'Checklist de conformidad TR-2', true),
    ('C02', 'l', 'Registro de carga operativa cruzada', true),
    ('C16', 'a', 'Registro de asistencia o tareo', false),
    ('C16', 'b', 'Acta de reunión de equipo', false),
    ('C16', 'c', 'Reporte de incidencia', false),
    ('C16', 'd', 'Solicitud de permiso o licencia', false),
    ('C16', 'e', 'Reporte de horas extra', false),
    ('C16', 'f', 'Evaluación de desempeño del equipo', false),
    ('C16', 'g', 'Registro de entrega de EPP o herramientas', false),
    ('C16', 'h', 'Acta de llamada de atención', false),
    ('C16', 'i', 'Programación semanal por persona', false),
    ('C16', 'j', 'Ficha de rotación funcional RF-1', true),
    ('C16', 'k', 'Registro de conformidad de turno ISO-T', true),
    ('C16', 'l', 'Planilla de holgura asignada', true),
    ('C33', 'a', 'Sí', false),
    ('C33', 'b', 'No', false),
    ('C44', 'a', 'Sí', false),
    ('C44', 'b', 'No', false),
    ('C51', 'a', 'Las bases son distintas: 20 tareas frente a 60', false),
    ('C51', 'b', 'En trabajo real se hizo menos esta semana (19 frente a 48)', false),
    ('C51', 'c', 'El porcentaje sube si se programan menos tareas, sin mejorar nada', false),
    ('C51', 'd', 'No se explica por qué se programaron menos tareas', false),
    ('C51', 'e', 'Falta el nombre del responsable del reporte', true),
    ('C51', 'f', 'Debió compararse contra el mes y no contra la semana', true),
    ('C51', 'g', '95% no es un buen resultado', true),
    ('C51', 'h', 'Falta indicar en qué turno se ejecutaron', true),
    ('C55', 'a', 'Revisar la programación antes de empezar', false),
    ('C55', 'b', 'Recorrer el área o el frente', false),
    ('C55', 'c', 'Hablar con cada persona clave', false),
    ('C55', 'd', 'Registrar lo ocurrido en el día', false),
    ('C55', 'e', 'Dejar definido el trabajo de mañana', false),
    ('C55', 'f', 'Revisar el estado de equipos o materiales', false),
    ('C55', 'g', 'Actividad física', false),
    ('C55', 'h', 'Revisar mis indicadores', false),
    ('C55', 'i', 'Cierre documental de holgura', true),
    ('C55', 'j', 'Chequeo de índice de carga', true),
    ('C55', 'k', 'Registro de conformidad cruzada', true),
    ('O14', 'a', 'Revisar los pendientes del día anterior', false),
    ('O14', 'b', 'Verificar materiales o herramientas', false),
    ('O14', 'c', 'Revisar mi programación del día', false),
    ('O14', 'd', 'Ordenar y limpiar mi puesto al cerrar', false),
    ('O14', 'e', 'Dejar registrado lo avanzado', false),
    ('O14', 'f', 'Avisar lo que queda pendiente al que sigue', false),
    ('O14', 'g', 'Revisar el estado de mi equipo', false),
    ('O14', 'h', 'Firmar la ficha de holgura', true),
    ('O14', 'i', 'Registrar el índice de carga diaria', true),
    ('O14', 'j', 'Cerrar el ciclo documental TR-4', true),
    ('O18', 'a', 'Uso de equipo de protección personal', false),
    ('O18', 'b', 'Orden y limpieza del puesto de trabajo', false),
    ('O18', 'c', 'Reporte de incidentes o casi accidentes', false),
    ('O18', 'd', 'Verificación del equipo antes de usarlo', false),
    ('O18', 'e', 'Señalización del área de trabajo', false),
    ('O18', 'f', 'Manejo y rotulado de materiales', false),
    ('O18', 'g', 'Participación en charlas de seguridad', false),
    ('O18', 'h', 'Uso de permisos de trabajo', false),
    ('O18', 'i', 'Registro de holgura de riesgo', true),
    ('O18', 'j', 'Ficha de conformidad SR-5', true),
    ('O18', 'k', 'Chequeo de carga cruzada', true),
    ('O19', 'a', 'Sí', false),
    ('O19', 'b', 'No', false),
    ('O29', 'a', 'nunca', false),
    ('O29', 'b', 'una vez', false),
    ('O29', 'c', 'algunas veces', false),
    ('O39', 'a', 'Sí', false),
    ('O39', 'b', 'No', false),
    ('O46', 'a', 'Si hay un error, llegará al cliente y costará más corregirlo después', false),
    ('O46', 'b', 'La revisión final existe justamente para las entregas apuradas', false),
    ('O46', 'c', 'Si se salta una vez, es más fácil que se vuelva costumbre', false),
    ('O46', 'd', 'Conviene proponer entregar un poco después, o revisar solo lo crítico', false),
    ('O46', 'e', 'Hay que negarse y no entregar', true),
    ('O46', 'f', 'No es mi problema, yo solo ejecuto', true),
    ('O46', 'g', 'Hay que hacerlo sin comentarios, el jefe manda', true),
    ('O46', 'h', 'Conviene avisar al cliente que no se revisó, sin decírselo al jefe', true),
    ('O49', 'a', 'Llegar antes de la hora de entrada', false),
    ('O49', 'b', 'Revisar qué tengo que hacer antes de empezar', false),
    ('O49', 'c', 'Dejar mi puesto ordenado al salir', false),
    ('O49', 'd', 'Avisar los pendientes al que sigue', false),
    ('O49', 'e', 'Revisar mi trabajo antes de entregarlo', false),
    ('O49', 'f', 'Preguntar cuando no entiendo algo', false),
    ('O49', 'g', 'Actividad física', false),
    ('O49', 'h', 'Firmar la conformidad de holgura', true),
    ('O49', 'i', 'Registrar el índice de carga', true),
    ('O49', 'j', 'Cerrar el ciclo TR-4', true)
  ) AS v(codigo, letra, texto, es_distractor)
 WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%'
ON CONFLICT (pregunta_id, letra) DO NOTHING;

-- ============================================================================
-- 2 · La opción «d2» de D66, que nunca fue una opción
-- ============================================================================
-- El importador leía las opciones en todo el texto del ítem, y la regla de abajo
-- («Contradicción: opción (d) + frecuencia de seguimiento "diario"...») lleva dentro un
-- «(d)» que se coló como si fuera una quinta alternativa. Al candidato le aparecía la
-- penalización escrita entre las respuestas.
--
-- Se borra solo si nadie la eligió. Si alguien la hubiera elegido, la fila se queda y hay
-- que decidir a mano qué hacer con esa respuesta: aquí no se tira el trabajo de nadie.
DELETE FROM opcion o
 USING pregunta p, version_banco vb
 WHERE o.pregunta_id = p.id
   AND p.version_banco_id = vb.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND p.codigo = 'D66'
   AND o.letra = 'd2'
   AND NOT EXISTS (SELECT 1 FROM respuesta r WHERE r.opcion_id = o.id);

-- ============================================================================
-- 3 · Los enunciados que quedaron a medias
-- ============================================================================
-- El importador se quedaba con la primera línea del PDF, y una pregunta rara vez cabe en
-- una línea. Así, D52 preguntaba «Tus 3 últimos jefes: nombre · cargo · empresa ·
-- teléfono o correo.» y se comía justo lo que hay que contestar: «¿Autorizas que los
-- contactemos? Sí / No».
--
-- La condición de abajo es la garantía de que esto solo completa y nunca reemplaza: se
-- exige que lo guardado sea el principio exacto del texto nuevo. Si alguien editó ese
-- enunciado a mano desde el panel, no se le pisa.
UPDATE pregunta p
   SET enunciado = v.enunciado
  FROM version_banco vb, (VALUES
    ('C33', 'Tus 2 últimos jefes: nombre · cargo · empresa · contacto. ¿Autorizas que los contactemos? Sí / No'),
    ('C43', 'De los tres problemas de tus últimas 48 horas, ¿cuántos se repiten todos los meses? ___'),
    ('C51', 'Lee: "Esta semana cumplimos el 95% de las tareas, mejor que el 80% de la semana pasada. Esta semana se programaron 20 tareas; la anterior, 60." Marca todos los problemas de esa comparación.'),
    ('D02', 'Marca los instrumentos de evaluación de desempeño que has aplicado tú mismo.'),
    ('D17', 'Tu reporte directo te dice "todo bien" cada semana y a fin de mes el resultado no salió. Marca las causas reales.'),
    ('D52', 'Tus 3 últimos jefes: nombre · cargo · empresa · teléfono o correo. ¿Autorizas que los contactemos? Sí / No'),
    ('D66', 'En el caso del trabajador que recuperaste, ¿qué habría dicho esa persona de cómo la manejaste?'),
    ('D67', 'De los tres problemas de tus últimas 48 horas, ¿cuántos se repiten todos los meses? ___'),
    ('D70', 'Autorizo la verificación de referencias laborales, certificados y cifras declaradas en este formulario. Sí / No'),
    ('D79', 'Lee: "La satisfacción del cliente subió de 4.1 a 4.6 tras la nueva política. Encuestamos a 12 clientes en junio y a 9 en julio. Es la mejora más grande del año." Marca todos los problemas reales de esta afirmación.'),
    ('D81', 'Un gerente propone medir productividad así: tareas terminadas ÷ n° de trabajadores. Marca todos los problemas de ese indicador.'),
    ('O19', '¿Por qué saliste de tu último trabajo? (lista + texto ≤ 60 car.) · Nombre y contacto de tu jefe anterior. ¿Autorizas que lo llamemos? Sí / No'),
    ('O29', '¿Alguna vez no cumpliste un compromiso de trabajo? (nunca / una vez / algunas veces)'),
    ('O39', 'Autorizo la verificación de mis referencias, certificados y datos declarados. Sí / No'),
    ('O46', 'Tu jefe dice: "Hay que apurar la entrega, saltemos la revisión final para llegar a la hora." Marca todo lo que es cierto sobre esa decisión.')
  ) AS v(codigo, enunciado)
 WHERE p.version_banco_id = vb.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND p.codigo = v.codigo
   AND p.enunciado <> v.enunciado
   AND starts_with(v.enunciado, p.enunciado);

-- ============================================================================
-- 4 · Los cuatro que preguntaban «Opción     Valor»
-- ============================================================================
-- Eso no era la pregunta: era el rótulo de las dos columnas de la tabla del PDF, que el
-- importador tomó por texto del ítem. Se ve en el banco de EJECUCION, ítem 37 (O37), y en
-- otros tres: D51, D60 y C40.
--
-- OJO, Y HAY QUE DECIRLO: en el documento del cliente estos cuatro ítems no tienen
-- enunciado propio. Arrancan directamente en su tabla de cuatro opciones. Lo que se
-- escribe aquí es la instrucción del formato EF-4 tal como la define la sección 0.2 del
-- propio documento —marcar la más parecida y la menos—, no una pregunta inventada. Si
-- Renaser quiere un enunciado propio para alguno, se cambia desde el panel sin desplegar.
UPDATE pregunta p
   SET enunciado = v.enunciado
  FROM version_banco vb, (VALUES
    ('C40', 'Marca la afirmación MÁS parecida a ti y la MENOS parecida a ti (no puede ser la misma).'),
    ('D51', 'Marca la afirmación MÁS parecida a ti y la MENOS parecida a ti (no puede ser la misma).'),
    ('D60', 'Marca la afirmación MÁS parecida a ti y la MENOS parecida a ti (no puede ser la misma).'),
    ('O37', 'Marca la afirmación MÁS parecida a ti y la MENOS parecida a ti (no puede ser la misma).')
  ) AS v(codigo, enunciado)
 WHERE p.version_banco_id = vb.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND p.codigo = v.codigo
   AND p.enunciado LIKE 'Opci_n%';
