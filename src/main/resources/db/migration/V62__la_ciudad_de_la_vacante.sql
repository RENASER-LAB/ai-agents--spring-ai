-- ============================================================================
-- La ciudad de la vacante, sacada del mismo catálogo que la del candidato
-- ============================================================================
--
-- Hasta aquí, dónde está el puesto era un texto libre en vacante.ubicacion: «Lima»,
-- «LIMA» y «Selva Alegre» conviven, y el portal no puede filtrar por ciudad con eso. Desde
-- ahora la vacante lleva una ciudad del catálogo ubigeo (V47) —una provincia o «Fuera del
-- Perú», el mismo tipo que persona.ciudad_ubigeo— y el texto de siempre pasa a ser la «zona o
-- referencia»: el barrio, el distrito o la dirección, que se ve en la ficha y no se filtra.
--
--
-- LO QUE ESTA MIGRACIÓN NO HACE
--
-- No renombra ni toca vacante.ubicacion. Es lo que el equipo escribió y sigue viéndose tal
-- cual; solo cambia cómo se llama en las pantallas.
--
--
-- EL RESCATE, Y POR QUÉ ES TAN ESTRICTO
--
-- Se rescata la ciudad SOLO cuando el texto de ubicación —sin espacios de los bordes, sin
-- mayúsculas y sin tildes— es exactamente el nombre de UNA sola provincia. «Lima» y «LIMA»
-- quedan en Lima; «Arequipa, Perú», «Selva Alegre» y «test» se quedan sin ciudad, y las
-- elige el equipo desde el panel. Al revés que la V47, no se parte por comas ni se prueba
-- con el segundo trozo: aquí quien escribió el texto es el equipo y va a volver a tocar la
-- vacante; adivinar de más le pondría a una convocatoria una ciudad que nadie decidió, y una
-- vacante con la ciudad equivocada se filtra mal para cada persona que busca.
--
-- Si el nombre casara con varias provincias se deja vacía. Hoy las 196 son distintas, pero
-- la regla se escribe para el día en que no lo sean.

ALTER TABLE vacante ADD COLUMN ciudad_ubigeo varchar(6) REFERENCES ubigeo(codigo);

COMMENT ON COLUMN vacante.ciudad_ubigeo IS
    'Ciudad del puesto: provincia del catalogo ubigeo o EXT. Vacio = sin ciudad. ubicacion queda como zona o referencia.';

CREATE INDEX vacante_ciudad_idx ON vacante (ciudad_ubigeo) WHERE ciudad_ubigeo IS NOT NULL;

UPDATE vacante v
   SET ciudad_ubigeo = u.codigo
  FROM ubigeo u
 WHERE v.ciudad_ubigeo IS NULL
   AND v.ubicacion IS NOT NULL
   AND u.nivel = 2
   AND u.activo
   AND translate(lower(u.nombre), 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunaeiouun')
     = translate(lower(btrim(v.ubicacion)), 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunaeiouun')
   AND (SELECT count(*)
          FROM ubigeo u2
         WHERE u2.nivel = 2
           AND u2.activo
           AND translate(lower(u2.nombre), 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunaeiouun')
             = translate(lower(btrim(v.ubicacion)), 'áéíóúüñÁÉÍÓÚÜÑ', 'aeiouunaeiouun')) = 1;
