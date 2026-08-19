-- Hito 2 · Fase C. El contenido que faltaba para que exista una evaluación de verdad.
--
-- Hasta aquí el hito 2 tenía las tablas y la administración, pero ninguna pregunta, ningún
-- peso por dimensión y ninguna plantilla. Es decir: se podía administrar un banco vacío.
--
-- Las 200 preguntas salen del Banco Maestro del cliente
-- (docs/insumos/Banco_Maestro_Preguntas_RENASER_*.docx), convertidas por
-- scripts/importar-banco-maestro.py. No se transcribieron a mano: son 200 preguntas con
-- sus claves de puntuación y copiarlas a mano garantiza erratas.

-- ============================================================================
-- 1 · Una versión de pesos nueva
-- ============================================================================
-- La v1 ya está PUBLICADA, y una versión publicada es inmutable (RF-138): es la regla que
-- permite reproducir una decisión vieja tal como se tomó. Así que esto NO la toca: crea una
-- v2. Las postulaciones que ya existan siguen atadas a la v1, que es justo lo que debe pasar.
INSERT INTO version_pesos (organizacion_id, etiqueta, estado, publicada_en)
SELECT id, 'v2 hito 2', 'PUBLICADA', now() FROM organizacion WHERE codigo = 'RENASER';

-- El reparto entre etapas no cambia respecto de la v1
INSERT INTO peso_etapa (version_pesos_id, etapa_codigo, peso)
SELECT vp.id, e.codigo, e.peso::numeric
FROM version_pesos vp,
     (VALUES ('PERFIL_INTEGRAL', 40), ('PRUEBA_PUESTO', 30), ('SIMULACION', 15), ('VALIDACION', 15))
         AS e(codigo, peso)
WHERE vp.etiqueta = 'v2 hito 2';

-- El módulo psicométrico no existe todavía. Si se dejara su 5% donde está, TODO el mundo
-- tendría 35 de 40 como techo y nadie se daría cuenta hasta ver que "las notas salen bajas".
-- La regla dice que mientras no exista, su peso se reparte entre los otros dos (RF-37).
INSERT INTO peso_componente_perfil (version_pesos_id, componente, peso)
SELECT vp.id, c.componente, c.peso::numeric
FROM version_pesos vp,
     (VALUES ('CV', 12), ('PSICOMETRICO', 0), ('EVALUACION', 28)) AS c(componente, peso)
WHERE vp.etiqueta = 'v2 hito 2';

-- ============================================================================
-- 2 · Los ocho criterios del currículum
-- ============================================================================
-- Son globales (version_plantilla_prueba_id vacío): valen para cualquier vacante, a
-- diferencia de los criterios de una prueba del puesto, que son de su plantilla.
-- metodo_verificacion = AGENTE porque los puntúa la IA leyendo el CV, no una regla ni
-- una persona.
INSERT INTO criterio (codigo, nombre, descripcion, etapa_codigo, metodo_verificacion, orden) VALUES
    ('CV_RESULTADOS',  'Resultados demostrables',      'Logros con evidencia verificable, no funciones descritas', 'PERFIL_INTEGRAL', 'AGENTE', 1),
    ('CV_COMPLEJIDAD', 'Complejidad y alcance',        'Tamaño del problema que ha manejado',                      'PERFIL_INTEGRAL', 'AGENTE', 2),
    ('CV_SISTEMAS',    'Sistemas o procesos creados',  'Dejó algo que sigue funcionando sin él',                   'PERFIL_INTEGRAL', 'AGENTE', 3),
    ('CV_PERSONAS',    'Desarrollo de personas',       'Formó o hizo crecer a otros',                              'PERFIL_INTEGRAL', 'AGENTE', 4),
    ('CV_APRENDIZAJE', 'Velocidad de aprendizaje',     'Incorpora lo nuevo y mejora rápido',                       'PERFIL_INTEGRAL', 'AGENTE', 5),
    ('CV_INICIATIVA',  'Iniciativa o creación',        'Puso en marcha algo por decisión propia',                  'PERFIL_INTEGRAL', 'AGENTE', 6),
    ('CV_HABILIDADES', 'Habilidades del puesto',       'Lo que el puesto concreto exige saber hacer',              'PERFIL_INTEGRAL', 'AGENTE', 7),
    ('CV_EVIDENCIA',   'Calidad de la evidencia',      'Lo que dice se puede comprobar',                           'PERFIL_INTEGRAL', 'AGENTE', 8);

-- El peso de cada criterio cambia según el nivel, con la tabla de RF-43 tal cual.
-- Se lee por columnas: Dirección, Supervisión, Ejecución.
INSERT INTO peso_criterio (version_pesos_id, nivel_puesto_codigo, criterio_id, peso)
SELECT vp.id, x.nivel, c.id, x.peso::numeric
FROM version_pesos vp
JOIN (VALUES
        ('CV_RESULTADOS',  'DIRECCION', 25), ('CV_RESULTADOS',  'SUPERVISION', 20), ('CV_RESULTADOS',  'EJECUCION', 20),
        ('CV_COMPLEJIDAD', 'DIRECCION', 15), ('CV_COMPLEJIDAD', 'SUPERVISION', 10), ('CV_COMPLEJIDAD', 'EJECUCION',  5),
        ('CV_SISTEMAS',    'DIRECCION', 15), ('CV_SISTEMAS',    'SUPERVISION', 15), ('CV_SISTEMAS',    'EJECUCION', 10),
        ('CV_PERSONAS',    'DIRECCION', 15), ('CV_PERSONAS',    'SUPERVISION', 15), ('CV_PERSONAS',    'EJECUCION',  0),
        ('CV_APRENDIZAJE', 'DIRECCION', 10), ('CV_APRENDIZAJE', 'SUPERVISION', 10), ('CV_APRENDIZAJE', 'EJECUCION', 15),
        ('CV_INICIATIVA',  'DIRECCION',  5), ('CV_INICIATIVA',  'SUPERVISION', 10), ('CV_INICIATIVA',  'EJECUCION', 10),
        ('CV_HABILIDADES', 'DIRECCION',  5), ('CV_HABILIDADES', 'SUPERVISION', 10), ('CV_HABILIDADES', 'EJECUCION', 25),
        ('CV_EVIDENCIA',   'DIRECCION', 10), ('CV_EVIDENCIA',   'SUPERVISION', 10), ('CV_EVIDENCIA',   'EJECUCION', 15)
     ) AS x(codigo, nivel, peso) ON true
JOIN criterio c ON c.codigo = x.codigo AND c.version_plantilla_prueba_id IS NULL
WHERE vp.etiqueta = 'v2 hito 2';

-- ============================================================================
-- 3 · Las instrucciones de los tres agentes de la Fase C
-- ============================================================================
-- La tabla instruccion_ia estaba vacía: el panel permitía escribirlas pero no había
-- ninguna, así que un agente no tendría prompt. Quedan editables desde el panel, que es
-- para lo que se construyó; esto es solo el punto de partida.
INSERT INTO instruccion_ia (agente_codigo, version, texto, es_activa, publicada_en) VALUES
    ('EVIDENCIA_CV', 1,
     'Lees un currículum ya anonimizado y lo puntúas sobre 100 con ocho criterios: resultados '
     'demostrables, complejidad y alcance, sistemas o procesos creados, desarrollo de personas, '
     'velocidad de aprendizaje, iniciativa, habilidades del puesto y calidad de la evidencia.' || chr(10) ||
     'Reglas que no puedes romper:' || chr(10) ||
     '- Cada nota va con su explicación y la frase del currículum en que te basas. Una nota sin '
     'explicación no sirve.' || chr(10) ||
     '- La antigüedad no da puntos por sí sola. Cuentan los resultados, no los años.' || chr(10) ||
     '- Clasifica cada afirmación como DEMOSTRADA, DECLARADA, CONTRADICHA o FALTA_INFO. '
     '"Declarada" no significa mentira: significa que hay que preguntarlo.' || chr(10) ||
     '- No puntúas edad, sexo, embarazo, raza, religión, discapacidad ni orientación sexual. Si '
     'aparece algo así, lo ignoras.' || chr(10) ||
     '- Este puntaje no descarta a nadie. Ordena, no filtra.',
     true, now()),

    ('EVALUADOR', 1,
     'Calificas respuestas abiertas de una evaluación de selección, de 0 a 4:' || chr(10) ||
     '0 · No da un caso, responde en abstracto o evade.' || chr(10) ||
     '1 · Cuenta un caso pero fue pasivo, sin contribución propia clara.' || chr(10) ||
     '2 · Hubo acción clara, pero poca medición, evidencia o aprendizaje.' || chr(10) ||
     '3 · Actuó por iniciativa, con criterio y resultado verificable.' || chr(10) ||
     '4 · Anticipó, priorizó, comunicó, actuó, midió y convirtió el aprendizaje en sistema.' || chr(10) ||
     'Reglas que no puedes romper:' || chr(10) ||
     '- Cita la parte concreta de la respuesta en que te basas. Sin evidencia citada la nota no '
     'se guarda.' || chr(10) ||
     '- Lenguaje impecable sin un solo caso verificable baja la confianza, no sube la nota.' || chr(10) ||
     '- Si no puedes calificar con lo que hay, dilo. Nunca pongas cero por falta de información.',
     true, now()),

    ('POTENCIAL_RIESGO', 1,
     'Armas el Perfil de Talento de un candidato a partir de lo que ya calificaron los otros '
     'agentes y de las respuestas cerradas, que se puntúan por código.' || chr(10) ||
     'Devuelves adecuación al puesto, potencial, alto rendimiento y confianza de la evidencia, '
     'más los hallazgos.' || chr(10) ||
     'Reglas que no puedes romper:' || chr(10) ||
     '- Cada hallazgo lleva su tipo y su evidencia. Los cinco tipos no se mezclan: FORTALEZA, '
     'RIESGO_CRITICO, RIESGO_DESARROLLABLE, PREFERENCIA y FALTA_EVIDENCIA. Un riesgo '
     'desarrollable es algo que la persona hace mal y se puede corregir; una falta de evidencia '
     'es algo que no sabemos. No son lo mismo.' || chr(10) ||
     '- La confianza de la evidencia distingue a quien fue evaluado a fondo de quien apenas dejó '
     'rastro. Bájala cuando falten datos.' || chr(10) ||
     '- Una contradicción es una alerta para conversar, nunca un descarte.' || chr(10) ||
     '- No decides si alguien se contrata. Preparas la evidencia para que una persona decida.',
     true, now());

