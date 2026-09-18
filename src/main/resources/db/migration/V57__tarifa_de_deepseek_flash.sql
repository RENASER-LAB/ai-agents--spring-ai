-- ============================================================================
-- La tarifa de deepseek-flash: el costo dejó de anotarse hace una semana (pieza E)
-- ============================================================================
--
-- El 10/09/2026 DeepSeek retiró V4 Flash y publicó DeepSeek-V4.1-Flash. El nombre nuevo
-- es 'deepseek-flash' a secas, y los nombres viejos NO dejaron de funcionar: siguen
-- respondiendo, enrutados al nuevo. Por eso nadie se enteró — el sistema llevaba una
-- semana hablando con V4.1 Flash sin que ningún archivo cambiara.
--
-- Lo que sí se rompió es el costo. La bitácora guarda EL MODELO QUE EL PROVEEDOR DICE
-- HABER USADO, no el que se pidió (ClienteModeloDeepSeek#preguntar), y desde ese día el
-- proveedor responde 'deepseek-flash' en las tres rutas del sistema:
--
--     se pide 'deepseek-v4-flash'  -> responde 'deepseek-flash' (razonando)
--     se pide 'deepseek-chat'      -> responde 'deepseek-flash' (sin razonar)
--     se pide 'deepseek-v4-pro'    -> responde 'deepseek-v4-pro'
--
-- Sin una fila para 'deepseek-flash', TODAS las calificaciones y TODAS las lecturas de
-- currículum quedaron con costo NULL: el gasto del mes no suma, y el tope mensual de la
-- pieza E —el freno que hace esperar a lo nuevo cuando una empresa se pasa— lleva una
-- semana sin ver nada. Es el mismo fallo que arregló la V39, reaparecido por otra puerta:
-- allá faltaba el nombre de la pasada rápida, aquí falta el nombre al que el proveedor
-- renombró todo.
--
-- La fila de 'deepseek-chat' que sembró la V39 se queda. Ya no se llenará más (el
-- proveedor dejó de responder ese nombre) pero la vigencia es por fecha y borrarla
-- reescribiría lo que costaron las lecturas de CV de agosto y setiembre.


-- ============================================================================
-- Los precios, y por qué estos y no los otros
-- ============================================================================
--
-- DeepSeek cobra dos tarifas por el mismo modelo según la hora: 01:00-04:00 y 06:00-10:00
-- UTC de lunes a viernes son horario punta y cuesta el doble. En hora de Perú (UTC-5) esa
-- punta cae de 20:00 a 23:00 y de 01:00 a 05:00, o sea de madrugada y de noche.
--
-- Esta tabla no tiene franja horaria, solo vigencia por fecha, así que hay que elegir UN
-- número. Se elige EL DE FUERA DE PUNTA, que es el que corresponde al tráfico de verdad:
--
--   · Las horas de Renaser caen enteras fuera de punta. Quien postula, quien mira el panel y
--     quien dispara una calificación lo hace en horario de oficina peruano, y la punta no
--     empieza hasta las 20:00. No es un promedio optimista: es la franja en la que ocurre
--     casi todo lo que este sistema hace.
--   · Elegir el de punta «por si acaso» no es prudencia, es un error del doble en el 100%
--     del tráfico para cubrir una minoría. El costo que se enseña sería sistemáticamente el
--     doble del que llega en la factura, y el tope frenaría a una empresa con la mitad de su
--     gasto real — un freno que muerde antes de tiempo, no uno más seguro.
--   · Lo que ese freno de más cuesta NO se ve, y por eso importa: al 100% del tope,
--     frenarOPublicar deja el trabajo EN_ESPERA y retorna ANTES de avisarSiCruzaElUmbral, de
--     modo que no sale ningún correo —solo un warn en el registro—; el candidato ve EN_CURSO,
--     indistinguible de un proceso normal; y solo se despierta cuando la empresa recupera
--     cupo, o sea cuando Renaser sube el tope o empieza el mes. Una postulación congelada sin
--     que nadie se entere es peor que un dólar mal contado.
--
-- ⚠️ Lo que este número NO cubre, dicho sin adornos: una llamada hecha entre las 20:00 y las
-- 23:00 o entre la 01:00 y las 05:00 hora de Perú se cobra el doble de lo que aquí se anota.
-- Si algún día la criba se pone a correr de madrugada en tandas grandes, el consumo del mes
-- se quedará corto en esa parte y hay que volver aquí. Hoy no pasa: no hay ningún trabajo
-- programado que llame al modelo, la cola se mueve cuando alguien postula.
--
-- Precios de fuera de punta / punta por millón de tokens, catálogo del 17/09/2026. Se siembra
-- la PRIMERA columna:
--
--     deepseek-flash    entrada 0.15 / 0.30      salida 0.60 / 1.20
--     deepseek-v4-pro   entrada 0.66 / 1.32      salida 1.98 / 3.96
--
-- La entrada que se siembra es la de caché FALLADA, que es la cara. El acierto de caché
-- cuesta 50 veces menos (0.003 contra 0.15 en flash, fuera de punta) y esta tabla no
-- distingue los dos casos, así que lo que se anota es el techo de la entrada: el prefijo de
-- estas llamadas se repite mucho y acierta a menudo, de modo que por este lado el número
-- tira si acaso hacia arriba. Es la holgura que compensa lo que se pierde en las llamadas
-- de madrugada del aviso de arriba.
--
-- Siguen siendo PROVISIONALES igual que las de la V38: el número que manda es el de la
-- factura, y cuando llegue se registra una tarifa nueva —no se edita esta, o se reescribiría
-- el pasado.
--
-- vigente_desde es la fecha real en que DeepSeek cambió los precios (10/09/2026 04:00
-- UTC), no now(): si algún día se recalcula lo ejecutado desde entonces, cae en la tarifa
-- que de verdad estaba corriendo. Lo anterior a esa fecha sigue con la tarifa de la V38.


-- ============================================================================
-- deepseek-flash: el modelo con el que corre hoy TODO el sistema
-- ============================================================================
--
-- ON CONFLICT y no el NOT EXISTS que usó la V39, a propósito. Aquí hay una fila que alguien
-- pudo registrar a mano estos días al ver los costos vacíos, y un NOT EXISTS por (proveedor,
-- modelo) se desactivaría entero al encontrarla: si esa fila lleva otro precio, manda ella y
-- la migración miente; y si lleva fecha futura —un dedazo de año— NO hay tarifa vigente hoy,
-- el costo sigue saliendo NULL y la migración que existía para arreglarlo dice que fue bien.
--
-- Con ON CONFLICT sobre la clave única entera, esta fila se inserta pase lo que pase y sin
-- romper nada. Si además hay una de otra fecha, la vigencia decide: gana la más reciente que
-- ya empezó, que es exactamente lo que la tabla promete.

INSERT INTO tarifa_modelo (proveedor, modelo, precio_entrada_por_millon, precio_salida_por_millon, vigente_desde)
VALUES ('deepseek', 'deepseek-flash', 0.1500, 0.6000, TIMESTAMPTZ '2026-09-10 04:00:00+00')
ON CONFLICT (proveedor, modelo, vigente_desde) DO NOTHING;


-- ============================================================================
-- deepseek-v4-pro: nunca tuvo tarifa, y sigue siendo un modelo de verdad
-- ============================================================================
--
-- No es parte del cambio de modelo: es un agujero que lleva abierto desde la V38. El
-- ORCHESTRATOR corre en deepseek-v4-pro (AgentModelSelectorImpl) y ese nombre SÍ sobrevive
-- al cambio de catálogo —DeepSeek anunció que lo seguiría sirviendo después del 14/09 con
-- la misma facturación—, así que el proveedor lo responde tal cual y cae en una fila que
-- no existe. Se siembra aquí porque arreglar el costo a medias es dejarlo roto.
--
-- Su vigente_desde NO es el 10/09 sino el 25/08/2026, el día de la V38. El precio de v4-pro
-- no cambió esta semana: lo que pasó es que nunca se registró. Fecharlo el 10/09 dejaría sin
-- tarifa todas las corridas del ORCHESTRATOR anteriores a esa fecha, que son justo las que
-- llevan sin costo desde que existe la pieza E. Con la fecha de la V38, cualquier recálculo
-- de lo ejecutado las encuentra.

INSERT INTO tarifa_modelo (proveedor, modelo, precio_entrada_por_millon, precio_salida_por_millon, vigente_desde)
VALUES ('deepseek', 'deepseek-v4-pro', 0.6600, 1.9800, TIMESTAMPTZ '2026-08-25 00:00:00+00')
ON CONFLICT (proveedor, modelo, vigente_desde) DO NOTHING;
