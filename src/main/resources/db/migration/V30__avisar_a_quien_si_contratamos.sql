-- El unico candidato que no recibia ningun correo era el que contratabamos.
--
-- QUE PASABA. MaquinaEstados.avisarAlCandidato solo genera correo en tres casos: NO_CONTINUA,
-- CERRADA, y los estados cuyo `espera_a` es CANDIDATO. CONTRATADO no es ninguno de los tres
-- —es final, y no espera nada de nadie—, asi que caia en el `return` de abajo y salia sin
-- avisar. Al que le decimos que no, se entera. Al que le decimos que si, no.
--
-- Es el punto B7 del checklist («comprobar explicitamente si el candidato contratado recibe
-- correo») y la mitad de CP-10, que pide contratacion registrada, candidato notificado y
-- ficha de colaborador creada.
--
-- POR QUE UNA PLANTILLA Y NO UN TEXTO EN EL CODIGO. Las otras siete viven aqui, se versionan
-- por fila y el panel las edita sin tocar el codigo. Ademas `correo_enviado` guarda el cuerpo
-- ya armado: si dentro de un ano alguien pregunta que le dijimos exactamente, se lee. Un
-- literal en Java rompe las dos cosas.
--
-- El {{enlace}} va por lo mismo que en la V26: un aviso que dice «entra a tu panel» sin decir
-- por donde no sirve de nada, y a los candidatos que entraron por una carpeta de curriculums
-- les creo la cuenta el cargador, con una contrasena que nadie les dijo.

INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa)
SELECT o.id, p.codigo, 1, p.asunto, p.cuerpo, true
FROM organizacion o,
     (VALUES
        ('POSTULACION_CONTRATADA', 'Te damos la bienvenida a Renaser · {{vacante}}',
         'Hola {{nombre}}:' || E'\n\n'
         || 'Nos alegra decirte que quedaste seleccionada o seleccionado para «{{vacante}}». '
         || 'Gracias por el tiempo que le dedicaste a cada etapa del proceso.' || E'\n\n'
         || 'En los proximos dias te escribiremos para acordar tu incorporacion. '
         || 'Mientras tanto puedes ver el estado de tu postulacion aqui:' || E'\n'
         || '{{enlace}}' || E'\n\n'
         || 'Tu codigo de postulacion es {{codigo}}, por si necesitas escribirnos.' || E'\n\n'
         || 'Equipo de Talento · Renaser')
     ) AS p(codigo, asunto, cuerpo)
WHERE o.codigo = 'RENASER';
