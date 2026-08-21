-- Los textos del banco v3 que quedaron cortados, completos por fin.
--
-- QUÉ PASABA. El importador del banco v3 leía los campos numerados de un CD quedándose
-- con el primer renglón de cada uno: cuando la lista de alternativas seguía en el renglón
-- de abajo —que es casi siempre, porque son largas— la tiraba. Así quedaron 24 etiquetas
-- cortadas a media lista en seis ítems (C03, D05, D14, D22, D34, O03): «medio día / un
-- día o» sin el «más)». Lo perdido eran justo las alternativas de respuesta, que en un CD
-- no viven en ninguna otra tabla. Y con la misma mecánica quedaron cortados 29 enunciados,
-- que además son texto que el candidato lee tal cual.
--
-- Un tercer defecto era el mismo accidente al revés: en los 31 CD que describen sus campos
-- corridos y separados por «·», el primer campo se guardó con la pregunta entera pegada
-- delante («Tu día típico. (5 campos) Hora en que despiertas ___»), repitiendo el
-- enunciado que la fila de pregunta ya tiene.
--
-- POR QUÉ PASÓ Y NADIE LO VIO. Las comprobaciones del importador cuentan y no leen: siete
-- campos cortados siguen siendo siete campos, y todos los totales cuadraban. Ya está
-- arreglado en scripts/importar-banco-v3.py (pliega las continuaciones, separa el
-- preámbulo y avisa de paréntesis sin pareja), y scripts/comparar-banco-v3-con-base.py
-- compara desde entonces lo guardado contra el PDF, texto a texto. Estos VALUES salieron
-- de ese comparador y se revisaron a mano contra el documento, fila a fila; los seis
-- enunciados que el parser no sabe leer enteros están decididos a mano y viven también en
-- el diccionario ENUNCIADOS_A_MANO del comparador, con su porqué.
--
-- SE PUEDE REPETIR SIN MIEDO. Los UPDATE de completar exigen que lo guardado sea el
-- principio exacto del texto nuevo (starts_with, como la V25); el de recortar exige
-- igualdad con el texto viejo exacto. En los dos casos una edición hecha a mano desde el
-- panel queda intacta: la fila simplemente no casa y no se toca.

