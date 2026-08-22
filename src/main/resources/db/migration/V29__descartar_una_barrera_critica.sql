-- Poder deshacer una barrera critica confirmada por error.
--
-- QUE PASABA. `barrera_detectada` tiene `descartada_en` desde la V15 y NADIE lo escribia
-- nunca: el controlador solo permite registrar barreras, y ningun servicio tocaba esa
-- columna. Mientras una barrera confirmada solo servia para informar, daba igual.
--
-- Desde el 22/08/2026 ya no da igual: contratar con una barrera critica confirmada devuelve
-- 409 —«ningun promedio alto la tapa», RF-115—, asi que una barrera puesta por equivocacion
-- deja al candidato sin poder ser contratado y sin ninguna forma de deshacerlo desde el
-- panel. La unica salida era entrar a la base a mano, que es justo lo que no puede ser la
-- salida cuando hay una persona esperando una oferta.
--
-- POR QUE DOS COLUMNAS Y NO SOLO LA FECHA. Descartar no es un detalle administrativo:
-- levanta el unico bloqueo que el sistema no deja saltarse. Una barrera que desaparece sin
-- firma es indistinguible de una que nunca existio, y seis meses despues nadie puede
-- responder quien la quito ni con que argumento. Autor y motivo van con la fecha, como en
-- cualquier otra decision que mueve a un candidato.

ALTER TABLE barrera_detectada
    ADD COLUMN descartada_por_usuario_id bigint REFERENCES usuario(id),
    ADD COLUMN motivo_descarte           text;

-- O no esta descartada, o se sabe quien y por que. Sin termino medio.
--
-- El CHECK no sobra teniendo la validacion en Java: el descarte que hubo que hacer a mano
-- mientras esta ruta no existia se hizo con un UPDATE suelto, y esa via sigue abierta para
-- cualquiera con acceso a la base. Lo que la regla protege no es al codigo, es al registro.
ALTER TABLE barrera_detectada
    ADD CONSTRAINT barrera_detectada_descarte_completo CHECK (
        (descartada_en IS NULL
            AND descartada_por_usuario_id IS NULL
            AND motivo_descarte IS NULL)
        OR (descartada_en IS NOT NULL
            AND descartada_por_usuario_id IS NOT NULL
            AND motivo_descarte IS NOT NULL)
    );
