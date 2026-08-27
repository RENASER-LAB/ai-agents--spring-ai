-- ============================================================================
-- Quién eligió cada fecha, y quién puede cambiar quién lo ve
-- ============================================================================
-- Hasta aquí el panel solo recibía el número de inscritos de una sesión. El nombre estaba
-- en la base —inscripcion_sesion -> postulacion -> usuario -> persona— pero no salía por
-- ningún endpoint, así que quien conduce una sesión no podía saber a quién esperaba ni
-- obtener las inscripcion_id que exigen /inscripciones/{id}/marcas y /asistencia.
--
-- Dos permisos nuevos y ninguna tabla nueva: el reparto ya vive en rol_permiso y el
-- FiltroIdentidad lo relee en cada petición, así que lo de abajo es un punto de partida,
-- no una decisión grabada en el código.

-- ---------- 1 · Ver quién viene a una sesión ----------
INSERT INTO permiso (codigo, etiqueta, grupo, orden) VALUES
    ('ver_inscritos_simulacion', 'Ver quién eligió cada sesión de simulación', 'SESIONES', 9);

-- ---------- 2 · Cambiar el reparto sin desplegar ----------
-- Separado a propósito de crear_usuarios_y_asignar_roles: dar un rol a alguien es una cosa,
-- redefinir lo que ese rol significa es otra bastante mayor. Quien escribe en rol_permiso
-- puede concederse todo, así que sigue la regla del doc 04 —el Administrador administra, no
-- opera el proceso— y arranca solo en ese rol.
-- Orden 12 y no 6, que es donde le tocaría por parentesco con crear_roles: el 6 ya lo ocupa
-- editar_banco_preguntas desde la V12. La columna no tiene único y nada fallaría, pero dos
-- permisos con el mismo orden salen en el panel en el orden que quiera la base, y el grupo
-- CONFIGURACION ya arrastra una colisión así en el 11.
INSERT INTO permiso (codigo, etiqueta, grupo, orden) VALUES
    ('administrar_permisos', 'Cambiar qué puede cada rol y con qué alcance', 'CONFIGURACION', 12);

-- ---------- 3 · El reparto inicial ----------
INSERT INTO rol_permiso (rol_id, permiso_id, alcance)
SELECT r.id, p.id, x.alcance
FROM (VALUES
    -- Talento y Dirección operan la simulación entera: ven la lista completa
    ('TALENTO',          'ver_inscritos_simulacion', 'TODO'),
    ('DIRECCION',        'ver_inscritos_simulacion', 'TODO'),
    -- El responsable de área, solo los inscritos de sus vacantes. Mismo alcance que ya
    -- tiene en marcar_eventos_simulacion, que es justo lo que va a hacer con esta lista:
    -- sin ella no puede llegar a las inscripcion_id que ese endpoint le pide.
    ('RESPONSABLE_AREA', 'ver_inscritos_simulacion', 'SUS_VACANTES'),
    -- Administrar el reparto
    ('ADMINISTRADOR',    'administrar_permisos',     'TODO')
) AS x(rol_codigo, permiso_codigo, alcance)
JOIN rol r ON r.codigo = x.rol_codigo
JOIN permiso p ON p.codigo = x.permiso_codigo
WHERE r.organizacion_id = (SELECT id FROM organizacion WHERE codigo = 'RENASER');