-- ============================================================================
-- 4 · Umbrales de los grupos de prioridad
-- ============================================================================
-- ⚠️ Ningún documento dice dónde está la frontera entre los cuatro grupos. El Banco Maestro
-- trae bandas de interpretación (80-100 fuerte, 65-79 prometedor, 50-64 mixto, <50 débil) y
-- de ahí salen estos números. Van como parámetro y no en el código a propósito: es una
-- interpretación nuestra y Renaser tiene que poder corregirla sin que nadie recompile.
INSERT INTO parametro (organizacion_id, codigo, valor, tipo, descripcion)
SELECT o.id, p.codigo, p.valor, p.tipo, p.descripcion
FROM organizacion o,
     (VALUES
        ('umbral_grupo_alta', '80', 'ENTERO',
         'Nota del Perfil Integral a partir de la cual un candidato es de alta prioridad'),
        ('umbral_grupo_priorizado', '65', 'ENTERO',
         'Nota por debajo de la cual un candidato queda como no priorizado')
     ) AS p(codigo, valor, tipo, descripcion)
WHERE o.codigo = 'RENASER';

-- ============================================================================
-- 5 · Una plantilla de evaluación por nivel
-- ============================================================================
-- La plantilla es la receta: cuántas preguntas de cada tipo le tocan a un puesto. El banco
-- tiene 90/60/50 preguntas, pero nadie responde el banco entero (RF-47): la plantilla elige.
-- Los minutos objetivo salen de RF-49 y respetan el aviso de los 60 minutos.
INSERT INTO plantilla_evaluacion (organizacion_id, nombre, nivel_puesto_codigo, version, estado,
                                  minutos_objetivo, vigencia_meses, publicada_en)
SELECT o.id, x.nombre, x.nivel, 1, 'PUBLICADA', x.minutos, 12, now()
FROM organizacion o,
     (VALUES
        ('Evaluación Dirección',   'DIRECCION',   45),
        ('Evaluación Supervisión', 'SUPERVISION', 40),
        ('Evaluación Ejecución',   'EJECUCION',   30)
     ) AS x(nombre, nivel, minutos)
WHERE o.codigo = 'RENASER';

-- Las cuotas por tipo. Suman menos que el banco entero a propósito: el resto queda como
-- reserva para que dos candidatos del mismo puesto no vean exactamente las mismas preguntas.
INSERT INTO cuota_plantilla_evaluacion (plantilla_evaluacion_id, tipo_banco, tipo_pregunta,
                                        cantidad_min, cantidad_max)
SELECT pe.id, 'NIVEL', x.tipo, x.minimo, x.maximo
FROM plantilla_evaluacion pe
JOIN (VALUES
        ('Evaluación Dirección',   'ESTILO',       6,  8),
        ('Evaluación Dirección',   'SITUACION',    8, 10),
        ('Evaluación Dirección',   'CONDUCTUAL',   4,  6),
        ('Evaluación Dirección',   'MICROCASO',    3,  4),
        ('Evaluación Dirección',   'DILEMA',       4,  6),
        ('Evaluación Dirección',   'CONSISTENCIA', 3,  4),
        ('Evaluación Supervisión', 'ESTILO',       5,  6),
        ('Evaluación Supervisión', 'SITUACION',    7,  9),
        ('Evaluación Supervisión', 'CONDUCTUAL',   4,  5),
        ('Evaluación Supervisión', 'MICROCASO',    3,  4),
        ('Evaluación Supervisión', 'DILEMA',       3,  4),
        ('Evaluación Ejecución',   'ESTILO',       4,  5),
        ('Evaluación Ejecución',   'SITUACION',    6,  8),
        ('Evaluación Ejecución',   'CONDUCTUAL',   3,  4),
        ('Evaluación Ejecución',   'MICROCASO',    3,  4),
        ('Evaluación Ejecución',   'DILEMA',       2,  3),
        ('Evaluación Ejecución',   'CONSISTENCIA', 2,  3)
     ) AS x(plantilla, tipo, minimo, maximo) ON x.plantilla = pe.nombre;

-- ============================================================================
-- 6 · Las vacantes que ya existen necesitan plantilla
-- ============================================================================
-- Desde ahora una vacante sin plantilla no puede admitir postulaciones: no habría con qué
-- armar la evaluación. Las vacantes ya publicadas no tienen ninguna, así que se les asigna
-- la de su nivel. Sin esto, cualquier postulación a una vacante vieja fallaría, y el motivo
-- no sería nada evidente.
UPDATE vacante v
SET plantilla_evaluacion_id = pe.id
FROM puesto pu
JOIN plantilla_evaluacion pe ON pe.nivel_puesto_codigo = pu.nivel_puesto_codigo
                            AND pe.estado = 'PUBLICADA'
WHERE v.puesto_id = pu.id AND v.plantilla_evaluacion_id IS NULL;

-- 7 · Las 200 preguntas del Banco Maestro
-- ============================================================================
-- Generado por scripts/importar-banco-maestro.py. Para regenerarlo:
--     python3 scripts/importar-banco-maestro.py

