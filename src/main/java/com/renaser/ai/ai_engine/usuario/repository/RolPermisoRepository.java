package com.renaser.ai.ai_engine.usuario.repository;

import com.renaser.ai.ai_engine.usuario.entity.RolPermiso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RolPermisoRepository extends JpaRepository<RolPermiso, RolPermiso.Clave> {

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
