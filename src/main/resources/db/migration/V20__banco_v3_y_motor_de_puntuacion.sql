-- Banco RENASER v3 · el esquema que necesita.
--
-- El cliente reemplazó el banco de preguntas entero (docs/insumos/banco-renaser-v3-completo.pdf).
-- No es más contenido del mismo tipo: son ocho formatos nuevos, cada uno con su propia fórmula
-- de puntuación, y ninguno coincide con los seis que había. Ver docs/AVANCE-BANCO-V3-2026-08-19.md.
--
-- El v3 **reemplaza** al v0.1, no convive con él: son instrumentos distintos y tener los dos
-- publicados significaría evaluar a dos candidatos con reglas diferentes. Por eso esta migración
-- borra las 200 preguntas viejas (sección 10) antes de cargar las 190 nuevas.
--
-- Se puede borrar porque no había ninguna respuesta ni ninguna nota en la base: nadie ha sido
-- evaluado todavía. El día que existan evaluaciones de verdad, un cambio así ya no sería posible
-- sin romper decisiones tomadas, y habría que convivir con las dos versiones.
--
-- Los cambios de ESQUEMA sí son aditivos: no se quita ninguna columna ni ninguna tabla.

-- ============================================================================
-- 1 · Los ocho formatos nuevos
-- ============================================================================
-- Un CHECK no se puede ampliar: se borra y se escribe otro. Los seis viejos SIGUEN en la lista
-- a propósito. Quitarlos dejaría 200 filas ya publicadas apuntando a un valor que el CHECK
-- rechaza, y Hibernate no avisaría: `ddl-auto: validate` mira que las entidades cuadren con sus
-- columnas, no que los datos cumplan las restricciones.
ALTER TABLE pregunta DROP CONSTRAINT pregunta_tipo_check;
ALTER TABLE pregunta ADD CONSTRAINT pregunta_tipo_check CHECK (tipo IN (
    -- Banco v0.1, en uso por las evaluaciones ya hechas
    'ESTILO', 'SITUACION', 'CONDUCTUAL', 'MICROCASO', 'DILEMA', 'CONSISTENCIA',
    -- Banco v3
    'EF-4',    -- Elección forzada: se marca la MÁS y la MENOS parecida a uno
    'SJT-R',   -- Situacional: se califica cada opción del 1 al 5
    'SEC',     -- Ordenamiento: cinco pasos que hay que poner en orden
    'INV',     -- Inventario con distractores: la lista trae elementos inventados
    'DE',      -- Detección de error: ocho afirmaciones, cuatro ciertas y cuatro no
    'CD',      -- Caso descompuesto: un caso partido en campos cerrados
    'V',       -- Dato verificable: cada ítem trae su propia tabla de puntaje
    'PC'       -- Par de consistencia: no suma, solo penaliza si se contradice con su pareja
));

-- La misma lista vive en las cuotas de plantilla. Si se amplía una y no la otra, una plantilla
-- no puede pedir preguntas de los formatos nuevos.
ALTER TABLE cuota_plantilla_evaluacion DROP CONSTRAINT cuota_plantilla_evaluacion_tipo_pregunta_check;
ALTER TABLE cuota_plantilla_evaluacion ADD CONSTRAINT cuota_plantilla_evaluacion_tipo_pregunta_check
    CHECK (tipo_pregunta IS NULL OR tipo_pregunta IN (
        'ESTILO', 'SITUACION', 'CONDUCTUAL', 'MICROCASO', 'DILEMA', 'CONSISTENCIA',
        'EF-4', 'SJT-R', 'SEC', 'INV', 'DE', 'CD', 'V', 'PC'));

-- ============================================================================
-- 2 · Peso, ítem clave y eliminatorio
-- ============================================================================
-- Hasta ahora una pregunta solo podía sumar o no sumar (es_puntuable). El v3 necesita tres
-- valores: peso 0 no suma, peso 1 vale hasta 3 puntos y peso 2 hasta 6. De ahí salen los
-- máximos que el documento declara: 288, 186 y 168.
--
-- peso queda NULL en las 200 preguntas del v0.1 y eso es lo correcto: aquel banco no tenía
-- pesos, y ponerle uno inventado cambiaría notas ya dadas.
ALTER TABLE pregunta ADD COLUMN peso smallint CHECK (peso IS NULL OR peso BETWEEN 0 AND 2);

-- El ítem clave (★ en el documento). En el v3 siempre coincide con peso 2 —está comprobado
-- ítem por ítem—, pero se guarda aparte porque son dos cosas distintas: una dice cuánto vale
-- y la otra que hay que preguntar por ella en la entrevista.
ALTER TABLE pregunta ADD COLUMN es_clave boolean NOT NULL DEFAULT false;

-- Los cinco filtros del documento (sección 0.4) descartan al candidato aunque su puntaje sea
-- alto. Marcar la pregunta no basta para aplicarlos —la condición está en filtro_eliminatorio—,
-- pero sirve para no mostrarla como si sumara.
ALTER TABLE pregunta ADD COLUMN es_eliminatorio boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN pregunta.peso IS
    'v3: 0 no suma, 1 vale hasta 3 puntos, 2 hasta 6. NULL en el banco v0.1, que no tenía pesos.';

-- ============================================================================
-- 3 · Lo que cada formato necesita de sus opciones
-- ============================================================================
-- puntaje ya existe y lo sigue usando el v0.1. Estas tres son del v3:
--   valor          EF-4 esconde un valor de −2 a +2 detrás de cada afirmación.
--   es_distractor  INV y DE mezclan elementos inventados; el candidato no los distingue.
--   orden_correcto SEC pide ordenar cinco pasos: aquí va el lugar que le toca a cada uno.
ALTER TABLE opcion ADD COLUMN valor numeric(5,2);
ALTER TABLE opcion ADD COLUMN es_distractor boolean NOT NULL DEFAULT false;
ALTER TABLE opcion ADD COLUMN orden_correcto smallint;

-- ============================================================================
-- 4 · Las tablas de puntaje de los ítems V
-- ============================================================================
-- Un ítem V no se puntúa por la opción elegida sino por el tramo en el que cae la respuesta:
-- "Directos 5-20 y niveles >= 2" vale 3, "Sin personal a cargo" vale 0. Cada ítem trae su
-- propia tabla, así que esto cuelga de la pregunta y no de un catálogo.
CREATE TABLE rango_pregunta (
    id             bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pregunta_id    bigint NOT NULL REFERENCES pregunta(id),
    orden          integer NOT NULL,
    condicion      text NOT NULL,
    puntaje        numeric(5,2) NOT NULL,
    -- Algunos tramos no solo dan 0: además levantan una bandera para la entrevista.
    genera_bandera boolean NOT NULL DEFAULT false,
    creado_en      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (pregunta_id, orden)
);

-- Unos pocos ítems V no repiten la tabla: remiten a la de otro ("Misma tabla que D57"), o dan
-- la fórmula escrita en vez de tramos. Se guarda tal cual en vez de copiarla, para que si un
-- día cambia la tabla de origen no queden dos versiones distintas.
ALTER TABLE pregunta ADD COLUMN rangos_de_pregunta_codigo text;
ALTER TABLE pregunta ADD COLUMN formula_puntaje text;

-- ============================================================================
-- 5 · Los campos de los casos descompuestos (CD)
-- ============================================================================
-- Un CD no tiene opciones: es un caso partido en campos cerrados que se validan uno a uno.
-- El puntaje sale de cuántos campos son válidos sobre el total, así que hay que saber cuáles
-- son y qué hace válido a cada uno.
CREATE TABLE campo_caso (
    id          bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    pregunta_id bigint NOT NULL REFERENCES pregunta(id),
    orden       integer NOT NULL,
    etiqueta    text NOT NULL,
    -- Qué se acepta: la lista de valores, el rango, o el límite de caracteres.
    validacion  text,
    creado_en   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (pregunta_id, orden)
);

-- Cuántos campos tiene cada caso y cuántos casos se piden. En varios ítems el enunciado dice
-- "5 campos x 3", que son 15 campos a llenar pero cinco preguntas distintas repetidas.
ALTER TABLE pregunta ADD COLUMN casos_pedidos smallint;

-- ============================================================================
-- 6 · Ponderación por familia de puesto
-- ============================================================================
-- El mismo banco directivo sirve para cualquier dirección: lo que cambia es cuánto pesa cada
-- bloque según la familia del puesto (sección 0.5).
--
-- OJO con familia_codigo: el documento nombra cuatro familias —Obras/Proyectos, Recursos
-- Humanos, Marketing/Comercial y Administración/Finanzas— que NO son las siete del catálogo
-- `familia` (TALENTO, OPERACIONES, TECNOLOGIA...). Nadie ha dicho cuál corresponde a cuál, así
-- que se guarda la etiqueta del documento tal cual y familia_codigo queda vacío hasta que
-- Renaser confirme el mapeo. Inventarlo cambiaría notas sin que nadie lo hubiera decidido.
CREATE TABLE multiplicador_bloque (
    id                bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version_banco_id  bigint NOT NULL REFERENCES version_banco(id),
    familia_documento text NOT NULL,
    familia_codigo    text REFERENCES familia(codigo),
    bloque            text NOT NULL,
    multiplicador     numeric(4,2) NOT NULL,
    creado_en         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (version_banco_id, familia_documento, bloque)
);

-- ============================================================================
-- 7 · Los niveles certificables
-- ============================================================================
-- El porcentaje sobre el máximo decide si el candidato avanza y con qué nivel (sección 0.3).
CREATE TABLE umbral_nivel (
    id               bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version_banco_id bigint NOT NULL REFERENCES version_banco(id),
    porcentaje_min   numeric(5,2) NOT NULL,
    resultado        text NOT NULL,
    nivel            text,
    creado_en        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (version_banco_id, porcentaje_min)
);

-- ============================================================================
-- 8 · Los filtros eliminatorios
-- ============================================================================
-- Cinco reglas que descartan al candidato aunque el puntaje global sea alto. Van como datos y
-- no escritas en el código: apuntan a códigos de pregunta concretos, y esa clase de cosa la
-- cambia el cliente sin avisar.
CREATE TABLE filtro_eliminatorio (
    id               bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version_banco_id bigint NOT NULL REFERENCES version_banco(id),
    codigo           text NOT NULL,
    descripcion      text NOT NULL,
    -- Los códigos de pregunta a los que mira, separados por coma. Puede ser más de uno porque
    -- la misma regla aplica al ítem equivalente de cada banco (D70, C44, O39).
    preguntas        text NOT NULL,
    creado_en        timestamptz NOT NULL DEFAULT now(),
    UNIQUE (version_banco_id, codigo)
);

-- ============================================================================
-- 9 · Los pares de consistencia del v3
-- ============================================================================
-- par_consistencia ya existía para el v0.1, con `diferencia_maxima`: dos preguntas que miden lo
-- mismo no pueden responderse muy distinto. El v3 no funciona así: el par se contradice o no,
-- y si se contradice descuenta un 5% del puntaje global y levanta bandera roja. Además el par
-- tiene que aparecer al menos 15 ítems después de su pareja, o el candidato se da cuenta.
--
-- La tabla está vacía —el cliente nunca dijo qué pregunta iba con cuál en el v0.1—, así que
-- ampliarla es seguro. diferencia_maxima pasa a admitir vacío: los pares del v3 no la usan.
ALTER TABLE par_consistencia ALTER COLUMN diferencia_maxima DROP NOT NULL;
ALTER TABLE par_consistencia ADD COLUMN penalizacion_porcentaje numeric(5,2);
ALTER TABLE par_consistencia ADD COLUMN separacion_minima_items smallint;
ALTER TABLE par_consistencia ADD COLUMN condicion text;

CREATE INDEX idx_rango_pregunta ON rango_pregunta(pregunta_id);
CREATE INDEX idx_campo_caso ON campo_caso(pregunta_id);

-- ============================================================================
-- 10 · Fuera el banco v0.1
-- ============================================================================
-- El v3 no se suma al anterior: lo reemplaza. Son instrumentos distintos —ningún formato
-- de los seis viejos existe en el nuevo— y dejar los dos publicados significaría evaluar a
-- dos candidatos con reglas diferentes según cuál cogiera el sistema.
--
-- Se puede borrar sin perder nada: al escribir esta migración no había NI UNA respuesta ni
-- una nota en la base (`select count(*) from respuesta` y `nota_respuesta` daban 0). Nadie
-- ha sido evaluado todavía con el banco viejo, así que no hay decisión pasada que dependa
-- de él y RF-138 no protege nada aquí.
--
-- El orden lo mandan las claves foráneas, de la hoja a la raíz. Si alguna vez esto falla
-- por una FK nueva, el error dirá cuál: añadirla arriba, no quitar el borrado.
DELETE FROM nota_respuesta WHERE respuesta_id IN (
    SELECT r.id FROM respuesta r JOIN pregunta p ON p.id = r.pregunta_id
     JOIN version_banco vb ON vb.id = p.version_banco_id WHERE vb.etiqueta LIKE '%V0.1');
DELETE FROM respuesta WHERE pregunta_id IN (
    SELECT p.id FROM pregunta p JOIN version_banco vb ON vb.id = p.version_banco_id
     WHERE vb.etiqueta LIKE '%V0.1');
DELETE FROM opcion_dimension WHERE opcion_id IN (
    SELECT o.id FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
     JOIN version_banco vb ON vb.id = p.version_banco_id WHERE vb.etiqueta LIKE '%V0.1');
DELETE FROM alerta WHERE pregunta_a_id IN (
    SELECT p.id FROM pregunta p JOIN version_banco vb ON vb.id = p.version_banco_id
     WHERE vb.etiqueta LIKE '%V0.1');
DELETE FROM orden_pregunta WHERE pregunta_id IN (
    SELECT p.id FROM pregunta p JOIN version_banco vb ON vb.id = p.version_banco_id
     WHERE vb.etiqueta LIKE '%V0.1');
DELETE FROM pregunta_dimension WHERE pregunta_id IN (
    SELECT p.id FROM pregunta p JOIN version_banco vb ON vb.id = p.version_banco_id
     WHERE vb.etiqueta LIKE '%V0.1');
DELETE FROM par_consistencia WHERE version_banco_id IN (
    SELECT id FROM version_banco WHERE etiqueta LIKE '%V0.1');
DELETE FROM opcion WHERE pregunta_id IN (
    SELECT p.id FROM pregunta p JOIN version_banco vb ON vb.id = p.version_banco_id
     WHERE vb.etiqueta LIKE '%V0.1');

-- Las evaluaciones que apuntaban al banco viejo. Al escribir esto eran cinco, todas en
-- PENDIENTE y sin una sola respuesta: son los candidatos de prueba que se cargaron a mano
-- el 18/08 para ver el flujo. En una base nueva no existe ninguna y esto no borra nada.
DELETE FROM evaluacion WHERE version_banco_nivel_id IN (
    SELECT id FROM version_banco WHERE etiqueta LIKE '%V0.1');

DELETE FROM pregunta WHERE version_banco_id IN (
    SELECT id FROM version_banco WHERE etiqueta LIKE '%V0.1');
DELETE FROM version_banco WHERE etiqueta LIKE '%V0.1';

-- ============================================================================
-- 11 · Los datos del banco v3
-- ============================================================================
-- Lo que sigue lo genera scripts/importar-banco-v3.py leyendo el PDF del cliente. No se
-- transcribió a mano: son 190 ítems con sus claves y copiarlos garantiza erratas. El script
-- comprueba cuatro totales que el propio documento declara (ítems, cuáles puntúan, cuáles son
-- clave y el puntaje máximo de cada banco) y no genera nada si alguno no cuadra.

-- Generado por scripts/importar-banco-v3.py — no editar a mano: si hay que cambiar
-- algo, se corrige el script y se vuelve a generar, o la próxima carga lo pisa.

