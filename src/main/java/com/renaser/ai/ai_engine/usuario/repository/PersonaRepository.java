package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.Persona;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    /**
     * ¿Otra persona usa este celular? (V73). Dos cuentas pueden compartirlo —una familia—, y el
     * borrado de los datos de una no puede llevarse lo que sigue siendo de la otra.
     */
    boolean existsByTelefonoAndIdNot(String telefono, Long id);
}