-- ================= DIRECCION: 90 preguntas =================
INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_en)
SELECT id, 'NIVEL', 'DIRECCION', 'Banco Dirección V0.1', 'PUBLICADA', now()
FROM organizacion WHERE codigo = 'RENASER';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D01', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→VEL+2, INI+1 | B→CRI+2, DEC+1 · VEL vs CRI',
       false, 1
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Lanzar una prueba reversible pequeña en 24 h para obtener datos.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D01';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D01'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D01'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Invertir unas horas primero en identificar variables que podrían invalidar la prueba.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D01';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D01'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'DEC', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D01'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D02', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→CTL+2,SYS+1 | B→COM+2,CTL+1 · control vs alineación',
       false, 2
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Definir con precisión 3 indicadores antes de empezar.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D02';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D02'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D02'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Asegurar primero que todos entiendan el resultado y luego construir el control mínimo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D02';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D02'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D02'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D03', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→SUP+2,DEC+1 | B→CRI+2,CTL+1 · intervención vs evidencia',
       false, 3
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Intervenir rápido cuando el rendimiento cae.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D03';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SUP', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D03'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'DEC', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D03'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Observar una iteración adicional para distinguir patrón de ruido.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D03';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D03'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D03'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D04', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→PPL+2,AUT+1 | B→BUS+2,DEC+1 · expertise vs integración',
       false, 4
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Delegar una decisión si el responsable tiene más expertise técnico.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D04';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PPL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D04'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'AUT', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D04'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Mantener la decisión si el impacto cruza varias unidades.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D04';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'BUS', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D04'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'DEC', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D04'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D05', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→PRI+2,DEC+1 | B→SER+2,SYS+1 · foco vs servicio',
       false, 5
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Proteger una prioridad crítica aunque decepcione a otras áreas.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D05';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D05'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'DEC', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D05'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Buscar una forma de preservar parcialmente ambos compromisos.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D05';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SER', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D05'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D05'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D06', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→OWN+2,VEL+1 | B→PPL+2,AUT+1 · intervención vs desarrollo',
       false, 6
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Corregir personalmente un problema crítico y luego rediseñar el sistema.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D06';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'OWN', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D06'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D06'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Exigir que el owner lo resuelva aunque tome algo más de tiempo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D06';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PPL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D06'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'AUT', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D06'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D07', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→COM+2,DEC+1 | B→CRI+2,CTL+1 · prevención vs precisión',
       false, 7
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Comunicar un riesgo con 60% de evidencia.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D07';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D07'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'DEC', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D07'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Investigar hasta tener 80% antes de escalar.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D07';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D07'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D07'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D08', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→REL+2,BUS+1 | B→LRN+2,INI+1 · mastery vs potencial',
       false, 8
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Contratar a alguien que ya domina el problema.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D08';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D08'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'BUS', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D08'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Elegir a alguien con alta capacidad de aprender y elevarse rápido.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D08';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D08'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D08'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D09', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→INT+2,QUA+1 | B→SUP+2,CTL+1 · consistencia vs supervisión adaptativa',
       false, 9
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Mantener estándares idénticos para todos.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D09';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INT', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D09'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D09'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Ajustar el nivel de seguimiento según autonomía y riesgo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D09';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SUP', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D09'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D09'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D10', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→BUS+2,PRI+1 | B→DEC+2,BUS+1 · liquidez vs opcionalidad',
       false, 10
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Priorizar cash aun si reduce crecimiento de corto plazo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D10';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'BUS', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D10'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D10'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Priorizar crecimiento cuando existe una ventana estratégica difícil de repetir.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D10';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'DEC', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D10'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'BUS', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D10'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D11', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→SYS+2,CTL+1 | B→VEL+2,INI+1 · sistema vs velocidad',
       false, 11
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Documentar una decisión antes de ejecutarla.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D11';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D11'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D11'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Ejecutar la parte reversible y documentar inmediatamente después.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D11';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D11'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D11'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D12', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→SUP+2,COM+1 | B→CTL+2,REL+1 · inmediatez vs cadencia',
       false, 12
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Dar feedback inmediatamente tras observar una conducta riesgosa.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D12';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SUP', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D12'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D12'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Esperar al checkpoint acordado si el riesgo no es material.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D12';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D12'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D12'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D13', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→VEL+2,LRN+1 | B→QUA+2,SER+1 · MVP vs calidad',
       false, 13
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Aceptar un 85% funcional y aprender en producción controlada.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D13';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D13'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D13'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Llevar a 98% antes de exponerlo al cliente.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D13';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D13'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SER', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D13'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D14', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→CRI+2,CTL+1 | B→SYS+2,CRI+1 · dato vs sistema',
       false, 14
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Cuestionar primero la métrica.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D14';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D14'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D14'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Cuestionar primero el proceso que produjo la métrica.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D14';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D14'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D14'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D15', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→PPL+2,LRN+1 | B→BUS+2,QUA+1 · desarrollo vs bar raising',
       false, 15
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Desarrollar internamente a una persona de alto potencial.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D15';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PPL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D15'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D15'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Incorporar talento externo para elevar inmediatamente la barra.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D15';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'BUS', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D15'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D15'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D16', 'SITUACION', 'SITUACION',
       'Evento fuera de ruta', 'Meta 700; proyección 510; CPL correcto; show rate cae.', 'C=4,D=1,A=1,B=0',
       true, 16
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Duplicar Ads', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Exigir más horas', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Diagnosticar funnel y atacar show rate manteniendo adquisición controlada', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Esperar 48h', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D16';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D16';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D16';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D16';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D17', 'SITUACION', 'SITUACION',
       'Manager 54%', 'Tres semanas bajo meta; atribuye todo a dependencias.', 'C=4,A=1,B=0,D=0',
       true, 17
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Despedir hoy', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Aceptar contexto', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Separar causas controlables/no controlables, plan con KPI y fecha de decisión', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Hacer su trabajo temporalmente', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D17';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D17';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D17';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'OWN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D17';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D18', 'SITUACION', 'SITUACION',
       'CV prestigioso, challenge 58', '10 años y empresas conocidas; ejecución débil.', 'C=4,B=2,A=0,D=0',
       true, 18
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Contratar por trayectoria', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Descartar sin más', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Pedir segunda evidencia real y evaluar discrepancia', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Bajar estándar del challenge', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D18';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D18';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'POT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D18';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D19', 'SITUACION', 'SITUACION',
       '2 años, challenge 95', 'Poca antigüedad, gran velocidad y evidencia.', 'B=4,C=2,A=0,D=0',
       true, 19
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Descartar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Pasar a kickoff/trial', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Contratar directo', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Pedir más años', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D19';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D19';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D19';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D20', 'SITUACION', 'SITUACION',
       '27 aprobaciones fundador', 'Los procesos esperan dirección.', 'B=4,A=1,C=1,D=0',
       true, 20
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Contratar asistente', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Crear niveles de autoridad, guardrails y excepciones', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Pedir respuesta más rápida', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Mantener por control', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D20';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D20';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D20';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D20';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D21', 'SITUACION', 'SITUACION',
       'Métrica mala ocultada', 'Supervisor la ocultó para intentar arreglarla antes.', 'C=4,B=1,D=1,A=0',
       true, 21
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Valorar intención', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Despedir de inmediato', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Verificar gravedad/patrón y tratar integridad+comunicación como riesgo crítico', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Pedir que la próxima vez avise', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D21';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D21';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D21';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D22', 'SITUACION', 'SITUACION',
       'Cash limitado', 'Proyecto A factura más; B cobra antes y tiene mejor margen.', 'B=4,A=1,C=1,D=0',
       true, 22
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Elegir A', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Elegir por escenarios cash/margen/estrategia', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Mitad y mitad', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Elegir el favorito del CEO', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D22';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D22';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D22';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D23', 'SITUACION', 'SITUACION',
       'CEO contradice evidencia', 'Ordena una acción que tus datos cuestionan.', 'C=4,B=1,A=0,D=0',
       true, 23
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Ejecutar sin comentar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D23';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Negarse', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D23';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Exponer evidencia, riesgo, alternativa; luego ejecutar decisión final válida con tracking', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D23';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Retrasar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D23';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D23';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D23';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D23';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D24', 'SITUACION', 'SITUACION',
       'Riesgo concentrado', '7 participantes en riesgo; 4 con mismo mentor.', 'B=4,A=1,C=1,D=0',
       true, 24
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Enviar 7 recordatorios', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D24';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Investigar patrón del mentor/proceso y atender urgentes', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D24';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Reemplazar mentor inmediatamente', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D24';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Ignorar por muestra pequeña', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D24';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D24';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D24';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D24';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D25', 'SITUACION', 'SITUACION',
       'Tech al 118%', 'Retrasos aumentan.', 'C=4,A=1,B=0,D=0',
       true, 25
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Contratar ya', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D25';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Pedir horas extra', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D25';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Medir demanda/capacidad, eliminar/automatizar y calcular gap real', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D25';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Bajar estándares', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D25';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D25';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D25';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D25';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D26', 'SITUACION', 'SITUACION',
       'Mismo error por 3 personas', 'Fallo idéntico.', 'C=4,B=1,A=0,D=0',
       true, 26
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Cambiar personas', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D26';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Capacitar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D26';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Revisar proceso, criterio, interfaz y SOP antes de atribuirlo a personas', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D26';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Aceptar tasa', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D26';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D26';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D26';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D27', 'SITUACION', 'SITUACION',
       'Cliente no obtiene valor', 'Checklist se cumplió.', 'C=4,B=1,A=0,D=0',
       true, 27
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Defender alcance', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D27';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Regalar más trabajo', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D27';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Corregir gap outcome/entregable y redefinir definition of done', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D27';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Culpar expectativa', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D27';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D27';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D27';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D27';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D28', 'SITUACION', 'SITUACION',
       'Decisión con 60% información', 'Deadline hoy.', 'C=4,B=1,A=0,D=0',
       true, 28
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Esperar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D28';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Decidir y no revisar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D28';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Separar hechos/hipótesis, elegir acción reversible y trigger de revisión', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D28';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Delegar para evitar riesgo', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D28';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D28';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D28';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D29', 'SITUACION', 'SITUACION',
       'Alto performer rompe controles', '125% de meta, riesgo operativo.', 'C=4,B=1,D=1,A=0',
       true, 29
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Tolerar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D29';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Sancionar sin analizar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D29';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Preservar outcome y corregir controles/conducta', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D29';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Reducir responsabilidades', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D29';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D29';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D29';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D30', 'SITUACION', 'SITUACION',
       'Ventas promete imposible', 'Operaciones no puede cumplir.', 'C=4,B=1,A=0,D=0',
       true, 30
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Exigir cumplimiento', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D30';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Informar al cliente sin plan', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D30';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Cuantificar gap, crear opciones, proteger relación y corregir proceso comercial', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D30';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Ocultar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D30';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D30';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D30';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D30';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D31', 'SITUACION', 'SITUACION',
       'Idea fuera de estrategia', 'Gran oportunidad aparente.', 'C=4,A=1,B=1,D=0',
       true, 31
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Iniciar ya', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D31';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Rechazar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D31';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Aplicar filtro impacto/costo/opcionalidad/capacidad y test pequeño', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D31';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Crear nueva unidad', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D31';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D31';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D31';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D31';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D32', 'SITUACION', 'SITUACION',
       'Scope creep diario', 'Equipo tech cambia prioridades constantemente.', 'C=4,B=1,D=1,A=0',
       true, 32
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Aceptar por agilidad', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D32';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Congelar todo', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D32';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Crear intake, reglas de cambio y capacidad protegida', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D32';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Contratar más', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D32';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D32';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D32';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D33', 'SITUACION', 'SITUACION',
       'Campaña gasta rápido', '80% spend con 50% del periodo.', 'C=4,B=1,A=0,D=0',
       true, 33
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Ignorar si hay leads', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D33';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Pausar todo', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D33';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Revisar downstream, pacing y límites; ajustar según guardrail', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D33';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Duplicar presupuesto', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D33';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D33';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D33';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D34', 'SITUACION', 'SITUACION',
       'Dos áreas se culpan', 'Conflicto interfuncional.', 'B=4,A=1,C=0,D=0',
       true, 34
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Reunión para desahogo', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D34';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Outcome compartido, hechos, handoffs, owners y reglas', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D34';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Elegir culpable', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D34';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Separarlas', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D34';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D34';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D34';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D35', 'SITUACION', 'SITUACION',
       'Promedio bueno, dispersión extrema', 'KPI global en meta pero mitad de clientes mal.', 'C=4,B=0,A=0,D=1',
       true, 35
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Celebrar resultado', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D35';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Mirar solo promedio', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D35';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Segmentar distribución y riesgo antes de concluir', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D35';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Subir meta', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D35';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D35';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D35';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D36', 'CONDUCTUAL', 'CONDUCTUAL',
       'Cuéntame una operación realmente fuera de control que recibiste. ¿Cómo supiste que estaba fuera de control? ¿Qué datos, indicadores, cadencia y herramientas instalaste? ¿Qué cambió?', NULL, NULL,
       true, 36
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D37', 'CONDUCTUAL', 'CONDUCTUAL',
       'Háblame de una persona excelente en resultados pero difícil de gestionar. ¿Qué hiciste y qué preservaste?', NULL, NULL,
       true, 37
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D38', 'CONDUCTUAL', 'CONDUCTUAL',
       'Caso real de alguien que no daba resultados. ¿Cómo diferenciaste falta de capacidad, actitud, claridad o proceso?', NULL, NULL,
       true, 38
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D39', 'CONDUCTUAL', 'CONDUCTUAL',
       'Cuéntame una desvinculación que hayas decidido o recomendado. ¿Qué evidencia acumulaste y qué intentaste antes?', NULL, NULL,
       true, 39
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D40', 'CONDUCTUAL', 'CONDUCTUAL',
       'La peor contratación que hiciste. ¿Qué señal ignoraste y cómo cambiaste el sistema?', NULL, NULL,
       true, 40
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D41', 'CONDUCTUAL', 'CONDUCTUAL',
       'Persona que creció notablemente bajo tu liderazgo. ¿Qué cambiaste en contexto, feedback, responsabilidad y seguimiento?', NULL, NULL,
       true, 41
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D42', 'CONDUCTUAL', 'CONDUCTUAL',
       'Un error tuyo cuyo reporte te perjudicaba. ¿Cuándo informaste y qué cambió después?', NULL, NULL,
       true, 42
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D43', 'CONDUCTUAL', 'CONDUCTUAL',
       'Problema que comunicaste antes de que ocurriera. ¿Qué señal débil viste?', NULL, NULL,
       true, 43
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D44', 'CONDUCTUAL', 'CONDUCTUAL',
       'Última vez que cuestionaste una decisión de un jefe. ¿Qué evidencia usaste y cómo actuaste después?', NULL, NULL,
       true, 44
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D45', 'CONDUCTUAL', 'CONDUCTUAL',
       'Acción de alto impacto que nadie te pidió. ¿Cómo calculaste que valía la pena?', NULL, NULL,
       true, 45
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D46', 'CONDUCTUAL', 'CONDUCTUAL',
       'Proyecto que debía tardar semanas y entregaste en días. ¿Qué eliminaste, paralelizaste, automatizaste o pospusiste?', NULL, NULL,
       true, 46
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D47', 'CONDUCTUAL', 'CONDUCTUAL',
       'Caso donde cumplir el contrato o alcance no era suficiente para servir bien.', NULL, NULL,
       true, 47
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D48', 'CONDUCTUAL', 'CONDUCTUAL',
       'Error o actividad repetitiva que convertiste en proceso, SOP, automatización o producto.', NULL, NULL,
       true, 48
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D49', 'CONDUCTUAL', 'CONDUCTUAL',
       'Decisión en la que elegiste cash, margen o sostenibilidad por encima de volumen.', NULL, NULL,
       true, 49
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D50', 'CONDUCTUAL', 'CONDUCTUAL',
       'La mayor crisis operativa que lideraste: primera hora, primer día, primera semana.', NULL, NULL,
       true, 50
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D51', 'MICROCASO', 'MICROCASO',
       'Portafolio RENASER', '6 personas; 5 aplicativos, evento 700, evento 300, 30 clientes y manuales. Diseña prioridades 30 días.', 'Diagnóstico, tradeoffs, capacidad, cash, owner.',
       true, 51
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D51';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D51';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D51';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D52', 'MICROCASO', 'MICROCASO',
       'Evento 700', 'Show rate histórico 42%. Construye driver tree y calcula qué variable mover primero.', 'Funnel, hipótesis, matemática básica.',
       true, 52
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D52';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D52';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D53', 'MICROCASO', 'MICROCASO',
       'Evento pagado 300', 'Diseña funnel anuncio→lead→WhatsApp→venta→factura→cash y 5 KPIs maestros.', 'Economics, revenue quality.',
       true, 53
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D53';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D53';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D54', 'MICROCASO', 'MICROCASO',
       '30 clientes', '6 están en riesgo. Diseña una revisión semanal que no dependa de perseguir personas.', 'Leading indicators, owner, triggers.',
       true, 54
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D54';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D54';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D54';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D55', 'MICROCASO', 'MICROCASO',
       'S/50K por cobrar', 'Obligaciones en 14 días. Prioriza cobranza y decisiones.', 'Cash, concentración, escenarios.',
       true, 55
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D55';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D55';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D56', 'MICROCASO', 'MICROCASO',
       'Tech 118%', '¿Cuándo contratar y cuándo automatizar? Presenta decisión y datos necesarios.', 'Capacity logic.',
       true, 56
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D56';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D56';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D57', 'MICROCASO', 'MICROCASO',
       'People 120/82/55', 'Tres performers con tendencias distintas. Plan 30 días.', 'Supervisión diferenciada.',
       true, 57
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D57';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D57';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D58', 'MICROCASO', 'MICROCASO',
       'CPL -20%, ventas -25%', '¿Qué revisas antes de tocar Ads?', 'Downstream, attribution, offer.',
       true, 58
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D58';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D58';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D59', 'MICROCASO', 'MICROCASO',
       '27 aprobaciones', 'Rediseña gobierno sin perder control.', 'Guardrails, delegation, audit.',
       true, 59
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D59';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D59';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D60', 'MICROCASO', 'MICROCASO',
       'Nueva unidad Z', 'Sin revenue, potencial alto. Define hitos para liberar siguiente tramo de recursos.', 'Optionality, milestones.',
       true, 60
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D60';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D60';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D61', 'MICROCASO', 'MICROCASO',
       'Paraíso consume dirección', 'Diseña gobernanza para que no paralice Core.', 'Portfolio governance.',
       true, 61
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D61';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D61';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D62', 'MICROCASO', 'MICROCASO',
       'Manuales/certificación', 'Hay que producir calidad en semanas, no meses. Diseña método.', 'Parallelization, QA.',
       true, 62
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'VEL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D62';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D62';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D63', 'MICROCASO', 'MICROCASO',
       'Cliente pide growth, ops falla', 'Diseña diagnóstico de consultoría antes de vender más.', 'Root cause, constraints.',
       true, 63
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D63';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D63';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D64', 'MICROCASO', 'MICROCASO',
       '3 high performers quieren irse', '¿Qué datos pides antes de ofrecer más dinero?', 'Job design, manager, fit, compensation.',
       true, 64
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D64';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D64';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D65', 'MICROCASO', 'MICROCASO',
       'Eliminar 20% trabajo', 'Sin bajar resultados. ¿Cómo decides qué desaparece?', 'Value stream, 80/20.',
       true, 65
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D65';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D65';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D66', 'DILEMA', 'DILEMA',
       'Tienes 2 h libres hoy.', 'A revisar un dashboard estable | B entrevistar un cliente crítico | C documentar un proceso repetido | D acelerar una tarea que vence mañana', 'B=4,D=3,C=2,A=1 según contexto; candidato debe justificar',
       true, 66
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'revisar un dashboard estable', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D66';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'entrevistar un cliente crítico', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D66';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'documentar un proceso repetido', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D66';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'acelerar una tarea que vence mañana', 3
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D66';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D66';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D66';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D67', 'DILEMA', 'DILEMA',
       'Un colaborador pide aprobación para una decisión reversible de bajo riesgo.', 'A aprobar | B devolver con límites para que decida | C decidir tú | D posponer', 'B=4,A=2,C=1,D=0',
       true, 67
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'aprobar', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D67';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'devolver con límites para que decida', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D67';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'decidir tú', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D67';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'posponer', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D67';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D67';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D67';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D68', 'DILEMA', 'DILEMA',
       'El equipo tiene demasiados KPIs.', 'A añadir resumen | B eliminar los que no disparan decisiones | C exigir actualizar mejor | D crear más dashboards', 'B=4,A=2,C=1,D=0',
       true, 68
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'añadir resumen', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D68';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'eliminar los que no disparan decisiones', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D68';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'exigir actualizar mejor', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D68';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'crear más dashboards', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D68';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D68';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D68';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D69', 'DILEMA', 'DILEMA',
       'Un proyecto estratégico está 10% tarde pero 30% sobre calidad.', 'A celebrar calidad | B acelerar sin cambios | C revisar si calidad extra crea valor y recortar sobreproceso | D cambiar equipo', 'C=4,B=2,A=1,D=0',
       true, 69
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'celebrar calidad', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D69';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'acelerar sin cambios', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D69';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'revisar si calidad extra crea valor y recortar sobreproceso', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D69';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'cambiar equipo', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D69';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'VEL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D69';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D69';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D70', 'DILEMA', 'DILEMA',
       'Una idea generó resultado una vez.', 'A convertir en SOP | B repetir prueba antes de estandarizar | C escalar a toda empresa | D ignorar', 'B=4,A=2,C=1,D=0',
       true, 70
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'convertir en SOP', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D70';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'repetir prueba antes de estandarizar', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D70';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'escalar a toda empresa', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D70';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'ignorar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D70';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D70';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D70';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D71', 'DILEMA', 'DILEMA',
       'Fundador pide urgencia no conectada a objetivo.', 'A hacerla | B ignorar | C aclarar impacto y tradeoff antes de desplazar prioridades | D delegarla sin contexto', 'C=4,A=2,D=1,B=0',
       true, 71
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'hacerla', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D71';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'ignorar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D71';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'aclarar impacto y tradeoff antes de desplazar prioridades', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D71';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'delegarla sin contexto', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D71';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D71';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D71';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D72', 'DILEMA', 'DILEMA',
       'Manager pide más personas.', 'A abrir vacante | B negar | C pedir capacity/process evidence | D tercerizar ya', 'C=4,A=1,D=1,B=0',
       true, 72
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'abrir vacante', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D72';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'negar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D72';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'pedir capacity/process evidence', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D72';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'tercerizar ya', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D72';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D72';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D72';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D73', 'DILEMA', 'DILEMA',
       'Cliente grande pide excepción.', 'A aceptar por facturación | B negar por regla | C valorar impacto, precedentes y costo; excepción explícita si conviene | D que ventas decida', 'C=4,A=1,B=1,D=0',
       true, 73
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'aceptar por facturación', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D73';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'negar por regla', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D73';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'valorar impacto, precedentes y costo; excepción explícita si conviene', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D73';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'que ventas decida', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D73';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D73';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D73';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D74', 'DILEMA', 'DILEMA',
       'Dos personas tienen misma performance; una crea sistemas y otra depende de seguimiento.', 'A iguales | B priorizar a quien crea sistemas para crecimiento | C priorizar a quien obedece más | D depende solo de antigüedad', 'B=4,A=2,C=0,D=0',
       true, 74
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'iguales', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D74';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'priorizar a quien crea sistemas para crecimiento', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D74';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'priorizar a quien obedece más', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D74';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'depende solo de antigüedad', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D74';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D74';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D74';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D75', 'DILEMA', 'DILEMA',
       'Te llega una métrica excelente sin fuente clara.', 'A usarla | B pedir fuente/definición antes de decisión | C promediar con histórico | D ignorarla', 'B=4,C=1,D=1,A=0',
       true, 75
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'usarla', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D75';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'pedir fuente/definición antes de decisión', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D75';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'promediar con histórico', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D75';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'ignorarla', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D75';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D75';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D75';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D76', 'DILEMA', 'DILEMA',
       'Equipo falla por criterio ambiguo.', 'A feedback individual | B mejorar definition of done y ejemplos | C más supervisión | D contratar QA', 'B=4,C=2,A=1,D=1',
       true, 76
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'feedback individual', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D76';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'mejorar definition of done y ejemplos', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D76';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'más supervisión', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D76';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'contratar QA', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D76';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D76';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D76';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D77', 'DILEMA', 'DILEMA',
       'Se puede ahorrar 20h/mes automatizando algo de bajo riesgo.', 'A automatizar ya sin mapear | B mapear mínimo, probar y medir ahorro | C esperar gran proyecto de automatización | D delegar manual', 'B=4,A=2,D=1,C=0',
       true, 77
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'automatizar ya sin mapear', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D77';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'mapear mínimo, probar y medir ahorro', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D77';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'esperar gran proyecto de automatización', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D77';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'delegar manual', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D77';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D77';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'VEL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D77';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D78', 'DILEMA', 'DILEMA',
       'Tu mejor líder quiere más autonomía.', 'A dársela sin límites | B establecer outcomes, guardrails y revisión por excepciones | C mantener control actual | D ascenderlo inmediatamente', 'B=4,A=2,D=1,C=0',
       true, 78
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'dársela sin límites', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D78';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'establecer outcomes, guardrails y revisión por excepciones', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D78';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'mantener control actual', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D78';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'ascenderlo inmediatamente', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D78';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D78';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D78';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D79', 'DILEMA', 'DILEMA',
       'Una mala decisión produjo buen resultado por suerte.', 'A premiarla | B analizar calidad de decisión separada del outcome | C repetirla | D ignorar', 'B=4,A=1,C=0,D=0',
       true, 79
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'premiarla', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D79';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'analizar calidad de decisión separada del outcome', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D79';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'repetirla', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D79';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'ignorar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D79';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D79';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D79';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D80', 'DILEMA', 'DILEMA',
       'Una buena decisión produjo mal resultado por un factor improbable.', 'A castigar | B revisar proceso de decisión y actualizar probabilidades | C decir mala suerte y seguir | D cambiar objetivo', 'B=4,C=2,A=0,D=0',
       true, 80
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'castigar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D80';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'revisar proceso de decisión y actualizar probabilidades', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D80';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'decir mala suerte y seguir', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D80';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'cambiar objetivo', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D80';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D80';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D80';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D81', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué dato negativo estarías dispuesto a no comunicar temporalmente para proteger un resultado?', NULL, 'Respuesta esperada: solo razones legítimas de seguridad/confidencialidad; conveniencia = review.',
       false, 81
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D81';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D82', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué harías si una meta solo puede alcanzarse rompiendo una regla interna no crítica?', NULL, 'Debe diferenciar regla obsoleta vs ocultar incumplimiento; documentar excepción.',
       false, 82
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D82';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D82';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D83', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué parte de dirigir personas disfrutas menos?', NULL, 'No hay respuesta correcta; evaluar realismo y role fit.',
       false, 83
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'FIT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D83';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D84', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué tipo de feedback te hace rendir peor?', NULL, 'Advisory: expectativas y madurez, no filtro.',
       false, 84
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'FIT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D84';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D85', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué porcentaje de tus decisiones importantes debería poder tomar tu equipo sin ti? Explica.', NULL, 'Contrastar delegación/control.',
       false, 85
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D85';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D85';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D86', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Cuándo pedir ayuda demuestra alto rendimiento y cuándo dependencia?', NULL, 'Criterio de escalamiento.',
       false, 86
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D86';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D86';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D87', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué te haría renunciar a un trabajo aunque la compensación fuera buena?', NULL, 'Retention hypothesis; no puntuación HPI.',
       false, 87
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'FIT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D87';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D88', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué situación te haría quedarte un año más en una empresa?', NULL, 'Growth/meaning/role fit; advisory.',
       false, 88
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'FIT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D88';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D89', 'CONSISTENCIA', 'CONSISTENCIA',
       'Si tu equipo logra la meta pero tú fuiste innecesario, ¿es éxito o fracaso?', NULL, 'Esperado: éxito si sistema/people funcionan; revisar ego/control.',
       false, 89
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D89';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D89';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'D90', 'CONSISTENCIA', 'CONSISTENCIA',
       'Si tus datos dicen que tú eres el cuello de botella, ¿qué harías primero?', NULL, 'Ownership y capacidad de rediseñar autoridad.',
       false, 90