-- Las tres versiones del banco, una por nivel de puesto. Publicadas: el v3 reemplaza
-- al v0.1, que esta misma migración borra, así que son las únicas que quedan. El
-- documento del cliente viene cerrado y él lo declara definitivo.
INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_en)
SELECT id, 'NIVEL', 'DIRECCION', 'Banco RENASER v3 · Directivo', 'PUBLICADA', now()
  FROM organizacion WHERE codigo = 'RENASER';
INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_en)
SELECT id, 'NIVEL', 'SUPERVISION', 'Banco RENASER v3 · Coordinación y Supervisión', 'PUBLICADA', now()
  FROM organizacion WHERE codigo = 'RENASER';
INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_en)
SELECT id, 'NIVEL', 'EJECUCION', 'Banco RENASER v3 · Ejecutivo y Operativo', 'PUBLICADA', now()
  FROM organizacion WHERE codigo = 'RENASER';

-- Banco RENASER v3 · Directivo · 85 ítems
INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado,
                      es_puntuable, peso, es_clave, es_eliminatorio, orden,
                      casos_pedidos, rangos_de_pregunta_codigo, formula_puntaje)
SELECT vb.id, v.codigo, v.bloque, v.tipo, v.enunciado, v.es_puntuable, v.peso,
       v.es_clave, v.es_eliminatorio, v.orden, v.casos_pedidos,
       v.rangos_de, v.formula
  FROM version_banco vb, (VALUES
    ('D01', 'A1', 'V', 'Personas a tu cargo en tu último puesto:', true, 1, false, false, 1, NULL, NULL, NULL),
    ('D02', 'A1', 'INV', 'Marca los instrumentos de evaluación de desempeño que has aplicado tú', true, 1, false, false, 2, NULL, NULL, NULL),
    ('D03', 'A1', 'INV', 'Marca los documentos que has redactado tú (no leído ni firmado).', true, 1, false, false, 3, NULL, NULL, NULL),
    ('D04', 'A1', 'SEC', 'Ordena tu secuencia real ante un colaborador con tres meses bajo meta.', true, 2, true, false, 4, NULL, NULL, NULL),
    ('D05', 'A1', 'CD', 'Caso: un trabajador que no rendía y lograste revertir. (7 campos)', true, 2, true, false, 5, 7, NULL, NULL),
    ('D06', 'A1', 'SJT-R', 'Un colaborador cumplió su plan de mejora en el papel, pero el resultado del área', true, 1, false, false, 6, NULL, NULL, NULL),
    ('D07', 'A1', 'EF-4', 'La carga de trabajo de mi gente la defino principalmente por:', true, 1, false, false, 7, NULL, NULL, NULL),
    ('D08', 'A1', 'V', 'De tu última semana típica: ___% dirigiendo personas · ___% ejecutando tareas', true, 1, false, false, 8, NULL, NULL, NULL),
    ('D09', 'A1', 'SJT-R', 'Lunes 8:00 a.m. Tu equipo de 14 personas arranca la semana.', true, 1, false, false, 9, NULL, NULL, NULL),
    ('D10', 'A1', 'SEC', 'Heredas un equipo que no armaste. Ordena tus primeros 30 días.', true, 1, false, false, 10, NULL, NULL, NULL),
    ('D11', 'A2', 'CD', 'Declara los 3 indicadores con los que dirigías tu área. Por cada uno (6 campos × 3', true, 2, true, false, 11, 6, NULL, NULL),
    ('D12', 'A2', 'EF-4', 'Mis indicadores los revisaba:', true, 1, false, false, 12, NULL, NULL, NULL),
    ('D13', 'A2', 'INV', 'Sistemas que has operado o configurado tú.', true, 1, false, false, 13, NULL, NULL, NULL),
    ('D14', 'A2', 'CD', 'Caso: un problema que se te escapó y del que te enteraste tarde. (4 campos)', true, 1, false, false, 14, 4, NULL, NULL),
    ('D15', 'A2', 'SJT-R', 'Tres personas, mismo mes. Califica del 1 al 5 la efectividad de cada tratamiento.', true, 2, true, false, 15, NULL, NULL, NULL),
    ('D16', 'A2', 'DE', 'Marca las afirmaciones correctas sobre supervisar y controlar.', true, 1, false, false, 16, NULL, NULL, NULL),
    ('D17', 'A2', 'DE', 'Tu reporte directo te dice "todo bien" cada semana y a fin de mes el resultado no', true, 2, true, false, 17, NULL, NULL, NULL),
    ('D18', 'A2', 'EF-4', 'De cada supervisión quedaba:', true, 1, false, false, 18, NULL, NULL, NULL),
    ('D19', 'A2', 'CD', 'Caso: una decisión que tomaste leyendo un dato antes de que el problema', true, 1, false, false, 19, 5, NULL, NULL),
    ('D20', 'A2', 'SJT-R', 'Te ausentas dos semanas sin previo aviso. Califica del 1 al 5 qué tan probable es', true, 2, true, false, 20, NULL, NULL, NULL),
    ('D21', 'A3', 'CD', 'Tu mapa de reuniones fijas. Hasta 5 reuniones, 5 campos cada una:', true, 1, false, false, 21, 5, NULL, NULL),
    ('D22', 'A3', 'CD', 'Los 3 últimos problemas que resolviste en tus últimas 48 horas de trabajo. Por', true, 2, true, false, 22, 5, NULL, NULL),
    ('D23', 'A3', 'EF-4', 'Cuando algo se rompe en mi área, lo más frecuente es que:', true, 1, false, false, 23, NULL, NULL, NULL),
    ('D24', 'A3', 'SJT-R', 'Descubres el martes que un compromiso grande con gerencia no se va a cumplir.', true, 1, false, false, 24, NULL, NULL, NULL),
    ('D25', 'A3', 'SJT-R', 'Das una instrucción clara y se ejecuta distinto.', true, 1, false, false, 25, NULL, NULL, NULL),
    ('D26', 'A3', 'SJT-R', 'Tu superior toma una decisión que afecta a tu área y no te consultó.', true, 1, false, false, 26, NULL, NULL, NULL),
    ('D27', 'A3', 'SEC', 'Ordena los componentes de un mensaje de alineamiento semanal.', true, 1, false, false, 27, NULL, NULL, NULL),
    ('D28', 'A3', 'INV', 'Marca los elementos que SIEMPRE incluyes al comunicar una meta a tu equipo.', true, 1, false, false, 28, NULL, NULL, NULL),
    ('D29', 'A3', 'V', 'De tus comunicaciones del último mes: ___% planificadas (con agenda previa) ·', true, 1, false, false, 29, NULL, NULL, NULL),
    ('D30', 'A4', 'INV', 'Campos que incluye un perfil de puesto que hayas escrito.', true, 1, false, false, 30, NULL, NULL, NULL),
    ('D31', 'A4', 'EF-4', 'Entre dos candidatos igual de buenos en papel, decido por:', true, 1, false, false, 31, NULL, NULL, NULL),
    ('D32', 'A4', 'SEC', 'Ordena los primeros 7 días de un ingresante.', true, 1, false, false, 32, NULL, NULL, NULL),
    ('D33', 'A4', 'SEC', 'Ordena un proceso de cese por bajo rendimiento.', true, 1, false, false, 33, NULL, NULL, NULL),
    ('D34', 'A4', 'CD', 'Un error de contratación que cometiste. (5 campos)', true, 1, false, false, 34, 5, NULL, NULL),
    ('D35', 'A4', 'V', 'Últimos 12 meses: ___ ceses sobre una dotación de ___ personas. De esos ceses:', true, 1, false, false, 35, NULL, NULL, NULL),
    ('D36', 'A5', 'CD', 'Tu resultado más importante de los últimos 12 meses. (7 campos)', true, 2, true, false, 36, 7, NULL, NULL),
    ('D37', 'A5', 'INV', 'Componentes de un plan de trabajo que hayas construido.', true, 1, false, false, 37, NULL, NULL, NULL),
    ('D38', 'A5', 'SJT-R', 'A mitad de mes el plan se cayó: se fue una persona clave y un proveedor', true, 1, false, false, 38, NULL, NULL, NULL),
    ('D39', 'A5', 'EF-4', 'Cuando todo es urgente, priorizo por:', true, 1, false, false, 39, NULL, NULL, NULL),
    ('D40', 'A5', 'V', 'Presupuesto anual administrado: (no he manejado / < S/ 100K / 100K–500K /', true, 1, false, false, 40, NULL, NULL, NULL),
    ('D41', 'A5', 'CD', 'Un proceso que dejaste escrito y funcionando sin ti. (6 campos)', true, 2, true, false, 41, 6, NULL, NULL),
    ('D42', 'A5', 'CD', 'Una mejora que propusiste sin que nadie te la pidiera. (5 campos)', true, 1, false, false, 42, 5, NULL, NULL),
    ('D43', 'A5', 'SJT-R', 'Dos áreas se culpan mutuamente por un incumplimiento con un cliente.', true, 1, false, false, 43, NULL, NULL, NULL),
    ('D44', 'A5', 'EF-4', 'Sé que mi mes como director fue bueno cuando:', true, 1, false, false, 44, NULL, NULL, NULL),
    ('D45', 'A5', 'SJT-R', 'Debes tomar una decisión correcta que el equipo va a rechazar.', true, 1, false, false, 45, NULL, NULL, NULL),
    ('D46', 'B1', 'EF-4', 'Cuando tengo un problema personal fuerte:', true, 1, false, false, 46, NULL, NULL, NULL),
    ('D47', 'B1', 'EF-4', 'Cuando algo me supera, acudo a:', true, 1, false, false, 47, NULL, NULL, NULL),
    ('D48', 'B1', 'SJT-R', 'Un colaborador de buen rendimiento te falta el respeto delante del equipo.', true, 1, false, false, 48, NULL, NULL, NULL),
    ('D49', 'B1', 'EF-4', 'Lo que más me desgasta de trabajar con otros:', true, 1, false, false, 49, NULL, NULL, NULL),
    ('D50', 'B1', 'V', 'Relaciones laborales importantes cerradas en malos términos en los últimos 3', true, 1, false, false, 50, NULL, NULL, NULL),
    ('D51', 'B1', 'EF-4', 'Opción                                                                  Valor', true, 1, false, false, 51, NULL, NULL, NULL),
    ('D52', 'B1', 'PC', 'Tus 3 últimos jefes: nombre · cargo · empresa · teléfono o correo.', false, 0, false, true, 52, NULL, NULL, NULL),
    ('D53', 'B1', 'EF-4', 'La crítica más dura que dirían de mí quienes me reportaron:', true, 1, false, false, 53, NULL, NULL, NULL),
    ('D54', 'B2', 'CD', 'Tu día típico. (5 campos)', true, 1, false, false, 54, 5, NULL, NULL),
    ('D55', 'B2', 'EF-4', 'Mis horas de sueño entre semana:', true, 1, false, false, 55, NULL, NULL, NULL),
    ('D56', 'B2', 'EF-4', 'Recupero energía principalmente con:', true, 1, false, false, 56, NULL, NULL, NULL),
    ('D57', 'B2', 'V', 'Actividad física: (nunca / esporádica / 1–2 veces por semana / 3–4 veces / diaria) ·', true, 1, false, false, 57, NULL, NULL, NULL),
    ('D58', 'B2', 'EF-4', 'Bajo presión sostenida más de un mes, lo primero que descuido es:', true, 1, false, false, 58, NULL, NULL, NULL),
    ('D59', 'B2', 'EF-4', 'El hábito que sé que me resta rendimiento y aún no cambio:', true, 1, false, false, 59, NULL, NULL, NULL),
    ('D60', 'B3', 'EF-4', 'Opción                                                                        Valor', true, 2, true, false, 60, NULL, NULL, NULL),
    ('D61', 'B3', 'EF-4', 'Lo que me haría dejar un puesto en 6 meses:', true, 1, false, false, 61, NULL, NULL, NULL),
    ('D62', 'B3', 'EF-4', 'Cuando negocio mi sueldo, mi argumento es:', true, 1, false, false, 62, NULL, NULL, NULL),
    ('D63', 'B3', 'SJT-R', 'A los 4 meses de entrar te llega una oferta con 40% más de sueldo, en medio de', true, 1, false, false, 63, NULL, NULL, NULL),
    ('D64', 'B3', 'EF-4', 'Administro mi dinero:', true, 1, false, false, 64, NULL, NULL, NULL),
    ('D65', 'B3', 'SJT-R', 'Tu bono depende de un indicador. Descubres una forma legal de mejorar el', true, 2, true, true, 65, NULL, NULL, NULL),
    ('D66', 'B4', 'PC', 'En el caso del trabajador que recuperaste, ¿qué habría dicho esa persona de', false, 0, false, false, 66, NULL, NULL, NULL),
    ('D67', 'B4', 'PC', 'De los tres problemas de tus últimas 48 horas, ¿cuántos se repiten todos los', false, 0, false, false, 67, NULL, NULL, NULL),
    ('D68', 'B4', 'V', 'Del CV que enviaste, marca el dato que más te costaría sustentar con documento', true, 1, false, false, 68, NULL, NULL, NULL),
    ('D69', 'B4', 'EF-4', 'Algo en lo que me he presentado como experto y no domino del todo:', true, 1, false, false, 69, NULL, NULL, NULL),
    ('D70', 'B4', 'PC', 'Autorizo la verificación de referencias laborales, certificados y cifras declaradas', false, 0, false, true, 70, NULL, NULL, NULL),
    ('D71', 'C', 'EF-4', 'Servir, desde un cargo directivo, significa principalmente:', true, 1, false, false, 71, NULL, NULL, NULL),
    ('D72', 'C', 'CD', 'Lo último que hiciste por un cliente o colaborador sin que te lo pidieran y sin', true, 1, false, false, 72, 4, NULL, NULL),
    ('D73', 'C', 'SJT-R', 'Viernes 8 p.m. Un cliente importante escribe molesto por un error de tu área.', true, 1, false, false, 73, NULL, NULL, NULL),
    ('D74', 'C', 'CD', 'El estándar de calidad más alto que has exigido. (4 campos)', true, 1, false, false, 74, 4, NULL, NULL),
    ('D75', 'C', 'CD', 'Tres cosas que empezaste sin que nadie te lo pidiera. (3 campos × 3)', true, 2, true, false, 75, 3, NULL, NULL),
    ('D76', 'C', 'EF-4', 'El último mes, fuera de mi descripción de puesto:', true, 1, false, false, 76, NULL, NULL, NULL),
    ('D77', 'C', 'SJT-R', 'Detectas un problema serio en un área que no es la tuya, cuyo jefe es tu par.', true, 1, false, false, 77, NULL, NULL, NULL),
    ('D78', 'C', 'EF-4', 'Entre que veo algo mal y actúo pasa:', true, 1, false, false, 78, NULL, NULL, NULL),
    ('D79', 'C', 'DE', 'Lee: "La satisfacción del cliente subió de 4.1 a 4.6 tras la nueva política.', true, 2, true, false, 79, NULL, NULL, NULL),
    ('D80', 'C', 'EF-4', 'La última vez que cambié de opinión sobre algo importante en el trabajo fue:', true, 1, false, false, 80, NULL, NULL, NULL),
    ('D81', 'C', 'DE', 'Un gerente propone medir productividad así: tareas terminadas ÷ n° de', true, 2, true, false, 81, NULL, NULL, NULL),
    ('D82', 'C', 'SJT-R', 'Tu jefe da una instrucción que, por lo que conoces del terreno, va a fallar.', true, 2, true, false, 82, NULL, NULL, NULL),
    ('D83', 'C', 'EF-4', 'Sobre mi rubro, creo que la mayoría se equivoca en:', true, 1, false, false, 83, NULL, NULL, NULL),
    ('D84', 'C', 'V', '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Formato (curso formal /', true, 1, false, false, 84, NULL, NULL, NULL),
    ('D85', 'C', 'INV', 'Marca lo que haces todos los días laborales sin falta.', true, 1, false, false, 85, NULL, NULL, NULL)
  ) AS v(codigo, bloque, tipo, enunciado, es_puntuable, peso, es_clave,
         es_eliminatorio, orden, casos_pedidos, rangos_de, formula)
 WHERE vb.etiqueta = 'Banco RENASER v3 · Directivo';

