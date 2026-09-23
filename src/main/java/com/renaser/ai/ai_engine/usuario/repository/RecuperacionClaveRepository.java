package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.RecuperacionClave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RecuperacionClaveRepository extends JpaRepository<RecuperacionClave, Long> {

    // Al restablecer se busca siempre por el hash del token, nunca por el token.
    Optional<RecuperacionClave> findByTokenHash(String tokenHash);

    // El límite de solicitudes: cuántos enlaces recibió la cuenta desde un momento dado.
    // Acotado por fecha y con índice (usuario_id, creado_en): la tabla puede crecer sin
    // que esta consulta lo note.
    long countByUsuarioIdAndCreadoEnAfter(Long usuarioId, Instant desde);

    // Pedir otro enlace deja sin efecto los anteriores sin usar, vencidos o no: el índice
    // de «solo uno vivo» cuenta como vivo a todo lo que no está usado ni invalidado.
    @Modifying
    @Query("""
            update RecuperacionClave r set r.invalidadoEn = :ahora
             where r.usuarioId = :usuarioId and r.usadoEn is null and r.invalidadoEn is null""")
    int invalidarLosVivos(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);

    // El gasto atómico, como el de la invitación: dos pestañas con el mismo enlace leen
    // las dos «sirve», pero este UPDATE condicional solo le da la fila a la primera.
    @Modifying
    @Query("""
            update RecuperacionClave r set r.usadoEn = :ahora
             where r.id = :id and r.usadoEn is null and r.invalidadoEn is null
               and r.venceEn > :ahora""")
    int gastar(@Param("id") Long id, @Param("ahora") Instant ahora);
}
