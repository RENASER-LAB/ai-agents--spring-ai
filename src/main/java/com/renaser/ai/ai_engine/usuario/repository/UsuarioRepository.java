package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.Usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    // El correo es único por organización sin distinguir mayúsculas (índice funcional)
    @Query("select u from Usuario u where u.organizacionId = :organizacionId and lower(u.correo) = lower(:correo)")
    Optional<Usuario> buscarPorCorreo(@Param("organizacionId") Long organizacionId, @Param("correo") String correo);

    Optional<Usuario> findByOrganizacionIdAndUsuarioRenaserOsId(Long organizacionId, String usuarioRenaserOsId);

    List<Usuario> findByOrganizacionIdAndEsEquipoTrue(Long organizacionId);

    // El login del panel no sabe de qué organización es quien escribe: busca la cuenta de
    // equipo por el correo en todas. El orden hace determinista el caso raro de un mismo
    // correo con cuenta de equipo en dos empresas: gana la más antigua que autentique.
    @Query("select u from Usuario u where u.esEquipo = true and lower(u.correo) = lower(:correo) order by u.creadoEn")
    List<Usuario> equipoPorCorreo(@Param("correo") String correo);

    Optional<Usuario> findFirstByPersonaId(Long personaId);
}
