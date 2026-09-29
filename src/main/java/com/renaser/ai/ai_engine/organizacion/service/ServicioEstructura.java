package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.CrearCargo;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.GuardarSede;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.ListaDeCargos;
import com.renaser.ai.ai_engine.organizacion.dto.DtosEstructura.ListaDeSedes;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

/**
 * La estructura de la empresa en Configuración: sedes, que son nuevas, y cargos, que son el
 * catálogo de puestos de siempre con editar, desactivar y reactivar (V64).
 *
 * <p>Nada se borra. Una sede o un cargo desactivado deja de poder elegirse y quien ya lo tenía
 * lo conserva.
 */
public interface ServicioEstructura {

    ListaDeSedes sedes(ContextoUsuario quien);

    Long crearSede(ContextoUsuario quien, GuardarSede datos);

    void editarSede(ContextoUsuario quien, Long id, GuardarSede datos);

    void activarSede(ContextoUsuario quien, Long id, boolean activa);

    ListaDeCargos cargos(ContextoUsuario quien);

    Long crearCargo(ContextoUsuario quien, CrearCargo datos);

    void renombrarCargo(ContextoUsuario quien, Long id, String nombre);

    void activarCargo(ContextoUsuario quien, Long id, boolean activo);
}