-- Banco RENASER v3 · Coordinación y Supervisión · 55 ítems
INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado,
                      es_puntuable, peso, es_clave, es_eliminatorio, orden,
                      casos_pedidos, rangos_de_pregunta_codigo, formula_puntaje)
SELECT vb.id, v.codigo, v.bloque, v.tipo, v.enunciado, v.es_puntuable, v.peso,
       v.es_clave, v.es_eliminatorio, v.orden, v.casos_pedidos,
       v.rangos_de, v.formula
  FROM version_banco vb, (VALUES
    ('C01', 'A1', 'V', 'Personas que supervisabas: ___ · Turnos o frentes simultáneos: ___ · ¿Alguno de', true, 1, false, false, 1, NULL, NULL, NULL),
    ('C02', 'A1', 'INV', 'Marca lo que revisabas al inicio y al cierre de cada jornada.', true, 1, false, false, 2, NULL, NULL, NULL),
    ('C03', 'A1', 'CD', 'Tu control diario. (6 campos)', true, 2, true, false, 3, 6, NULL, NULL),
    ('C04', 'A1', 'CD', 'Los 3 indicadores que reportabas. Por cada uno (4 campos × 3):', true, 1, false, false, 4, 4, NULL, NULL),
    ('C05', 'A1', 'CD', 'Caso: un desvío que detectaste a tiempo. (5 campos)', true, 2, true, false, 5, 5, NULL, NULL),
    ('C06', 'A1', 'CD', 'Caso: un desvío que se te pasó. (4 campos)', true, 1, false, false, 6, 4, NULL, NULL),
    ('C07', 'A1', 'SJT-R', 'Una tarea programada no se cumplió.', true, 1, false, false, 7, NULL, NULL, NULL),
    ('C08', 'A1', 'SJT-R', 'Tres personas de tu equipo. Califica del 1 al 5 el tratamiento de seguimiento.', true, 2, true, false, 8, NULL, NULL, NULL),
    ('C09', 'A1', 'EF-4', 'De cada supervisión quedaba:', true, 1, false, false, 9, NULL, NULL, NULL),
    ('C10', 'A1', 'SJT-R', 'Faltas una semana completa sin previo aviso. Califica del 1 al 5 qué tan probable', true, 2, true, false, 10, NULL, NULL, NULL),
    ('C11', 'A2', 'EF-4', 'El trabajo del día lo distribuyo:', true, 1, false, false, 11, NULL, NULL, NULL),
    ('C12', 'A2', 'EF-4', 'Sé que alguien está sobrecargado porque:', true, 1, false, false, 12, NULL, NULL, NULL),
    ('C13', 'A2', 'CD', 'Caso: levantaste el rendimiento de una persona concreta. (5 campos)', true, 1, false, false, 13, 5, NULL, NULL),
    ('C14', 'A2', 'SJT-R', 'Dos personas de tu equipo están en conflicto y afecta el trabajo.', true, 1, false, false, 14, NULL, NULL, NULL),
    ('C15', 'A2', 'SJT-R', 'Tienes a alguien que produce más que nadie pero desordena al equipo.', true, 1, false, false, 15, NULL, NULL, NULL),
    ('C16', 'A2', 'INV', 'Documentos de personal que llenabas tú.', true, 1, false, false, 16, NULL, NULL, NULL),
    ('C17', 'A2', 'SEC', 'Ordena cómo entrenas a alguien nuevo en tu área.', true, 1, false, false, 17, NULL, NULL, NULL),
    ('C18', 'A3', 'CD', 'Tus reuniones o puntos de coordinación fijos. Hasta 4, con 4 campos:', true, 1, false, false, 18, 4, NULL, NULL),
    ('C19', 'A3', 'CD', 'Los 3 últimos problemas que resolviste en tus últimas 48 horas de trabajo. (5', true, 2, true, false, 19, 5, NULL, NULL),
    ('C20', 'A3', 'EF-4', 'Normalmente me entero de los problemas:', true, 1, false, false, 20, NULL, NULL, NULL),
    ('C21', 'A3', 'SJT-R', 'Ocurre algo grave en tu turno que tu jefe no sabe.', true, 1, false, false, 21, NULL, NULL, NULL),
    ('C22', 'A3', 'SEC', 'Ordena tu charla de inicio de jornada.', true, 1, false, false, 22, NULL, NULL, NULL),
    ('C23', 'A3', 'SJT-R', 'Tu jefe te pide algo que tu equipo no puede cumplir en ese plazo.', true, 1, false, false, 23, NULL, NULL, NULL),
    ('C24', 'A3', 'EF-4', 'Me aseguro de que una instrucción se entendió:', true, 1, false, false, 24, NULL, NULL, NULL),
    ('C25', 'A4', 'CD', 'Tu mejor resultado del último año. (6 campos)', true, 2, true, false, 25, 6, NULL, NULL),
    ('C26', 'A4', 'CD', 'La vez que te faltó gente, tiempo o recursos y aun así cumpliste. (4 campos)', true, 1, false, false, 26, 4, NULL, NULL),
    ('C27', 'A4', 'CD', 'Una mejora que aplicaste a un proceso de tu área. (4 campos)', true, 1, false, false, 27, 4, NULL, NULL),
    ('C28', 'A4', 'EF-4', 'Cuando todo es urgente, priorizo por:', true, 1, false, false, 28, NULL, NULL, NULL),
    ('C29', 'B1', 'EF-4', 'Cuando tengo un problema personal fuerte:', true, 1, false, false, 29, NULL, NULL, NULL),
    ('C30', 'B1', 'EF-4', 'Cuando algo me supera, acudo a:', true, 1, false, false, 30, NULL, NULL, NULL),
    ('C31', 'B1', 'SJT-R', 'Alguien de tu equipo te falta el respeto delante de los demás.', true, 1, false, false, 31, NULL, NULL, NULL),
    ('C32', 'B1', 'EF-4', 'Lo que más me desgasta de trabajar con otros:', true, 1, false, false, 32, NULL, NULL, NULL),
    ('C33', 'B1', 'PC', 'Tus 2 últimos jefes: nombre · cargo · empresa · contacto. ¿Autorizas que los', false, 0, false, true, 33, NULL, NULL, NULL),
    ('C34', 'B2', 'CD', 'Tu día típico. (5 campos)', true, 1, false, false, 34, 5, NULL, NULL),
    ('C35', 'B2', 'EF-4', 'Mis horas de sueño entre semana:', true, 1, false, false, 35, NULL, NULL, NULL),
    ('C36', 'B2', 'V', 'Actividad física: (nunca / esporádica / 1–2 por semana / 3–4 / diaria) · Años', true, 1, false, false, 36, NULL, 'D57', NULL),
    ('C37', 'B2', 'EF-4', 'Recupero energía con:', true, 1, false, false, 37, NULL, NULL, NULL),
    ('C38', 'B2', 'EF-4', 'Bajo presión sostenida, lo primero que descuido es:', true, 1, false, false, 38, NULL, NULL, NULL),
    ('C39', 'B3', 'EF-4', 'Lo que me haría dejar un puesto en 6 meses:', true, 1, false, false, 39, NULL, NULL, NULL),
    ('C40', 'B3', 'EF-4', 'Opción                                                                      Valor', true, 2, true, false, 40, NULL, NULL, NULL),
    ('C41', 'B3', 'EF-4', 'Administro mi sueldo:', true, 1, false, false, 41, NULL, NULL, NULL),
    ('C42', 'B3', 'SJT-R', 'Descubres una forma de que tu reporte se vea mejor sin que el trabajo real', true, 2, true, true, 42, NULL, NULL, NULL),
    ('C43', 'B4', 'PC', 'De los tres problemas de tus últimas 48 horas, ¿cuántos se repiten todos los', false, 0, false, false, 43, NULL, NULL, NULL),
    ('C44', 'B4', 'PC', 'Autorizo la verificación de referencias, certificados y datos declarados. Sí / No', false, 0, false, true, 44, NULL, NULL, NULL),
    ('C45', 'C', 'EF-4', 'Servir, desde un puesto de supervisión, es principalmente:', true, 1, false, false, 45, NULL, NULL, NULL),
    ('C46', 'C', 'CD', 'Lo último que hiciste por un compañero o cliente sin que te lo pidieran. (4', true, 1, false, false, 46, 4, NULL, NULL),
    ('C47', 'C', 'SJT-R', 'Un cliente o usuario reclama fuera de tu horario.', true, 1, false, false, 47, NULL, NULL, NULL),
    ('C48', 'C', 'CD', 'El estándar de calidad más alto que has exigido. (4 campos)', true, 1, false, false, 48, 4, NULL, NULL),
    ('C49', 'C', 'CD', 'Dos cosas que empezaste sin que nadie te lo pidiera. (3 campos × 2)', true, 1, false, false, 49, 3, NULL, NULL),
    ('C50', 'C', 'SJT-R', 'Ves un problema serio en un área que no es la tuya.', true, 1, false, false, 50, NULL, NULL, NULL),
    ('C51', 'C', 'DE', 'Lee: "Esta semana cumplimos el 95% de las tareas, mejor que el 80% de la semana', true, 2, true, false, 51, NULL, NULL, NULL),
    ('C52', 'C', 'EF-4', 'La última vez que cambié de opinión sobre algo del trabajo fue:', true, 1, false, false, 52, NULL, NULL, NULL),
    ('C53', 'C', 'SJT-R', 'Tu jefe da una instrucción que, por lo que ves en campo, va a fallar.', true, 2, true, false, 53, NULL, NULL, NULL),
    ('C54', 'C', 'V', '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Formato (lista) · Horas', true, 1, false, false, 54, NULL, 'D84', NULL),
    ('C55', 'C', 'INV', 'Marca lo que haces todos los días de trabajo sin falta.', true, 1, false, false, 55, NULL, NULL, NULL)
  ) AS v(codigo, bloque, tipo, enunciado, es_puntuable, peso, es_clave,
         es_eliminatorio, orden, casos_pedidos, rangos_de, formula)
 WHERE vb.etiqueta = 'Banco RENASER v3 · Coordinación y Supervisión';

-- Banco RENASER v3 · Ejecutivo y Operativo · 50 ítems
INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado,
                      es_puntuable, peso, es_clave, es_eliminatorio, orden,
                      casos_pedidos, rangos_de_pregunta_codigo, formula_puntaje)
SELECT vb.id, v.codigo, v.bloque, v.tipo, v.enunciado, v.es_puntuable, v.peso,
       v.es_clave, v.es_eliminatorio, v.orden, v.casos_pedidos,
       v.rangos_de, v.formula
  FROM version_banco vb, (VALUES
    ('O01', 'A1', 'V', 'Años haciendo este trabajo: ___ · En cuántas empresas: ___ · Nombre exacto de', true, 1, false, false, 1, NULL, NULL, NULL),
    ('O02', 'A1', 'V', 'Escribe hasta 5 herramientas, equipos o programas que dominas para este', true, 1, false, false, 2, NULL, NULL, '(campos llenos ÷ 5) × 3'),
    ('O03', 'A1', 'CD', 'Tu tarea principal. (6 campos)', true, 2, true, false, 3, 6, NULL, NULL),
    ('O04', 'A1', 'SJT-R', 'Recibes una instrucción y no entiendes bien una parte.', true, 1, false, false, 4, NULL, NULL, NULL),
    ('O05', 'A1', 'CD', 'El trabajo más difícil que te tocó. (5 campos)', true, 2, true, false, 5, 5, NULL, NULL),
    ('O06', 'A1', 'EF-4', 'Si no sé cómo hacer algo:', true, 1, false, false, 6, NULL, NULL, NULL),
    ('O07', 'A1', 'V', 'Certificados, cursos o constancias del oficio: hasta 3 (nombre + institución + año).', true, 1, false, false, 7, NULL, NULL, '(campos completos ÷ 9) × 3'),
    ('O08', 'A1', 'EF-4', 'En qué soy mejor que el promedio en este trabajo:', true, 1, false, false, 8, NULL, NULL, NULL),
    ('O09', 'A2', 'EF-4', 'Organizo mi día:', true, 1, false, false, 9, NULL, NULL, NULL),
    ('O10', 'A2', 'SEC', 'Ordena qué haces al recibir una tarea nueva.', true, 1, false, false, 10, NULL, NULL, NULL),
    ('O11', 'A2', 'EF-4', 'Sé que terminé bien una tarea cuando:', true, 1, false, false, 11, NULL, NULL, NULL),
    ('O12', 'A2', 'SJT-R', 'Te asignan tres cosas urgentes al mismo tiempo.', true, 1, false, false, 12, NULL, NULL, NULL),
    ('O13', 'A2', 'SJT-R', 'Te equivocas en algo y nadie se dio cuenta.', true, 2, true, false, 13, NULL, NULL, NULL),
    ('O14', 'A2', 'INV', 'Marca lo que haces siempre al iniciar y al cerrar tu jornada.', true, 1, false, false, 14, NULL, NULL, NULL),
    ('O15', 'A3', 'V', 'En tu último trabajo, el último mes: tardanzas ___ · faltas ___ · ¿avisaste con', true, 1, false, false, 15, NULL, NULL, NULL),
    ('O16', 'A3', 'CD', 'Una vez que te comprometiste a algo y no pudiste cumplir. (5 campos)', true, 2, true, false, 16, 5, NULL, NULL),
    ('O17', 'A3', 'SJT-R', 'Te piden quedarte una hora más por una urgencia real.', true, 1, false, false, 17, NULL, NULL, NULL),
    ('O18', 'A3', 'INV', 'Marca las normas de orden y seguridad que aplicabas en tu puesto anterior.', true, 1, false, false, 18, NULL, NULL, NULL),
    ('O19', 'A3', 'PC', '¿Por qué saliste de tu último trabajo? (lista + texto ≤ 60 car.) · Nombre y contacto', false, 0, false, true, 19, NULL, NULL, NULL),
    ('O20', 'A4', 'SJT-R', 'Un compañero no está haciendo su parte y eso te afecta.', true, 1, false, false, 20, NULL, NULL, NULL),
    ('O21', 'A4', 'CD', 'Un problema que tuviste con un compañero. (4 campos)', true, 1, false, false, 21, 4, NULL, NULL),
    ('O22', 'A4', 'EF-4', 'Mis compañeros dirían de mí que:', true, 1, false, false, 22, NULL, NULL, NULL),
    ('O23', 'A4', 'CD', '¿Has enseñado tu trabajo a alguien? (4 campos)', true, 1, false, false, 23, 4, NULL, NULL),
    ('O24', 'A4', 'SJT-R', 'Tu jefe te corrige delante de otros compañeros.', true, 1, false, false, 24, NULL, NULL, NULL),
    ('O25', 'B1', 'EF-4', 'Cuando tengo un problema personal fuerte:', true, 1, false, false, 25, NULL, NULL, NULL),
    ('O26', 'B1', 'EF-4', 'Cuando algo me supera, acudo a:', true, 1, false, false, 26, NULL, NULL, NULL),
    ('O27', 'B1', 'EF-4', 'Lo que más me molesta de trabajar con otros:', true, 1, false, false, 27, NULL, NULL, NULL),
    ('O28', 'B1', 'EF-4', 'Mis dos últimos jefes dirían de mí que:', true, 1, false, false, 28, NULL, NULL, NULL),
    ('O29', 'B1', 'PC', '¿Alguna vez no cumpliste un compromiso de trabajo? (nunca / una vez / algunas', false, 0, false, false, 29, NULL, NULL, NULL),
    ('O30', 'B2', 'CD', 'Tu día típico. (5 campos)', true, 1, false, false, 30, 5, NULL, NULL),
    ('O31', 'B2', 'EF-4', 'Mis horas de sueño entre semana:', true, 1, false, false, 31, NULL, NULL, NULL),
    ('O32', 'B2', 'V', 'Actividad física: (nunca / esporádica / 1–2 por semana / 3–4 / diaria) · Años', true, 1, false, false, 32, NULL, 'D57', NULL),
    ('O33', 'B2', 'EF-4', 'Recupero energía con:', true, 1, false, false, 33, NULL, NULL, NULL),
    ('O34', 'B2', 'SJT-R', 'Estás cansado o con un malestar leve y hay una entrega comprometida para hoy.', true, 2, true, false, 34, NULL, NULL, NULL),
    ('O35', 'B3', 'EF-4', 'Lo que me haría dejar un trabajo en 6 meses:', true, 1, false, false, 35, NULL, NULL, NULL),
    ('O36', 'B3', 'EF-4', 'Administro mi sueldo:', true, 1, false, false, 36, NULL, NULL, NULL),
    ('O37', 'B3', 'EF-4', 'Opción                                                                       Valor', true, 2, true, false, 37, NULL, NULL, NULL),
    ('O38', 'B3', 'SJT-R', 'Encuentras dinero o material de la empresa que nadie reclama.', true, 2, true, true, 38, NULL, NULL, NULL),
    ('O39', 'B3', 'PC', 'Autorizo la verificación de mis referencias, certificados y datos declarados. Sí /', false, 0, false, true, 39, NULL, NULL, NULL),
    ('O40', 'C', 'EF-4', 'Atender bien a alguien es principalmente:', true, 1, false, false, 40, NULL, NULL, NULL),
    ('O41', 'C', 'CD', 'La última vez que ayudaste a alguien sin que te lo pidieran. (4 campos)', true, 1, false, false, 41, 4, NULL, NULL),
    ('O42', 'C', 'SJT-R', 'Un cliente está molesto y el error no fue tuyo.', true, 1, false, false, 42, NULL, NULL, NULL),
    ('O43', 'C', 'CD', 'Algo de lo que te sientes orgulloso en un trabajo. (4 campos)', true, 1, false, false, 43, 4, NULL, NULL),
    ('O44', 'C', 'CD', 'Algo que mejoraste por iniciativa propia. (4 campos)', true, 1, false, false, 44, 4, NULL, NULL),
    ('O45', 'C', 'SJT-R', 'Ves algo mal hecho que no es tu responsabilidad.', true, 1, false, false, 45, NULL, NULL, NULL),
    ('O46', 'C', 'DE', 'Tu jefe dice: "Hay que apurar la entrega, saltemos la revisión final para llegar a la', true, 2, true, false, 46, NULL, NULL, NULL),
    ('O47', 'C', 'SJT-R', 'Te indican hacer algo de una forma que, por tu experiencia, va a dañar el material', true, 2, true, false, 47, NULL, NULL, NULL),
    ('O48', 'C', 'V', '¿Qué estás aprendiendo actualmente? (texto ≤ 40 car.) · Cómo (curso / por mi', true, 1, false, false, 48, NULL, 'D84', NULL),
    ('O49', 'C', 'INV', 'Marca lo que haces todos los días de trabajo sin falta.', true, 1, false, false, 49, NULL, NULL, NULL),
    ('O50', 'C', 'EF-4', 'Deberían elegirme a mí y no a otro con la misma experiencia porque:', true, 1, false, false, 50, NULL, NULL, NULL)
  ) AS v(codigo, bloque, tipo, enunciado, es_puntuable, peso, es_clave,
         es_eliminatorio, orden, casos_pedidos, rangos_de, formula)
 WHERE vb.etiqueta = 'Banco RENASER v3 · Ejecutivo y Operativo';

