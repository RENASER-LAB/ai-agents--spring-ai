package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.Persona;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonaRepository extends JpaRepository<Persona, Long> {

    /**
     * ¿Otra persona usa este celular? (V73). Desde la V92 un celular es de una sola cuenta: el
     * portal lo pregunta antes de guardarlo y responde 409 TELEFONO_EN_USO. El borrado de datos
     * también lo mira, por las cuentas que lo compartían antes de la regla.
     */
    boolean existsByTelefonoAndIdNot(String telefono, Long id);

    /** ¿Alguien usa ya este celular? (V92): el registro, antes de crear la cuenta. */
    boolean existsByTelefono(String telefono);

    /**
     * Quién tiene este celular en su cuenta (V92): a quién se le apaga WhatsApp cuando el número
     * escribe BAJA. Como mucho una persona desde el índice único; lista por las de antes.
     */
    List<Persona> findAllByTelefono(String telefono);
}
