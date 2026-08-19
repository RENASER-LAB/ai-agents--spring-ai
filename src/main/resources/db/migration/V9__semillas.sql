-- Datos iniciales del hito 1. Todo lo de aquí es configuración, no transacciones:
-- catálogos cerrados, la organización, roles y permisos, parámetros y plantillas.

-- digest() para el hash del consentimiento. gen_random_uuid() es nativo desde PG 13.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============ Organización ============
INSERT INTO organizacion (codigo, nombre) VALUES ('RENASER', 'Clínica Renaser S.A.C.');

-- ============ Catálogos cerrados ============
INSERT INTO nivel_puesto (codigo, nombre, preguntas_banco, minutos_objetivo_min, minutos_objetivo_max, orden) VALUES
    ('DIRECCION',   'Dirección',   90, 40, 50, 1),
    ('SUPERVISION', 'Supervisión', 60, 35, 45, 2),
    ('EJECUCION',   'Ejecución',   50, 25, 35, 3);

INSERT INTO familia (codigo, nombre, orden) VALUES
    ('DIRECCION_NEGOCIO', 'Dirección de negocio', 1),
    ('OPERACIONES',       'Operaciones',          2),
    ('CRECIMIENTO',       'Crecimiento',          3),
    ('TECNOLOGIA',        'Tecnología',           4),
    ('CREATIVO',          'Creativo',             5),
    ('TALENTO',           'Talento',              6),
    ('SEGURIDAD_CRITICA', 'Seguridad crítica',    7);

INSERT INTO etapa (codigo, nombre, orden) VALUES
    ('PERFIL_INTEGRAL', 'Perfil Integral de Preselección', 1),
    ('PRUEBA_PUESTO',   'Prueba del puesto',               2),
    ('SIMULACION',      'Simulación de trabajo',           3),
    ('VALIDACION',      'Validación práctica',             4),
    ('DECISION',        'Decisión',                        5);

-- ============ Los 18 estados ============
-- La forma es una rejilla: etapa x momento. El estado siguiente se calcula, no se busca.
-- orden es el recorrido normal, para mostrar. La decisión se entra por POR_CONFIRMAR:
-- su TURNO_CANDIDATO solo existe para el ámbar (hito 3) y por eso va después en el orden.
INSERT INTO estado_postulacion (codigo, nombre, etapa_codigo, momento_codigo, espera_a, orden, es_final) VALUES
    ('POSTULADA',                  'Postulada',                          NULL,              NULL,              'SISTEMA',   1,  false),
    ('PERFIL_TURNO_CANDIDATO',     'Perfil Integral · turno del candidato', 'PERFIL_INTEGRAL', 'TURNO_CANDIDATO', 'CANDIDATO', 2,  false),
    ('PERFIL_CALIFICANDO',         'Perfil Integral · calificando',      'PERFIL_INTEGRAL', 'CALIFICANDO',     'SISTEMA',   3,  false),
    ('PERFIL_POR_CONFIRMAR',       'Perfil Integral · por confirmar',    'PERFIL_INTEGRAL', 'POR_CONFIRMAR',   'TALENTO',   4,  false),
    ('PRUEBA_TURNO_CANDIDATO',     'Prueba · turno del candidato',       'PRUEBA_PUESTO',   'TURNO_CANDIDATO', 'CANDIDATO', 5,  false),
    ('PRUEBA_CALIFICANDO',         'Prueba · calificando',               'PRUEBA_PUESTO',   'CALIFICANDO',     'SISTEMA',   6,  false),
    ('PRUEBA_POR_CONFIRMAR',       'Prueba · por confirmar',             'PRUEBA_PUESTO',   'POR_CONFIRMAR',   'TALENTO',   7,  false),
    ('SIMULACION_POR_HABILITAR',   'Simulación · por habilitar',         'SIMULACION',      'POR_HABILITAR',   'TALENTO',   8,  false),
    ('SIMULACION_TURNO_CANDIDATO', 'Simulación · turno del candidato',   'SIMULACION',      'TURNO_CANDIDATO', 'CANDIDATO', 9,  false),
    ('SIMULACION_POR_CONFIRMAR',   'Simulación · por confirmar',         'SIMULACION',      'POR_CONFIRMAR',   'TALENTO',   10, false),
    ('VALIDACION_POR_HABILITAR',   'Validación · por habilitar',         'VALIDACION',      'POR_HABILITAR',   'TALENTO',   11, false),
    ('VALIDACION_TURNO_CANDIDATO', 'Validación · turno del candidato',   'VALIDACION',      'TURNO_CANDIDATO', 'CANDIDATO', 12, false),
    ('VALIDACION_POR_CONFIRMAR',   'Validación · por confirmar',         'VALIDACION',      'POR_CONFIRMAR',   'AREA',      13, false),
    ('DECISION_POR_CONFIRMAR',     'Decisión · por confirmar',           'DECISION',        'POR_CONFIRMAR',   'AREA',      14, false),
    ('DECISION_TURNO_CANDIDATO',   'Decisión · turno del candidato',     'DECISION',        'TURNO_CANDIDATO', 'CANDIDATO', 15, false),
    ('CONTRATADO',                 'Contratado',                         NULL,              NULL,              'NADIE',     16, true),
    ('NO_CONTINUA',                'No continúa',                        NULL,              NULL,              'NADIE',     17, true),
    ('CERRADA',                    'Cerrada',                            NULL,              NULL,              'NADIE',     18, true);

