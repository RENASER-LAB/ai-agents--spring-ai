package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.RolPermiso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, RolPermiso.Clave> {

    List<RolPermiso> findByRolId(Long rolId);

    /**
     * En cuántos roles <b>de una organización</b> está concedido un permiso.
     *
     * <p>Para la regla del último administrador aplicada a los permisos: revocar el último
     * {@code administrar_permisos} que queda deja el reparto sin nadie que pueda tocarlo, y
     * de ahí solo se sale por SQL.
     *
     * <p>Por organización y no en total, aunque hoy solo haya una: contando en global, dos
     * organizaciones con un administrador cada una suman dos, y las dos podrían quedarse sin
     * el suyo creyendo que el otro las cubre. Un candado que se abre solo cuando hay dos
     * inquilinos no es un candado.
     */
    @Query("""
            select count(rp)
            from RolPermiso rp, Rol r
            where rp.permisoId = :permisoId and r.id = rp.rolId
              and r.organizacionId = :organizacionId
            """)
    long contarEnOrganizacion(@Param("permisoId") Long permisoId,
                              @Param("organizacionId") Long organizacionId);

    // Los permisos efectivos de un usuario: código del permiso y alcance, por cada rol.
    // Si dos roles dan el mismo permiso con distinto alcance, el servicio se queda con
    // el más amplio (TODO > SUS_VACANTES > PROPIO).
    @Query("""
            select p.codigo, rp.alcance
            from RolPermiso rp, Permiso p
            where p.id = rp.permisoId and rp.rolId in :rolIds
            """)
    List<Object[]> permisosDeRoles(@Param("rolIds") List<Long> rolIds);
}
