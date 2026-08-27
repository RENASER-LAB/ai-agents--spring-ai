package com.renaser.ai.ai_engine.ai.repository;

import com.renaser.ai.ai_engine.ai.model.TarifaModelo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface TarifaModeloRepository extends JpaRepository<TarifaModelo, Long> {

    /**
     * La tarifa vigente de (proveedor, modelo) en un momento dado: la de
     * {@code vigente_desde} más reciente que ya empezó. Una tarifa con fecha futura no
     * rige todavía, y una nueva no toca costos ya escritos.
     *
     * <p>Sin distinguir mayúsculas: el proveedor lo escribe la bitácora en minúsculas
     * ('deepseek') y una tarifa registrada a mano como 'DEEPSEEK' debe encontrarse igual.
     */
    Optional<TarifaModelo>
    findFirstByProveedorIgnoreCaseAndModeloIgnoreCaseAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(
            String proveedor, String modelo, Instant momento);
}