-- ============ Versión de pesos v1 ============
-- Publicada desde el primer día aunque dos etapas vayan a cero en el MVP: cada nota
-- queda atada a la versión con que se calculó, y cambiar pesos es crear otra versión.
INSERT INTO version_pesos (organizacion_id, etiqueta, estado, publicada_en)
SELECT id, 'v1 inicial', 'PUBLICADA', now() FROM organizacion WHERE codigo = 'RENASER';

INSERT INTO peso_etapa (version_pesos_id, etapa_codigo, peso)
SELECT vp.id, e.codigo, e.peso::numeric
FROM version_pesos vp,
     (VALUES ('PERFIL_INTEGRAL', 40), ('PRUEBA_PUESTO', 30), ('SIMULACION', 15), ('VALIDACION', 15))
         AS e(codigo, peso)
WHERE vp.etiqueta = 'v1 inicial';

INSERT INTO peso_componente_perfil (version_pesos_id, componente, peso)
SELECT vp.id, c.componente, c.peso::numeric
FROM version_pesos vp,
     (VALUES ('CV', 10), ('PSICOMETRICO', 5), ('EVALUACION', 25)) AS c(componente, peso)
WHERE vp.etiqueta = 'v1 inicial';

-- ============ Permisos del hito 1 ============
-- En lenguaje normal, como manda el doc 04. El catálogo solo crece con migraciones:
-- los permisos de evaluación con IA, simulación y validación llegan con sus hitos.
INSERT INTO permiso (codigo, etiqueta, grupo, orden) VALUES
    ('crear_solicitud',                'Registrar una Solicitud de Talento',              'SOLICITUDES',   1),
    ('ver_solicitudes',                'Ver las Solicitudes de Talento',                  'SOLICITUDES',   2),
    ('aprobar_solicitud',              'Aprobar o rechazar una Solicitud de Talento',     'SOLICITUDES',   3),
    ('ver_vacantes',                   'Ver las vacantes',                                'VACANTES',      1),
    ('crear_vacante',                  'Crear una vacante',                               'VACANTES',      2),
    ('editar_vacante',                 'Editar una vacante',                              'VACANTES',      3),
    ('publicar_vacante',               'Publicar una vacante',                            'VACANTES',      4),
    ('cerrar_vacante',                 'Cerrar una vacante',                              'VACANTES',      5),
    ('definir_requisitos_objetivos',   'Definir los requisitos objetivos indispensables', 'VACANTES',      6),
    ('elegir_version_pesos',           'Elegir qué versión de pesos rige una vacante',    'VACANTES',      7),
    ('ver_candidatos',                 'Ver la lista de candidatos',                      'CANDIDATOS',    1),
    ('abrir_ficha_candidato',          'Abrir la ficha completa de un candidato',         'CANDIDATOS',    2),
    ('ver_cv_completo',                'Ver el currículum sin ocultar datos',             'CANDIDATOS',    3),
    ('descargar_entregables',          'Descargar los entregables de un candidato',       'CANDIDATOS',    4),
    ('confirmar_avance',               'Confirmar que un candidato avanza',               'EVALUACION',    1),
    ('postular_vacante',               'Postular a una vacante',                          'CIERRE',        1),
    ('retirar_postulacion',            'Retirar su propia postulación',                   'CIERRE',        2),
    ('retirar_consentimiento_futuros', 'Retirar su consentimiento de futuros contactos',  'CIERRE',        3),
    ('mover_postulacion',              'Mover una postulación a otro estado',             'CIERRE',        4),
    ('cerrar_postulacion',             'Cerrar una postulación a mano',                   'CIERRE',        5),
    ('reabrir_postulacion',            'Reabrir una postulación cerrada',                 'CIERRE',        6),
    ('decidir_postulaciones_al_cerrar','Decidir qué pasa con las postulaciones al cerrar la vacante', 'CIERRE', 7),
    ('pedir_borrado_datos',            'Pedir el borrado de sus datos',                   'CIERRE',        8),
    ('ejecutar_borrado_datos',         'Ejecutar el borrado de datos de una persona',     'CIERRE',        9),
    ('ver_embudo',                     'Ver el embudo de una vacante',                    'METRICAS',      1),
    ('editar_textos_correo',           'Editar los textos de correo',                     'CONFIGURACION', 1),
    ('editar_parametros',              'Editar los parámetros del sistema',               'CONFIGURACION', 2),
    ('ver_auditoria',                  'Ver el registro de auditoría',                    'CONFIGURACION', 3),
    ('crear_usuarios_y_asignar_roles', 'Crear usuarios y asignar roles',                  'CONFIGURACION', 4),
    ('crear_roles',                    'Crear roles nuevos',                              'CONFIGURACION', 5);