-- Opciones. EF-4 esconde un valor de −2 a +2 (columna valor); en SJT-R el
-- número es la calificación correcta de 1 a 5 (columna puntaje).
INSERT INTO opcion (pregunta_id, letra, texto, valor, puntaje)
SELECT p.id, v.letra, v.texto, v.valor, v.puntaje
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES
    ('D06', 'a', 'Revisar si el indicador del plan medía actividad en lugar de resultado', NULL, 5),
    ('D06', 'b', 'Extender el plan de mejora un mes más', NULL, 2),
    ('D06', 'c', 'Revisar si el problema es de la persona o del proceso en que trabaja', NULL, 5),
    ('D06', 'd', 'Iniciar el proceso de separación, el plan ya se le dio', NULL, 1),
    ('D06', 'e', 'Pedirle a él que proponga qué cambiar', NULL, 3),
    ('D07', 'a', 'Los entregables comprometidos y el tiempo esperado de cada uno', 2, NULL),
    ('D07', 'b', 'La cantidad de actividades asignadas en la semana', 0, NULL),
    ('D07', 'c', 'Lo que la operación va exigiendo día a día', -2, NULL),
    ('D07', 'd', 'Lo que cada persona históricamente logra sostener', 1, NULL),
    ('D09', 'a', 'Reunión de 20 min con las 3 prioridades y el responsable de cada una', NULL, 5),
    ('D09', 'b', 'Mensaje escrito con las metas de la semana y sus números', NULL, 4),
    ('D09', 'c', 'Cada jefe intermedio baja la información a su gente', NULL, 4),
    ('D09', 'd', 'Cada quien ya sabe lo que le toca; se hace seguimiento durante la semana', NULL, 1),
    ('D09', 'e', 'Reunión de una hora para revisar la semana anterior y planificar', NULL, 2),
    ('D12', 'a', 'En una plataforma, cuando quería, con el dato ya actualizado', 2, NULL),
    ('D12', 'b', 'Todos los días a primera hora, aunque fuera a mano', 2, NULL),
    ('D12', 'c', 'En una reunión semanal fija donde se presentaban', 1, NULL),
    ('D12', 'd', 'En el informe mensual del área', -1, NULL),
    ('D15', 'a', 'Reunión semanal de control igual que al resto', NULL, 2),
    ('D15', 'b', 'Revisión mensual y foco en darle un reto mayor', NULL, 5),
    ('D15', 'c', 'Lo dejo trabajar y solo miro su indicador', NULL, 4),
    ('D15', 'd', 'Seguimiento diario para que no se relaje', NULL, 1),
    ('D15', 'a2', 'Seguimiento semanal con revisión de sus números', NULL, 5),
    ('D15', 'b2', 'Conversación motivacional y confianza en que reaccione', NULL, 2),
    ('D15', 'c2', 'Seguimiento diario documentado Opción                                                                        Clave', NULL, 3),
    ('D15', 'd2', 'Esperar el cierre de mes para ver si se recupera', NULL, 1),
    ('D15', 'a3', 'Seguimiento diario documentado, con plan y fecha de corte', NULL, 5),
    ('D15', 'b3', 'Seguimiento semanal como al resto', NULL, 2),
    ('D15', 'c3', 'Reasignarlo a una tarea más simple', NULL, 2),
    ('D15', 'd3', 'Iniciar la separación de inmediato', NULL, 2),
    ('D18', 'a', 'Registro en sistema con fecha, hora y responsable', 2, NULL),
    ('D18', 'b', 'Un acta o checklist firmado', 1, NULL),
    ('D18', 'c', 'Una anotación mía para hacer seguimiento', 0, NULL),
    ('D18', 'd', 'El acuerdo verbal, que es lo que la gente realmente cumple', -2, NULL),
    ('D20', 'a', 'El reporte de indicadores del área Opción                                                                    Clave', NULL, 5),
    ('D20', 'b', 'La reunión semanal del equipo', NULL, 5),
    ('D20', 'c', 'Las decisiones de prioridad del día a día', NULL, 4),
    ('D20', 'd', 'La relación con el cliente o área usuaria', NULL, 4),
    ('D20', 'e', 'La corrección de un desvío que aparezca', NULL, 4),
    ('D23', 'a', 'Lo detecte yo en el dato antes de que alguien me lo cuente', 2, NULL),
    ('D23', 'b', 'Me lo reporte el responsable el mismo día', 1, NULL),
    ('D23', 'c', 'Salga en la reunión semanal', 0, NULL),
    ('D23', 'd', 'Me entere cuando ya afectó a un cliente o a otra área', -2, NULL),
    ('D24', 'a', 'Avisar hoy mismo con el escenario y dos alternativas', NULL, 5),
    ('D24', 'b', 'Avisar hoy mismo, aunque todavía no tengas la solución', NULL, 4),
    ('D24', 'c', 'Trabajar dos días para ver si se rescata y avisar el jueves', NULL, 2),
    ('D24', 'd', 'Avisar en la reunión semanal, que es el canal establecido', NULL, 2),
    ('D24', 'e', 'Resolverlo primero y reportar el hecho consumado', NULL, 1),
    ('D25', 'a', 'Preguntar cómo entendió la instrucción antes de corregir', NULL, 5),
    ('D25', 'b', 'Revisar si tu forma de instruir dejó espacio a interpretación', NULL, 5),
    ('D25', 'c', 'Establecer que en adelante toda instrucción se confirme por escrito', NULL, 4),
    ('D25', 'd', 'Corregir el resultado y volver a explicar', NULL, 3),
    ('D25', 'e', 'Hacerlo tú esta vez y explicar bien la próxima', NULL, 1),
    ('D26', 'a', 'Pedir una reunión y exponer el impacto con datos antes de ejecutar', NULL, 5),
    ('D26', 'b', 'Ejecutarla y dejar por escrito tu observación técnica', NULL, 4),
    ('D26', 'c', 'Ejecutarla sin comentarios, él tiene la información completa', NULL, 2),
    ('D26', 'd', 'Comentar el desacuerdo con tus pares para medir el respaldo', NULL, 1),
    ('D26', 'e', 'Ejecutarla parcialmente hasta ver cómo evoluciona', NULL, 1),
    ('D31', 'a', 'Cuál resolvió mejor un caso real puesto en la evaluación', 2, NULL),
    ('D31', 'b', 'Cuál trae la experiencia más cercana a nuestro problema actual', 1, NULL),
    ('D31', 'c', 'Cuál encaja mejor con la forma de trabajo del equipo Opción                                                                       Valor', 1, NULL),
    ('D31', 'd', 'Cuál mostró más ganas y disposición en la entrevista', -1, NULL),
    ('D38', 'a', 'Recalcular qué parte de la meta sigue siendo alcanzable y comunicarlo hoy Opción                                                                       Clave', NULL, 5),
    ('D38', 'b', 'Redistribuir la carga y sostener la meta original', NULL, 3),
    ('D38', 'c', 'Buscar reemplazo del proveedor antes de mover nada más', NULL, 3),
    ('D38', 'd', 'Avisar a gerencia que la meta no se cumplirá', NULL, 2),
    ('D38', 'e', 'Que el equipo trabaje horas extra hasta recuperar', NULL, 1),
    ('D39', 'a', 'El impacto de cada cosa sobre el resultado del mes', 2, NULL),
    ('D39', 'b', 'El costo de no hacerlo', 2, NULL),
    ('D39', 'c', 'Lo que puedo cerrar rápido para descongestionar', 0, NULL),
    ('D39', 'd', 'Lo que más se está reclamando', -2, NULL),
    ('D43', 'a', 'Reconstruir la trazabilidad del proceso con fechas y responsables antes de hablar con nadie', NULL, 5),
    ('D43', 'b', 'Atender primero al cliente y después buscar responsables', NULL, 5),
    ('D43', 'c', 'Reunir a ambos jefes y que expongan su versión', NULL, 3),
    ('D43', 'd', 'Definir tú el responsable y seguir adelante', NULL, 2),
    ('D43', 'e', 'Escalarlo a gerencia para que defina', NULL, 1),
    ('D44', 'a', 'El área funcionó sin que yo tuviera que intervenir', 2, NULL),
    ('D44', 'b', 'Avanzamos en algo estructural, no solo en la operación', 2, NULL),
    ('D44', 'c', 'Los indicadores del área cerraron en meta', 1, NULL),
    ('D44', 'd', 'Mi jefe quedó conforme con los resultados', -1, NULL),
    ('D45', 'a', 'Comunicarla con el criterio y los datos detrás, y sostenerla', NULL, 5),
    ('D45', 'b', 'Comunicarla y abrir un canal para atender el impacto en cada persona', NULL, 5),
    ('D45', 'c', 'Consultarla con el equipo antes, aunque la decisión ya esté tomada', NULL, 2),
    ('D45', 'd', 'Implementarla por partes para que se note menos', NULL, 1),
    ('D45', 'e', 'Delegar la comunicación a RRHH', NULL, 1),
    ('D46', 'a', 'Mantengo mi rendimiento y lo trabajo fuera del horario', 2, NULL),
    ('D46', 'b', 'Aviso a mi jefe que estaré a media máquina unos días', 1, NULL),
    ('D46', 'c', 'El trabajo es justamente lo que me ordena en esos momentos', 0, NULL),
    ('D46', 'd', 'Me cuesta concentrarme varios días y lo recupero después', -1, NULL),
    ('D47', 'a', 'Un mentor o un par de confianza fuera de mi línea', 2, NULL),
    ('D47', 'b', 'Acompañamiento profesional', 2, NULL),
    ('D47', 'c', 'Mi entorno cercano', 1, NULL),
    ('D47', 'd', 'Lo resuelvo solo, no me gusta cargar a otros', -1, NULL),
    ('D48', 'a', 'Cortar la situación en el momento sin discutir, y conversarlo en privado el mismo día', NULL, 5),
    ('D48', 'b', 'Dejarlo pasar y conversarlo después, para no escalar delante de todos', NULL, 3),
    ('D48', 'c', 'Documentar el hecho y aplicar el procedimiento disciplinario', NULL, 3),
    ('D48', 'd', 'Responderle en el momento con la misma firmeza', NULL, 1),
    ('D48', 'e', 'Ignorarlo, su rendimiento compensa', NULL, 1),
    ('D49', 'a', 'Que no avisen a tiempo cuando algo se traba', 1, NULL),
    ('D49', 'b', 'La falta de compromiso con lo acordado', 1, NULL),
    ('D49', 'c', 'Tener que repetir lo mismo varias veces', 0, NULL),
    ('D49', 'd', 'Los conflictos personales entre miembros del equipo', 0, NULL),
    ('D51', 'a', 'Digo lo que hay que decir aunque incomode', 2, NULL),
    ('D51', 'b', 'Prefiero que el equipo me respete aunque no me quiera', 1, NULL),
    ('D51', 'c', 'Necesito reconocimiento para sostener el ritmo', -1, NULL),
    ('D51', 'd', 'Evito el conflicto hasta que se vuelve inevitable', -2, NULL),
    ('D53', 'a', 'Que a veces me adelanto y resuelvo lo que debía resolver el equipo', 1, NULL),
    ('D53', 'b', 'Que soy directo hasta un punto que puede incomodar', 1, NULL),
    ('D53', 'c', 'Que soy muy exigente con los plazos', 1, NULL),
    ('D53', 'd', 'Que me cuesta reconocer cuando algo bueno se hizo', 0, NULL),
    ('D55', 'a', '7–8 horas, casi siempre a la misma hora', 2, NULL),
    ('D55', 'b', '6–7 horas, es lo que me alcanza', 0, NULL),
    ('D55', 'c', 'Variable, según cómo venga la semana', -1, NULL),
    ('D55', 'd', 'Menos de 6, prefiero ganar tiempo de trabajo', -2, NULL),
    ('D56', 'a', 'Actividad física en horario fijo', 2, NULL),
    ('D56', 'b', 'Descanso y alimentación planificados', 2, NULL),
    ('D56', 'c', 'Salir y desconectarme del tema', 0, NULL),
    ('D56', 'd', 'Dormir el fin de semana lo que no dormí en la semana', -1, NULL),
    ('D58', 'a', 'Nada, justamente ahí sostengo más la rutina', 2, NULL),
    ('D58', 'b', 'El ejercicio', 0, NULL),
    ('D58', 'c', 'La alimentación', 0, NULL),
    ('D58', 'd', 'El sueño', -1, NULL),
    ('D59', 'a', 'Trabajar hasta tarde y arrastrar el cansancio', 0, NULL),
    ('D59', 'b', 'Revisar el celular apenas despierto', 0, NULL),
    ('D59', 'c', 'Asumir tareas que debería delegar', -1, NULL),
    ('D59', 'd', 'Ninguno relevante, tengo mis hábitos bajo control', -2, NULL),
    ('D60', 'a', 'Un trabajo que me exige y me hace crecer vale más que uno que paga mejor', 2, NULL),
    ('D60', 'b', 'Me quedaría en un proyecto en el que creo aunque el pago tarde', 1, NULL),
    ('D60', 'c', 'Lo primero que evalúo de una oferta es la compensación', -2, NULL),
    ('D60', 'd', 'La compensación es la medida más honesta de cuánto vale tu trabajo', -2, NULL),
    ('D61', 'a', 'Que no me dejen cambiar lo que veo mal', 2, NULL),
    ('D61', 'b', 'No tener claro hacia dónde crezco', 1, NULL),
    ('D61', 'c', 'Un liderazgo que no respeto', 1, NULL),
    ('D61', 'd', 'Una oferta con mejor compensación', -2, NULL),
    ('D62', 'a', 'El resultado medible que puedo generar', 2, NULL),
    ('D62', 'b', 'Lo que paga el mercado por este puesto', 1, NULL),
    ('D62', 'c', 'Mi trayectoria y años de experiencia', 0, NULL),
    ('D62', 'd', 'Lo que necesito para mi situación actual', -2, NULL),
    ('D63', 'a', 'Rechazarla: comprometiste ese proyecto', NULL, 5),
    ('D63', 'b', 'Conversarlo abiertamente con tu jefe actual', NULL, 4),
    ('D63', 'c', 'Aceptarla pero negociar quedarte hasta cerrar el proyecto', NULL, 3),
    ('D63', 'd', 'Usarla para negociar un aumento', NULL, 2),
    ('D63', 'e', 'Aceptarla, es una oportunidad que no se repite', NULL, 1),
    ('D64', 'a', 'Con presupuesto y ahorro fijo mensual', 2, NULL),
    ('D64', 'b', 'Priorizo invertir en formación o proyectos antes que ahorrar', 1, NULL),
    ('D64', 'c', 'Ahorro lo que queda al final del mes', 0, NULL),
    ('D64', 'd', 'No llevo un control detallado', -1, NULL),
    ('D65', 'a', 'Reportar el hallazgo: el indicador está mal diseñado', NULL, 5),
    ('D65', 'b', 'Proponer el indicador corregido aunque te reduzca el bono', NULL, 5),
    ('D65', 'c', 'No usarlo y no decir nada', NULL, 3),
    ('D65', 'd', 'Usarlo y avisar que debería cambiarse el próximo periodo', NULL, 2),
    ('D65', 'e', 'Usarlo: es legal y el indicador es el indicador', NULL, 1),
    ('D66', 'a', 'Que fui exigente pero justo', NULL, NULL),
    ('D66', 'b', 'Que fui duro y no le gustó en el momento', NULL, NULL),
    ('D66', 'c', 'Que lo apoyé', NULL, NULL),
    ('D66', 'd', 'Que no se dio cuenta de que estaba siendo evaluado', NULL, NULL),
    ('D66', 'd2', '+ frecuencia de seguimiento "diario" o "2 veces por', NULL, NULL),
    ('D69', 'a', 'Un área que dirigí sin haberla ejecutado nunca', 2, NULL),
    ('D69', 'b', 'Alguna herramienta técnica que uso a nivel usuario', 1, NULL),
    ('D69', 'c', 'Un tema del que sé lo suficiente para sostener una reunión', 1, NULL),
    ('D69', 'd', 'Nada, no me presento como experto en lo que no domino', -1, NULL),
    ('D71', 'a', 'Quitarle obstáculos al equipo para que pueda rendir', 2, NULL),
    ('D71', 'b', 'Dar el ejemplo en el estándar que exijo', 2, NULL),
    ('D71', 'c', 'Asegurar que el cliente reciba lo prometido, cueste lo que cueste', 1, NULL),
    ('D71', 'd', 'Estar disponible cuando me necesitan', 0, NULL),
    ('D73', 'a', 'Responder en el momento reconociendo y dando plazo de solución', NULL, 5),
    ('D73', 'b', 'Llamarlo por teléfono en lugar de escribir', NULL, 5),
    ('D73', 'c', 'Responder el sábado con la solución ya trabajada', NULL, 3),
    ('D73', 'd', 'Derivarlo al responsable del área para que responda', NULL, 2),
    ('D73', 'e', 'Responder el lunes en horario de oficina', NULL, 1),
    ('D76', 'a', 'Levanté un problema de otra área y propuse cómo resolverlo', 2, NULL),
    ('D76', 'b', 'Documenté algo que no estaba documentado', 2, NULL),
    ('D76', 'c', 'Cubrí funciones de alguien que faltó', 0, NULL),
    ('D76', 'd', 'Nada, el puesto ya demanda todo mi tiempo', -2, NULL),
    ('D77', 'a', 'Llevarlo directo al jefe del área con evidencia y una propuesta', NULL, 5),
    ('D77', 'b', 'Ofrecer ayuda para resolverlo en conjunto', NULL, 4),
    ('D77', 'c', 'Comentarlo en la reunión de gerencia', NULL, 2),
    ('D77', 'd', 'Avisar a tu jefe para que él lo maneje', NULL, 2),
    ('D77', 'e', 'No intervenir, no es tu ámbito', NULL, 1),
    ('D78', 'a', 'El mismo día', 2, NULL),
    ('D78', 'b', 'Esa semana, cuando tengo la evidencia armada', 2, NULL),
    ('D78', 'c', 'Lo anoto y lo veo cuando haya espacio', -1, NULL),
    ('D78', 'd', 'Cuando alguien más lo levanta también', -2, NULL),
    ('D80', 'a', 'Este mes', 2, NULL),
    ('D80', 'b', 'Este año', 1, NULL),
    ('D80', 'c', 'Hace más de un año Opción                                                               Valor', -1, NULL),
    ('D80', 'd', 'No lo recuerdo', -2, NULL),
    ('D82', 'a', 'Exponer el análisis con datos antes de ejecutar, y acatar la decisión final', NULL, 5),
    ('D82', 'b', 'Pedir hacer una prueba pequeña antes de aplicarlo a todo', NULL, 5),
    ('D82', 'c', 'Ejecutar y dejar constancia escrita de tu observación', NULL, 4),
    ('D82', 'd', 'Ejecutar sin comentarios, él tiene más información', NULL, 2),
    ('D82', 'e', 'Ejecutar tu versión corregida, que sabes que funciona', NULL, 1),
    ('D83', 'a', 'Confundir actividad con resultado', 2, NULL),
    ('D83', 'b', 'Medir tarde, cuando ya no se puede corregir', 2, NULL),
    ('D83', 'c', 'No documentar lo que funciona', 1, NULL),
    ('D83', 'd', 'No invertir lo suficiente en su gente', 0, NULL),
    ('C07', 'a', 'Revisar si la instrucción y los recursos estaban dados antes de reclamar Opción                                                                      Clave', NULL, 5),
    ('C07', 'b', 'Preguntar al responsable qué pasó, antes de sacar conclusiones', NULL, 4),
    ('C07', 'c', 'Reprogramarla y llamar la atención', NULL, 3),
    ('C07', 'd', 'Hacerla tú para que salga', NULL, 1),
    ('C07', 'e', 'Reportarla como incumplimiento y seguir', NULL, 2),
    ('C08', 'a', 'Revisión mensual y un reto mayor', NULL, 5),
    ('C08', 'b', 'Lo dejo trabajar y miro su resultado', NULL, 4),
    ('C08', 'c', 'Seguimiento igual que al resto', NULL, 2),
    ('C08', 'd', 'Seguimiento diario para que no se relaje', NULL, 1),
    ('C08', 'a2', 'Seguimiento semanal con sus números a la vista', NULL, 5),
    ('C08', 'b2', 'Seguimiento diario documentado', NULL, 3),
    ('C08', 'c2', 'Conversación motivacional y confiar', NULL, 2),
    ('C08', 'd2', 'Esperar el cierre del mes', NULL, 1),
    ('C08', 'a3', 'Seguimiento diario documentado con plan y fecha de corte', NULL, 5),
    ('C08', 'b3', 'Reasignarlo a algo más simple', NULL, 2),
    ('C08', 'c3', 'Seguimiento semanal como al resto', NULL, 2),
    ('C08', 'd3', 'Reportarlo para separación inmediata', NULL, 2),
    ('C09', 'a', 'Registro en sistema con fecha, hora y responsable', 2, NULL),
    ('C09', 'b', 'Un checklist o acta firmada', 1, NULL),
    ('C09', 'c', 'Registro fotográfico con hora', 1, NULL),
    ('C09', 'd', 'El acuerdo verbal, que es lo que la gente cumple', -2, NULL),
    ('C10', 'a', 'La programación diaria del trabajo', NULL, 5),
    ('C10', 'b', 'El registro y reporte de avance', NULL, 5),
    ('C10', 'c', 'La resolución de un imprevisto en campo', NULL, 4),
    ('C10', 'd', 'El orden y la disciplina del equipo', NULL, 4),
    ('C11', 'a', 'Según lo programado la tarde anterior, con responsable definido', 2, NULL),
    ('C11', 'b', 'Según la capacidad demostrada de cada persona', 1, NULL),
    ('C11', 'c', 'En la reunión de arranque, según cómo llegue el día', 0, NULL),
    ('C11', 'd', 'Sobre la marcha, según lo que vaya apareciendo', -2, NULL),
    ('C12', 'a', 'Sus tiempos de entrega se empiezan a alargar en el registro', 2, NULL),
    ('C12', 'b', 'Comparo su carga asignada con la del resto', 2, NULL),
    ('C12', 'c', 'Me lo dice o se le nota', 0, NULL),
    ('C12', 'd', 'Empiezan a aparecer errores en su trabajo', 1, NULL),
    ('C14', 'a', 'Hablar con cada uno por separado y luego reunirlos con acuerdos concretos', NULL, 5),
    ('C14', 'b', 'Reunirlos de inmediato para que lo resuelvan delante tuyo', NULL, 3),
    ('C14', 'c', 'Separarlos de tarea o de turno', NULL, 2),
    ('C14', 'd', 'Reportarlo a RRHH para que intervengan', NULL, 2),
    ('C14', 'e', 'Dejar que se acomoden solos, son adultos', NULL, 1),
    ('C15', 'a', 'Conversarlo en privado poniendo condición y plazo claros', NULL, 5),
    ('C15', 'b', 'Darle una responsabilidad que canalice esa energía', NULL, 4),
    ('C15', 'c', 'Corregirlo delante del equipo para marcar el estándar', NULL, 2),
    ('C15', 'd', 'Dejarlo, su producción compensa', NULL, 1),
    ('C15', 'e', 'Reportarlo para que lo cambien de área', NULL, 2),
    ('C20', 'a', 'Yo los veo primero en mi recorrido o en el registro', 2, NULL),
    ('C20', 'b', 'Me los reporta el responsable el mismo día Opción                                                             Valor', 1, NULL),
    ('C20', 'c', 'Salen en la reunión de coordinación', 0, NULL),
    ('C20', 'd', 'Cuando ya escalaron a mi jefe o al cliente', -2, NULL),
    ('C21', 'a', 'Avisarle de inmediato con lo que ya hiciste para contenerlo', NULL, 5),
    ('C21', 'b', 'Avisarle de inmediato aunque aún no tengas la solución', NULL, 4),
    ('C21', 'c', 'Resolverlo primero y reportarlo al final del día', NULL, 3),
    ('C21', 'd', 'Reportarlo en el informe del día siguiente', NULL, 1),
    ('C21', 'e', 'Resolverlo y no reportarlo si no tuvo consecuencias', NULL, 1),
    ('C23', 'a', 'Presentar el escenario real con alternativas y su costo', NULL, 5),
    ('C23', 'b', 'Decir qué parte sí puedes cumplir en ese plazo y qué parte no', NULL, 5),
    ('C23', 'c', 'Consultarlo con el equipo antes de responder', NULL, 3),
    ('C23', 'd', 'Aceptar y ver cómo se resuelve', NULL, 1),
    ('C23', 'e', 'Decir directamente que no se puede', NULL, 2),
    ('C24', 'a', 'Pidiendo que me la repitan con sus palabras', 2, NULL),
    ('C24', 'b', 'Dejándola por escrito y confirmando recepción', 2, NULL),
    ('C24', 'c', 'Preguntando si quedó claro', -1, NULL),
    ('C24', 'd', 'Verificando el resultado al final', 0, NULL),
    ('C28', 'a', 'Lo que afecta el resultado comprometido del día', 2, NULL),
    ('C28', 'b', 'Lo que más caro sale si no se hace Opción                                                                      Valor', 2, NULL),
    ('C28', 'c', 'Lo que puedo cerrar rápido', 0, NULL),
    ('C28', 'd', 'Lo que más se está reclamando', -2, NULL),
    ('C29', 'a', 'Mantengo mi rendimiento y lo trabajo fuera del horario', 2, NULL),
    ('C29', 'b', 'Aviso a mi jefe que estaré a media máquina unos días', 1, NULL),
    ('C29', 'c', 'El trabajo me ordena en esos momentos', 0, NULL),
    ('C29', 'd', 'Me cuesta concentrarme varios días', -1, NULL),
    ('C30', 'a', 'Un mentor o alguien de confianza con más experiencia', 2, NULL),
    ('C30', 'b', 'Acompañamiento profesional', 2, NULL),
    ('C30', 'c', 'Mi entorno cercano', 1, NULL),
    ('C30', 'd', 'Lo resuelvo solo', -1, NULL),
    ('C31', 'a', 'Cortar la situación sin discutir y conversarlo en privado el mismo día', NULL, 5),
    ('C31', 'b', 'Documentar el hecho y aplicar el procedimiento', NULL, 3),
    ('C31', 'c', 'Dejarlo pasar y hablarlo después', NULL, 3),
    ('C31', 'd', 'Responderle en el momento con la misma firmeza', NULL, 1),
    ('C31', 'e', 'Ignorarlo, no vale la pena', NULL, 1),
    ('C32', 'a', 'Que no avisen a tiempo cuando algo se traba', 1, NULL),
    ('C32', 'b', 'La falta de compromiso con lo acordado', 1, NULL),
    ('C32', 'c', 'Tener que repetir lo mismo', 0, NULL),
    ('C32', 'd', 'Los conflictos personales del equipo', 0, NULL),
    ('C35', 'a', '7–8 h, casi siempre a la misma hora', 2, NULL),
    ('C35', 'b', '6–7 h', 0, NULL),
    ('C35', 'c', 'Variable según la semana', -1, NULL),
    ('C35', 'd', 'Menos de 6 h', -2, NULL),
    ('C37', 'a', 'Actividad física en horario fijo', 2, NULL),
    ('C37', 'b', 'Descanso y alimentación planificados', 2, NULL),
    ('C37', 'c', 'Salir y desconectar', 0, NULL),
    ('C37', 'd', 'Dormir el fin de semana lo que no dormí', -1, NULL),
    ('C38', 'a', 'Nada, ahí sostengo más la rutina', 2, NULL),
    ('C38', 'b', 'El ejercicio', 0, NULL),
    ('C38', 'c', 'La alimentación', 0, NULL),
    ('C38', 'd', 'El sueño', -1, NULL),
    ('C39', 'a', 'Que no me dejen mejorar lo que veo mal', 2, NULL),
    ('C39', 'b', 'No tener claro hacia dónde crezco', 1, NULL),
    ('C39', 'c', 'Un jefe que no respeto', 1, NULL),
    ('C39', 'd', 'Una oferta con mejor sueldo', -2, NULL),
    ('C40', 'a', 'Un trabajo que me exige y me hace crecer vale más que uno que paga mejor', 2, NULL),
    ('C40', 'b', 'Prefiero un lugar donde aprenda aunque pague algo menos', 1, NULL),
    ('C40', 'c', 'Lo primero que evalúo de una oferta es el sueldo', -2, NULL),
    ('C40', 'd', 'El sueldo es la medida más honesta de cuánto vale tu trabajo', -2, NULL),
    ('C41', 'a', 'Con presupuesto y ahorro fijo mensual', 2, NULL),
    ('C41', 'b', 'Priorizo invertir en formación', 1, NULL),
    ('C41', 'c', 'Ahorro lo que queda', 0, NULL),
    ('C41', 'd', 'No llevo control detallado', -1, NULL),
    ('C42', 'a', 'Avisar que el indicador está mal planteado', NULL, 5),
    ('C42', 'b', 'Proponer cómo medirlo bien aunque tu número baje', NULL, 5),
    ('C42', 'c', 'No usarlo y no decir nada', NULL, 3),
    ('C42', 'd', 'Usarlo y avisar que debería cambiarse después', NULL, 2),
    ('C42', 'e', 'Usarlo, el indicador es el indicador', NULL, 1),
    ('C45', 'a', 'Quitarle trabas al equipo para que pueda cumplir', 2, NULL),
    ('C45', 'b', 'Dar el ejemplo en el estándar que exijo', 2, NULL),
    ('C45', 'c', 'Asegurar que el cliente reciba lo prometido', 1, NULL),
    ('C45', 'd', 'Estar disponible cuando me necesitan', 0, NULL),
    ('C47', 'a', 'Responder reconociendo y dando un plazo concreto', NULL, 5),
    ('C47', 'b', 'Llamar en lugar de escribir', NULL, 4),
    ('C47', 'c', 'Avisar al responsable para que lo tome', NULL, 3),
    ('C47', 'd', 'Responder al día siguiente en horario', NULL, 2),
    ('C47', 'e', 'No responder, no es mi horario', NULL, 1),
    ('C50', 'a', 'Avisar al responsable con evidencia y una propuesta', NULL, 5),
    ('C50', 'b', 'Ofrecer ayuda para resolverlo', NULL, 4),
    ('C50', 'c', 'Comentárselo a mi jefe para que lo maneje', NULL, 2),
    ('C50', 'd', 'Comentarlo en la reunión de coordinación', NULL, 2),
    ('C50', 'e', 'No intervenir, no es mi área', NULL, 1),
    ('C52', 'a', 'Este mes', 2, NULL),
    ('C52', 'b', 'Este año', 1, NULL),
    ('C52', 'c', 'Hace más de un año', -1, NULL),
    ('C52', 'd', 'No lo recuerdo', -2, NULL),
    ('C53', 'a', 'Exponerle lo que ves con datos antes de ejecutar, y acatar su decisión', NULL, 5),
    ('C53', 'b', 'Proponer probarlo en pequeño antes de aplicarlo a todo', NULL, 5),
    ('C53', 'c', 'Ejecutar y dejar constancia escrita de tu observación', NULL, 4),
    ('C53', 'd', 'Ejecutar sin comentarios, él sabe más', NULL, 2),
    ('C53', 'e', 'Ejecutar tu versión corregida, que sabes que funciona', NULL, 1),
    ('O04', 'a', 'Preguntar de inmediato antes de empezar', NULL, 5),
    ('O04', 'b', 'Preguntarle a un compañero que ya lo hizo', NULL, 4),
    ('O04', 'c', 'Empezar por la parte que sí entiendes y preguntar lo demás', NULL, 3),
    ('O04', 'd', 'Hacerlo como creo y mostrarlo antes de terminar', NULL, 2),
    ('O04', 'e', 'Hacerlo como creo, si está mal me dirán', NULL, 1),
    ('O06', 'a', 'Pregunto de inmediato, prefiero no perder tiempo', 2, NULL),
    ('O06', 'b', 'Busco por mi cuenta y luego confirmo si voy bien', 2, NULL),
    ('O06', 'c', 'Lo intento y si falla pregunto', 0, NULL),
    ('O06', 'd', 'Espero que me indiquen cómo', -2, NULL),
    ('O08', 'a', 'En hacerlo bien a la primera, sin errores', 2, NULL),
    ('O08', 'b', 'En el orden y el método con que trabajo', 2, NULL),
    ('O08', 'c', 'En resolver los imprevistos que aparecen', 0, NULL),
    ('O08', 'd', 'En la rapidez', -1, NULL),
    ('O09', 'a', 'Reviso lo pendiente antes de empezar y defino el orden', 2, NULL),
    ('O09', 'b', 'Sigo la programación que me dan', 1, NULL),
    ('O09', 'c', 'Voy resolviendo según lo que va llegando', -2, NULL),
    ('O09', 'd', 'Empiezo por lo más rápido para avanzar', 0, NULL),
    ('O11', 'a', 'La reviso contra el estándar o el checklist', 2, NULL),
    ('O11', 'b', 'La comparo con cómo quedó la última vez que salió bien', 1, NULL),
    ('O11', 'c', 'Mi jefe la aprueba', 0, NULL),
    ('O11', 'd', 'Nadie reclama', -2, NULL),
    ('O12', 'a', 'Preguntar cuál va primero y avisar los tiempos reales de las otras', NULL, 5),
    ('O12', 'b', 'Empezar por la que más afecta si no se hace, y avisar de las otras', NULL, 5),
    ('O12', 'c', 'Hacer primero la más rápida para descargar', NULL, 2),
    ('O12', 'd', 'Hacerlas en el orden en que llegaron', NULL, 2),
    ('O12', 'e', 'Hacer las tres a la vez', NULL, 1),
    ('O13', 'a', 'Lo corrijo y aviso, aunque no se hubiera notado', NULL, 5),
    ('O13', 'b', 'Lo corrijo y aviso solo si puede afectar a alguien más', NULL, 3),
    ('O13', 'c', 'Lo corrijo sin decir nada', NULL, 2),
    ('O13', 'd', 'Lo dejo si no afecta el resultado', NULL, 1),
    ('O13', 'e', 'Depende del tamaño del error', NULL, 2),
    ('O17', 'a', 'Me quedo y dejo el trabajo cerrado', NULL, 5),
    ('O17', 'b', 'Me quedo y coordino cómo se compensa después', NULL, 4),
    ('O17', 'c', 'Me quedo si me avisan con tiempo Opción                                                                   Clave', NULL, 3),
    ('O17', 'd', 'Depende de si es mi responsabilidad directa', NULL, 2),
    ('O17', 'e', 'Mi horario es mi horario', NULL, 1),
    ('O20', 'a', 'Hablar con él directamente primero', NULL, 5),
    ('O20', 'b', 'Ofrecerle ayuda para ver si está trabado en algo', NULL, 4),
    ('O20', 'c', 'Avisar al jefe si después de hablar no cambia', NULL, 4),
    ('O20', 'd', 'Hacer yo su parte para que salga el trabajo', NULL, 1),
    ('O20', 'e', 'Avisar al jefe de una vez', NULL, 2),
    ('O22', 'a', 'Se puede contar conmigo cuando algo se complica', 2, NULL),
    ('O22', 'b', 'Hago mi parte bien y a tiempo', 2, NULL),
    ('O22', 'c', 'Soy tranquilo y no genero problemas', 0, NULL),
    ('O22', 'd', 'Soy exigente y a veces incomodo', 1, NULL),
    ('O24', 'a', 'Acepto la corrección ahí y, si hay algo que aclarar, lo hablo después en privado', NULL, 5),
    ('O24', 'b', 'Acepto la corrección y no digo nada más', NULL, 3),
    ('O24', 'c', 'Explico ahí mismo por qué lo hice así', NULL, 2),
    ('O24', 'd', 'Me molesto pero no lo demuestro', NULL, 1),
    ('O24', 'e', 'Le digo ahí mismo que no me corrija delante de todos', NULL, 1),
    ('O25', 'a', 'Cumplo igual mi trabajo y lo veo fuera del horario', 2, NULL),
    ('O25', 'b', 'Aviso que estaré con menos ritmo unos días', 1, NULL),
    ('O25', 'c', 'El trabajo me distrae y me ayuda', 0, NULL),
    ('O25', 'd', 'Me cuesta concentrarme varios días', -1, NULL),
    ('O26', 'a', 'Alguien con más experiencia en quien confío', 2, NULL),
    ('O26', 'b', 'Ayuda profesional', 2, NULL),
    ('O26', 'c', 'Mi entorno cercano', 1, NULL),
    ('O26', 'd', 'Lo resuelvo solo', -1, NULL),
    ('O27', 'a', 'Que no avisen cuando algo se traba', 1, NULL),
    ('O27', 'b', 'Que no cumplan lo que acordaron', 1, NULL),
    ('O27', 'c', 'Tener que repetir lo mismo', 0, NULL),
    ('O27', 'd', 'Los chismes y conflictos personales', 0, NULL),
    ('O28', 'a', 'Cumplo lo que me comprometo', 2, NULL),
    ('O28', 'b', 'Aprendo rápido y pregunto lo que no sé', 2, NULL),
    ('O28', 'c', 'Soy tranquilo y no doy problemas', 0, NULL),
    ('O28', 'd', 'A veces hago las cosas a mi manera', -1, NULL),
    ('O31', 'a', '7–8 h, casi siempre a la misma hora', 2, NULL),
    ('O31', 'b', '6–7 h', 0, NULL),
    ('O31', 'c', 'Variable', -1, NULL),
    ('O31', 'd', 'Menos de 6 h', -2, NULL),
    ('O33', 'a', 'Actividad física regular', 2, NULL),
    ('O33', 'b', 'Descanso y comida ordenados', 2, NULL),
    ('O33', 'c', 'Salir y distraerme', 0, NULL),
    ('O33', 'd', 'Dormir el fin de semana lo que no dormí', -1, NULL),
    ('O34', 'a', 'Cumplo la entrega y me recupero después', NULL, 4),
    ('O34', 'b', 'Aviso cómo estoy y coordino para que la entrega igual salga', NULL, 5),
    ('O34', 'c', 'Pido que alguien me cubra sin avisar el motivo', NULL, 2),
    ('O34', 'd', 'Hago lo que alcance y explico al final', NULL, 2),
    ('O34', 'e', 'No voy, la salud es primero', NULL, 2),
    ('O35', 'a', 'Que no me dejen crecer ni aprender', 2, NULL),
    ('O35', 'b', 'Un ambiente en el que no se puede trabajar', 1, NULL),
    ('O35', 'c', 'Un jefe que no respeto', 1, NULL),
    ('O35', 'd', 'Que me ofrezcan más sueldo en otro lado', -2, NULL),
    ('O36', 'a', 'Con un presupuesto y un ahorro fijo', 2, NULL),
    ('O36', 'b', 'Priorizo invertir en aprender algo', 1, NULL),
    ('O36', 'c', 'Ahorro lo que queda', 0, NULL),
    ('O36', 'd', 'No llevo control', -1, NULL),
    ('O37', 'a', 'Prefiero un trabajo donde aprenda aunque pague algo menos', 2, NULL),
    ('O37', 'b', 'Un trabajo que me gusta vale más que uno que paga mejor', 2, NULL),
    ('O37', 'c', 'Lo primero que miro de un trabajo es el sueldo', -2, NULL),
    ('O37', 'd', 'El sueldo es lo que mide cuánto vales', -2, NULL),
    ('O38', 'a', 'Lo entrego a mi jefe o al área responsable de inmediato', NULL, 5),
    ('O38', 'b', 'Lo dejo donde está y aviso dónde lo vi', NULL, 4),
    ('O38', 'c', 'Lo guardo hasta que alguien lo reclame', NULL, 3),
    ('O38', 'd', 'Si nadie lo reclama en unos días, me lo quedo', NULL, 1),
    ('O38', 'e', 'Si es poca cosa, no vale la pena avisar', NULL, 1),
    ('O40', 'a', 'Resolverle el problema, no solo escucharlo', 2, NULL),
    ('O40', 'b', 'Cumplir lo que se le prometió', 2, NULL),
    ('O40', 'c', 'Tratarlo con amabilidad', 0, NULL),
    ('O40', 'd', 'Responderle rápido', 1, NULL),
    ('O42', 'a', 'Escucharlo, no discutir de quién es la culpa y decirle qué se va a hacer', NULL, 5),
    ('O42', 'b', 'Resolverlo yo si está a mi alcance', NULL, 5),
    ('O42', 'c', 'Explicarle que ese no fue mi trabajo', NULL, 1),
    ('O42', 'd', 'Derivarlo al responsable de inmediato', NULL, 3),
    ('O42', 'e', 'Llamar a mi jefe para que lo atienda', NULL, 2),
    ('O45', 'a', 'Aviso al responsable directamente', NULL, 5),
    ('O45', 'b', 'Lo corrijo si puedo y aviso', NULL, 4),
    ('O45', 'c', 'Aviso a mi jefe', NULL, 3),
    ('O45', 'd', 'No digo nada, no es mi área Opción                                                                        Clave', NULL, 1),
    ('O45', 'e', 'Lo comento con un compañero', NULL, 1),
    ('O47', 'a', 'Decirlo antes de empezar, explicando qué va a pasar', NULL, 5),
    ('O47', 'b', 'Proponer probarlo en una pieza o parte pequeña primero', NULL, 5),
    ('O47', 'c', 'Hacerlo como me indican y avisar apenas se note el problema', NULL, 3),
    ('O47', 'd', 'Hacerlo como me indican, ellos sabrán', NULL, 1),
    ('O47', 'e', 'Hacerlo a mi manera, que sé que funciona', NULL, 1),
    ('O50', 'a', 'Cumplo lo que prometo y aviso a tiempo cuando algo se complica', 2, NULL),
    ('O50', 'b', 'Trabajo con orden y reviso antes de entregar', 2, NULL),
    ('O50', 'c', 'Aprendo rápido y me adapto', 1, NULL),
    ('O50', 'd', 'Le pongo muchas ganas y soy responsable', -1, NULL)
  ) AS v(codigo, letra, texto, valor, puntaje)
 WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';

