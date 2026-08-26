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
}