-- ============ Los cinco roles de sistema ============
INSERT INTO rol (organizacion_id, codigo, nombre, es_sistema)
SELECT o.id, r.codigo, r.nombre, true
FROM organizacion o,
     (VALUES ('CANDIDATO',        'Candidato'),
             ('TALENTO',          'Equipo de Talento'),
             ('RESPONSABLE_AREA', 'Responsable del área'),
             ('DIRECCION',        'Dirección'),
             ('ADMINISTRADOR',    'Administrador')) AS r(codigo, nombre)
WHERE o.codigo = 'RENASER';

-- ============ Qué puede cada rol, y con qué alcance ============
-- Derivado de las matrices del doc 04. Regla: Talento prepara, Dirección aprueba,
-- Administrador administra. El candidato solo lo suyo.
INSERT INTO rol_permiso (rol_id, permiso_id, alcance)
SELECT r.id, p.id, x.alcance
FROM (VALUES
    -- Candidato: solo sus propias cosas. Las vacantes publicadas son públicas.
    ('CANDIDATO', 'ver_vacantes',                   'TODO'),
    ('CANDIDATO', 'postular_vacante',               'PROPIO'),
    ('CANDIDATO', 'retirar_postulacion',            'PROPIO'),
    ('CANDIDATO', 'retirar_consentimiento_futuros', 'PROPIO'),
    ('CANDIDATO', 'pedir_borrado_datos',            'PROPIO'),
    ('CANDIDATO', 'abrir_ficha_candidato',          'PROPIO'),
    ('CANDIDATO', 'ver_cv_completo',                'PROPIO'),
    -- Equipo de Talento: la operación completa, sin lo que aprueba Dirección
    ('TALENTO', 'crear_solicitud',                 'TODO'),
    ('TALENTO', 'ver_solicitudes',                 'TODO'),
    ('TALENTO', 'ver_vacantes',                    'TODO'),
    ('TALENTO', 'crear_vacante',                   'TODO'),
    ('TALENTO', 'editar_vacante',                  'TODO'),
    ('TALENTO', 'publicar_vacante',                'TODO'),
    ('TALENTO', 'cerrar_vacante',                  'TODO'),
    ('TALENTO', 'definir_requisitos_objetivos',    'TODO'),
    ('TALENTO', 'ver_candidatos',                  'TODO'),
    ('TALENTO', 'abrir_ficha_candidato',           'TODO'),
    ('TALENTO', 'ver_cv_completo',                 'TODO'),
    ('TALENTO', 'descargar_entregables',           'TODO'),
    ('TALENTO', 'confirmar_avance',                'TODO'),
    ('TALENTO', 'mover_postulacion',               'TODO'),
    ('TALENTO', 'cerrar_postulacion',              'TODO'),
    ('TALENTO', 'reabrir_postulacion',             'TODO'),
    ('TALENTO', 'decidir_postulaciones_al_cerrar', 'TODO'),
    ('TALENTO', 'ver_embudo',                      'TODO'),
    ('TALENTO', 'editar_textos_correo',            'TODO'),
    -- Responsable del área: solo lo de sus vacantes
    ('RESPONSABLE_AREA', 'crear_solicitud',                 'PROPIO'),
    ('RESPONSABLE_AREA', 'ver_solicitudes',                 'SUS_VACANTES'),
    ('RESPONSABLE_AREA', 'ver_vacantes',                    'TODO'),
    ('RESPONSABLE_AREA', 'definir_requisitos_objetivos',    'SUS_VACANTES'),
    ('RESPONSABLE_AREA', 'ver_candidatos',                  'SUS_VACANTES'),
    ('RESPONSABLE_AREA', 'abrir_ficha_candidato',           'SUS_VACANTES'),
    ('RESPONSABLE_AREA', 'ver_cv_completo',                 'SUS_VACANTES'),
    ('RESPONSABLE_AREA', 'descargar_entregables',           'SUS_VACANTES'),
    ('RESPONSABLE_AREA', 'decidir_postulaciones_al_cerrar', 'SUS_VACANTES'),
    ('RESPONSABLE_AREA', 'ver_embudo',                      'SUS_VACANTES'),
    -- Dirección: todo lo de Talento más lo que solo ella aprueba
    ('DIRECCION', 'crear_solicitud',                 'TODO'),
    ('DIRECCION', 'ver_solicitudes',                 'TODO'),
    ('DIRECCION', 'aprobar_solicitud',               'TODO'),
    ('DIRECCION', 'ver_vacantes',                    'TODO'),
    ('DIRECCION', 'crear_vacante',                   'TODO'),
    ('DIRECCION', 'editar_vacante',                  'TODO'),
    ('DIRECCION', 'publicar_vacante',                'TODO'),
    ('DIRECCION', 'cerrar_vacante',                  'TODO'),
    ('DIRECCION', 'definir_requisitos_objetivos',    'TODO'),
    ('DIRECCION', 'elegir_version_pesos',            'TODO'),
    ('DIRECCION', 'ver_candidatos',                  'TODO'),
    ('DIRECCION', 'abrir_ficha_candidato',           'TODO'),
    ('DIRECCION', 'ver_cv_completo',                 'TODO'),
    ('DIRECCION', 'descargar_entregables',           'TODO'),
    ('DIRECCION', 'confirmar_avance',                'TODO'),
    ('DIRECCION', 'mover_postulacion',               'TODO'),
    ('DIRECCION', 'cerrar_postulacion',              'TODO'),
    ('DIRECCION', 'reabrir_postulacion',             'TODO'),
    ('DIRECCION', 'decidir_postulaciones_al_cerrar', 'TODO'),
    ('DIRECCION', 'ejecutar_borrado_datos',          'TODO'),
    ('DIRECCION', 'ver_embudo',                      'TODO'),
    ('DIRECCION', 'editar_textos_correo',            'TODO'),
    ('DIRECCION', 'editar_parametros',               'TODO'),
    ('DIRECCION', 'ver_auditoria',                   'TODO'),
    -- Administrador: administra, no opera el proceso
    ('ADMINISTRADOR', 'ver_auditoria',                   'TODO'),
    ('ADMINISTRADOR', 'editar_parametros',               'TODO'),
    ('ADMINISTRADOR', 'ejecutar_borrado_datos',          'TODO'),
    ('ADMINISTRADOR', 'crear_usuarios_y_asignar_roles',  'TODO'),
    ('ADMINISTRADOR', 'crear_roles',                     'TODO')
) AS x(rol_codigo, permiso_codigo, alcance)
JOIN rol r ON r.codigo = x.rol_codigo
JOIN permiso p ON p.codigo = x.permiso_codigo
WHERE r.organizacion_id = (SELECT id FROM organizacion WHERE codigo = 'RENASER');

