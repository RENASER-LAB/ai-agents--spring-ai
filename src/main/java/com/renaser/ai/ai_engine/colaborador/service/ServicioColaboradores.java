package com.renaser.ai.ai_engine.colaborador.service;

import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.AltaColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.ContratadoPendiente;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.DatosPersonales;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.EntradaHistorial;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.FichaColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.OpcionesColaborador;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.PaginaColaboradores;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Precarga;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.RegistrarCambio;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.RegistrarCese;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Reingreso;
import com.renaser.ai.ai_engine.colaborador.dto.DtosColaborador.Filtros;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * La gestión de personas: la lista, la ficha y todo lo que le pasa a alguien entre su ingreso
 * y su cese.
 *
 * <p>⚠️ <b>En esta versión solo cuenta el alcance TODO.</b> Con cualquier otro, el usuario no
 * alcanza a ningún colaborador: la lista sale vacía y una ficha responde 404.
 */
public interface ServicioColaboradores {

    int TAMANO_PAGINA = 50;

    PaginaColaboradores listar(ContextoUsuario quien, Filtros filtros, int pagina);

    OpcionesColaborador opciones(ContextoUsuario quien);

    List<ContratadoPendiente> pendientes(ContextoUsuario quien);

    void noDarDeAlta(ContextoUsuario quien, Long postulacionId, String motivo);

    Precarga precarga(ContextoUsuario quien, Long postulacionId);

    Long darDeAlta(ContextoUsuario quien, AltaColaborador datos);

    FichaColaborador ficha(ContextoUsuario quien, Long id);

    List<EntradaHistorial> historial(ContextoUsuario quien, Long id);

    void editarPerfil(ContextoUsuario quien, Long id, DatosPersonales datos);

    Long registrarCambio(ContextoUsuario quien, Long id, RegistrarCambio datos);

    void anularCambio(ContextoUsuario quien, Long id, Long situacionId, String motivo);

    void registrarCese(ContextoUsuario quien, Long id, RegistrarCese datos);

    void anularCese(ContextoUsuario quien, Long id, String motivo);

    void reingresar(ContextoUsuario quien, Long id, Reingreso datos);
}
