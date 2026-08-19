-- Hito 2 · Fase B. Permiso de lectura que faltó en V12: separado de editar_banco_preguntas
-- por el mismo criterio que ver_vacantes/crear_vacante en el hito 1 (ver ≠ escribir).
INSERT INTO permiso (codigo, etiqueta, grupo, orden) VALUES
    ('ver_banco_preguntas', 'Ver el banco de preguntas', 'CONFIGURACION', 11);

INSERT INTO rol_permiso (rol_id, permiso_id, alcance)
SELECT r.id, p.id, 'TODO'
FROM rol r
JOIN permiso p ON p.codigo = 'ver_banco_preguntas'
WHERE r.organizacion_id = (SELECT id FROM organizacion WHERE codigo = 'RENASER')
  AND r.codigo IN ('TALENTO', 'DIRECCION');
