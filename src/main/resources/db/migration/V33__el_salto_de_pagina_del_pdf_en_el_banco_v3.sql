-- El salto de página del PDF, incrustado e invisible en cinco campos del banco v3
--
-- QUÉ PASABA · pdftotext marca cada cambio de página con un carácter de control (\f,
-- 0x0c) pegado al primer texto de la página siguiente. Cinco campos de caso cruzaban
-- de página justo a media frase, y el importador de la V20 se llevó ese carácter
-- dentro del texto: D41.3, D74.4, C48.3, O21.2 y O41.2 decían, por ejemplo,
-- «...(yo / él / el \f jefe / nadie...)». No se ve en pantalla —un navegador lo pinta
-- como nada— pero es basura: rompe cualquier comparación exacta del texto y saltó a
-- la vista al volcar el banco a Excel, donde la librería se negó a escribirlo.
--
-- Y de paso, los espacios dobles de la misma familia: en la rama «suelta» del
-- documento (campos separados por ·), el salto de línea más la sangría de la página
-- siguiente dejaban dos espacios seguidos dentro de diez campos. El importador ya
-- aplana toda corrida de blancos a un espacio (mismo arreglo, otro síntoma); aquí la
-- base converge a esa misma forma, para que lo guardado y lo que el importador emite
-- vuelvan a ser idénticos carácter a carácter.
--
-- POR QUÉ AHORA · el arreglo del importador (scripts/importar-banco-v3.py) evita que
-- esto vuelva a entrar; esta migración limpia lo que ya entró. Las dos piezas van
-- juntas en el mismo cambio.
--
-- SE PUEDE REPETIR SIN MIEDO · el WHERE solo alcanza filas que aún tengan un carácter
-- de control o un espacio doble: en una base ya limpia no toca nada. Y si el panel
-- editó a mano alguna de estas etiquetas quitando la basura por su cuenta, esa fila
-- ya no matchea y se respeta.

UPDATE campo_caso cc
   SET etiqueta = btrim(regexp_replace(regexp_replace(cc.etiqueta,
                        '[\x01-\x08\x0b\x0c\x0e-\x1f]', ' ', 'g'),
                        '  +', ' ', 'g'))
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id
 WHERE cc.pregunta_id = p.id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND (cc.etiqueta ~ '[\x01-\x08\x0b\x0c\x0e-\x1f]' OR cc.etiqueta ~ '  ');
