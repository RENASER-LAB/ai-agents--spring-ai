package com.renaser.ai.ai_engine.organizacion.repository;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizacionRepository extends JpaRepository<Organizacion, Long> {
    Optional<Organizacion> findByCodigo(String codigo);

    // La dueña de la plataforma. Solo una puede serlo (índice único parcial, V37);
    // reemplaza al findByCodigo("RENASER") que ataba el código a un nombre.
    Optional<Organizacion> findByEsPlataformaTrue();

    /**
     * Las que tienen ese código sin mirar mayúsculas: el perfil público va en
     * {@code /empresas/<código en minúsculas>} (V85). Una lista y no un Optional porque el
     * índice único de la columna distingue mayúsculas: quien llama decide qué hacer si hay dos.
     */
    @org.springframework.data.jpa.repository.Query(
            "select o from Organizacion o where lower(o.codigo) = lower(:codigo)")
    java.util.List<Organizacion> conElCodigo(
            @org.springframework.data.repository.query.Param("codigo") String codigo);

    /**
     * La organización con su fila bloqueada hasta el final de la transacción (V85). Guardar la
     * presentación borra y vuelve a escribir los beneficios y las redes: dos guardados a la vez
     * pasan de uno en uno y gana el último, en vez de chocar en el índice único.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Organizacion o where o.id = :id")
    Optional<Organizacion> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
}
