package com.renaser.ai.ai_engine.comun.exception;

import com.renaser.ai.ai_engine.administracion.controller.AdministracionController;
import com.renaser.ai.ai_engine.ai.controller.perfilintegral.AgentesIaPanelController;
import com.renaser.ai.ai_engine.catalogo.controller.CatalogoController;
import com.renaser.ai.ai_engine.decision.controller.DecisionPanelController;
import com.renaser.ai.ai_engine.perfilintegral.controller.BancoPreguntasController;
import com.renaser.ai.ai_engine.perfilintegral.service.ImportacionInvalidaException;
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
import com.renaser.ai.ai_engine.seguridad.exception.CredencialesInvalidasException;
import com.renaser.ai.ai_engine.seguridad.exception.DemasiadosIntentosException;
import com.renaser.ai.ai_engine.solicitud.controller.SolicitudesController;
import com.renaser.ai.ai_engine.vacante.controller.VacantesPanelController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Los errores propios del módulo de selección de personal.
 *
 * <p>Vive aquí y no en el advice general por dos razones: el advice general es del módulo de
 * agentes y lo mantiene otra persona, y su handler de {@code Exception} convertiría estos casos
 * en un 500 opaco. Con {@code HIGHEST_PRECEDENCE} se consulta antes que el general.
 *
 * <p>La lista de controladores es la frontera: solo se aplica a los nuestros. Se declara por
 * clase y no por nombre de paquete para que un cambio de nombre no la deje apuntando al vacío.
 * <b>Al añadir un controlador nuevo hay que sumarlo aquí</b>, o sus errores volverán a salir
 * como 500.
 */