-- SEC · los cinco pasos y el lugar que le toca a cada uno
INSERT INTO opcion (pregunta_id, letra, texto, orden_correcto)
SELECT p.id, v.letra, v.texto, v.orden
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES
    ('D04', '1', '① Contrastar su resultado con los datos del área en el mismo periodo', 3),
    ('D04', '2', '② Conversación uno a uno para escuchar su lectura del problema', 1),
    ('D04', '3', '③ Verificar si el estándar, los recursos y la instrucción estaban dados de mi lado', 2),
    ('D04', '4', '④ Plan de mejora escrito, con indicador y fecha de corte', 4),
    ('D04', '5', '⑤ Decisión documentada al vencer el plazo', 5),
    ('D10', '1', '① Entrevistar uno a uno a cada integrante', 2),
    ('D10', '2', '② Levantar el estado real de los indicadores del área', 1),
    ('D10', '3', '③ Revisar los procesos y documentos existentes', 3),
    ('D10', '4', '④ Comunicar la forma de trabajo que vas a instalar', 4),
    ('D10', '5', '⑤ Hacer el primer ajuste de estructura o de asignación', 5),
    ('D27', '1', '① El resultado o número objetivo de la semana', 2),
    ('D27', '2', '② Lo que se cerró la semana anterior', 1),
    ('D27', '3', '③ Las 3 prioridades con responsable', 3),
    ('D27', '4', '④ Los riesgos identificados y quién los cubre', 4),
    ('D27', '5', '⑤ La fecha y hora del punto de control', 5),
    ('D32', '1', '① Entregarle su perfil de puesto y sus indicadores por escrito', 1),
    ('D32', '2', '② Presentarlo con las áreas con las que va a trabajar', 2),
    ('D32', '3', '③ Asignarle un entregable pequeño y real', 3),
    ('D32', '4', '④ Revisar con él ese entregable y darle retroalimentación', 4),
    ('D32', '5', '⑤ Definir con él sus metas de los primeros 90 días', 5),
    ('D33', '1', '① Evidencia documentada del bajo rendimiento', 1),
    ('D33', '2', '② Plan de mejora con plazo e indicador, entregado por escrito', 2),
    ('D33', '3', '③ Seguimiento documentado durante el plazo', 3),
    ('D33', '4', '④ Coordinación con el área legal o de RRHH sobre causal y procedimiento', 4),
    ('D33', '5', '⑤ Comunicación al colaborador y al equipo', 5),
    ('C17', '1', '① Explicarle qué se espera de él y cómo se mide', 1),
    ('C17', '2', '② Mostrarle la tarea completa haciéndola tú', 2),
    ('C17', '3', '③ Que la haga él acompañado, corrigiendo en el momento', 3),
    ('C17', '4', '④ Que la haga solo mientras tú verificas el resultado', 4),
    ('C17', '5', '⑤ Soltarlo con revisión periódica', 5),
    ('C22', '1', '① Cómo cerró el día anterior', 1),
    ('C22', '2', '② La meta o el objetivo de hoy', 2),
    ('C22', '3', '③ La asignación por persona', 3),
    ('C22', '4', '④ Los riesgos o puntos críticos del día', 4),
    ('C22', '5', '⑤ La hora del punto de control', 5),
    ('O10', '1', '① Entender qué se espera exactamente y para cuándo', 1),
    ('O10', '2', '② Verificar que tengo lo necesario para hacerla', 2),
    ('O10', '3', '③ Hacerla', 3),
    ('O10', '4', '④ Revisarla antes de entregarla', 4),
    ('O10', '5', '⑤ Entregarla avisando que está lista', 5)
  ) AS v(codigo, letra, texto, orden)
 WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';

