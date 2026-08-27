package com.renaser.ai.ai_engine.ai.service.impl;

import com.renaser.ai.ai_engine.ai.model.TarifaModelo;
import com.renaser.ai.ai_engine.ai.repository.TarifaModeloRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Pone precio a una llamada al modelo (pieza E).
 *
 * <p>(tokens de entrada × precio de entrada + tokens de salida × precio de salida) ÷ un
 * millón, con la tarifa vigente <b>en el momento del cierre</b>: un cambio de precios
 * posterior no reescribe lo ya ejecutado.
 *
 * <p><b>Nunca rompe la ejecución.</b> Sin tarifa registrada, o sin ningún conteo de
 * tokens, el costo queda vacío y se anota un aviso: una calificación vale más que su
 * contabilidad, y el hueco se ve después en el panel de consumo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CalculadoraCostoIa {

    private static final BigDecimal MILLON = BigDecimal.valueOf(1_000_000);

    private final TarifaModeloRepository tarifas;

    /** @return el costo en la moneda de la tarifa, o vacío si no se pudo poner precio */
    public BigDecimal costoDe(String proveedor, String modelo,
                              Integer tokensEntrada, Integer tokensSalida, Instant momento) {
        if (tokensEntrada == null && tokensSalida == null) {
            // No hay nada medido a lo que ponerle precio; pasa cuando el proveedor no
            // devolvió el uso. Sin aviso: el aviso útil es el de la tarifa que falta.
            return null;
        }
        TarifaModelo tarifa = tarifas
                .findFirstByProveedorIgnoreCaseAndModeloIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
                        proveedor, modelo, momento)
                .orElse(null);
        if (tarifa == null) {
            log.warn("No hay tarifa vigente para {}/{}: la ejecución queda sin costo. "
                    + "Registra la tarifa en tarifa_modelo para que el consumo cuadre",
                    proveedor, modelo);
            return null;
        }
        // Un conteo ausente vale cero: el otro sí se midió y su precio es real.
        BigDecimal entrada = BigDecimal.valueOf(tokensEntrada == null ? 0 : tokensEntrada)
                .multiply(tarifa.getPrecioEntradaPorMillon());
        BigDecimal salida = BigDecimal.valueOf(tokensSalida == null ? 0 : tokensSalida)
                .multiply(tarifa.getPrecioSalidaPorMillon());
        // numeric(10,4): la columna de ejecucion_ia manda la escala.
        return entrada.add(salida).divide(MILLON, 4, RoundingMode.HALF_UP);
    }
}