-- ============ Parámetros iniciales ============
INSERT INTO parametro (organizacion_id, codigo, valor, tipo, descripcion)
SELECT o.id, p.codigo, p.valor, p.tipo, p.descripcion
FROM organizacion o,
     (VALUES
        ('dias_inactividad_cierre', '60', 'ENTERO',
         'Días sin avanzar antes de que una postulación se cierre sola'),
        ('tope_rondas_evidencia', '2', 'ENTERO',
         'Cuántas veces puede pedirse evidencia adicional cuando la decisión sale ámbar'),
        ('max_mb_cv', '10', 'ENTERO',
         'Tamaño máximo del currículum, en megabytes'),
        ('intentos_login_max', '5', 'ENTERO',
         'Intentos de entrada fallidos seguidos antes de bloquear temporalmente'),
        ('minutos_bloqueo_login', '15', 'ENTERO',
         'Cuántos minutos dura el bloqueo tras agotar los intentos'),
        ('datos_ocultos_cv', 'nombre,apellidos,correo,telefono,documento,fecha_nacimiento,direccion,foto', 'LISTA',
         'Qué datos se ocultan del currículum antes de mandárselo a la máquina')
     ) AS p(codigo, valor, tipo, descripcion)
WHERE o.codigo = 'RENASER';

