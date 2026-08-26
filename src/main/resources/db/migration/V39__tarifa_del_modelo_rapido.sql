-- ============================================================================
-- La tarifa del modelo rápido: el gasto más frecuente por fin se ve (pieza E)
-- ============================================================================
--
-- La V38 sembró la tarifa de deepseek-v4-flash (default-model) y del embedding de
-- Google, pero la lectura del currículum —AgenteDatosCv, la llamada más frecuente del
-- sistema: una por candidato— corre con razona=false y pide el MODELO RÁPIDO
-- (renaser.ai.chat.modelo-rapido: deepseek-chat). La bitácora guarda el modelo que el
-- proveedor dice haber usado, que es el pedido: 'deepseek-chat'. Sin su tarifa, TODAS
-- las lecturas de CV quedaban con costo NULL, ese gasto no sumaba en el consumo del mes
-- y el tope no lo veía: el freno de la pieza E estaba ciego justo donde más se gasta.
--
-- Mismos precios provisionales que la V38 a propósito: los dos nombres resuelven al
-- mismo modelo con distinto modo de trabajo (ver ClienteModeloDeepSeek#llamar), y el
-- número exacto lo pone Renaser cuando llegue la factura real.
--
-- Con NOT EXISTS por si alguien ya la registró a mano desde que la V38 salió.

INSERT INTO tarifa_modelo (proveedor, modelo, precio_entrada_por_millon, precio_salida_por_millon, vigente_desde)
SELECT 'deepseek', 'deepseek-chat', 0.2700, 1.1000, now()
WHERE NOT EXISTS (
      SELECT 1 FROM tarifa_modelo
       WHERE proveedor = 'deepseek' AND lower(modelo) = 'deepseek-chat');