@RestControllerAdvice(basePackageClasses = {
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
        ValidacionPanelController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ManejadorErrores {

    private static final String BASE_URI = "https://api.renaser.com/errors/";

    // Fallar al entrar es 401, no 400: la petición está bien escrita, lo que no cuadra es
    // la identidad. Se registra sin el correo, que en este caso es dato de un intento fallido.
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ProblemDetail credencialesInvalidas(CredencialesInvalidasException ex, WebRequest request) {
        log.warn("Intento de entrada fallido - Path: {}", request.getDescription(false));
        return construir(HttpStatus.UNAUTHORIZED, "No se pudo entrar", "credenciales-invalidas",
                ex.getMessage());
    }

    // Freno por ritmo, no conflicto de estado: 429 con Retry-After, para que el frontend
    // sepa cuándo puede volver a probar en vez de adivinar.
    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ProblemDetail> demasiadosIntentos(DemasiadosIntentosException ex,
                                                            WebRequest request) {
        log.warn("Entrada bloqueada por intentos fallidos - Path: {}", request.getDescription(false));
        ProblemDetail problema = construir(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos",
                "demasiados-intentos", ex.getMessage());
        problema.setProperty("segundosDeEspera", ex.getSegundosDeEspera());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosDeEspera()))
                .body(problema);
    }

    // El Excel del banco no sirve, y el porqué son N problemas, no uno: 400 con la
    // lista completa {hoja, fila, mensaje}, que es lo que el administrador necesita
    // para corregir el archivo de una sola pasada.
    @ExceptionHandler(ImportacionInvalidaException.class)
    public ProblemDetail importacionInvalida(ImportacionInvalidaException ex, WebRequest request) {
        log.warn("Importación inválida - Path: {}, Problemas: {}",
                request.getDescription(false), ex.getErrores().size());
        ProblemDetail problema = construir(HttpStatus.BAD_REQUEST,
                "El archivo tiene problemas y no se importó nada",
                "importacion-invalida", ex.getMessage());
        problema.setProperty("errores", ex.getErrores());
        return problema;
    }

    // Una regla de negocio incumplida es un 400 con explicación, no un 500 opaco:
    // «toda transición manual exige motivo», «el archivo debe ser PDF o Word»…
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail reglaDeNegocio(IllegalArgumentException ex, WebRequest request) {
        log.warn("Regla de negocio - Path: {}, Message: {}", request.getDescription(false), ex.getMessage());
        return construir(HttpStatus.BAD_REQUEST, "La petición no cumple una regla",
                "regla-de-negocio", ex.getMessage());
    }

    // El estado actual no permite la operación: 409, con el porqué
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail estadoInvalido(IllegalStateException ex, WebRequest request) {
        log.warn("Estado no lo permite - Path: {}, Message: {}", request.getDescription(false), ex.getMessage());
        return construir(HttpStatus.CONFLICT, "El estado actual no permite esta operación",
                "estado-invalido", ex.getMessage());
    }

    /* Una URL que no existe se iba por el manejador de «error inesperado»: 500, y en el
       registro un ERROR con traza entera. Las dos cosas mienten. A quien llama le dice que
       el problema esta en el servidor cuando esta en su direccion —y le hace reintentar, que
       es lo peor que puede hacer—; y a quien mantiene le llena el registro de averias falsas
       que tapan las de verdad, que es como se pierde el aviso que si importaba.

       Va con nivel de aviso y sin traza a proposito: una URL equivocada es un dato malo de
       quien llama, no un fallo del sistema. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail rutaQueNoExiste(NoResourceFoundException ex, WebRequest request) {
        log.warn("Ruta inexistente - Path: {}", request.getDescription(false));
        return construir(HttpStatus.NOT_FOUND, "Esa dirección no existe", "ruta-inexistente",
                "No hay ningún endpoint en «" + ex.getResourcePath() + "». Revisa la ruta; "
                        + "la lista completa está en /swagger-ui.html");
    }

    // El documento de roles exige un mensaje claro cuando falta un permiso, no un error opaco
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail permisoDenegado(AccessDeniedException ex, WebRequest request) {
        log.warn("Permiso denegado - Path: {}, Message: {}", request.getDescription(false), ex.getMessage());
        return construir(HttpStatus.FORBIDDEN, "Permiso denegado", "permiso-denegado",
                "No tienes permiso para hacer esto. Si crees que deberías, pide el acceso al administrador.");
    }

    /* Mandar un código que no existe —una familia, un nivel, un puesto— reventaba con un 500
       mudo: la base rechaza la clave foránea y nadie traducía ese fallo. Es un dato malo de
       quien llama, no una avería del sistema, así que es un 400 que dice cuál es el dato. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail datoQueNoCuadra(DataIntegrityViolationException ex, WebRequest request) {
        log.warn("Integridad de datos - Path: {}, Message: {}",
                request.getDescription(false), ex.getMostSpecificCause().getMessage());
        return construir(HttpStatus.BAD_REQUEST, "Hay un dato que no cuadra",
                "dato-invalido", explicar(ex));
    }

    /* El mensaje de Postgres trae el nombre de la restricción y el valor culpable. Se saca lo
       justo para que quien lo lea sepa qué corregir, sin enseñar el SQL entero. */
    private static String explicar(DataIntegrityViolationException ex) {
        String causa = ex.getMostSpecificCause().getMessage();
        if (causa == null) return "Alguno de los datos enviados no es válido.";

        Matcher clave = Pattern.compile("Key \\((\\w+)\\)=\\(([^)]*)\\)").matcher(causa);
        String campo = clave.find() ? clave.group(1) : null;
        String valor = campo != null ? clave.group(2) : null;

        if (causa.contains("is not present in table")) {
            return campo != null
                    ? "El valor «%s» de %s no existe. Consulta los valores admitidos en /panel/catalogos."
                            .formatted(valor, campo)
                    : "Uno de los códigos enviados no existe en el sistema.";
        }
        if (causa.contains("duplicate key") || causa.contains("already exists")) {
            return campo != null
                    ? "Ya existe un registro con %s = «%s».".formatted(campo, valor)
                    : "Ya existe un registro con esos datos.";
        }
        if (causa.contains("violates not-null")) {
            Matcher col = Pattern.compile("column \"(\\w+)\"").matcher(causa);
            return col.find()
                    ? "Falta un dato obligatorio: %s.".formatted(col.group(1))
                    : "Falta un dato obligatorio.";
        }
        if (causa.contains("violates check constraint")) {
            return "Alguno de los valores enviados no está entre los admitidos. "
                    + "Consulta los valores válidos en /panel/catalogos.";
        }
        return "Alguno de los datos enviados no es válido.";
    }

    static ProblemDetail construir(HttpStatus estado, String titulo, String sufijoTipo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setType(URI.create(BASE_URI + sufijoTipo));
        problema.setProperty("Timestamp", Instant.now());
        return problema;
    }
}
