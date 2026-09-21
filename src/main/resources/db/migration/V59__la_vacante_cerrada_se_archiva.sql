-- Una vacante cerrada se retira de la lista de todos los días, y se puede volver a traer
-- (19/09/2026, confirmado por el cliente).
--
-- El problema es de la pantalla más usada del panel: `/admin` enseña TODAS las vacantes de la
-- empresa, y las cerradas se acumulan para siempre. A los seis meses, la mesa de trabajo son
-- cuatro convocatorias vivas debajo de treinta que ya terminaron.
--
-- Lo que NO se hace aquí, y por eso basta una sola columna:
--
--   · No se inventa un estado. Archivar no es cerrar: una archivada sigue CERRADA, con sus
--     postulaciones tal como quedaron. Meterlo en `estado` habría obligado a cada `case` del
--     sistema —la máquina de estados, el portal, el ranking— a aprender un estado nuevo que
--     no significa nada para ellos, y habría perdido el dato de cómo terminó la vacante.
--
--   · No se borra nada. Archivar es reversible: quitar la fecha devuelve la vacante a la
--     lista, CERRADA, sin reabrir ninguna postulación. La eliminación excepcional es de la
--     entrega 03 y tendrá su propia marca.
--
-- Vacío = no archivada, que es como quedan TODAS las vacantes existentes: esta migración no
-- archiva ninguna. Quién la archivó no se guarda aquí: eso es de `auditoria`, que ya anota
-- persona, fecha y acción, y duplicarlo en la fila daría dos verdades que se separan.

ALTER TABLE vacante
    ADD COLUMN archivada_en TIMESTAMPTZ;

COMMENT ON COLUMN vacante.archivada_en IS
    'Cuándo se retiró esta vacante de la lista habitual del panel. Vacío = no archivada, y '
    'es lo que trae por defecto la lista de /admin. Archivar no cambia el estado ni toca las '
    'postulaciones: la vacante sigue CERRADA y su proceso se consulta en «Archivadas». '
    'Desarchivar vuelve a dejarla vacía (V59).';

-- ============ Solo se archiva una cerrada, y la base también lo dice ============
--
-- La regla vive en el servicio, que además exige que no quede nadie en carrera. Aquí abajo
-- va la mitad que un script, una carga o un `update` a mano no pueden saltarse: archivar una
-- vacante publicada la sacaría de la lista del panel dejándola visible en el portal, o sea
-- recibiendo postulaciones que nadie mira. Es el fallo que no da ninguna señal.
--
-- «Nadie en carrera» no se puede comprobar aquí: depende de otra tabla y cambia con cada
-- postulación. Esa se queda donde puede mirarse entera, en `ServicioVacantesPanelImpl`.
ALTER TABLE vacante
    ADD CONSTRAINT vacante_archivada_solo_si_cerrada CHECK (
        archivada_en IS NULL OR estado = 'CERRADA'
    );

-- ============ El índice de la lista de todos los días ============
--
-- Parcial y no completo: la consulta que se paga veinte veces al día es «las de esta empresa
-- SIN archivar», y un índice sobre toda la columna cargaría también con las archivadas, que
-- son justo las que no se leen. No es único —una empresa archiva tantas como quiera—, así que
-- no hay ningún orden de escritura que respetar al archivar.
CREATE INDEX ix_vacante_sin_archivar
    ON vacante (organizacion_id, creado_en DESC)
 WHERE archivada_en IS NULL;
