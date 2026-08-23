-- La vacante fija cuándo cierra su prueba, y una persona puede tener su propia fecha
-- (22/08/2026).
--
-- POR QUE
-- -------
-- El plazo de la prueba vivia solo en la version de la plantilla, en DIAS, y contados desde
-- que cada candidato entra. Dos personas invitadas el mismo lunes cerraban en dias
-- distintos, asi que no habia forma de decir «esta convocatoria cierra el domingo». Y el
-- plazo de una version publicada no se edita —ni debe: hay gente calificada con ella—, de
-- modo que cambiarlo obligaba a crear una version nueva, publicarla y reasignarla.
--
-- LAS DOS COLUMNAS, Y POR QUE SON DOS
-- -----------------------------------
-- `vacante.prueba_cierra_en` es la fecha de la convocatoria: la que se le aplica a todos.
--
-- `intento_prueba.plazo_propio` marca a quien se le puso una fecha distinta a mano —el que
-- pidio mas horas—. Sin esa marca no se podria distinguir su fecha de la heredada, y al
-- mover la de la convocatoria se le borraria la suya sin que nadie se enterara. Con ella, la
-- fecha de la vacante se propaga a los intentos abiertos y salta a los que tienen la suya.
--
-- El plazo efectivo sigue viviendo en `intento_prueba.vence_en`, que es lo que el reloj y el
-- barrido de vencidos ya miran: esto no cambia como se lee, solo quien lo escribe.
ALTER TABLE vacante
    ADD COLUMN prueba_cierra_en timestamptz;

COMMENT ON COLUMN vacante.prueba_cierra_en IS
    'Cuando cierra la prueba de esta vacante, para todos. Vacio: se cuentan los dias de la '
    'version de la plantilla desde que cada candidato empieza, como siempre.';

ALTER TABLE intento_prueba
    ADD COLUMN plazo_propio boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN intento_prueba.plazo_propio IS
    'A esta persona se le fijo su propia fecha de cierre. Cambiar la de la vacante no se la '
    'toca: por eso «mas horas para este candidato» no se pierde al mover la convocatoria.';
