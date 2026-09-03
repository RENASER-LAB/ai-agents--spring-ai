-- El archivo de un entregable pasa a pertenecer a la empresa de la vacante, no a la plataforma.
--
-- QUE ESTABA MAL
-- --------------
-- Al subir un entregable, `ServicioPruebaImpl` sellaba el archivo con la organizacion de QUIEN
-- LO SUBE, y quien lo sube es el candidato: todas las cuentas del portal nacen en la
-- organizacion plataforma. El panel de la empresa, en cambio, busca ese archivo con
-- `findByIdAndOrganizacionId` usando la organizacion de la vacante.
--
-- Resultado: para cualquier empresa que no sea la plataforma, abrir o descargar un entregable
-- responde 404 sobre un archivo que el candidato si subio. El curriculum nunca tuvo el fallo
-- —`ServicioPostulacionPortalImpl` sella con la organizacion de la vacante, y lo dice en un
-- comentario—; al escribir la subida de entregables se perdio esa regla.
--
-- POR QUE NADIE LO HABIA VISTO
-- ----------------------------
-- RENASER es a la vez la plataforma y la unica empresa con vacantes, asi que las dos
-- organizaciones son la misma y los dos caminos coinciden por casualidad. Es la clase de fuga
-- que solo aparece con la segunda empresa, y la pantalla que enseña los entregables en el panel
-- —que llega con esta misma rama— es la primera que la habria destapado.
--
-- LO QUE ESTA MIGRACION HACE, Y LO QUE NO
-- ---------------------------------------
-- Reetiqueta SOLO los archivos que cuelgan de un `entregable`, y solo cuando difieren. No toca
-- los curriculums, que ya estan bien, ni los materiales de las plantillas de prueba, que son de
-- la empresa que escribio la plantilla y no de ninguna vacante.
--
-- Es idempotente: volver a correrla no cambia ninguna fila.

update archivo a
   set organizacion_id = p.organizacion_id
  from entregable e
  join intento_prueba i on i.id = e.intento_prueba_id
  join postulacion p on p.id = i.postulacion_id
 where a.id = e.archivo_id
   and a.organizacion_id <> p.organizacion_id;
