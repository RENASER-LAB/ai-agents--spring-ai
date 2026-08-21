-- El aviso de que la postulacion avanza ahora lleva el enlace para entrar.
--
-- QUE PASABA. El texto decia «Entra a tu panel para ver que sigue» y no daba ni la direccion
-- ni la forma de entrar. Para quien se postulo por el portal eso era solo incomodo: tiene su
-- contrasena. Para los candidatos de una convocatoria cargada como carpeta de curriculums era
-- imposible: su cuenta la creo el cargador, con un correo inventado y una clave que nadie les
-- dijo. Diecinueve personas recibieron ese aviso y no pudieron hacer nada con el.
--
-- Y no daba ninguna señal: el correo salio, quedo registrado como ENVIADO, y el sistema los
-- dio por avisados. Se descubrio leyendo el cuerpo de uno de los correos ya enviados.
--
-- QUE CAMBIA. MaquinaEstados genera un enlace de acceso al avisar y lo pasa como {{enlace}}.
-- El enlace entra sin contrasena, vence, y cada uno reemplaza al anterior: el que vale es
-- siempre el del ultimo correo.
--
-- Se publica como version NUEVA de la plantilla y no se edita la vieja: cada correo ya
-- enviado guarda el texto exacto que salio y con que version, y reescribir la anterior haria
-- que un candidato que reclame lea algo distinto de lo que recibio.

INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa, creado_en)
SELECT p.organizacion_id,
       p.codigo,
       p.version + 1,
       p.asunto,
       'Hola {{nombre}}:' || chr(10) || chr(10) ||
       'Tu postulacion a «{{vacante}}» paso a la siguiente parte del proceso: {{estado}}.' || chr(10) || chr(10) ||
       'Entra desde aqui, sin contraseña:' || chr(10) ||
       '{{enlace}}' || chr(10) || chr(10) ||
       'El enlace es personal: no lo compartas. Si caduca, escribenos respondiendo a este correo y te mandamos uno nuevo.' || chr(10) || chr(10) ||
       'Equipo de Talento — Renaser',
       true,
       now()
  FROM plantilla_correo p
 WHERE p.codigo = 'POSTULACION_AVANZA'
   AND p.es_activa
   AND NOT EXISTS (
         SELECT 1 FROM plantilla_correo n
          WHERE n.organizacion_id = p.organizacion_id
            AND n.codigo = p.codigo
            AND n.cuerpo LIKE '%{{enlace}}%');

-- Solo puede haber una activa por codigo: la vieja se jubila.
UPDATE plantilla_correo p
   SET es_activa = false
 WHERE p.codigo = 'POSTULACION_AVANZA'
   AND p.cuerpo NOT LIKE '%{{enlace}}%';