-- ============================================================================
-- 1 · Las 24 etiquetas cortadas a media lista: se completan, nunca se pisan
-- ============================================================================
UPDATE campo_caso cc
   SET etiqueta = v.etiqueta
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES
    ('C03', 1, '¿Existía un formato de control diario? (no / uno que armé yo / uno de la empresa en papel / uno de la empresa en sistema)'),
    ('C03', 2, '¿Qué registrabas en él? (multi: avance / incidencias / asistencia / calidad / tiempos / nada, era mental)'),
    ('C03', 3, '¿A quién se lo entregabas? (a nadie / a mi jefe / al sistema / a la siguiente guardia)'),
    ('C03', 4, '¿Con qué frecuencia lo llenabas? (varias veces al día / al cierre del día / cuando había algo relevante / rara vez)'),
    ('C03', 6, '¿Podía otra persona leerlo y entender el estado del área? (sí / más o menos / no)'),
    ('D05', 1, '¿Con qué te diste cuenta? (reclamo de cliente / reporte de un compañero / indicador en sistema / revisión propia programada / me lo dijo mi jefe / lo noté en su conducta)'),
    ('D05', 2, 'Herramienta o documento principal que usaste (plan de mejora escrito / reunión de seguimiento con acta / tablero de indicadores / checklist de tarea / acompañamiento en terreno / capacitación formal / cambio de asignación / otro)'),
    ('D05', 3, 'Frecuencia de seguimiento aplicada (diario / 2 veces por semana / semanal / quincenal / mensual)'),
    ('D05', 5, 'Valor antes ___ · Valor después ___ · Unidad (% / soles / unidades / horas / n° de incidencias)'),
    ('D05', 7, '¿Qué quedó instalado después? (nada, se resolvió y ya / el plan quedó como formato del área / cambió el procedimiento / cambió el perfil de puesto / cambié mi frecuencia de supervisión)'),
    ('D14', 1, '¿Cuánto tardaste en enterarte? (mismo día / esa semana / al cierre del mes / cuando ya reclamó un cliente o gerencia)'),
    ('D14', 2, '¿Cómo te enteraste? (por el dato / por un reporte del equipo / por un tercero / por el cliente / por mi jefe)'),
    ('D14', 3, '¿Qué cambiaste en tu sistema de control? (nada / hablé con el responsable / agregué un indicador / cambié la frecuencia de revisión / cambié a un soporte con dato en tiempo real / agregué un punto de control en el proceso / cambié a la persona)'),
    ('D22', 2, '¿Era previsible? (sí, y no lo previne / sí, y falló la prevención que había / no, era imprevisible)'),
    ('D22', 3, 'Origen (falla de una persona / falla del proceso / cliente o factor externo / decisión de otra área / falta de información a tiempo)'),
    ('D22', 4, '¿Con qué frecuencia ocurre? (primera vez / ha pasado antes este año / pasa todos los meses / pasa todas las semanas)'),
    ('D22', 5, '¿Qué dejaste hecho para que no se repita? (nada, se resolvió / avisé a quien corresponde / cambié el procedimiento / agregué un punto de control / lo escalé)'),
    ('D34', 1, '¿En qué fallaste al elegir? (me fié del CV / me fié de la entrevista / me presionó la urgencia por cubrir / no verifiqué referencias / evalué lo técnico y no lo actitudinal / no definí bien el perfil antes de buscar)'),
    ('D34', 2, '¿Cuánto tardaste en darte cuenta? (primera semana / primer mes / segundo o tercer mes / después de seis meses)'),
    ('D34', 5, '¿Qué cambiaste en tu forma de contratar? (nada / agregué prueba práctica / agregué verificación de referencias / cambié el perfil / involucré a más evaluadores / alargué el periodo de prueba / definí indicadores desde el día 1)'),
    ('O03', 2, 'Cuánto te toma normalmente (< 30 min / 30 min–2 h / medio día / un día o más)'),
    ('O03', 3, 'Qué revisas ANTES de empezar (multi: instrucción o especificación / materiales o insumos / estado del equipo / trabajo anterior / nada, empiezo directo)'),
    ('O03', 4, 'Cómo sabes que quedó bien (la entrego y ya / la reviso contra un estándar o checklist / la mide alguien más / nadie reclama)'),
    ('O03', 6, 'Qué haces si sale mal (lo corrijo y aviso / lo corrijo callado / aviso y espero indicación / sigo, no siempre se puede corregir)')
  ) AS v(codigo, orden, etiqueta)
 WHERE cc.pregunta_id = p.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND p.codigo = v.codigo
   AND cc.orden = v.orden
   AND cc.etiqueta <> v.etiqueta
   AND starts_with(v.etiqueta, cc.etiqueta);

