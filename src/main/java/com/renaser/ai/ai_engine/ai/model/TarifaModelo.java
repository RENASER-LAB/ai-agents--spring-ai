package com.renaser.ai.ai_engine.ai.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * El precio por millón de tokens de un modelo, con vigencia por fecha (pieza E).
 *
 * <p>Cuando el proveedor cambia sus precios se registra una fila nueva y <b>lo ya
 * ejecutado conserva el precio que tenía</b>: la vigente de (proveedor, modelo) es la de
 * {@code vigenteDesde} más reciente que ya empezó. Sin {@code vigenteHasta} a propósito —
 * así no hay huecos ni solapes que validar.
 *
 * <p>Las sembradas en la V38 son PROVISIONALES: el número exacto lo pone Renaser cuando
 * llegue la factura real.
 */
@Entity
@Table(name = "tarifa_modelo")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TarifaModelo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // En minúsculas, como lo escribe la bitácora ('deepseek'); la búsqueda compara sin
    // distinguir mayúsculas por si alguien registra una tarifa a mano con otra caja.
    private String proveedor;
    private String modelo;
    private BigDecimal precioEntradaPorMillon;
    private BigDecimal precioSalidaPorMillon;
    private Instant vigenteDesde;
    private Instant creadoEn;
}