FROM version_banco WHERE etiqueta = 'Banco Dirección V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'OWN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D90';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Dirección V0.1' AND p.codigo = 'D90';

-- ================= SUPERVISION: 60 preguntas =================
INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_en)
SELECT id, 'NIVEL', 'SUPERVISION', 'Banco Supervisión V0.1', 'PUBLICADA', now()
FROM organizacion WHERE codigo = 'RENASER';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S01', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→CTL+2,PRI+1 | B→COM+2,SUP+1',
       false, 1
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Revisar el tablero al inicio y actuar sobre excepciones.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S01';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S01'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S01'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Hablar brevemente con cada responsable antes de revisar datos.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S01';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S01'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SUP', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S01'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S02', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→INI+2,SUP+1 | B→CRI+2,CTL+1',
       false, 2
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Corregir un problema al primer indicio.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S02';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S02'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SUP', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S02'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Esperar un segundo dato si el impacto aún es bajo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S02';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S02'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S02'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S03', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→SYS+2,VEL+1 | B→LRN+2,QUA+1',
       false, 3
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Crear una plantilla para una tarea repetitiva.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S03';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S03'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S03'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Entrenar a la persona para que comprenda el criterio sin plantilla.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S03';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S03'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S03'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S04', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→PRI+2,VEL+1 | B→QUA+2,COM+1',
       false, 4
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Proteger el deadline aunque haya que reducir alcance.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S04';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S04'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S04'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Proteger el alcance aunque haya que renegociar fecha.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S04';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S04'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S04'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S05', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→AUT+2,SUP+1 | B→CTL+2,REL+1',
       false, 5
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Dar más autonomía al que rinde bien.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S05';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'AUT', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S05'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SUP', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S05'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Mantener la misma cadencia para todo el equipo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S05';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S05'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S05'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S06', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→SER+2,VEL+1 | B→SUP+2,PPL+1',
       false, 6
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Resolver tú una urgencia para proteger al cliente.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S06';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SER', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S06'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S06'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Hacer que el owner la resuelva con apoyo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S06';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SUP', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S06'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PPL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S06'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S07', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→COM+2,DEC+1 | B→CRI+2,CTL+1',
       false, 7
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Escalar un riesgo con información parcial.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S07';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S07'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'DEC', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S07'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Investigar un poco más antes de escalar.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S07';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S07'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S07'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S08', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→SYS+2,VEL+1 | B→CRI+2,LRN+1',
       false, 8
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Estandarizar después de dos repeticiones.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S08';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S08'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S08'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Esperar más casos antes de convertirlo en SOP.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S08';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S08'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S08'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S09', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→PRI+2,CTL+1 | B→REL+2,PRI+1',
       false, 9
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Priorizar a la persona bloqueada en tarea crítica.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S09';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S09'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S09'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Priorizar la tarea que vence primero aunque tenga menor impacto.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S09';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S09'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S09'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S10', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→PRI+2,CTL+1 | B→CRI+2,CTL+1',
       false, 10
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Medir pocas métricas que disparen acciones.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S10';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S10'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S10'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Tener mayor cobertura de métricas para entender mejor el sistema.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S10';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S10'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CTL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S10'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S11', 'SITUACION', 'SITUACION',
       'Bloqueo 35 min', 'Persona espera acceso crítico.', 'C=4,B=1,A=0,D=0',
       true, 11
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Esperar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S11';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Hacer tú la tarea', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S11';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Registrar, alternativa y escalamiento por SLA', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S11';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Culpar IT', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S11';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S11';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S11';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S12', 'SITUACION', 'SITUACION',
       '125% rompe proceso', NULL, 'B=4,C=1,D=1,A=0',
       true, 12
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Ignorar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S12';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Corregir riesgo preservando performance', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S12';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Sancionar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S12';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Quitar responsabilidad', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S12';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S12';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S12';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S13', 'SITUACION', 'SITUACION',
       '55% 3 semanas', NULL, 'B=4,C=1,A=0,D=0',
       true, 13
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Recordar más', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S13';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Plan con KPI/causa/checkpoints', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S13';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Despedir hoy', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S13';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Hacer su trabajo', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S13';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S13';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S14', 'SITUACION', 'SITUACION',
       'Seguimiento diario', NULL, 'B=4,A=1,C=0,D=0',
       true, 14
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Mensajes cada hora', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S14';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Tablero+checkpoints+alertas', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S14';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Reunión 2h', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S14';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Nada', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S14';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S14';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S14';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S15', 'SITUACION', 'SITUACION',
       '25% tiempo en reuniones', NULL, 'B=4,C=1,D=1,A=0',
       true, 15
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Aceptar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S15';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Medir output y eliminar/recortar', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S15';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Pedir puntualidad', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S15';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Más agendas', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S15';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S15';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'VEL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S15';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S16', 'SITUACION', 'SITUACION',
       'Reporte 12h/mes', NULL, 'B=4,C=1,D=0,A=0',
       true, 16
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Mantener', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'SOP+automatización', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Delegar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Contratar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S16';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S16';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S17', 'SITUACION', 'SITUACION',
       'Cliente reclama', 'Checklist cumplido.', 'B=4,C=1,A=0,D=0',
       true, 17
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Defender equipo', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Revisar outcome y done', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Descuento', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Culpar briefing', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S17';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S17';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S17';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S18', 'SITUACION', 'SITUACION',
       'Instrucción ambigua', NULL, 'C=4,A=1,B=1,D=0',
       true, 18
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Interpretar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Parar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Aclarar criterio crítico y avanzar reversible', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Delegar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S18';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S18';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S18';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S19', 'SITUACION', 'SITUACION',
       'Urgencias conflictivas', NULL, 'C=4,A=1,B=0,D=0',
       true, 19
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Hacer ambas', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Mayor rango', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Comparar impacto/deadline y escalar tradeoff', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Primero en llegar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S19';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S19';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S19';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S20', 'SITUACION', 'SITUACION',
       'Deadline en riesgo', NULL, 'B=4,C=1,A=0,D=0',
       true, 20
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Avisar al vencer', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Avisar riesgo+opciones+plan antes', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Ocultar y trabajar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Culpar dependencia', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S20';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S20';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'REL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S20';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S21', 'SITUACION', 'SITUACION',
       'Sin evidencia', NULL, 'B=4,C=1,D=0,A=0',
       true, 21
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Aceptar confianza', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Devolver para evidencia/validación', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Cerrar y revisar luego', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Sancionar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S21';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S21';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S21';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S22', 'SITUACION', 'SITUACION',
       '130% vs 65% carga', NULL, 'B=4,D=1,A=0,C=0',
       true, 22
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Horas extra', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Redistribuir según skill/dependencias', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Dejar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Contratar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S22';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S22';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S22';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S23', 'SITUACION', 'SITUACION',
       'Handoff 8h', NULL, 'B=4,A=1,C=1,D=0',
       true, 23
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Exigir rapidez', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S23';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'SLA/owner/trigger', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S23';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Reunión', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S23';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Culpar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S23';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S23';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S24', 'SITUACION', 'SITUACION',
       'Nuevo ingreso confundido', NULL, 'B=4,A=1,C=1,D=0',
       true, 24
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Repetir', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S24';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Ejemplo/checklist/done/checkpoint', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S24';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Que aprenda solo', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S24';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Cambiar persona', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S24';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S24';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S24';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S25', 'SITUACION', 'SITUACION',
       'Excepción de SOP', NULL, 'C=4,B=1,D=1,A=0',
       true, 25
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Romper sin registro', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S25';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Negar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S25';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Excepción consciente + registrar aprendizaje', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S25';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Pedir permiso para todo', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S25';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S25';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S25';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S26', 'SITUACION', 'SITUACION',
       'Problemas aparecen semanalmente', NULL, 'B=4,A=1,C=1,D=0',
       true, 26
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Más reuniones', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S26';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Triggers preventivos', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S26';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Regañar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S26';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Aceptar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S26';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S26';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S26';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S27', 'SITUACION', 'SITUACION',
       'Escalar dirección', NULL, 'B=4,D=1,A=0,C=0',
       true, 27
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Escalar todo', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S27';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Escalar cuando supera autoridad/impacto con recomendación', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S27';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Nunca', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S27';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Solo datos crudos', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S27';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S27';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S27';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S28', 'SITUACION', 'SITUACION',
       'Mismo error 3 entregables', NULL, 'B=4,A=1,C=1,D=0',
       true, 28
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Feedback a cada uno', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S28';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Revisar criterio/plantilla/QA y luego personas', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S28';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Contratar QA', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S28';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Aceptar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S28';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S28';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S28';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S29', 'SITUACION', 'SITUACION',
       'Nueva info vuelve inútil tarea', NULL, 'B=4,D=1,A=0,C=0',
       true, 29
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Terminar por compromiso', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S29';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Parar y reorientar', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S29';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Ocultar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S29';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Esperar orden', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S29';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S29';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S29';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S30', 'SITUACION', 'SITUACION',
       'Colaborador mejora SOP', NULL, 'B=4,C=1,A=0,D=0',
       true, 30
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Exigir SOP actual', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S30';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Probar mejora, medir y actualizar', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S30';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Dejar informal', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S30';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Ignorar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S30';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S30';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S30';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S31', 'CONDUCTUAL', 'CONDUCTUAL',
       'Caso real donde ordenaste un área desorganizada. ¿Qué KPI, cadencia, tablero/herramienta y resultado?', NULL, NULL,
       true, 31
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S32', 'CONDUCTUAL', 'CONDUCTUAL',
       '¿Cómo haces seguimiento sin perseguir personas? Dame un caso.', NULL, NULL,
       true, 32
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S33', 'CONDUCTUAL', 'CONDUCTUAL',
       'Buen performer que empezó a caer. ¿Cómo detectaste y qué hiciste?', NULL, NULL,
       true, 33
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S34', 'CONDUCTUAL', 'CONDUCTUAL',
       'Riesgo que escalaste antes del vencimiento.', NULL, NULL,
       true, 34
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S35', 'CONDUCTUAL', 'CONDUCTUAL',
       'Dos personas en conflicto afectaban outcome. ¿Cómo separaste hechos de posiciones?', NULL, NULL,
       true, 35
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S36', 'CONDUCTUAL', 'CONDUCTUAL',
       'Proceso donde bajaste errores/retrabajo. Antes/después.', NULL, NULL,
       true, 36
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S37', 'CONDUCTUAL', 'CONDUCTUAL',
       'Mejora que implementaste sin que te la pidieran.', NULL, NULL,
       true, 37
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S38', 'CONDUCTUAL', 'CONDUCTUAL',
       'Cambiaste el plan para proteger resultado de cliente.', NULL, NULL,
       true, 38
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S39', 'CONDUCTUAL', 'CONDUCTUAL',
       'Feedback que cambió tu forma de supervisar.', NULL, NULL,
       true, 39
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S40', 'CONDUCTUAL', 'CONDUCTUAL',
       'Cómo decidiste desarrollar o desvincular con evidencia.', NULL, NULL,
       true, 40
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S41', 'MICROCASO', 'MICROCASO',
       'In-basket', '6 personas, 20 tareas, 3 bloqueadas, 2 clientes reclamando. Ordena primeras 2h.', NULL,
       true, 41
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S41';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S41';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S41';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S42', 'MICROCASO', 'MICROCASO',
       'Evento 700', 'Diseña tablero mínimo y triggers.', NULL,
       true, 42
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S42';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S43', 'MICROCASO', 'MICROCASO',
       'Capacity', '40h disponibles, 58h demanda. ¿Qué haces?', NULL,
       true, 43
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S43';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S43';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S44', 'MICROCASO', 'MICROCASO',
       'Diseño 35% retrabajo', 'Intervención de una semana.', NULL,
       true, 44
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S44';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S44';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S45', 'MICROCASO', 'MICROCASO',
       '3 clientes vencidos', 'Sin seguimiento registrado. Diseña plan hoy.', NULL,
       true, 45
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'REL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S45';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S45';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S46', 'MICROCASO', 'MICROCASO',
       '90 Días', '4 participantes sin evidencia 3 días.', NULL,
       true, 46
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S46';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S46';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S47', 'MICROCASO', 'MICROCASO',
       'Tech bloqueado 2h por API', 'Plan para no parar.', NULL,
       true, 47
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S47';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S47';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S48', 'MICROCASO', 'MICROCASO',
       'Cierre diario', 'Diseña cierre mínimo que capture resultado y know-how.', NULL,
       true, 48
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S48';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S48';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S49', 'MICROCASO', 'MICROCASO',
       'Nuevo colaborador', 'Tiene talento pero pregunta todo. Plan 10 días para subir autonomía.', NULL,
       true, 49
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S49';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S49';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S50', 'MICROCASO', 'MICROCASO',
       'Evento mañana', 'Dos proveedores fallan. Diseña contingencia y comunicación.', NULL,
       true, 50
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S50';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S50';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S51', 'DILEMA', 'DILEMA',
       'Tarea crítica vs cliente molesto', NULL, 'C=4; A/B dependen; justificar',
       true, 51
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'desbloquear tarea crítica', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S51';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'responder cliente primero', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S51';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'delegar cliente y resolver bloqueo', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S51';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'reunión de equipo', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S51';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S51';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S51';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S52', 'DILEMA', 'DILEMA',
       'Alto performer pide menos reuniones', NULL, 'B=4',
       true, 52
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'aceptar sin control', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S52';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'pasar a checkpoints por outcome/excepción', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S52';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'mantener igual', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S52';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'quitarlo de reuniones', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S52';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S52';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S52';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S53', 'DILEMA', 'DILEMA',
       'Persona deficiente mejora 10% semanal', NULL, 'B=4',
       true, 53
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'despedir por estar bajo meta', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S53';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'mantener plan si trajectory+quality mejoran', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S53';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'ignorar meta', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S53';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'hacer tú su trabajo', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S53';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S53';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S53';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S54', 'DILEMA', 'DILEMA',
       'Dos tareas vencen hoy; una impacta S/50K y otra una mejora interna.', NULL, 'C=4',
       true, 54
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'primera creada', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S54';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'interna', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S54';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'S/50K', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S54';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'ambas a medias', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S54';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S54';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'BUS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S54';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S55', 'DILEMA', 'DILEMA',
       'Un proceso falla solo 1 de 20 veces pero el costo del fallo es alto.', NULL, 'B=4',
       true, 55
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'ignorar', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S55';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'analizar control preventivo', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S55';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'aumentar volumen', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S55';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'cambiar persona', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S55';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S55';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S55';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S56', 'DILEMA', 'DILEMA',
       'Un colaborador trae un problema sin solución.', NULL, 'B=4',
       true, 56
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'pedir que vuelva con opciones', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S56';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'ayudar a estructurar opciones según urgencia y enseñar método', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S56';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'resolverlo', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S56';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'ignorar', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S56';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SUP'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S56';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PPL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S56';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S57', 'DILEMA', 'DILEMA',
       'El dashboard dice verde pero cliente se queja.', NULL, 'B=4',
       true, 57
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'confiar dashboard', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S57';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'investigar mismatch dato/outcome', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S57';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'descartar queja', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S57';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'cambiar KPI ya', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S57';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S57';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S57';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S58', 'DILEMA', 'DILEMA',
       'El equipo no usa SOP.', NULL, 'B=4',
       true, 58
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'exigir cumplimiento', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S58';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'investigar fricción/valor y rediseñar', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S58';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'eliminar SOP', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S58';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'sancionar', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S58';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S58';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S58';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S59', 'DILEMA', 'DILEMA',
       'Tu jefe quiere saber ''todo''.', NULL, 'B=4',
       true, 59
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'enviar 30 métricas', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S59';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'resumir excepciones y permitir drill-down', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S59';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'llamada diaria', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S59';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'omitir problemas pequeños', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S59';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S59';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CTL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S59';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'S60', 'CONDUCTUAL', 'CONDUCTUAL',
       '¿Pedir ayuda temprano o nunca pedir ayuda?', NULL, 'Esperado: escala por riesgo/autoridad, no dependencia ni silencio.',
       true, 60
