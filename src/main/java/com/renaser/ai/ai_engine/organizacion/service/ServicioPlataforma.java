package com.renaser.ai.ai_engine.organizacion.service;

import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.ConsumoEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.CrearEmpresa;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaCreada;
import com.renaser.ai.ai_engine.organizacion.dto.DtosOrganizacion.EmpresaPanel;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import java.util.List;

/**
 * Renaser como dueña de la plataforma: dar de alta empresas.
 *
 * <p>El alta es el camino de tres pasos de la pieza C con los dos primeros colapsados:
 * la solicitud la crea la plataforma y se aprueba sola. El día que exista un formulario
 * público, ese formulario creará la misma solicitud y aquí aparecerá la bandeja de
 * aprobación — sin rehacer nada de esto.
 *
 * <p>Además del permiso {@code administrar_plataforma}, el servicio exige que quien llama
 * SEA de la organización plataforma: el permiso viaja con la copia de roles solo si
 * alguien lo concediera a mano, y esta segunda llave hace que ese error no alcance.
 */
public interface ServicioPlataforma {

    /**
     * Crea la empresa y la deja lista para su día uno: roles y permisos copiados de la
     * plataforma (sin {@code administrar_plataforma}), parámetros, textos legales en
     * borrador, correos activos, y la invitación de su primer administrador ya enviada.
     */
    EmpresaCreada crearEmpresa(ContextoUsuario quien, CrearEmpresa datos);

    List<EmpresaPanel> empresas(ContextoUsuario quien);

    /**
     * El consumo de IA de un mes ({@code YYYY-MM}, hora de Lima) por organización, con
     * su desglose por agente (pieza E). Es la mirada de Renaser sobre el gasto: con
     * estos números cobra fuera del sistema lo que acuerde con cada empresa.
     */
    List<ConsumoEmpresa> consumo(ContextoUsuario quien, String mes);

    /**
     * Suspende una empresa: congelada, no borrada (pieza F). Su equipo no entra —ni con
     * tokens vivos—, sus vacantes salen del tablón, y los candidatos que ya estaban
     * dentro conservan acceso y datos. La plataforma no puede suspenderse a sí misma.
     */
    void suspender(ContextoUsuario quien, Long empresaId, String motivo);

    /** Al reactivar, todo vuelve tal cual: login, tokens y tablón. */
    void reactivar(ContextoUsuario quien, Long empresaId, String motivo);

    /**
     * Pone, sube o quita ({@code tope} vacío) el tope mensual de IA de una empresa
     * (pieza E). El sondeo de la cola despierta solo lo que quedó EN_ESPERA si el cambio
     * le devuelve el cupo.
     */
    void ponerTopeIa(ContextoUsuario quien, Long empresaId, String tope);
}
