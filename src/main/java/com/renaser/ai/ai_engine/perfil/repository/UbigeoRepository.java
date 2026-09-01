package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.Ubigeo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UbigeoRepository extends JpaRepository<Ubigeo, String> {

    // El catalogo entero de una vez. Son 222 filas y no crecen entre despliegues: filtrar
    // por nivel en la base obligaria a dos consultas —las provincias y sus departamentos,
    // que son quienes ponen el nombre— para ahorrar 26 filas.
    List<Ubigeo> findByActivoTrue();
}