-- V · la tabla de tramos propia de cada ítem
INSERT INTO rango_pregunta (pregunta_id, orden, condicion, puntaje, genera_bandera)
SELECT p.id, v.orden, v.condicion, v.puntaje, v.bandera
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES
    ('D01', 1, 'Directos 5–20 y niveles ≥ 2', 3, false),
    ('D01', 2, 'Directos 3–4, o niveles = 1 con total ≥ 8', 2, false),
    ('D01', 3, 'Directos 1–2', 1, false),
    ('D01', 4, 'Sin personal a cargo', 0, false),
    ('D08', 1, '30–55%', 3, false),
    ('D08', 2, '20–29% o 56–70%', 2, false),
    ('D08', 3, '15–19% o >70%', 1, false),
    ('D08', 4, '<15%', 0, true),
    ('D29', 1, '≥ 60%', 3, false),
    ('D29', 2, '40–59%', 2, false),
    ('D29', 3, '20–39%', 1, false),
    ('D29', 4, '< 20%', 0, false),
    ('D35', 1, '5–15%', 3, false),
    ('D35', 2, '0–4% o 16–25%', 2, false),
    ('D35', 3, '26–35%', 1, false),
    ('D35', 4, '> 35%', 0, true),
    ('D40', 1, '≥ 500K y define estructura o reasigna partidas', 3, false),
    ('D40', 2, '< 500K y toma al menos 2 decisiones', 2, false),
    ('D40', 3, 'Solo ejecutaba lo aprobado', 1, false),
    ('D40', 4, 'No ha manejado', 0, false),
    ('D50', 1, '0o2', 2, false),
    ('D50', 2, '≥4', 0, true),
    ('D57', 1, '≥ 3 veces/semana y ≥ 2 años', 3, false),
    ('D57', 2, '≥ 3 veces/semana y < 2 años, o 1–2 veces y ≥ 2 años', 2, false),
    ('D57', 3, '1–2 veces/semana y < 2 años', 1, false),
    ('D57', 4, 'Esporádica o nunca', 0, false),
    ('D68', 1, 'Marca un dato concreto', 3, false),
    ('D68', 2, 'Marca "ninguno"', 2, true),
    ('D84', 1, '≥ 3 h/semana, ≥ 3 meses y con evidencia', 3, false),
    ('D84', 2, '≥ 2 h/semana y ≥ 2 meses', 2, false),
    ('D84', 3, 'Declara algo pero sin constancia ni evidencia', 1, false),
    ('D84', 4, 'Nada', 0, false),
    ('C01', 1, '≥ 8 personas y ≥ 2 frentes', 3, false),
    ('C01', 2, '4–7 personas', 2, false),
    ('C01', 3, '1–3 personas', 1, false),
    ('C01', 4, 'Ninguna', 0, false),
    ('O01', 1, '≥ 3 años y ≤ 3 empresas', 3, false),
    ('O01', 2, '≥ 3 años y > 3 empresas', 2, false),
    ('O01', 3, '1–2 años', 2, false),
    ('O01', 4, '< 1 año', 1, false),
    ('O01', 5, 'Sin experiencia en el rubro', 0, false),
    ('O15', 1, '0 tardanzas y 0 faltas', 3, false),
    ('O15', 2, '≤ 2 tardanzas, 0 faltas, avisó siempre', 2, false),
    ('O15', 3, '3–5 tardanzas o 1 falta avisada', 1, false),
    ('O15', 4, '> 5 tardanzas o faltas sin aviso', 0, false)
  ) AS v(codigo, orden, condicion, puntaje, bandera)
 WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';