-- ============================================================================
-- 2 · El primer campo de los 31 CD sueltos, sin la pregunta pegada delante
-- ============================================================================
-- Son 31 y no los 3 del hallazgo inicial (D11, D19, D21) porque el defecto resultó ser de
-- toda la rama: en cada CD suelto el primer campo arrastraba el enunciado. El rótulo de la
-- casilla debe decir qué va en la casilla; la pregunta ya la dice la fila de pregunta.
-- La guarda de igualdad con el texto viejo exacto hace inofensiva la amplitud: solo se
-- recorta exactamente lo que la V20 dejó, y nada editado a mano después.
UPDATE campo_caso cc
   SET etiqueta = v.etiqueta_nueva
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES
    ('C04', 1, 'Los 3 indicadores que reportabas. Por cada uno (4 campos × 3): Nombre (texto ≤ 40 car.)', 'Nombre (texto ≤ 40 car.)'),
    ('C05', 1, 'Caso: un desvío que detectaste a tiempo. (5 campos) Qué se estaba desviando (texto ≤ 60 car.)', 'Qué se estaba desviando (texto ≤ 60 car.)'),
    ('C06', 1, 'Caso: un desvío que se te pasó. (4 campos) Qué pasó (texto ≤ 60 car.)', 'Qué pasó (texto ≤ 60 car.)'),
    ('C13', 1, 'Caso: levantaste el rendimiento de una persona concreta. (5 campos) Qué estaba fallando (texto ≤ 60 car.)', 'Qué estaba fallando (texto ≤ 60 car.)'),
    ('C18', 1, 'Tus reuniones o puntos de coordinación fijos. Hasta 4, con 4 campos: Con quién (mi equipo / mi jefe / otras áreas / cliente)', 'Con quién (mi equipo / mi jefe / otras áreas / cliente)'),
    ('C19', 1, 'Los 3 últimos problemas que resolviste en tus últimas 48 horas de trabajo. (5 campos × 3) Descripción (texto ≤ 60 car.)', 'Descripción (texto ≤ 60 car.)'),
    ('C25', 1, 'Tu mejor resultado del último año. (6 campos) Indicador (texto ≤ 40 car.)', 'Indicador (texto ≤ 40 car.)'),
    ('C26', 1, 'La vez que te faltó gente, tiempo o recursos y aun así cumpliste. (4 campos) Qué faltó (personal / tiempo / materiales / equipos / información)', 'Qué faltó (personal / tiempo / materiales / equipos / información)'),
    ('C27', 1, 'Una mejora que aplicaste a un proceso de tu área. (4 campos) Qué mejoraste (texto ≤ 60 car.)', 'Qué mejoraste (texto ≤ 60 car.)'),
    ('C34', 1, 'Tu día típico. (5 campos) Hora en que despiertas ___', 'Hora en que despiertas ___'),
    ('C46', 1, 'Lo último que hiciste por un compañero o cliente sin que te lo pidieran. (4 campos) Qué hiciste (texto ≤ 60 car.)', 'Qué hiciste (texto ≤ 60 car.)'),
    ('C48', 1, 'El estándar de calidad más alto que has exigido. (4 campos) Qué exigías (texto ≤ 60 car.)', 'Qué exigías (texto ≤ 60 car.)'),
    ('C49', 1, 'Dos cosas que empezaste sin que nadie te lo pidiera. (3 campos × 2) Qué era (texto ≤ 40 car.)', 'Qué era (texto ≤ 40 car.)'),
    ('D11', 1, 'Declara los 3 indicadores con los que dirigías tu área. Por cada uno (6 campos × 3 = 18 campos): Nombre (texto ≤ 40 car.)', 'Nombre (texto ≤ 40 car.)'),
    ('D19', 1, 'Caso: una decisión que tomaste leyendo un dato antes de que el problema explotara. (5 campos) Dato o señal que lo anticipó (texto ≤ 40 car.)', 'Dato o señal que lo anticipó (texto ≤ 40 car.)'),
    ('D21', 1, 'Tu mapa de reuniones fijas. Hasta 5 reuniones, 5 campos cada una: Con quién (lista)', 'Con quién (lista)'),
    ('D36', 1, 'Tu resultado más importante de los últimos 12 meses. (7 campos) Indicador (texto ≤ 40 car.)', 'Indicador (texto ≤ 40 car.)'),
    ('D41', 1, 'Un proceso que dejaste escrito y funcionando sin ti. (6 campos) Nombre del proceso (texto ≤ 40 car.)', 'Nombre del proceso (texto ≤ 40 car.)'),
    ('D42', 1, 'Una mejora que propusiste sin que nadie te la pidiera. (5 campos) Qué proponías (texto ≤ 60 car.)', 'Qué proponías (texto ≤ 60 car.)'),
    ('D54', 1, 'Tu día típico. (5 campos) Hora en que despiertas ___', 'Hora en que despiertas ___'),
    ('D72', 1, 'Lo último que hiciste por un cliente o colaborador sin que te lo pidieran y sin beneficio para ti. (4 campos) Qué hiciste (texto ≤ 60 car.)', 'Qué hiciste (texto ≤ 60 car.)'),
    ('D74', 1, 'El estándar de calidad más alto que has exigido. (4 campos) Qué exigías (texto ≤ 60 car.)', 'Qué exigías (texto ≤ 60 car.)'),
    ('D75', 1, 'Tres cosas que empezaste sin que nadie te lo pidiera. (3 campos × 3) Qué era (texto ≤ 40 car.)', 'Qué era (texto ≤ 40 car.)'),
    ('O05', 1, 'El trabajo más difícil que te tocó. (5 campos) Qué era (texto ≤ 60 car.)', 'Qué era (texto ≤ 60 car.)'),
    ('O16', 1, 'Una vez que te comprometiste a algo y no pudiste cumplir. (5 campos) Qué era (texto ≤ 60 car.)', 'Qué era (texto ≤ 60 car.)'),
    ('O21', 1, 'Un problema que tuviste con un compañero. (4 campos) Qué pasó (texto ≤ 60 car.)', 'Qué pasó (texto ≤ 60 car.)'),
    ('O23', 1, '¿Has enseñado tu trabajo a alguien? (4 campos) ¿A cuántas personas? ___', '¿A cuántas personas? ___'),
    ('O30', 1, 'Tu día típico. (5 campos) Hora en que despiertas ___', 'Hora en que despiertas ___'),
    ('O41', 1, 'La última vez que ayudaste a alguien sin que te lo pidieran. (4 campos) Qué hiciste (texto ≤ 60 car.)', 'Qué hiciste (texto ≤ 60 car.)'),
    ('O43', 1, 'Algo de lo que te sientes orgulloso en un trabajo. (4 campos) Qué fue (texto ≤ 60 car.)', 'Qué fue (texto ≤ 60 car.)'),
    ('O44', 1, 'Algo que mejoraste por iniciativa propia. (4 campos) Qué mejoraste (texto ≤ 60 car.)', 'Qué mejoraste (texto ≤ 60 car.)')
  ) AS v(codigo, orden, etiqueta_vieja, etiqueta_nueva)
 WHERE cc.pregunta_id = p.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND p.codigo = v.codigo
   AND cc.orden = v.orden
   AND cc.etiqueta = v.etiqueta_vieja;

