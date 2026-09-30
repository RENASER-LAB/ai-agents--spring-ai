-- Lo que un cambio anulado tenía detrás (29/09/2026).
--
-- El historial enseña cada cambio como «antes → después». Una situación anulada ya no forma
-- parte de la línea de tiempo, y medirla contra la situación viva que hoy le queda delante le
-- atribuía lo que cambiaron otros después: un traslado anulado pasaba a enseñar el cargo y el
-- sueldo de una promoción registrada más tarde, o se quedaba sin ninguna línea si la promoción
-- dejaba el mismo dato.
--
-- Tampoco basta con apuntar a la fila que tenía detrás: los ajustes de solo sueldo se
-- reescriben cuando un cambio anterior los arrastra. Por eso se copian los valores.
--
-- Al anular (a mano o por un cese), la fila guarda aquí la situación viva que tenía justo
-- detrás. Solo la llevan las anuladas; en las vivas el «antes» es la viva anterior, como
-- siempre.

ALTER TABLE situacion_laboral
    ADD COLUMN antes_sede_id              bigint REFERENCES sede(id),
    ADD COLUMN antes_area_id              bigint REFERENCES area(id),
    ADD COLUMN antes_puesto_id            bigint REFERENCES puesto(id),
    ADD COLUMN antes_jefe_colaborador_id  bigint REFERENCES colaborador(id),
    ADD COLUMN antes_tipo_contrato        varchar(9),
    ADD COLUMN antes_fin_contrato         date,
    ADD COLUMN antes_fin_periodo_prueba   date,
    ADD COLUMN antes_regimen_laboral      varchar(2),
    -- ⚠️ Igual que sueldo_base: solo lo ve quien tiene ver_sueldos.
    ADD COLUMN antes_sueldo_base          numeric(12, 2),
    ADD COLUMN antes_moneda               varchar(3),
    ADD CONSTRAINT situacion_laboral_antes_solo_anuladas
        CHECK (antes_sede_id IS NULL OR anulada_en IS NOT NULL),
    ADD CONSTRAINT situacion_laboral_antes_sueldo_con_moneda
        CHECK (antes_sueldo_base IS NULL OR antes_moneda IS NOT NULL);

-- Las que ya estaban anuladas: la viva que tenían detrás en el momento de anularse, es decir,
-- registrada antes de ese momento y no anulada antes de él (las que anuló el mismo cese en la
-- misma tanda cuentan como vivas). Sus valores son los de hoy: si después la arrastró otro
-- cambio, se nota; solo afecta a lo anulado antes de esta migración.
WITH detras AS (
    SELECT s.id,
           (SELECT p.id
              FROM situacion_laboral p
             WHERE p.periodo_id = s.periodo_id
               AND p.registrado_en <= s.anulada_en
               AND (p.anulada_en IS NULL OR p.anulada_en >= s.anulada_en)
               AND (p.vigente_desde, p.id) < (s.vigente_desde, s.id)
             ORDER BY p.vigente_desde DESC, p.id DESC
             LIMIT 1) AS anterior_id
      FROM situacion_laboral s
     WHERE s.anulada_en IS NOT NULL
)
UPDATE situacion_laboral s
   SET antes_sede_id             = p.sede_id,
       antes_area_id             = p.area_id,
       antes_puesto_id           = p.puesto_id,
       antes_jefe_colaborador_id = p.jefe_colaborador_id,
       antes_tipo_contrato       = p.tipo_contrato,
       antes_fin_contrato        = p.fin_contrato,
       antes_fin_periodo_prueba  = p.fin_periodo_prueba,
       antes_regimen_laboral     = p.regimen_laboral,
       antes_sueldo_base         = p.sueldo_base,
       antes_moneda              = p.moneda
  FROM detras d
  JOIN situacion_laboral p ON p.id = d.anterior_id
 WHERE s.id = d.id;

COMMENT ON COLUMN situacion_laboral.antes_sede_id IS
    'Con las demás antes_*: la situación viva que un cambio tenía detrás cuando se anuló. El '
    'historial lo mide contra ella para siempre, aunque después se registren otros (V65).';