-- CD · los campos de cada caso, en orden
INSERT INTO campo_caso (pregunta_id, orden, etiqueta)
SELECT p.id, v.orden, v.etiqueta
  FROM pregunta p
  JOIN version_banco vb ON vb.id = p.version_banco_id, (VALUES
    ('D05', 1, '¿Con qué te diste cuenta? (reclamo de cliente / reporte de un compañero /'),
    ('D05', 2, 'Herramienta o documento principal que usaste (plan de mejora escrito /'),
    ('D05', 3, 'Frecuencia de seguimiento aplicada (diario / 2 veces por semana / semanal /'),
    ('D05', 4, 'Indicador con que mediste la mejora (texto ≤ 40 car.)'),
    ('D05', 5, 'Valor antes ___ · Valor después ___ · Unidad (% / soles / unidades / horas / n°'),
    ('D05', 6, 'Semanas que tomó ___'),
    ('D05', 7, '¿Qué quedó instalado después? (nada, se resolvió y ya / el plan quedó como'),
    ('D11', 1, 'Declara los 3 indicadores con los que dirigías tu área. Por cada uno (6 campos × 3 = 18 campos): Nombre (texto ≤ 40 car.)'),
    ('D11', 2, 'Numerador (texto)'),
    ('D11', 3, 'Denominador (texto)'),
    ('D11', 4, 'Meta (número)'),
    ('D11', 5, 'Resultado real del último mes (número)'),
    ('D11', 6, 'Frecuencia de revisión (tiempo real / diaria / semanal / quincenal / mensual)  Un indicador sin denominador no es indicador: ese campo es inválido y arrastra al numerador.'),
    ('D14', 1, '¿Cuánto tardaste en enterarte? (mismo día / esa semana / al cierre del mes /'),
    ('D14', 2, '¿Cómo te enteraste? (por el dato / por un reporte del equipo / por un tercero /'),
    ('D14', 3, '¿Qué cambiaste en tu sistema de control? (nada / hablé con el responsable /'),
    ('D14', 4, '¿Volvió a ocurrir? (sí / no / no lo sé)'),
    ('D19', 1, 'Caso: una decisión que tomaste leyendo un dato antes de que el problema explotara. (5 campos) Dato o señal que lo anticipó (texto ≤ 40 car.)'),
    ('D19', 2, '¿Dónde lo viste? (lista)'),
    ('D19', 3, 'Días de anticipación ___'),
    ('D19', 4, 'Decisión tomada (lista de 8)'),
    ('D19', 5, 'Impacto evitado estimado (rango en soles o en días)'),
    ('D21', 1, 'Tu mapa de reuniones fijas. Hasta 5 reuniones, 5 campos cada una: Con quién (lista)'),
    ('D21', 2, 'Frecuencia (lista)'),
    ('D21', 3, 'Duración (lista)'),
    ('D21', 4, '¿Agenda enviada antes? (sí/no)'),
    ('D21', 5, '¿Qué documento salía? (nada / notas personales / acta con acuerdos y responsables / registro en sistema)  Regla especial: cero reuniones con agenda previa y acta → ítem puntúa 0 aunque declare 5 reuniones.'),
    ('D22', 1, 'Descripción (texto ≤ 60 car.)'),
    ('D22', 2, '¿Era previsible? (sí, y no lo previne / sí, y falló la prevención que había / no, era'),
    ('D22', 3, 'Origen (falla de una persona / falla del proceso / cliente o factor externo /'),
    ('D22', 4, '¿Con qué frecuencia ocurre? (primera vez / ha pasado antes este año / pasa'),
    ('D22', 5, '¿Qué dejaste hecho para que no se repita? (nada, se resolvió / avisé a quien'),
    ('D34', 1, '¿En qué fallaste al elegir? (me fié del CV / me fié de la entrevista / me presionó'),
    ('D34', 2, '¿Cuánto tardaste en darte cuenta? (primera semana / primer mes / segundo o'),
    ('D34', 3, 'Meses que duró en el puesto ___'),
    ('D34', 4, 'Costo estimado (rango en soles)'),
    ('D34', 5, '¿Qué cambiaste en tu forma de contratar? (nada / agregué prueba práctica /'),
    ('D36', 1, 'Tu resultado más importante de los últimos 12 meses. (7 campos) Indicador (texto ≤ 40 car.)'),
    ('D36', 2, 'Valor antes ___'),
    ('D36', 3, 'Valor después ___'),
    ('D36', 4, 'Unidad (lista)'),
    ('D36', 5, 'Meses que tomó ___'),
    ('D36', 6, 'Tu aporte específico (diseñé el plan / conseguí los recursos / cambié el proceso / cambié al equipo / ejecuté personalmente la parte crítica / coordiné a las áreas)'),
    ('D36', 7, 'Quién puede confirmarlo (nombre + cargo + contacto)  El último campo hace verificable todo el bloque. Sin él, el ítem puntúa la mitad.'),
    ('D41', 1, 'Un proceso que dejaste escrito y funcionando sin ti. (6 campos) Nombre del proceso (texto ≤ 40 car.)'),
    ('D41', 2, '¿Existe documento? (no / instructivo / procedimiento con responsables / procedimiento en sistema)'),
    ('D41', 3, '¿Cuántas personas lo ejecutan hoy? ___'),
    ('D41', 4, '¿Sigue operando? (sí / no / no lo sé)'),
    ('D41', 5, 'Horas tuyas que liberó por semana ___'),
    ('D41', 6, '¿Cuándo lo dejaste instalado? (mes/año)  Si "¿Existe documento?" = "no", los seis campos se invalidan: no está escrito, no cuenta.'),
    ('D42', 1, 'Una mejora que propusiste sin que nadie te la pidiera. (5 campos) Qué proponías (texto ≤ 60 car.)'),
    ('D42', 2, '¿Se implementó? (sí completa / sí parcial / no)'),
    ('D42', 3, 'Indicador que mejoró (texto ≤ 40 car.)'),
    ('D42', 4, 'Antes ___ Después ___'),
    ('D42', 5, 'Días entre ver el problema y proponerlo (mismo día / esa semana / ese mes / más de un mes)'),
    ('D54', 1, 'Tu día típico. (5 campos) Hora en que despiertas ___'),
    ('D54', 2, 'Hora en que empiezas a trabajar ___'),
    ('D54', 3, 'Hora en que cierras ___'),
    ('D54', 4, 'Hora en que duermes ___'),
    ('D54', 5, '¿Cuántas veces revisas trabajo después de cerrar? (ninguna / 1 / 2–3 / todo el tiempo)  Validación: si "hora en que cierras" y "hora en que duermes" son la misma → campo inválido + bandera de sostenibilidad.'),
    ('D72', 1, 'Lo último que hiciste por un cliente o colaborador sin que te lo pidieran y sin beneficio para ti. (4 campos) Qué hiciste (texto ≤ 60 car.)'),
    ('D72', 2, 'Cuándo (esta semana / este mes / este año / no recuerdo)'),
    ('D72', 3, 'Tiempo que te tomó (< 1 h / medio día / un día / más)'),
    ('D72', 4, '¿Alguien se enteró? (sí / no)  "No recuerdo" en el campo 2 invalida el ítem completo.'),
    ('D74', 1, 'El estándar de calidad más alto que has exigido. (4 campos) Qué exigías (texto ≤ 60 car.)'),
    ('D74', 2, '¿Cómo se medía? (no se medía, era criterio / checklist de verificación / indicador numérico con meta / auditoría periódica / certificación externa)'),
    ('D74', 3, '¿Qué pasaba si no se cumplía? (nada / se corregía / no se entregaba hasta cumplir / había consecuencia formal)'),
    ('D74', 4, '% de cumplimiento alcanzado ___  "No se medía, era criterio" invalida ese campo: un estándar sin medición no es estándar. C-Iniciativa'),
    ('D75', 1, 'Tres cosas que empezaste sin que nadie te lo pidiera. (3 campos × 3) Qué era (texto ≤ 40 car.)'),
    ('D75', 2, '¿Llegó a implementarse? (sí / parcial / no)'),
    ('D75', 3, '¿Sigue viva hoy? (sí / no / no lo sé)  Regla especial: el puntaje cuenta solo las iniciativas con "implementada = sí o parcial". Tres ideas y cero implementadas = 0. Ideas vivas hoy = campo válido doble.'),
    ('C03', 1, '¿Existía un formato de control diario? (no / uno que armé yo / uno de la'),
    ('C03', 2, '¿Qué registrabas en él? (multi: avance / incidencias / asistencia / calidad /'),
    ('C03', 3, '¿A quién se lo entregabas? (a nadie / a mi jefe / al sistema / a la siguiente'),
    ('C03', 4, '¿Con qué frecuencia lo llenabas? (varias veces al día / al cierre del día / cuando'),
    ('C03', 5, '¿Cuánto tiempo te tomaba? (< 10 min / 10–30 min / > 30 min)'),
    ('C03', 6, '¿Podía otra persona leerlo y entender el estado del área? (sí / más o menos /'),
    ('C04', 1, 'Los 3 indicadores que reportabas. Por cada uno (4 campos × 3): Nombre (texto ≤ 40 car.)'),
    ('C04', 2, 'Cómo se calculaba (texto ≤ 40 car.)'),
    ('C04', 3, 'Frecuencia (diaria / semanal / mensual)'),
    ('C04', 4, 'A quién lo reportabas (lista)'),
    ('C05', 1, 'Caso: un desvío que detectaste a tiempo. (5 campos) Qué se estaba desviando (texto ≤ 60 car.)'),
    ('C05', 2, '¿Cómo lo detectaste? (revisión programada / un dato del reporte / recorrido en terreno / me lo avisó alguien del equipo / por casualidad)'),
    ('C05', 3, 'Días de anticipación ___'),
    ('C05', 4, 'Qué hiciste (lista de 6)'),
    ('C05', 5, '¿Se evitó el impacto? (sí completamente / en parte / no)'),
    ('C06', 1, 'Caso: un desvío que se te pasó. (4 campos) Qué pasó (texto ≤ 60 car.)'),
    ('C06', 2, '¿Cuándo te enteraste? (el mismo día / esa semana / al cierre / cuando ya reclamaron)'),
    ('C06', 3, '¿Qué cambiaste en tu forma de controlar? (nada / hablé con el responsable / agregué un punto de revisión / cambié la frecuencia / cambié el formato de control / cambié a la persona)'),
    ('C06', 4, '¿Volvió a pasar? (sí / no / no lo sé)  Campo 3 = "nada", "hablé con el responsable" o "cambié a la persona" → ítem puntúa 0.'),
    ('C13', 1, 'Caso: levantaste el rendimiento de una persona concreta. (5 campos) Qué estaba fallando (texto ≤ 60 car.)'),
    ('C13', 2, 'Qué hiciste (lista de 7)'),
    ('C13', 3, 'Frecuencia de seguimiento (lista)'),
    ('C13', 4, 'Semanas que tomó ___'),
    ('C13', 5, '¿Se sostuvo después? (sí / volvió a caer / se fue de la empresa)'),
    ('C18', 1, 'Tus reuniones o puntos de coordinación fijos. Hasta 4, con 4 campos: Con quién (mi equipo / mi jefe / otras áreas / cliente)'),
    ('C18', 2, 'Frecuencia (diaria / semanal / quincenal / mensual)'),
    ('C18', 3, '¿Agenda previa? (sí/no)'),
    ('C18', 4, '¿Qué quedaba registrado? (nada / mis notas / acta con acuerdos / registro en sistema)'),
    ('C19', 1, 'Los 3 últimos problemas que resolviste en tus últimas 48 horas de trabajo. (5 campos × 3) Descripción (texto ≤ 60 car.)'),
    ('C19', 2, '¿Era previsible? (sí, no lo previne / sí, falló la prevención / no, imprevisible)'),
    ('C19', 3, 'Origen (falla de persona / falla de proceso / externo / otra área / falta de información a tiempo)'),
    ('C19', 4, 'Frecuencia con que ocurre (primera vez / antes este año / todos los meses / todas las semanas)'),
    ('C19', 5, 'Qué dejaste hecho (nada / avisé a quien corresponde / cambié el procedimiento / agregué un punto de control / lo escalé)     Mismo detector de apagaincendios que D22. Aplicar idéntica fórmula de    índice.'),
    ('C25', 1, 'Tu mejor resultado del último año. (6 campos) Indicador (texto ≤ 40 car.)'),
    ('C25', 2, 'Antes ___'),
    ('C25', 3, 'Después ___'),
    ('C25', 4, 'Unidad (lista)'),
    ('C25', 5, 'Meses que tomó ___'),
    ('C25', 6, 'Quién puede confirmarlo (nombre + cargo + contacto)'),
    ('C26', 1, 'La vez que te faltó gente, tiempo o recursos y aun así cumpliste. (4 campos) Qué faltó (personal / tiempo / materiales / equipos / información)'),
    ('C26', 2, 'Qué hiciste (lista de 7)'),
    ('C26', 3, '¿Cumpliste al 100%? (sí / en parte / se cumplió con menor calidad)'),
    ('C26', 4, '¿Qué te costó? (horas extra mías / horas extra del equipo / calidad / nada, se reorganizó)'),
    ('C27', 1, 'Una mejora que aplicaste a un proceso de tu área. (4 campos) Qué mejoraste (texto ≤ 60 car.)'),
    ('C27', 2, '¿Te lo pidieron o fue tuyo? (me lo pidieron / fue idea mía)'),
    ('C27', 3, 'Qué mejoró (indicador + antes + después)'),
    ('C27', 4, '¿Sigue aplicándose? (sí / no / no lo sé)'),
    ('C34', 1, 'Tu día típico. (5 campos) Hora en que despiertas ___'),
    ('C34', 2, 'Hora de entrada ___'),
    ('C34', 3, 'Hora de salida real ___'),
    ('C34', 4, 'Hora en que duermes ___'),
    ('C34', 5, '¿Revisas trabajo después de salir? (nunca / a veces / todos los días)'),
    ('C46', 1, 'Lo último que hiciste por un compañero o cliente sin que te lo pidieran. (4 campos) Qué hiciste (texto ≤ 60 car.)'),
    ('C46', 2, 'Cuándo (esta semana / este mes / este año / no recuerdo)'),
    ('C46', 3, 'Tiempo que te tomó (lista)'),
    ('C46', 4, '¿Alguien se enteró? (sí / no)'),
    ('C48', 1, 'El estándar de calidad más alto que has exigido. (4 campos) Qué exigías (texto ≤ 60 car.)'),
    ('C48', 2, 'Cómo lo verificabas (a criterio / checklist / medición con número / revisión de un tercero)'),
    ('C48', 3, 'Qué pasaba si no se cumplía (nada / se corregía / no se entregaba hasta cumplir / consecuencia formal)'),
    ('C48', 4, '% de cumplimiento alcanzado ___'),
    ('C49', 1, 'Dos cosas que empezaste sin que nadie te lo pidiera. (3 campos × 2) Qué era (texto ≤ 40 car.)'),
    ('C49', 2, '¿Se implementó? (sí / parcial / no)'),
    ('C49', 3, '¿Sigue viva? (sí / no / no lo sé) Solo cuentan las implementadas.'),
    ('O03', 1, 'Nombre de la tarea (texto ≤ 40 car.)'),
    ('O03', 2, 'Cuánto te toma normalmente (< 30 min / 30 min–2 h / medio día / un día o'),
    ('O03', 3, 'Qué revisas ANTES de empezar (multi: instrucción o especificación /'),
    ('O03', 4, 'Cómo sabes que quedó bien (la entrego y ya / la reviso contra un estándar o'),
    ('O03', 5, 'Qué puede salir mal (texto ≤ 60 car.)'),
    ('O03', 6, 'Qué haces si sale mal (lo corrijo y aviso / lo corrijo callado / aviso y espero'),
    ('O05', 1, 'El trabajo más difícil que te tocó. (5 campos) Qué era (texto ≤ 60 car.)'),
    ('O05', 2, 'Por qué era difícil (plazo muy corto / no lo había hecho antes / faltaban recursos / era muy exigente en calidad / había presión del cliente)'),
    ('O05', 3, 'Qué hiciste (lista de 6)'),
    ('O05', 4, '¿Saliste adelante? (sí completo / en parte / no)'),
    ('O05', 5, '¿Alguien te ayudó? (nadie / un compañero / mi jefe / el equipo completo)'),
    ('O16', 1, 'Una vez que te comprometiste a algo y no pudiste cumplir. (5 campos) Qué era (texto ≤ 60 car.)'),
    ('O16', 2, 'Por qué no pudiste (no alcancé el tiempo / faltaron recursos / apareció algo más urgente / no sabía hacerlo / me equivoqué al calcular)'),
    ('O16', 3, '¿Avisaste antes del plazo? (sí, apenas lo supe / sí, casi al final / no, avisé después)'),
    ('O16', 4, 'Qué hiciste para resolverlo (lista de 5)'),
    ('O16', 5, '¿Volvió a pasar? (sí / no)'),
    ('O21', 1, 'Un problema que tuviste con un compañero. (4 campos) Qué pasó (texto ≤ 60 car.)'),
    ('O21', 2, '¿Quién dio el primer paso para resolverlo? (yo / él / el jefe / nadie, se pasó solo)'),
    ('O21', 3, 'Cómo se resolvió (hablando directamente / con el jefe de por medio / nos separaron / no se resolvió)'),
    ('O21', 4, '¿Cómo quedó la relación? (bien / normal, de trabajo / mal)'),
    ('O23', 1, '¿Has enseñado tu trabajo a alguien? (4 campos) ¿A cuántas personas? ___'),
    ('O23', 2, 'Cómo lo hiciste (le mostré y lo dejé practicar acompañado / le expliqué y lo dejé intentar / le pasé el procedimiento escrito / le dije que mirara cómo lo hago)'),
    ('O23', 3, '¿Cuánto tiempo le dedicaste? (lista)'),
    ('O23', 4, '¿Esa persona hoy lo hace bien? (sí / más o menos / no / ya no está)'),
    ('O30', 1, 'Tu día típico. (5 campos) Hora en que despiertas ___'),
    ('O30', 2, 'Hora de entrada ___'),
    ('O30', 3, 'Hora de salida real ___'),
    ('O30', 4, 'Hora en que duermes ___'),
    ('O30', 5, 'Tiempo de traslado al trabajo (< 30 min / 30–60 min / > 1 h)'),
    ('O41', 1, 'La última vez que ayudaste a alguien sin que te lo pidieran. (4 campos) Qué hiciste (texto ≤ 60 car.)'),
    ('O41', 2, 'Cuándo (esta semana / este mes / este año / no recuerdo)'),
    ('O41', 3, 'A quién (un compañero / un cliente / otra área / mi jefe)'),
    ('O41', 4, '¿Alguien se enteró? (sí / no)'),
    ('O43', 1, 'Algo de lo que te sientes orgulloso en un trabajo. (4 campos) Qué fue (texto ≤ 60 car.)'),
    ('O43', 2, '¿Por qué te sientes orgulloso? (quedó impecable / salvé una situación complicada / me lo reconocieron / aprendí algo nuevo / ayudé a alguien)'),
    ('O43', 3, '¿Alguien más lo notó? (sí / no)'),
    ('O43', 4, '¿Cuándo fue? (este año / hace 1–2 años / hace más)'),
    ('O44', 1, 'Algo que mejoraste por iniciativa propia. (4 campos) Qué mejoraste (texto ≤ 60 car.)'),
    ('O44', 2, '¿Te lo pidieron? (no, fue idea mía / me lo pidieron)'),
    ('O44', 3, 'Qué cambió (se hace más rápido / salen menos errores / es más seguro / es más ordenado)'),
    ('O44', 4, '¿Se sigue haciendo así? (sí / no / no lo sé)')
  ) AS v(codigo, orden, etiqueta)
 WHERE p.codigo = v.codigo AND vb.etiqueta LIKE 'Banco RENASER v3%';