FROM version_banco WHERE etiqueta = 'Banco Supervisión V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S60';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Supervisión V0.1' AND p.codigo = 'S60';

-- ================= EJECUCION: 50 preguntas =================
INSERT INTO version_banco (organizacion_id, tipo_banco, nivel_puesto_codigo, etiqueta, estado, publicada_en)
SELECT id, 'NIVEL', 'EJECUCION', 'Banco Ejecución V0.1', 'PUBLICADA', now()
FROM organizacion WHERE codigo = 'RENASER';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O01', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→QUA+2,COM+1 | B→INI+2,LRN+1',
       false, 1
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Pedir criterio de terminado antes de comenzar.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O01';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O01'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O01'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Empezar con una primera versión para descubrir dudas reales.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O01';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O01'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O01'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O02', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→AUT+2,INI+1 | B→COM+2,REL+1',
       false, 2
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Resolver un bloqueo solo durante un tiempo definido antes de escalar.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O02';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'AUT', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O02'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O02'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Escalar temprano si el impacto potencial es alto.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O02';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O02'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O02'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O03', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→REL+2,QUA+1 | B→INI+2,SYS+1',
       false, 3
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Usar una plantilla probada.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O03';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O03'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O03'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Probar una forma nueva si puede ahorrar mucho tiempo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O03';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O03'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O03'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O04', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→VEL+2,LRN+1 | B→QUA+2,SER+1',
       false, 4
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Entregar antes una versión funcional.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O04';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O04'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O04'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Usar el tiempo disponible para elevar la calidad.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O04';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O04'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SER', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O04'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O05', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→COM+2,REL+1 | B→AUT+2,PRI+1',
       false, 5
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Preguntar al responsable cuando hay prioridad ambigua.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O05';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O05'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O05'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Decidir con el contexto disponible y explicar criterio.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O05';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'AUT', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O05'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O05'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O06', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→VEL+2,OWN+1 | B→LRN+2,SYS+1',
       false, 6
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Corregir el error inmediatamente.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O06';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O06'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'OWN', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O06'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Primero entender por qué ocurrió para no repetirlo.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O06';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O06'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'SYS', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O06'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O07', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→REL+2,QUA+1 | B→CRI+2,INI+1',
       false, 7
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Seguir exactamente el SOP.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O07';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O07'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O07'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Señalar una mejora cuando el SOP no produce el mejor resultado.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O07';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'CRI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O07'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O07'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O08', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→LRN+2,COM+1 | B→AUT+2,QUA+1',
       false, 8
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Pedir revisión temprana.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O08';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O08'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'COM', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O08'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Completar una versión más madura antes del review.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O08';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'AUT', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O08'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O08'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O09', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→REL+2,PRI+1 | B→INI+2,VEL+1',
       false, 9
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Concentrarte en una tarea hasta terminar.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O09';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O09'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'PRI', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O09'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Cambiar de tarea si aparece un bloqueo y volver luego.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O09';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'INI', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O09'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'VEL', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O09'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O10', 'ESTILO', 'ESTILO',
       'Elige la opción con la que más te identificas.', NULL, 'A→LRN+2,AUT+1 | B→REL+2,QUA+1',
       false, 10
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Aprender una herramienta con tutorial breve y prueba.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O10';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'LRN', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O10'
  AND o.letra = 'A';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'AUT', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O10'
  AND o.letra = 'A';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Pedir ejemplo interno y reproducirlo primero.', NULL
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O10';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'REL', 2
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O10'
  AND o.letra = 'B';