-- ============================================================================
-- 3 · Los 29 enunciados cortados
-- ============================================================================
-- Mismo patrón que la V25 §3: solo se completa lo que es prefijo exacto del texto bueno.
-- Ninguno resultó necesitar otra guarda: en los 29, lo guardado es el principio del texto
-- verdadero. Diez no los puede leer entero el parser (C10, C36, C54, D08, D20, D40, D84,
-- O02, O32, O48: su lectura rompe en un «·» de la línea de continuación, o absorbería la
-- fórmula o la referencia a otra tabla): su texto se decidió leyendo el PDF y está
-- duplicado a propósito en ENUNCIADOS_A_MANO del comparador, que es quien vigila que base
-- y documento no se separen.
UPDATE pregunta p
   SET enunciado = v.enunciado
  FROM version_banco vb, (VALUES
    ('C01', 'Personas que supervisabas: ___ · Turnos o frentes simultáneos: ___ · ¿Alguno de tus supervisados dirigía a su vez a otros? (sí / no)'),
    ('C10', 'Faltas una semana completa sin previo aviso. Califica del 1 al 5 qué tan probable es que siga funcionando (1 = se cae · 5 = sigue igual).'),
    ('C19', 'Los 3 últimos problemas que resolviste en tus últimas 48 horas de trabajo. (5 campos × 3)'),
    ('C36', 'Actividad física: (nunca / esporádica / 1–2 por semana / 3–4 / diaria) · Años sosteniéndola: ___'),
    ('C42', 'Descubres una forma de que tu reporte se vea mejor sin que el trabajo real mejore.'),
    ('C46', 'Lo último que hiciste por un compañero o cliente sin que te lo pidieran. (4 campos)'),
    ('C54', '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Formato (lista) · Horas por semana ___ · Meses que llevas ___ · Evidencia (certificado / aplicado en el trabajo / ninguna aún)'),
    ('D06', 'Un colaborador cumplió su plan de mejora en el papel, pero el resultado del área no se movió.'),
    ('D08', 'De tu última semana típica: ___% dirigiendo personas · ___% ejecutando tareas propias · ___% en reuniones ajenas a tu área. (Debe sumar 100.)'),
    ('D11', 'Declara los 3 indicadores con los que dirigías tu área. Por cada uno (6 campos × 3 = 18 campos):'),
    ('D19', 'Caso: una decisión que tomaste leyendo un dato antes de que el problema explotara. (5 campos)'),
    ('D20', 'Te ausentas dos semanas sin previo aviso. Califica del 1 al 5 qué tan probable es que cada cosa siga funcionando (1 = se cae · 5 = sigue igual).'),
    ('D22', 'Los 3 últimos problemas que resolviste en tus últimas 48 horas de trabajo. Por cada uno (5 campos × 3):'),
    ('D29', 'De tus comunicaciones del último mes: ___% planificadas (con agenda previa) · ___% por emergencia. (Suman 100.)'),
    ('D38', 'A mitad de mes el plan se cayó: se fue una persona clave y un proveedor incumplió.'),
    ('D40', 'Presupuesto anual administrado: (no he manejado / < S/ 100K / 100K–500K / 500K–2M / 2M–10M / > 10M) Decisiones que tomabas (multi): aprobar gastos · reasignar partidas · negociar con proveedores · definir la estructura del presupuesto · solo ejecutaba lo aprobado'),
    ('D50', 'Relaciones laborales importantes cerradas en malos términos en los últimos 3 años: ___'),
    ('D57', 'Actividad física: (nunca / esporádica / 1–2 veces por semana / 3–4 veces / diaria) · Años sosteniendo esa frecuencia: ___'),
    ('D63', 'A los 4 meses de entrar te llega una oferta con 40% más de sueldo, en medio de un proyecto crítico que lideras.'),
    ('D65', 'Tu bono depende de un indicador. Descubres una forma legal de mejorar el número sin que el trabajo real mejore.'),
    ('D68', 'Del CV que enviaste, marca el dato que más te costaría sustentar con documento o referencia. (El sistema muestra los propios campos del CV cargado + opción "ninguno".)'),
    ('D72', 'Lo último que hiciste por un cliente o colaborador sin que te lo pidieran y sin beneficio para ti. (4 campos)'),
    ('D84', '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Formato (curso formal / autodidacta / con mentor / en el trabajo) · Horas por semana ___ · Meses que llevas ___ · Evidencia (certificado / proyecto aplicado / ninguna aún)'),
    ('O01', 'Años haciendo este trabajo: ___ · En cuántas empresas: ___ · Nombre exacto de tu último puesto: (texto ≤ 40 car.)'),
    ('O02', 'Escribe hasta 5 herramientas, equipos o programas que dominas para este puesto. (5 campos de texto ≤ 30 car.)'),
    ('O15', 'En tu último trabajo, el último mes: tardanzas ___ · faltas ___ · ¿avisaste con anticipación en cada caso? (siempre / a veces / no)'),
    ('O32', 'Actividad física: (nunca / esporádica / 1–2 por semana / 3–4 / diaria) · Años sosteniéndola: ___'),
    ('O47', 'Te indican hacer algo de una forma que, por tu experiencia, va a dañar el material o el resultado.'),
    ('O48', '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Cómo (curso / por mi cuenta / me enseña alguien / en el trabajo) · Horas por semana ___ · Meses que llevas ___')
  ) AS v(codigo, enunciado)
 WHERE p.version_banco_id = vb.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND p.codigo = v.codigo
   AND p.enunciado <> v.enunciado
   AND starts_with(v.enunciado, p.enunciado);
