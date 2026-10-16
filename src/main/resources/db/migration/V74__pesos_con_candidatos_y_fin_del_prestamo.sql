-- ============================================================================
-- V74 · El fin del préstamo del banco y de las pruebas, y el enlace del enunciado que
-- nadie usa (specs/pesos-con-candidatos-y-fin-del-prestamo.md, partes B y C).
--
-- La parte A —cambiar los pesos con candidatos dentro— no toca el esquema: el recálculo
-- reescribe las notas que ya existen y el cambio queda en la auditoría.
--
-- ⚠️ Corre en el MISMO despliegue que el código que deja de resolver el préstamo. Sin la
-- versión fija, una vacante vieja de una empresa sin banco propio se quedaría sin examen
-- al postular: el código ya no busca el banco de RENASER.
-- ============================================================================

-- ---------- B · La versión de banco fija de las vacantes viejas ----------

ALTER TABLE vacante ADD COLUMN version_banco_fija_id bigint REFERENCES version_banco(id);

COMMENT ON COLUMN vacante.version_banco_fija_id IS
    'La versión del banco de RENASER que rinde esta vacante, fija (V74). Solo la tienen las '
    'vacantes de antes de una empresa sin banco propio que leían el de RENASER: sus '
    'candidatos nuevos rinden exactamente la misma que los de antes, aunque RENASER publique '
    'otra. Vacía en el resto: rinden su propio banco o sus preguntas propias.';

-- Las vacantes que leían el banco prestado: de una empresa que no es la plataforma, sin la
-- bandera de banco propio y con el origen del banco por nivel. En cualquier estado: una
-- archivada que se desarchiva, o una que vuelve a encender su evaluación, usa la fija.
--
-- Se ata a la versión de su evaluación más reciente si ya tenía postulantes; si no, a la
-- publicada de RENASER para su nivel en este momento, con el mismo desempate que el código
-- que la buscaba en cada postulación (`laPublicadaDelNivel`: publicada_en desc).
--
-- Un nivel sin banco publicado de RENASER no tenía examen posible: la vacante se queda sin
-- versión fija y se comporta como hasta hoy.
WITH prestadas AS (
    SELECT v.id, p.nivel_puesto_codigo AS nivel
      FROM vacante v
      JOIN organizacion o ON o.id = v.organizacion_id
      JOIN puesto p ON p.id = v.puesto_id
     WHERE NOT o.es_plataforma
       AND NOT o.banco_propio
       AND v.origen_preguntas = 'NIVEL'
),
de_su_ultima_evaluacion AS (
    SELECT DISTINCT ON (pr.id) pr.id AS vacante_id, e.version_banco_nivel_id AS version_id
      FROM prestadas pr
      JOIN postulacion po ON po.vacante_id = pr.id
      JOIN evaluacion e ON e.id = po.evaluacion_id
      JOIN version_banco vb ON vb.id = e.version_banco_nivel_id
     WHERE vb.tipo_banco = 'NIVEL'
       AND vb.vacante_id IS NULL
     ORDER BY pr.id, e.creado_en DESC, e.id DESC
),
la_publicada_de_renaser AS (
    SELECT DISTINCT ON (pr.id) pr.id AS vacante_id, vb.id AS version_id
      FROM prestadas pr
      JOIN organizacion pl ON pl.es_plataforma
      JOIN version_banco vb ON vb.organizacion_id = pl.id
                           AND vb.tipo_banco = 'NIVEL'
                           AND vb.vacante_id IS NULL
                           AND vb.nivel_puesto_codigo = pr.nivel
                           AND vb.estado = 'PUBLICADA'
     ORDER BY pr.id, vb.publicada_en DESC, vb.id DESC
),
fijas AS (
    SELECT pr.id AS vacante_id, COALESCE(ev.version_id, pu.version_id) AS version_id
      FROM prestadas pr
      LEFT JOIN de_su_ultima_evaluacion ev ON ev.vacante_id = pr.id
      LEFT JOIN la_publicada_de_renaser pu ON pu.vacante_id = pr.id
)
UPDATE vacante v
   SET version_banco_fija_id = f.version_id
  FROM fijas f
 WHERE v.id = f.vacante_id
   AND f.version_id IS NOT NULL;

-- ---------- B · Las vacantes viejas en borrador sin prueba asignada ----------

-- Una vacante vieja en borrador que iba a rendir «la prueba del puesto» y todavía no tenía
-- ninguna elegida ya no puede elegir una de RENASER: pasa a la prueba del editor, como una
-- vacante nueva (V67), y su bloque «Prueba técnica» enseña «Armar la prueba». Sin minutos
-- propios: la prueba del editor lleva su tiempo dentro.
--
-- RENASER y las empresas que ya tenían sus pruebas propias no leían nada prestado: no
-- cambian.
UPDATE vacante v
   SET instrumento_etapa_tecnica = 'PRUEBA_PROPIA',
       minutos_etapa_tecnica = NULL
  FROM organizacion o
 WHERE o.id = v.organizacion_id
   AND NOT o.es_plataforma
   AND NOT o.pruebas_puesto_propias
   AND v.estado = 'BORRADOR'
   AND v.instrumento_etapa_tecnica = 'PLANTILLA'
   AND v.version_plantilla_prueba_id IS NULL;

-- ---------- B · Las banderas que nadie lee ----------

-- Se quedan las columnas y no se borra ningún valor: el código dejó de leerlas, y quitarlas
-- no da nada que valga lo que cuesta deshacerlo. El banco y las pruebas de una empresa son
-- siempre los suyos.
COMMENT ON COLUMN organizacion.banco_propio IS
    'Sin uso desde la V74: el banco de una empresa es siempre el suyo y el de RENASER ya no '
    'se presta. Se conserva como registro de quién lo había personalizado.';
COMMENT ON COLUMN organizacion.pruebas_puesto_propias IS
    'Sin uso desde la V74: las pruebas de una empresa son siempre las suyas y las de RENASER '
    'ya no se prestan. Se conserva como registro de quién las había personalizado.';

-- ---------- C · El enlace largo del enunciado del editor ----------

-- El editor dejaba aquí un enlace firmado de 180 días al PDF del enunciado, pensado para un
-- correo que ya no lo lleva. Nadie lo lee: el panel y el portal abren el archivo con un
-- enlace fresco. Se vacía para que no quede un enlace con caducidad esperando a que alguien
-- lo vuelva a usar. La columna se queda, sin escribirse.
--
-- ⚠️ Solo la de `version_banco`. La de `version_plantilla_prueba` no se toca: allí no hay
-- archivo, y ese enlace es la única pista de dónde está el PDF de las plantillas viejas.
UPDATE version_banco SET url_consigna = NULL WHERE url_consigna IS NOT NULL;

COMMENT ON COLUMN version_banco.url_consigna IS
    'Sin uso desde la V74: el enunciado adjunto se abre por consigna_archivo_id con un enlace '
    'fresco. Ni se escribe ni se lee.';