INSERT INTO opcion_dimension (opcion_id, dimension_codigo, incremento)
SELECT o.id, 'QUA', 1
FROM opcion o JOIN pregunta p ON p.id = o.pregunta_id
JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O10'
  AND o.letra = 'B';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O11', 'SITUACION', 'SITUACION',
       'Instrucción poco clara', NULL, 'C=4,A=1,B=1,D=0',
       true, 11
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Adivinar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O11';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Esperar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O11';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Aclarar criterio crítico y avanzar parte segura', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O11';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Compañero decide', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O11';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O11';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O11';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O12', 'SITUACION', 'SITUACION',
       'Dependencia bloquea', NULL, 'C=4,B=0,A=0,D=0',
       true, 12
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Esperar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O12';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Avisar al final', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O12';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Buscar alternativa y comunicar temprano', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O12';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Culpar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O12';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'OWN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O12';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O12';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O13', 'SITUACION', 'SITUACION',
       'Error propio', NULL, 'B=4,A=2,C=0,D=0',
       true, 13
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Corregir en silencio', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O13';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Informar y corregir según impacto', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O13';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Esperar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O13';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Borrar evidencia', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O13';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O13';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'OWN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O13';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O14', 'SITUACION', 'SITUACION',
       'Deadline en riesgo', NULL, 'B=4,C=1,A=0,D=0',
       true, 14
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Trabajar sin avisar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O14';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Avisar riesgo+nueva estimación+plan', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O14';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Pedir extensión sin explicar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O14';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Entregar incompleto sin aviso', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O14';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'REL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O14';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O14';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O15', 'SITUACION', 'SITUACION',
       'Tarea repetitiva', NULL, 'B=4,A=1,C=0,D=0',
       true, 15
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Repetir', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O15';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Crear plantilla/automatización y proponer', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O15';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Quejarte', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O15';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Dejarla', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O15';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O15';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O15';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O16', 'SITUACION', 'SITUACION',
       'Cumple pero baja calidad', NULL, 'B=4,D=1,A=0,C=0',
       true, 16
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Enviar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Self-QA y corregir', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Dejar que QA detecte', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O16';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Preguntar si importa', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O16';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O16';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O17', 'SITUACION', 'SITUACION',
       'Cliente pide fuera alcance', NULL, 'C=4,D=1,A=0,B=0',
       true, 17
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Ignorar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Prometer', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Detectar impacto y escalar/proponer alternativa', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O17';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Regalar todo', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O17';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O17';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O17';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O18', 'SITUACION', 'SITUACION',
       'Jefe no disponible', NULL, 'B=4,C=1,A=0,D=0',
       true, 18
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Detenerte', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Decisiones reversibles dentro de autoridad + documentar', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Decidir cualquier cosa', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O18';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Esperar todo el día', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O18';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O18';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O18';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O19', 'SITUACION', 'SITUACION',
       'No estás de acuerdo con feedback', NULL, 'C=4,D=1,A=0,B=0',
       true, 19
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Discutir', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Ignorar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Pedir evidencia, probar corrección y revisar resultado', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O19';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Aceptar sin pensar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O19';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O19';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O19';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O20', 'SITUACION', 'SITUACION',
       'Usaste IA', NULL, 'C=4,D=1,A=0,B=0',
       true, 20
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Entregar salida directa', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Ocultar uso', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Verificar/corregir y documentar decisiones', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O20';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'No usar IA', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O20';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O20';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O20';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O21', 'SITUACION', 'SITUACION',
       'Ves error de compañero', NULL, 'C=4,B=1,D=0,A=0',
       true, 21
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Callar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Corregir sin avisar', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Comunicar oportunamente y proteger resultado', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O21';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Reportar para sanción', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O21';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O21';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O21';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O22', 'SITUACION', 'SITUACION',
       'Tres urgencias', NULL, 'B=4,A=1,C=0,D=0',
       true, 22
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'Cualquiera', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'Priorizar por impacto/deadline/dependencia', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'Más fácil', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O22';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'Todas simultáneas', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O22';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O22';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O23', 'CONDUCTUAL', 'CONDUCTUAL',
       'Algo importante que aprendiste muy rápido para cumplir una tarea. ¿Cómo lo aprendiste y qué resultado produjo?', NULL, NULL,
       true, 23
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O24', 'CONDUCTUAL', 'CONDUCTUAL',
       'Última mejora útil que hiciste sin que te la pidieran.', NULL, NULL,
       true, 24
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O25', 'CONDUCTUAL', 'CONDUCTUAL',
       'Error propio y el mecanismo que creaste para no repetirlo.', NULL, NULL,
       true, 25
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O26', 'CONDUCTUAL', 'CONDUCTUAL',
       'Caso donde dependías de alguien y protegiste el deadline.', NULL, NULL,
       true, 26
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O27', 'CONDUCTUAL', 'CONDUCTUAL',
       'Algo adicional que hiciste porque el resultado final lo necesitaba.', NULL, NULL,
       true, 27
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O28', 'CONDUCTUAL', 'CONDUCTUAL',
       'Trabajo que redujiste de horas a minutos/días. ¿Cómo?', NULL, NULL,
       true, 28
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O29', 'CONDUCTUAL', 'CONDUCTUAL',
       'Error que detectaste antes de entregar gracias a tu propio control.', NULL, NULL,
       true, 29
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O30', 'CONDUCTUAL', 'CONDUCTUAL',
       'Una corrección que al inicio no compartías y luego cambió tu trabajo.', NULL, NULL,
       true, 30
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O31', 'MICROCASO', 'MICROCASO',
       'Prioridad', '5 tareas con deadlines e impacto distintos. Ordénalas y explica qué información falta.', NULL,
       true, 31
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O31';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O32', 'MICROCASO', 'MICROCASO',
       'Calidad', 'Entregable con 10 errores. Detecta y clasifica cuáles bloquean entrega.', NULL,
       true, 32
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O32';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O33', 'MICROCASO', 'MICROCASO',
       'Velocidad', 'Tarea estimada 3h debe tener demo en 45m. Define MVP.', NULL,
       true, 33
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'VEL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O33';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O33';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O34', 'MICROCASO', 'MICROCASO',
       'Herramienta nueva', '30m para aprender y producir un output útil.', NULL,
       true, 34
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O34';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O34';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O35', 'MICROCASO', 'MICROCASO',
       'Comunicación', 'Escribe mensaje preventivo de un bloqueo que puede afectar mañana.', NULL,
       true, 35
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O35';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O36', 'MICROCASO', 'MICROCASO',
       'SOP', 'Detecta pasos ambiguos/repetitivos de un procedimiento.', NULL,
       true, 36
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'CRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O36';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O36';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O37', 'MICROCASO', 'MICROCASO',
       'IA', 'Resuelve parte del caso con IA y documenta prompts, verificación y decisión propia.', NULL,
       true, 37
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O37';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O37';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O38', 'MICROCASO', 'MICROCASO',
       'Evidencia', 'Entrega una tarea de forma que otro pueda verificarla sin preguntarte.', NULL,
       true, 38
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'REL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O38';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O38';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O39', 'MICROCASO', 'MICROCASO',
       'Cliente interno', 'Tu output técnicamente cumple pero el siguiente equipo no puede usarlo. ¿Qué haces?', NULL,
       true, 39
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O39';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'OWN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O39';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O40', 'MICROCASO', 'MICROCASO',
       'Cambio inesperado', 'A mitad de tarea cambia un requisito crítico. Explica qué conservas, qué descartas y cómo comunicas.', NULL,
       true, 40
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'DEC'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O40';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O40';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O41', 'DILEMA', 'DILEMA',
       'Terminas 40 min antes.', NULL, 'D=4,C=3,B=2,A=0',
       true, 41
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'esperar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O41';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'mejorar algo ya terminado', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O41';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'preguntar prioridad siguiente', 3
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O41';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'buscar una mejora útil y luego confirmar', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O41';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O41';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'PRI'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O41';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O42', 'DILEMA', 'DILEMA',
       'Una tarea tarda siempre 20 min y se repite 3 veces por día.', NULL, 'B=4,D=2,C=1,A=0',
       true, 42
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'seguir', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O42';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'proponer plantilla/automatización', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O42';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'pedir que otro la haga', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O42';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'hacerlo más rápido manualmente', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O42';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SYS'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O42';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'VEL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O42';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O43', 'DILEMA', 'DILEMA',
       'No sabes usar una herramienta necesaria hoy.', NULL, 'C=4,B=1,A=1,D=0',
       true, 43
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'decir no sé', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O43';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'pedir curso', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O43';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'aprender mínimo, probar en entorno seguro y pedir ayuda específica', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O43';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'improvisar directo en producción', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O43';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'LRN'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O43';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O43';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O44', 'DILEMA', 'DILEMA',
       'Recibes feedback contradictorio de dos personas.', NULL, 'C=4,B=1,A=0,D=0',
       true, 44
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'elegir mayor rango', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O44';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'intentar satisfacer ambos', 1
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O44';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'aclarar owner/criterio final', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O44';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'ignorar uno', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O44';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O44';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'REL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O44';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O45', 'DILEMA', 'DILEMA',
       'La tarea está al 90% pero falta evidencia.', NULL, 'C=4,B=2,A=0,D=0',
       true, 45
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'A', 'cerrar', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O45';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'B', 'subir como parcial', 2
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O45';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'C', 'completar evidencia antes de done', 4
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O45';
INSERT INTO opcion (pregunta_id, letra, texto, puntaje)
SELECT p.id, 'D', 'pedir que alguien valide sin evidencia', 0
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O45';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O45';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'REL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O45';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O46', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué te resulta más difícil: empezar sin instrucciones completas o recibir muchas correcciones? Explica.', NULL, 'Fit/autonomía; advisory.',
       false, 46
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'FIT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O46';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O47', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Qué tipo de tarea te agota más aunque seas capaz de hacerla?', NULL, 'Role-content fit; no HPI.',
       false, 47
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'FIT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O47';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O48', 'CONSISTENCIA', 'CONSISTENCIA',
       'Si tu trabajo se mide todos los días con evidencia, ¿qué ventaja y qué riesgo ves?', NULL, 'Madurez hacia accountability.',
       false, 48
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'REL'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O48';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'INT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O48';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O49', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Cuándo pedir ayuda temprano demuestra fortaleza y cuándo dependencia?', NULL, 'Judgment.',
       false, 49
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'AUT'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O49';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'COM'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O49';

