-- El aviso de la prueba del puesto lleva el enunciado y a donde mandar lo que se haga.
--
-- QUE PASABA. Al entrar en la prueba salia el aviso generico —«tu postulacion avanza a X»—
-- que sirve para las otras cinco etapas pero no para esta: es el unico momento del recorrido
-- en que el candidato necesita algo mas que entrar, porque tiene que leer un enunciado y
-- entregar un trabajo.
--
-- Los primeros veintidos correos se mandaron a mano: se publicaba una version de la plantilla
-- con el PDF de arquitectura, se movia a esos trece, se publicaba otra con el de civil, se
-- movia a los nueve, y se restauraba la generica. Funciono porque nadie mas se movio en esos
-- minutos. Si alguien de otra vacante hubiera avanzado, habria recibido el enunciado
-- equivocado, y no habria forma de saberlo salvo leyendo el cuerpo del correo enviado.
--
-- QUE CAMBIA. El enlace del enunciado deja de vivir dentro del texto y pasa a tener columna
-- propia, asi que el correo lo saca de la version de la plantilla que le toca a SU vacante.
-- Reescribir la consigna ya no puede romper el aviso.

-- ============================================================================
-- 1 · Donde vive el enunciado
-- ============================================================================
ALTER TABLE version_plantilla_prueba ADD COLUMN url_consigna text;

COMMENT ON COLUMN version_plantilla_prueba.url_consigna IS
    'El PDF del enunciado, para el correo. Columna propia y no sacado del texto de materiales '
    'con una expresion regular: asi quien reescriba la consigna no rompe el aviso sin enterarse.';

-- Lo que ya estaba dentro del texto se sube a la columna. Se coge el primer enlace que
-- aparezca en el enunciado o en los materiales, que es como se venia leyendo.
UPDATE version_plantilla_prueba
   SET url_consigna = substring(
           coalesce(enunciado, '') || ' ' || coalesce(materiales, '')
           from 'https?://[^[:space:]]+')
 WHERE url_consigna IS NULL;

-- ============================================================================
-- 2 · A donde manda el candidato lo que hizo
-- ============================================================================
-- Va como parametro y no escrito en la plantilla porque es un telefono: cambia cuando cambia
-- la persona que atiende, y entonces hay que poder cambiarlo sin tocar el texto ni desplegar.
INSERT INTO parametro (organizacion_id, codigo, valor, tipo, descripcion, creado_en)
SELECT o.id, 'whatsapp_evidencia', '982255360', 'TEXTO',
       'El numero al que el candidato manda la evidencia de su prueba del puesto',
       now()
  FROM organizacion o
 WHERE NOT EXISTS (
       SELECT 1 FROM parametro p
        WHERE p.organizacion_id = o.id AND p.codigo = 'whatsapp_evidencia');

-- ============================================================================
-- 3 · El texto del aviso
-- ============================================================================
-- NO lleva el enlace al portal a proposito. Mientras la pantalla de entregables no este
-- terminada, mandar ahi al candidato es mandarlo a un sitio donde se atasca; la evidencia
-- llega por WhatsApp. Cuando el portal este listo, se anade {{enlace}} al texto desde el
-- panel —es una plantilla, no codigo— y deja de hacer falta ninguna migracion.
INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa, creado_en)
SELECT o.id, 'PRUEBA_DISPONIBLE', 1,
       'Tu prueba del puesto para {{vacante}}',
       'Hola {{nombre}}:' || chr(10) || chr(10) ||
       'Pasaste a la prueba del puesto para «{{vacante}}». Enhorabuena.' || chr(10) || chr(10) ||
       'DESCARGA AQUI EL ENUNCIADO:' || chr(10) ||
       '{{enlacePrueba}}' || chr(10) || chr(10) ||
       'Tienes {{plazo}} desde este correo.' || chr(10) || chr(10) ||
       'Cuando termines, manda tu evidencia por WhatsApp al {{whatsapp}}.' || chr(10) ||
       'Escribe tu nombre completo y a que puesto postulas, para saber de quien es.' || chr(10) || chr(10) ||
       'Si tienes cualquier duda, responde a este correo.' || chr(10) || chr(10) ||
       'Equipo de Talento — Renaser',
       true, now()
  FROM organizacion o
 WHERE NOT EXISTS (
       SELECT 1 FROM plantilla_correo p
        WHERE p.organizacion_id = o.id AND p.codigo = 'PRUEBA_DISPONIBLE');

-- Las versiones sueltas que se publicaron a mano para mandar los primeros veintidos se
-- jubilan: cumplieron su funcion y dejarlas activas confundiria a quien mire las plantillas.
-- No se borran — cada correo enviado apunta a la version con la que salio.
UPDATE plantilla_correo
   SET es_activa = false
 WHERE codigo = 'POSTULACION_AVANZA'
   AND position('pruebas-tecnicas' in cuerpo) > 0;
