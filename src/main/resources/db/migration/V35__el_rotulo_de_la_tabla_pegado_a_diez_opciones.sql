-- El rótulo de la tabla del PDF, pegado al final de diez opciones
--
-- QUÉ PASABA · en el PDF, cada tabla de opciones lleva su cabecera («Opción   Clave» u
-- «Opción   Valor»). Cuando un ítem trae varias sub-tablas seguidas —o la cabecera cae
-- sin línea en blanco de por medio—, el importador de la V20 absorbió esa cabecera como
-- si fuera la continuación de la última opción de la tabla anterior. El candidato leía
-- «Revisar si la instrucción y los recursos estaban dados antes de reclamar Opción
-- Clave». Son diez opciones: C07.a, C20.b, C28.b, D15.c2, D20.a, D31.c, D38.a, D80.c,
-- O17.c y O45.d. La auditoría del 23/08 las encontró al comparar por primera vez las
-- opciones contra el PDF (las revisiones anteriores cubrían enunciados y campos).
--
-- El importador ya no absorbe cabeceras (scripts/importar-banco-v3.py salta las líneas
-- que casan con CABECERA_TABLA también dentro de una tabla de opciones), y avisa si un
-- texto termina en rótulo. Esto limpia lo que ya estaba guardado.
--
-- SE PUEDE REPETIR SIN MIEDO · el WHERE solo alcanza textos que aún terminan en el
-- rótulo; una opción ya limpia —o editada a mano en el panel— no matchea.

UPDATE opcion o
   SET texto = btrim(regexp_replace(o.texto, '\s+Opci[oó]n\s+(Clave|Valor)\s*$', ''))
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id
 WHERE o.pregunta_id = p.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND o.texto ~ '\s+Opci[oó]n\s+(Clave|Valor)\s*$';
