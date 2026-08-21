-- Poder corregir el correo o el telefono que la IA saco mal de un curriculum.
--
-- QUE PASABA. Los datos de contacto de la ficha los escribe un agente leyendo el PDF, y a
-- veces la arroba se pierde en la maquetacion: «ariana_tineousmp.pe» en vez de una direccion
-- de verdad. Esa candidata queda invisible para el sistema de avisos —el correo se registra
-- como NO_ENVIADO y ella no se entera de nada— y NO habia forma de arreglarlo: el unico
-- sitio del codigo que escribe `dato_cv` es el propio agente. Ni el panel ni ningun endpoint
-- podian tocarlo, asi que la unica salida era entrar a la base a mano.
--
-- Hoy son 9 candidatos de 116 en la convocatoria de arquitectura e ingenieria civil. No es
-- un caso raro: leer una arroba de un PDF falla cada tantos curriculums, y va a seguir
-- pasando con cada carpeta nueva.
--
-- Va como permiso propio y no colgado de `ajustar_nota` porque son cosas distintas: una
-- cambia lo que el candidato vale, la otra corrige un dato suyo mal transcrito. Quien puede
-- lo uno no tiene por que poder lo otro.
INSERT INTO permiso (codigo, etiqueta, grupo, orden) VALUES
    ('corregir_contacto_candidato',
     'Corregir el correo o el telefono mal leidos de un curriculum',
     'EVALUACION', 10);

-- A los mismos que ya abren la ficha y ajustan notas. No a quien solo mira.
INSERT INTO rol_permiso (rol_id, permiso_id, alcance)
SELECT r.id, p.id, 'TODO'
FROM rol r
JOIN permiso p ON p.codigo = 'corregir_contacto_candidato'
WHERE r.organizacion_id = (SELECT id FROM organizacion WHERE codigo = 'RENASER')
  AND r.codigo IN ('TALENTO', 'DIRECCION');
