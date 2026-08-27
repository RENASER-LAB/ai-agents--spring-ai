-- El banco CAZATALENTOS: preguntas abiertas calificadas por criterios (C1..C4).
-- Ver docs/CAZATALENTOS-BANCO-RENASER.md y docs/insumos/CAZATALENTOS-*.xlsx.
--
-- La clienta trae un instrumento nuevo: 18/15/12 preguntas abiertas por nivel que se
-- puntúan de 0 a 4 contando cuatro criterios presentes o ausentes —episodio, autoría,
-- dato duro, la parte incómoda—, con una «señal de 0» por pregunta que corta el cálculo
-- antes de contar. El banco v3 no se toca: los bancos nuevos se añaden al lado y las
-- evaluaciones ya hechas conservan su versión (RF-138: se archiva, no se borra).
--
-- Todo lo que la clienta va a querer ajustar durante la calibración —señales, textos
-- esperados, pesos— entra aquí como DATOS, no como código: recalibrar tiene que ser un
-- UPDATE más una recalificación, nunca un despliegue.

-- ============================================================================
-- 1 · El tipo ABIERTA
-- ============================================================================
-- La misma lista vive en dos CHECK. V20 ya avisó de la trampa: si se amplía una y no la
-- otra, una plantilla no puede pedir preguntas del formato nuevo.
ALTER TABLE pregunta DROP CONSTRAINT pregunta_tipo_check;
ALTER TABLE pregunta ADD CONSTRAINT pregunta_tipo_check CHECK (tipo IN (
    -- Banco v0.1, en uso por las evaluaciones ya hechas
    'ESTILO', 'SITUACION', 'CONDUCTUAL', 'MICROCASO', 'DILEMA', 'CONSISTENCIA',
    -- Banco v3
    'EF-4', 'SJT-R', 'SEC', 'INV', 'DE', 'CD', 'V', 'PC',
    -- Banco CAZATALENTOS: respuesta de texto libre, calificada por criterios
    'ABIERTA'
));

ALTER TABLE cuota_plantilla_evaluacion DROP CONSTRAINT cuota_plantilla_evaluacion_tipo_pregunta_check;
ALTER TABLE cuota_plantilla_evaluacion ADD CONSTRAINT cuota_plantilla_evaluacion_tipo_pregunta_check
    CHECK (tipo_pregunta IS NULL OR tipo_pregunta IN (
        'ESTILO', 'SITUACION', 'CONDUCTUAL', 'MICROCASO', 'DILEMA', 'CONSISTENCIA',
        'EF-4', 'SJT-R', 'SEC', 'INV', 'DE', 'CD', 'V', 'PC',
        'ABIERTA'));

-- ============================================================================
-- 2 · Lo que cada pregunta declara para su evaluador
-- ============================================================================
-- «Cada pregunta declara qué cuenta como C3 y qué cuenta como C4 en ella. El evaluador
-- no decide qué es un dato duro: está escrito.» Son la guía del agente que califica, y
-- por eso se importan del Excel tal cual: cambiarlas es recalibrar, no reprogramar.
--
-- Quedan NULL en los bancos v0.1 y v3, que no se califican así.
ALTER TABLE pregunta ADD COLUMN c3_esperado text;
ALTER TABLE pregunta ADD COLUMN c4_esperado text;
-- Si la respuesta la cumple, el puntaje es 0 y se acaba el cálculo. En la eliminatoria
-- (R18, P12) cumplirla es además descarte automático.
ALTER TABLE pregunta ADD COLUMN senal_de_cero text;

-- ============================================================================
-- 3 · El discriminador del motor
-- ============================================================================
-- Dos motores conviven: el de claves versionadas (v3, puntúa el código contra tablas) y
-- el de criterios (CAZATALENTOS, puntúa contando C1..C4). Cuál rige lo dice la versión
-- del banco, no se adivina mirando los tipos de sus preguntas.
ALTER TABLE version_banco ADD COLUMN metodo_calificacion text
    CHECK (metodo_calificacion IS NULL OR metodo_calificacion IN ('CRITERIOS'));