-- ============ Textos de consentimiento v1.0 ============
-- Texto provisional: el definitivo lo aprueba Renaser (bloquea producción, no desarrollo).
-- El hash se calcula sobre el texto exacto: si alguien lo cambiara a mano, no cuadraría.
INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
SELECT o.id, t.tipo, '1.0', t.texto, encode(digest(t.texto, 'sha256'), 'hex'), now()
FROM organizacion o,
     (VALUES
        ('PROCESO',
         'Acepto que mis datos personales y mis respuestas se usen para evaluar mi postulación a esta vacante. Una inteligencia artificial participa en la evaluación y una persona revisa y confirma las decisiones. Puedo pedir el borrado de mis datos en cualquier momento. [TEXTO PROVISIONAL: pendiente de aprobación legal por Renaser]'),
        ('FUTUROS_CONTACTOS',
         'Acepto que Renaser conserve mis datos para contactarme por futuras oportunidades laborales. Este permiso es independiente de mi postulación actual y puedo retirarlo en cualquier momento sin que afecte al proceso en curso. [TEXTO PROVISIONAL: pendiente de aprobación legal por Renaser]')
     ) AS t(tipo, texto)
WHERE o.codigo = 'RENASER';

-- ============ Plantillas de correo v1 ============
-- Las variables van entre dobles llaves y se reemplazan al armar el correo. El correo
-- armado queda guardado en correo_enviado tal como salió.
INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa)
SELECT o.id, p.codigo, 1, p.asunto, p.cuerpo, true
FROM organizacion o,
     (VALUES
        ('CUENTA_CREADA', 'Tu cuenta en el portal de Talento de Renaser',
         'Hola {{nombre}}:' || E'\n\n' || 'Tu cuenta quedó creada. Desde tu panel puedes seguir el estado de tus postulaciones en todo momento.' || E'\n\n' || 'Equipo de Talento · Renaser'),
        ('POSTULACION_RECIBIDA', 'Recibimos tu postulación a {{vacante}}',
         'Hola {{nombre}}:' || E'\n\n' || 'Tu postulación a «{{vacante}}» quedó registrada con el código {{codigo}}. Te avisaremos por este medio cada vez que avance.' || E'\n\n' || 'Equipo de Talento · Renaser'),
        ('POSTULACION_AVANZA', 'Tu postulación a {{vacante}} avanza',
         'Hola {{nombre}}:' || E'\n\n' || 'Tu postulación a «{{vacante}}» pasó a la siguiente parte del proceso: {{estado}}. Entra a tu panel para ver qué sigue.' || E'\n\n' || 'Equipo de Talento · Renaser'),
        ('POSTULACION_NO_CONTINUA', 'Sobre tu postulación a {{vacante}}',
         'Hola {{nombre}}:' || E'\n\n' || 'Gracias por participar en el proceso de «{{vacante}}». En esta ocasión tu postulación no continúa. Valoramos el tiempo que dedicaste y nos gustaría contar contigo en futuras convocatorias.' || E'\n\n' || 'Equipo de Talento · Renaser'),
        ('POSTULACION_CERRADA', 'Tu postulación a {{vacante}} se cerró',
         'Hola {{nombre}}:' || E'\n\n' || 'Tu postulación a «{{vacante}}» quedó cerrada. Si crees que es un error, responde a este correo.' || E'\n\n' || 'Equipo de Talento · Renaser'),
        ('RETIRO_CONFIRMADO', 'Retiraste tu postulación a {{vacante}}',
         'Hola {{nombre}}:' || E'\n\n' || 'Confirmamos que retiraste tu postulación a «{{vacante}}». Tus datos se conservan según la política que aceptaste; puedes pedir su borrado cuando quieras.' || E'\n\n' || 'Equipo de Talento · Renaser'),
        ('BORRADO_EJECUTADO', 'Tus datos fueron eliminados',
         'Hola:' || E'\n\n' || 'Como pediste, tus datos personales fueron eliminados de nuestro sistema de selección. El registro anónimo de las decisiones se conserva, como exige la trazabilidad del proceso.' || E'\n\n' || 'Equipo de Talento · Renaser')
     ) AS p(codigo, asunto, cuerpo)
WHERE o.codigo = 'RENASER';
