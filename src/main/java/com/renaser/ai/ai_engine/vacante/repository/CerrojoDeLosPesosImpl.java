package com.renaser.ai.ai_engine.vacante.repository;

import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;

/**
 * Ver {@link CerrojoDeLosPesos}. Es un fragmento de {@link VacanteRepository}: Spring Data lo
 * encuentra por el nombre y lo engancha al repositorio.
 */
public class CerrojoDeLosPesosImpl implements CerrojoDeLosPesos {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void bloquearParaCalificar(Vacante vacante) {
        if (vacante == null || vacante.getId() == null || !seEscribe()) {
            return;
        }
        // Consulta nativa a propósito: lo que se quiere es el valor de la base después de la
        // espera, no el de la entidad que pueda haber ya en memoria.
        List<?> filas = em.createNativeQuery(
                        "select version_pesos_id from vacante where id = :id for share")
                .setParameter("id", vacante.getId())
                .getResultList();
        if (filas.isEmpty()) {
            return;
        }
        Object leida = filas.get(0);
        Long vigente = leida == null ? null : ((Number) leida).longValue();
        if (!Objects.equals(vigente, vacante.getVersionPesosId()) && em.contains(vacante)) {
            em.refresh(vacante);
        }
    }

    @Override
    public void bloquearParaCalificarPorId(Long vacanteId) {
        if (vacanteId == null || !seEscribe()) {
            return;
        }
        // La que haya en memoria, o la de la base si no había ninguna: después del bloqueo se
        // compara con lo confirmado y, si un cambio de pesos se coló, se relee.
        bloquearParaCalificar(em.find(Vacante.class, vacanteId));
    }

    @Override
    public void bloquearParaCalificarYReleer(Postulacion postulacion) {
        if (postulacion == null || !seEscribe()) {
            return;
        }
        bloquearParaCalificarPorId(postulacion.getVacanteId());
        if (postulacion.getId() == null || !em.contains(postulacion)) {
            return;
        }
        // Lo pendiente primero —refresh lo descartaría—, y ya con la vacante en la mano. Luego
        // la fila tal como está confirmada: si un cambio de pesos terminó mientras se esperaba,
        // trae su grupo; si el equipo la movió, su estado.
        em.flush();
        em.refresh(postulacion);
    }

    private static boolean seEscribe() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}