-- PC · cada par con su condición. Penalizan un 5% del global y levantan
-- bandera roja; se muestran al menos 15 ítems después de su pareja.
INSERT INTO par_consistencia (version_banco_id, pregunta_a_id, pregunta_b_id,
                              penalizacion_porcentaje, separacion_minima_items,
                              condicion)
SELECT pa.version_banco_id, pa.id, pb.id, 5.00, 15, v.condicion
  FROM (VALUES
    ('D66', 'D05', 'opción (d) + frecuencia de seguimiento "diario" o "2 veces por
semana" declarada en D05 → −5% global + bandera.'),
    ('D67', 'D22', 'número declarado aquí distinto al que arroja el campo 4 de D22 →
−5% global + bandera.'),
    ('D67', 'D29', 'número declarado aquí distinto al que arroja el campo 4 de D22 →
−5% global + bandera.'),
    ('C43', 'C19', 'con el campo 4 de C19 → −5% global + bandera.'),
    ('O29', 'O16', '¿Alguna vez no cumpliste un compromiso de trabajo? (nunca / una vez / algunas
veces)
Responder "nunca" habiendo descrito un caso en O16 → −5% global + bandera.')
  ) AS v(cod_a, cod_b, condicion)
  JOIN pregunta pa ON pa.codigo = v.cod_a
  JOIN pregunta pb ON pb.codigo = v.cod_b
  JOIN version_banco vb ON vb.id = pa.version_banco_id
                       AND vb.etiqueta LIKE 'Banco RENASER v3%'
 WHERE pb.version_banco_id = pa.version_banco_id;
-- ============================================================================
-- 12 · Los umbrales de nivel (sección 0.3 del documento)
-- ============================================================================
-- Estos no los genera el script: son cuatro filas fijas, iguales para los tres bancos, y se
-- leen mejor escritas que generadas.
INSERT INTO umbral_nivel (version_banco_id, porcentaje_min, resultado, nivel)
SELECT vb.id, u.pct, u.resultado, u.nivel
  FROM version_banco vb, (VALUES
    (90.00, 'Avanza directo, candidato a certificación', 'Nivel III · Alto Rendimiento'),
    (75.00, 'Avanza a prueba por puesto',                'Nivel II · Rendimiento Sólido'),
    (60.00, 'Avanza con observaciones para entrevista',  'Nivel I · Potencial'),
    (0.00,  'No avanza',                                 NULL)
  ) AS u(pct, resultado, nivel)
 WHERE vb.etiqueta LIKE 'Banco RENASER v3%';

-- ============================================================================
-- 13 · Los cinco filtros eliminatorios (sección 0.4)
-- ============================================================================
-- Descartan al candidato aunque el puntaje global sea alto. Los tres primeros miran un ítem
-- concreto, y ese ítem es distinto en cada banco; los dos últimos miran el formulario entero.
INSERT INTO filtro_eliminatorio (version_banco_id, codigo, descripcion, preguntas)
SELECT vb.id, f.codigo, f.descripcion, f.preguntas
  FROM version_banco vb, (VALUES
    ('AUTORIZACION_VERIFICACION',
     'Responder "No" a la autorización de verificación', 'D70,C44,O39'),
    ('CONTACTOS_REFERENCIA',
     'Negarse a dar contactos de referencia sin justificación registrada', 'D52,C33,O19'),
    ('INTEGRIDAD',
     'Calificar con 4 o 5 la opción deshonesta en el ítem de integridad', 'D65,C42,O38'),
    ('SENTIDO_CRITICO',
     'Puntaje inferior al 50% en el subbloque de sentido crítico', ''),
    ('INFLACION',
     'Dos o más banderas de inflación (ítems INV con 2 o más falsos marcados)', '')
  ) AS f(codigo, descripcion, preguntas)
 WHERE vb.etiqueta LIKE 'Banco RENASER v3%';

-- ============================================================================
-- 14 · Los multiplicadores por familia (sección 0.5)
-- ============================================================================
-- Solo el banco Directivo: el mismo banco sirve para cualquier dirección y lo que cambia es
-- cuánto pesa cada bloque A según la familia del puesto.
--
-- familia_codigo queda vacío a propósito. El documento nombra cuatro familias que NO son las
-- del catálogo `familia` de este sistema, y nadie ha dicho cuál corresponde a cuál. Se guarda
-- la etiqueta del documento tal cual; el día que Renaser confirme el mapeo se rellena la
-- columna con un UPDATE. Adivinarlo aquí cambiaría notas sin que nadie lo hubiera decidido.
INSERT INTO multiplicador_bloque (version_banco_id, familia_documento, bloque, multiplicador)
SELECT vb.id, m.familia, m.bloque, m.mult
  FROM version_banco vb, (VALUES
    ('Obras / Proyectos',        'A1', 1.0), ('Obras / Proyectos',        'A2', 1.5),
    ('Obras / Proyectos',        'A3', 1.0), ('Obras / Proyectos',        'A4', 0.5),
    ('Obras / Proyectos',        'A5', 1.2),
    ('Recursos Humanos',         'A1', 1.5), ('Recursos Humanos',         'A2', 1.0),
    ('Recursos Humanos',         'A3', 1.2), ('Recursos Humanos',         'A4', 1.5),
    ('Recursos Humanos',         'A5', 1.0),
    ('Marketing / Comercial',    'A1', 0.8), ('Marketing / Comercial',    'A2', 1.0),
    ('Marketing / Comercial',    'A3', 1.3), ('Marketing / Comercial',    'A4', 0.5),
    ('Marketing / Comercial',    'A5', 1.5),
    ('Administración / Finanzas','A1', 1.2), ('Administración / Finanzas','A2', 1.3),
    ('Administración / Finanzas','A3', 1.0), ('Administración / Finanzas','A4', 1.0),
    ('Administración / Finanzas','A5', 1.2)
  ) AS m(familia, bloque, mult)
 WHERE vb.etiqueta = 'Banco RENASER v3 · Directivo';

-- Los bloques B (alto rendimiento) y C (excelencia) van siempre con 1.0: son el sello RENASER
-- y el documento dice que no se negocian por área. No se insertan filas para ellos —la
-- ausencia significa 1.0— para que nadie los cambie creyendo que es configuración.