COMMENT ON COLUMN version_banco.metodo_calificacion IS
    'NULL = motor de claves versionadas (v0.1 y v3) · CRITERIOS = conteo C1..C4 (CAZATALENTOS)';

-- ============================================================================
-- 4 · La nota guarda los criterios, no solo el número
-- ============================================================================
-- El agente devuelve qué criterios vio, y el puntaje lo calcula el código contándolos:
-- así la aritmética no depende del modelo, y las banderas del cuestionario completo
-- (SIN_INCOMODIDAD = ningún C4, SOLO_NOSOTROS = mitad sin C2) se vuelven consultas
-- sobre estas columnas en vez de otra pasada de IA.
--
-- NULL en todo lo histórico: las notas del v3 no tienen criterios y no se inventan.
ALTER TABLE nota_respuesta ADD COLUMN c1_episodio boolean;
ALTER TABLE nota_respuesta ADD COLUMN c2_autoria boolean;
ALTER TABLE nota_respuesta ADD COLUMN c3_dato boolean;
ALTER TABLE nota_respuesta ADD COLUMN c4_incomodidad boolean;
ALTER TABLE nota_respuesta ADD COLUMN cumple_senal_cero boolean;

-- ============================================================================
-- 5 · Los siete pilares, como dimensiones
-- ============================================================================
-- Los pilares de la clienta no son las 22 dimensiones del catálogo: son su propia
-- taxonomía y se agregan con sus propios pesos. Van como dimensiones nuevas para que
-- pregunta_dimension y peso_dimension sirvan tal cual, sin una tabla más (que además
-- habría que registrar en el copiador de instrumentos de multiempresa).
INSERT INTO dimension (codigo, nombre, definicion, es_obligatoria, orden) VALUES
    ('PIL_INICIATIVA',      'Iniciativa (pilar)',                  'Pilar CAZATALENTOS: además de proponer, empuja hasta el final',      false, 23),
    ('PIL_RESOLUCION',      'Resolución de problemas (pilar)',     'Pilar CAZATALENTOS: ataca la causa, no el síntoma',                  false, 24),
    ('PIL_EXCELENCIA',      'Excelencia (pilar)',                  'Pilar CAZATALENTOS: qué tan alto pone la vara y cómo la verifica',   false, 25),
    ('PIL_SERVICIO',        'Servicio (pilar)',                    'Pilar CAZATALENTOS: genera valor más allá de su función',            false, 26),
    ('PIL_RESPONSABILIDAD', 'Responsabilidad y resultados (pilar)','Pilar CAZATALENTOS: convierte trabajo en resultado medible',         false, 27),
    ('PIL_DIRECCION',       'Dirección de personas (pilar)',       'Pilar CAZATALENTOS: dirige distinto a quien rinde y a quien no',     false, 28),
    ('PIL_INTEGRIDAD',      'Integridad (pilar)',                  'Pilar CAZATALENTOS: eliminatorio, no pondera en el índice',          false, 29);

-- ============================================================================
-- 6 · Los pesos de pilar, en dos versiones: MICRO y MEDIA/GRANDE
-- ============================================================================
-- Solo DIR cambia según el tamaño de la empresa (hoja «Cálculo» corregida contra el .md,
-- parte 8.2): en la pequeña pesan más iniciativa y resolución, porque hay que armar lo
-- que no existe; en la grande pesa más dirección de personas, porque hay que mover una
-- estructura. SUP y OPE son idénticos en ambas versiones — duplicarlos es a propósito:
-- la vacante apunta a UNA versión de pesos y esa versión tiene que saber contestar por
-- cualquier nivel.
--
-- Integridad no lleva peso: es eliminatoria, no pondera.
--
-- Nacen en BORRADOR: se publican cuando el banco se publique, no antes. El tamaño de la
-- empresa sale de la ficha de vacante (Q5: MICRO ≤ 30 · MEDIA 31–200 · GRANDE 200+).
INSERT INTO version_pesos (organizacion_id, etiqueta, estado)
SELECT id, 'CAZATALENTOS · MICRO', 'BORRADOR' FROM organizacion WHERE codigo = 'RENASER';
INSERT INTO version_pesos (organizacion_id, etiqueta, estado)
SELECT id, 'CAZATALENTOS · MEDIA/GRANDE', 'BORRADOR' FROM organizacion WHERE codigo = 'RENASER';

INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT vp.id, v.nivel, v.dimension, v.peso::numeric
  FROM version_pesos vp, (VALUES
    -- DIR · empresa MICRO
    ('DIRECCION',   'PIL_INICIATIVA',      20),
    ('DIRECCION',   'PIL_RESOLUCION',      25),
    ('DIRECCION',   'PIL_EXCELENCIA',      15),
    ('DIRECCION',   'PIL_SERVICIO',        10),
    ('DIRECCION',   'PIL_RESPONSABILIDAD', 20),
    ('DIRECCION',   'PIL_DIRECCION',       10),
    -- SUP y OPE no varían por tamaño
    ('SUPERVISION', 'PIL_INICIATIVA',      15),
    ('SUPERVISION', 'PIL_RESOLUCION',      22),
    ('SUPERVISION', 'PIL_EXCELENCIA',      15),
    ('SUPERVISION', 'PIL_SERVICIO',        10),
    ('SUPERVISION', 'PIL_RESPONSABILIDAD', 18),
    ('SUPERVISION', 'PIL_DIRECCION',       20),
    ('EJECUCION',   'PIL_INICIATIVA',      15),
    ('EJECUCION',   'PIL_RESOLUCION',      20),
    ('EJECUCION',   'PIL_EXCELENCIA',      30),
    ('EJECUCION',   'PIL_SERVICIO',        20),
    ('EJECUCION',   'PIL_RESPONSABILIDAD', 15)
  ) AS v(nivel, dimension, peso)
 WHERE vp.etiqueta = 'CAZATALENTOS · MICRO';

INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT vp.id, v.nivel, v.dimension, v.peso::numeric
  FROM version_pesos vp, (VALUES
    -- DIR · empresa MEDIA o GRANDE
    ('DIRECCION',   'PIL_INICIATIVA',      15),
    ('DIRECCION',   'PIL_RESOLUCION',      22),
    ('DIRECCION',   'PIL_EXCELENCIA',      15),
    ('DIRECCION',   'PIL_SERVICIO',        10),
    ('DIRECCION',   'PIL_RESPONSABILIDAD', 18),
    ('DIRECCION',   'PIL_DIRECCION',       20),
    -- SUP y OPE, idénticos a la versión MICRO
    ('SUPERVISION', 'PIL_INICIATIVA',      15),
    ('SUPERVISION', 'PIL_RESOLUCION',      22),
    ('SUPERVISION', 'PIL_EXCELENCIA',      15),
    ('SUPERVISION', 'PIL_SERVICIO',        10),
    ('SUPERVISION', 'PIL_RESPONSABILIDAD', 18),
    ('SUPERVISION', 'PIL_DIRECCION',       20),
    ('EJECUCION',   'PIL_INICIATIVA',      15),
    ('EJECUCION',   'PIL_RESOLUCION',      20),
    ('EJECUCION',   'PIL_EXCELENCIA',      30),
    ('EJECUCION',   'PIL_SERVICIO',        20),
    ('EJECUCION',   'PIL_RESPONSABILIDAD', 15)
  ) AS v(nivel, dimension, peso)
 WHERE vp.etiqueta = 'CAZATALENTOS · MEDIA/GRANDE';