INSERT INTO pregunta (version_banco_id, codigo, bloque, tipo, enunciado, situacion, logica_interna, es_puntuable, orden)
SELECT id, 'O50', 'CONSISTENCIA', 'CONSISTENCIA',
       '¿Cuándo consideras que una tarea está realmente terminada?', NULL, 'Esperado: criterion/outcome/evidence, no solo esfuerzo.',
       false, 50
FROM version_banco WHERE etiqueta = 'Banco Ejecución V0.1';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'QUA'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O50';
INSERT INTO pregunta_dimension (pregunta_id, dimension_codigo)
SELECT p.id, 'SER'
FROM pregunta p JOIN version_banco v ON v.id = p.version_banco_id
WHERE v.etiqueta = 'Banco Ejecución V0.1' AND p.codigo = 'O50';

-- ================= Pesos por dimensión =================
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'BUS', 10.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'COM', 10.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'CRI', 12.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'CTL', 12.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'DEC', 6.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'INT', 10.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'LRN', 3.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'OWN', 10.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'PPL', 5.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'PRI', 6.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'SER', 3.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'SUP', 5.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'SYS', 3.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'DIRECCION', 'VEL', 3.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'AUT', 3.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'COM', 12.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'CTL', 18.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'INI', 3.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'OWN', 6.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'PRI', 12.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'QUA', 8.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'REL', 6.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'SER', 6.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'SUP', 15.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'SUPERVISION', 'SYS', 10.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'AUT', 5.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'COM', 10.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'INI', 5.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'LRN', 15.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'OWN', 12.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'PRI', 8.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'QUA', 18.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'REL', 15.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'SER', 7.00 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'SYS', 2.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';
INSERT INTO peso_dimension (version_pesos_id, nivel_puesto_codigo, dimension_codigo, peso)
SELECT id, 'EJECUCION', 'VEL', 2.50 FROM version_pesos WHERE etiqueta = 'v2 hito 2';

