-- Los casos descompuestos multiplicados enseñan por fin todas sus casillas
--
-- QUÉ PASABA · siete CD del banco v3 piden el bloque de campos POR CADA caso: «Los 3
-- indicadores que reportabas. Por cada uno (4 campos × 3):» son 12 casillas, no 4. El
-- importador de la V20 guardó solo el bloque base, así que el candidato veía 4 casillas
-- para contar 3 indicadores —solo podía declarar uno— y la fórmula del ítem dividía
-- entre un total que no era el del documento. Son C04 (4×3), C19 (5×3), C49 (3×2),
-- D11 (6×3), D21 (5 reuniones × 5), D22 (5×3) y D75 (3×3): 100 casillas reales donde
-- había 31.
--
-- CÓMO SE ARREGLA · cada bloque se repite con el número de su grupo delante
-- («Indicador 2 · Nombre (texto ≤ 40 car.)»), derivando las etiquetas de las filas que
-- ya están en la base —que la V28 completó y la V33 limpió— en vez de copiarlas aquí a
-- mano. casos_pedidos pasa a ser el total real, que es lo que el portal enseña y lo
-- que la fórmula usa de denominador.
--
-- Y LAS REGLAS PEGADAS · el documento escribe la regla de puntaje de algunos de estos
-- ítems a renglón seguido del último campo, y la V20 la dejó pegada a esa etiqueta: el
-- candidato leía «¿Sigue viva? (sí / no / no lo sé) Solo cuentan las implementadas.»
-- La regla se corta de la etiqueta y las reglas completas del documento quedan en
-- logica_interna, que nunca sale al portal (RF-53). La puntuación fina que esas reglas
-- describen (índice de apagaincendios, campo válido doble…) sigue pendiente del motor:
-- esta migración arregla lo que el candidato ve y responde, no cómo se puntúa.
--
-- SE PUEDE REPETIR SIN MIEDO · cada ítem se expande solo si conserva exactamente su
-- bloque base sin multiplicar; expandido ya no matchea. La lógica interna solo se
-- escribe donde está vacía: una edición del panel se respeta.

DO $$
DECLARE
    espec record;
    p record;
BEGIN
    FOR espec IN
        SELECT * FROM (VALUES
            ('C04', 4, 3, 'Indicador'),
            ('C19', 5, 3, 'Problema'),
            ('C49', 3, 2, 'Iniciativa'),
            ('D11', 6, 3, 'Indicador'),
            ('D21', 5, 5, 'Reunión'),
            ('D22', 5, 3, 'Problema'),
            ('D75', 3, 3, 'Iniciativa')
        ) AS v(codigo, base, veces, sustantivo)
    LOOP
        FOR p IN
            SELECT pr.id FROM pregunta pr
            JOIN version_banco vb ON vb.id = pr.version_banco_id
            WHERE vb.etiqueta LIKE 'Banco RENASER v3%'
              AND pr.codigo = espec.codigo
              AND (SELECT count(*) FROM campo_caso cc
                    WHERE cc.pregunta_id = pr.id) = espec.base
        LOOP
            -- Las filas nuevas nacen con un orden desplazado para no chocar con las
            -- base mientras conviven dentro de la misma transacción.
            INSERT INTO campo_caso (pregunta_id, orden, etiqueta, validacion)
            SELECT p.id,
                   1000 + (g - 1) * espec.base + cc.orden,
                   espec.sustantivo || ' ' || g || ' · ' ||
                   btrim(regexp_replace(cc.etiqueta,
                         '\s*(Regla especial:|Solo cuentan las).*$', '')),
                   cc.validacion
              FROM campo_caso cc, generate_series(1, espec.veces) g
             WHERE cc.pregunta_id = p.id AND cc.orden <= espec.base;

            DELETE FROM campo_caso WHERE pregunta_id = p.id AND orden <= espec.base;
            UPDATE campo_caso SET orden = orden - 1000
             WHERE pregunta_id = p.id AND orden > 1000;
            UPDATE pregunta SET casos_pedidos = espec.base * espec.veces
             WHERE id = p.id;
        END LOOP;
    END LOOP;
END $$;

-- Las reglas de puntaje de estos ítems, tal como las escribe el documento. Van a la
-- lógica interna: son para el evaluador y para el motor cuando las implemente.
UPDATE pregunta p
   SET logica_interna = v.regla
  FROM version_banco vb, (VALUES
    ('C19', 'Mismo detector de apagaincendios que D22: aplicar idéntica fórmula de índice.'),
    ('C49', 'Solo cuentan las iniciativas implementadas.'),
    ('D11', 'Un indicador sin denominador no es indicador: ese campo es inválido y arrastra al numerador.'),
    ('D21', 'Regla especial: cero reuniones con agenda previa y acta → el ítem puntúa 0 aunque declare 5 reuniones.'),
    ('D22', 'Detector de apagaincendios — corrección automática. Además del puntaje por campos válidos: índice = suma por cada problema de [campo 4 = «todos los meses» o «todas las semanas» → −1 · campo 5 = «nada, se resolvió» → −1 · campo 5 = «cambié el procedimiento» o «agregué un punto de control» → +1]. Índice ≤ −3: puntaje del ítem 0 y bandera de perfil reactivo / apagaincendios crónico. Índice −2 a 0: puntaje según campos válidos. Índice ≥ +2: puntaje del ítem 3, máximo directo.'),
    ('D75', 'Regla especial: el puntaje cuenta solo las iniciativas con «implementada = sí o parcial». Tres ideas y cero implementadas = 0. Ideas vivas hoy = campo válido doble.')
  ) AS v(codigo, regla)
 WHERE vb.id = p.version_banco_id
   AND vb.etiqueta LIKE 'Banco RENASER v3%'
   AND p.codigo = v.codigo
   AND p.logica_interna IS NULL;
