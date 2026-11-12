-- Los dos textos que seguían hablando del plazo del Perfil Integral (11/11/2026).
--
-- Desde la V78 la evaluación del Perfil Integral no vence por días: dura hasta que la vacante se
-- cierra. Quedaron dos textos de antes que lo contradicen, y los dos se dejaron fuera de esa
-- entrega a propósito, para decidirlos aparte:
--
--   1. El correo RECORDATORIO_EVALUACION dice «puedes empezar ahora y terminarla después, siempre
--      antes de que venza». El Perfil Integral ya no vence; la fecha, cuando la hay (el cuestionario
--      técnico de CAZATALENTOS), ya la dice {{aviso}}: «Tu evaluación para X vence el …». Se quita
--      la coletilla y la frase vale para los dos.
--   2. El texto legal PLATAFORMA 1.2 (V73) dice que el sistema «cierra mi proceso si se me pasa el
--      plazo que tenía para responder una evaluación o una prueba». Se publica la 1.3 con lo mismo
--      que dice ya la política pública del portal: «si la vacante se cierra antes de que termine mi
--      evaluación, o si se me pasa el plazo que tenía para dar una prueba».


-- ============================================================================
-- 1 · RECORDATORIO_EVALUACION sin «siempre antes de que venza»
-- ============================================================================
-- Como siempre que se edita un texto, se publica una versión nueva y la anterior se conserva
-- desactivada: cada correo enviado apunta a la versión con la que salió.
--
-- Funciona con cualquier texto, también si alguien editó el de producción desde el panel: solo
-- toca los que llevan la coletilla, y de ellos solo esa coletilla. Alcance: RECORDATORIO_EVALUACION
-- de cada organización y los textos propios que alguna vacante use para ese aviso.

WITH en_alcance AS (
    SELECT DISTINCT ON (pc.organizacion_id, pc.codigo)
           pc.organizacion_id, pc.codigo, pc.asunto, pc.cuerpo
      FROM plantilla_correo pc
     WHERE pc.es_activa
       AND (pc.codigo = 'RECORDATORIO_EVALUACION'
            OR EXISTS (SELECT 1
                         FROM plantilla_correo_vacante pcv
                         JOIN vacante v ON v.id = pcv.vacante_id
                        WHERE pcv.aviso_codigo = 'RECORDATORIO_EVALUACION'
                          AND pcv.plantilla_codigo = pc.codigo
                          AND v.organizacion_id = pc.organizacion_id))
     ORDER BY pc.organizacion_id, pc.codigo, pc.version DESC
)
INSERT INTO plantilla_correo (organizacion_id, codigo, version, asunto, cuerpo, es_activa, creado_en)
SELECT a.organizacion_id,
       a.codigo,
       (SELECT max(t.version) + 1 FROM plantilla_correo t
         WHERE t.organizacion_id = a.organizacion_id AND t.codigo = a.codigo),
       a.asunto,
       replace(replace(a.cuerpo, ', siempre antes de que venza', ''),
               ' siempre antes de que venza', ''),
       true,
       now()
  FROM en_alcance a
 WHERE a.cuerpo LIKE '%siempre antes de que venza%';

-- Y se jubilan las activas que todavía lo dicen. Nunca la más nueva de su código: es la que se
-- acaba de publicar.
UPDATE plantilla_correo pc
   SET es_activa = false
 WHERE pc.es_activa
   AND pc.cuerpo LIKE '%siempre antes de que venza%'
   AND (pc.codigo = 'RECORDATORIO_EVALUACION'
        OR EXISTS (SELECT 1
                     FROM plantilla_correo_vacante pcv
                     JOIN vacante v ON v.id = pcv.vacante_id
                    WHERE pcv.aviso_codigo = 'RECORDATORIO_EVALUACION'
                      AND pcv.plantilla_codigo = pc.codigo
                      AND v.organizacion_id = pc.organizacion_id))
   AND pc.version < (SELECT max(t.version) FROM plantilla_correo t
                      WHERE t.organizacion_id = pc.organizacion_id AND t.codigo = pc.codigo);

-- El parámetro del segundo recordatorio sigue sirviendo a lo que sí tiene fecha.
UPDATE parametro
   SET descripcion = 'Horas antes de que venza el cuestionario técnico o la prueba en que sale el '
                     || 'segundo recordatorio. El Perfil Integral no tiene fecha (V78): solo recibe el primero'
 WHERE codigo = 'recordatorio_horas_antes_del_plazo';


-- ============================================================================
-- 2 · PLATAFORMA 1.3: el proceso se cierra cuando se cierra la vacante
-- ============================================================================
-- La 1.0, la 1.1 y la 1.2 no se tocan: quien las aceptó queda ligado a la suya y no se le pide
-- aceptar otra vez. Las cuentas nuevas firman la vigente, que es la publicada más reciente; por
-- eso la 1.3 se publica por lo menos un segundo después de la última de su tipo.
--
-- Se compone a partir de la 1.2 y solo cambia la cosa (2): todo lo demás queda palabra por
-- palabra. Si la 1.2 no dijera la frase de antes, no se publica nada.

INSERT INTO texto_consentimiento (organizacion_id, tipo, version, texto, hash, publicado_en)
SELECT t.organizacion_id, 'PLATAFORMA', '1.3', n.texto, encode(digest(n.texto, 'sha256'), 'hex'),
       greatest(now(),
                coalesce((SELECT max(prev.publicado_en) + interval '1 second'
                            FROM texto_consentimiento prev
                           WHERE prev.organizacion_id = t.organizacion_id AND prev.tipo = 'PLATAFORMA'),
                         now()))
  FROM texto_consentimiento t
  JOIN organizacion o ON o.id = t.organizacion_id
 CROSS JOIN LATERAL (SELECT replace(t.texto,
           '(2) cierra mi proceso si se me pasa el plazo que tenía para responder una evaluación o una prueba;',
           '(2) cierra mi proceso si la vacante se cierra antes de que termine mi evaluación, o si se me '
               || 'pasa el plazo que tenía para dar una prueba;') AS texto) n
 WHERE o.es_plataforma
   AND t.tipo = 'PLATAFORMA'
   AND t.version = '1.2'
   AND t.texto LIKE '%(2) cierra mi proceso si se me pasa el plazo que tenía para responder una evaluación o una prueba;%'
   AND NOT EXISTS (
       SELECT 1 FROM texto_consentimiento ya
        WHERE ya.organizacion_id = t.organizacion_id AND ya.tipo = 'PLATAFORMA' AND ya.version = '1.3');
