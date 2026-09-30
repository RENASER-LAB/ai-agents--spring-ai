package com.renaser.ai.ai_engine.organizacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Las sedes y los cargos de Configuración (V64).
 *
 * <p>Las listas llevan {@code puedeEditar}: quien no tiene {@code editar_estructura} las ve sin
 * acciones, y el panel no tiene otra forma de saberlo sin intentar y leer el 403.
 */
public final class DtosEstructura {

    private DtosEstructura() {}

    public record SedePanel(Long id, String nombre, String direccion, String provinciaUbigeo,
                            String provinciaNombre, String codigoSunat, boolean esActiva) {}

    public record ListaDeSedes(boolean puedeEditar, List<SedePanel> sedes) {}

    public record GuardarSede(@NotBlank @Size(max = 120) String nombre,
                              @Size(max = 300) String direccion,
                              String provinciaUbigeo,
                              String codigoSunat) {}

    /**
     * Un cargo, que es un puesto del catálogo que ya usan las solicitudes y las vacantes.
     *
     * @param vacantes      cuántas vacantes vivas lo usan: renombrar les cambia el nombre
     * @param colaboradores cuántas personas lo tienen o lo tuvieron
     */
    public record CargoPanel(Long id, String nombre, String nivelPuestoCodigo, String nivelNombre,
                             String familiaCodigo, String familiaNombre, boolean esActivo,
                             long vacantes, long colaboradores) {}

    public record ListaDeCargos(boolean puedeEditar, List<CargoPanel> cargos) {}

    public record CrearCargo(@NotBlank @Size(max = 120) String nombre,
                             @NotBlank String nivelPuestoCodigo,
                             @NotBlank String familiaCodigo) {}

    public record RenombrarCargo(@NotBlank @Size(max = 120) String nombre) {}
}
