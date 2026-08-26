package com.renaser.ai.ai_engine.comun.config;

import com.renaser.ai.ai_engine.administracion.controller.AdministracionController;
import com.renaser.ai.ai_engine.ai.controller.perfilintegral.AgentesIaPanelController;
import com.renaser.ai.ai_engine.catalogo.controller.CatalogoController;
import com.renaser.ai.ai_engine.decision.controller.DecisionPanelController;
import com.renaser.ai.ai_engine.perfilintegral.controller.BancoPreguntasController;
import com.renaser.ai.ai_engine.perfilintegral.controller.EvaluacionPortalController;
import com.renaser.ai.ai_engine.perfilintegral.controller.PlantillasEvaluacionController;
import com.renaser.ai.ai_engine.pesos.controller.PesosController;
import com.renaser.ai.ai_engine.prueba.controller.CalificacionPruebaController;
import com.renaser.ai.ai_engine.simulacion.controller.SimulacionPanelController;
import com.renaser.ai.ai_engine.simulacion.controller.SimulacionPortalController;
import com.renaser.ai.ai_engine.validacion.controller.ValidacionPanelController;
import com.renaser.ai.ai_engine.prueba.controller.PlantillaPruebaController;
import com.renaser.ai.ai_engine.prueba.controller.PruebaPortalController;
import com.renaser.ai.ai_engine.portal.controller.PortalController;
import com.renaser.ai.ai_engine.postulacion.controller.PostulacionesPanelController;
import com.renaser.ai.ai_engine.seguridad.controller.PanelAuthController;
import com.renaser.ai.ai_engine.solicitud.controller.SolicitudesController;
import com.renaser.ai.ai_engine.vacante.controller.VacantesPanelController;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El candado de Swagger para el portal y el panel.
 *
 * <p>No toca el bean {@code OpenAPI} del módulo de agentes: lo completa desde fuera. Así el
 * botón «Authorize» aparece sin que haya que editar un archivo compartido.
 *
 * <p>Desde el 24/08/2026 el candado va también en las operaciones del motor de agentes, que
 * dejaron de estar abiertas. Esas se reconocen por nombre de paquete y no por clase, a
 * propósito: importarlas aquí crearía una dependencia de {@code comun.config} hacia
 * {@code ai.controller} y rompería la regla de ArchUnit que separa los dos módulos.
 *
 * <p>Los controladores se listan por clase, no por nombre de paquete, para que un cambio de
 * nombre no deje la lista apuntando al vacío. <b>Al añadir un controlador nuevo hay que sumarlo
 * aquí</b>, o sus endpoints saldrán en Swagger sin candado.
 */
@Configuration
public class ConfiguracionSwagger {

    private static final String ESQUEMA = "bearer";

    private static final List<Class<?>> CONTROLADORES = List.of(
            PortalController.class,
            AdministracionController.class,
            SolicitudesController.class,
            VacantesPanelController.class,
            PostulacionesPanelController.class,
            PanelAuthController.class,
            CatalogoController.class,
            EvaluacionPortalController.class,
            BancoPreguntasController.class,
            PlantillasEvaluacionController.class,
            PesosController.class,
            AgentesIaPanelController.class,
            PruebaPortalController.class,
            PlantillaPruebaController.class,
            CalificacionPruebaController.class,
            DecisionPanelController.class,
            SimulacionPanelController.class,
            SimulacionPortalController.class,
            ValidacionPanelController.class,
            com.renaser.ai.ai_engine.perfil.controller.PerfilPortalController.class,
            com.renaser.ai.ai_engine.organizacion.controller.PlataformaController.class);

    /**
     * El motor de agentes, por nombre de paquete y no por clase.
     *
     * <p>Sus rutas piden token de equipo desde el 24/08/2026, así que en Swagger tienen que
     * salir con candado. Pero listarlas como {@code Class} aquí crearía una dependencia de
     * compilación de {@code comun.config} hacia {@code ai.controller}, y eso rompe la regla de
     * ArchUnit que mantiene separados los dos módulos. Una cadena de texto no crea esa
     * dependencia, y el candado es cosmético: la seguridad de verdad está en
     * {@code ConfiguracionSeguridad} y la comprueba {@code SeguridadAgentesIaIT}.
     */
    private static final String PAQUETE_AGENTES = "com.renaser.ai.ai_engine.ai.controller";

    private static final Set<String> PAQUETES = CONTROLADORES.stream()
            .map(c -> c.getPackage().getName())
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Los paquetes cuyos endpoints llevan candado. Lo usa {@code ArquitecturaTest} para
     * volver comprobable el aviso de arriba: un controlador nuevo que no esté aquí ya no
     * sale en Swagger sin candado sin que nadie se entere — falla la compilación del PR.
     */
    public static Set<String> paquetesConCandado() {
        return PAQUETES;
    }

    // Sin esto no hay dónde pegar el token y los endpoints no se pueden probar desde Swagger
    @Bean
    public OpenApiCustomizer esquemaBearerSeleccion() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("El token que devuelve /portal/auth/login (candidato) "
                            + "o /panel/auth/dev-login (equipo)"));
        };
    }

    // El candado en todo lo que pide token: nuestras operaciones y, desde que el motor de
    // agentes dejó de estar abierto, también las suyas. Cuenta lo que esté en un subpaquete,
    // para que esto abarque lo mismo que el basePackageClasses de ManejadorErrores y no
    // queden dos fronteras distintas.
    @Bean
    public OperationCustomizer candadoEnLoQuePideToken() {
        return (operacion, metodo) -> {
            String paquete = metodo.getBeanType().getPackageName();
            boolean deSeleccion = PAQUETES.stream()
                    .anyMatch(p -> paquete.equals(p) || paquete.startsWith(p + "."));
            boolean deAgentes = paquete.equals(PAQUETE_AGENTES)
                    || paquete.startsWith(PAQUETE_AGENTES + ".");
            if (deSeleccion || deAgentes) {
                operacion.addSecurityItem(new SecurityRequirement().addList(ESQUEMA));
            }
            return operacion;
        };
    }
}
