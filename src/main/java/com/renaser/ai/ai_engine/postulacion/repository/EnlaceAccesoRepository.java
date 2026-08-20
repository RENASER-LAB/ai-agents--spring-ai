package com.renaser.ai.ai_engine.postulacion.repository;

import com.renaser.ai.ai_engine.postulacion.entity.EnlaceAcceso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnlaceAccesoRepository extends JpaRepository<EnlaceAcceso, Long> {

    /**
     * Se busca por el hash y nunca por el token: el token no está guardado en ningún sitio.
     */
    Optional<EnlaceAcceso> findByTokenHash(String tokenHash);
}
