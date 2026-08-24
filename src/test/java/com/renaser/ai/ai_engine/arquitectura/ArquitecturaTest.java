package com.renaser.ai.ai_engine.arquitectura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.renaser.ai.ai_engine.comun.config.ConfiguracionSwagger;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Las reglas de arquitectura que el proyecto ya tenía escritas, ahora comprobadas.
 *
 * <p>Todas salen del {@code CLAUDE.md} y de los documentos de {@code docs/}. Estaban en
 * prosa, y una regla en prosa se rompe sin que nadie se entere: alguien añade un import, el
 * código compila, las pruebas pasan y la frontera ya no existe. Estas pruebas no inventan
 * reglas nuevas, solo ponen a fallar las que ya estaban acordadas.
 *
 * <p><b>Por qué importa aquí más que en otros proyectos.</b> Este repositorio tiene dos
 * mitades que mantienen dos personas distintas —el motor de agentes bajo {@code ai/} y la
 * selección de personal en el resto— y la frontera entre ellas es un acuerdo, no un muro.
 * Sin algo que la vigile, el acuerdo dura hasta el primer atajo con prisa.
 */
@DisplayName("Las reglas de arquitectura que el proyecto ya tenía escritas")
class ArquitecturaTest {

    private static final String RAIZ = "com.renaser.ai.ai_engine";

    /**
     * Lo único que la selección puede usar del motor de agentes.
     *
     * <p>El CLAUDE.md decía «una sola clase». La lista creció al construir la criba, porque
     * la selección necesita encolar trabajos, y crecer está bien: lo que no puede es crecer
     * sin que nadie lo decida. Añadir una clase más falla esta prueba hasta que se escriba
     * aquí, y escribirla aquí obliga a mirar si la frontera sigue teniendo sentido.
     */
    private static final Set<String> ACORDADAS = Set.of(
            RAIZ + ".ai.exception.ResourceNotFoundException",
            RAIZ + ".ai.service.ColaCalificacionIa",
            RAIZ + ".ai.service.ColaCalificacionIa$Estado",
            RAIZ + ".ai.service.AgenteSeleccion",
            RAIZ + ".ai.service.EjecutorAgenteIa",
            RAIZ + ".ai.service.EjecutorAgenteIa$Ejecutado",
            RAIZ + ".ai.model.TrabajoIa",
            RAIZ + ".ai.service.impl.ColaCalificacionIaImpl",
            // Estos dos no los usa la selección: los nombra. El CLAUDE.md obliga a que
            // ManejadorErrores y ConfiguracionSwagger enumeren los controladores del motor,
            // para que sus errores no salgan como 500 y sus endpoints lleven candado. Es lo
            // contrario de una fuga: es la frontera declarándose.
            RAIZ + ".ai.controller.perfilintegral.AgentesIaPanelController");

    private static JavaClasses codigo;

    @BeforeAll
    static void leerElCodigo() {
        codigo = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RAIZ);
    }

    // ========================================================================
    // La frontera entre las dos mitades
    // ========================================================================

    /**
     * El CLAUDE.md dice que la selección usa <b>una sola clase</b> del motor de agentes, y
     * que conviene saber cuáles son porque si se mueven, se rompen.
     *
     * <p>Desde entonces la lista creció al construir la criba: la selección necesita
     * encolar trabajos. Se enumera aquí entera, y esta prueba es la que obliga a que crecer
     * sea una decisión y no un descuido: añadir una clase más falla hasta que alguien la
     * escriba en esta lista.
     */
    @Test
    void laSeleccionSoloCruzaLaFronteraPorLasClasesAcordadas() {
        DescribedPredicate<JavaClass> delMotorSinAcordar =
                new DescribedPredicate<>("del motor de agentes y no acordadas") {
                    @Override
                    public boolean test(JavaClass clase) {
                        return clase.getPackageName().startsWith(RAIZ + ".ai")
                                && !ACORDADAS.contains(clase.getFullName());
                    }
                };

        ArchRule regla = noClasses()
                .that().resideOutsideOfPackage(RAIZ + ".ai..")
                .should().dependOnClassesThat(delMotorSinAcordar)
                .because("la frontera con el motor de agentes es un acuerdo entre dos personas: "
                        + "cada clase nueva que se cruce tiene que estar escrita aquí y en el "
                        + "CLAUDE.md, o deja de ser una frontera");

        regla.check(codigo);
    }

    // ========================================================================
    // Las capas dentro de cada dominio
    // ========================================================================

    /**
     * Sin excepciones. Los dos controladores que se la saltaban ya no lo hacen.
     *
     * <p>Estuvieron nombrados aquí un tiempo, con su motivo escrito, y eso fue lo que hizo
     * que se arreglaran: una desviación a la vista se decide, una escondida tras un patrón
     * genérico se olvida. {@code CatalogoController} pide sus listas a
     * {@code ServicioCatalogo} y {@code PanelAuthController} el arranque del primer usuario
     * a {@code ServicioAccesoEquipo}.
     */
    @Test
    void ningunControladorHablaDirectamenteConUnRepositorio() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("entre la petición y la base hay reglas que cumplir —permisos con "
                        + "alcance, transiciones, auditoría— y el servicio es donde viven. Un "
                        + "controlador que va directo se las salta todas sin que se note");

        regla.check(codigo);
    }

    @Test
    void ningunRepositorioSabeDeUnServicio() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..service..")
                .because("un repositorio que llama a un servicio cierra un círculo: nadie "
                        + "puede leer una consulta sin abrir media aplicación");

        regla.check(codigo);
    }

    // ========================================================================
    // La regla más cara de romper: el estado de una postulación
    // ========================================================================

    /**
     * «Toda transición pasa por {@code MaquinaEstados.transicionar}; nunca tocar
     * {@code postulacion.estado_codigo} a mano.» Es la primera regla del CLAUDE.md.
     *
     * <p>Saltársela no da error: la postulación cambia de estado y ya está. Lo que no pasa
     * es el registro inmutable de quién la movió y por qué, ni el correo al candidato, ni la
     * auditoría. Se descubre semanas después, cuando alguien pregunta por qué un candidato
     * está donde está y no hay forma de saberlo.
     */
    @Test
    void soloLaMaquinaDeEstadosCambiaElEstadoDeUnaPostulacion() {
        ArchRule regla = noClasses()
                .that().resideOutsideOfPackage(RAIZ + ".postulacion.service..")
                .and().resideOutsideOfPackage(RAIZ + ".portal..")
                .should().callMethod(Postulacion.class, "setEstadoCodigo", String.class)
                .because("cambiar el estado a mano se salta el registro de quién lo movió, el "
                        + "correo al candidato y la auditoría. El portal es la excepción "
                        + "acordada: ahí nace la postulación, y nacer no es una transición");

        regla.check(codigo);
    }

    // ========================================================================
    // Que cada cosa esté donde se la busca
    // ========================================================================

    @Test
    void cadaClaseEstaEnElPaqueteQueSuNombrePromete() {
        classes().that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..controller..")
                .because("el CLAUDE.md describe un paquete por dominio con las mismas capas "
                        + "dentro, y quien busca un endpoint mira en controller")
                .check(codigo);

        classes().that().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..repository..")
                .check(codigo);
    }

    /**
     * La entidad no viaja por la API; lo que viaja es un dto.
     *
     * <p>También sin excepciones, y por lo mismo: al mover las consultas al servicio, las
     * entidades se quedaron detrás de él y los controladores solo ven dto.
     */
    @Test
    void lasEntidadesDeLaBaseNoSalenPorUnEndpoint() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..entity..")
                .because("una entidad que sale por la API convierte cualquier columna nueva en "
                        + "un cambio de contrato, y expone campos que nadie decidió publicar. "
                        + "Para eso están los dto");

        regla.check(codigo);
    }

    // ========================================================================
    // El candado de Swagger
    // ========================================================================

    /**
     * {@code ConfiguracionSwagger} avisa en su javadoc: «al añadir un controlador nuevo hay
     * que sumarlo aquí, o sus endpoints saldrán en Swagger sin candado». Un aviso en prosa
     * dura hasta el primer despiste; esta prueba lo convierte en compilación rota.
     *
     * <p>El motor de agentes ({@code ai/}) queda fuera de <b>esta comprobación</b>, no del
     * candado: desde el 24/08/2026 sus rutas piden token de equipo en la cadena
     * {@code @Order(3)} de {@code ConfiguracionSeguridad}, y sus controladores están en la
     * lista de {@code ConfiguracionSwagger}. La regla sigue mirando solo fuera de {@code ai/}
     * porque es ahí donde nacen controladores nuevos a diario.
     */
    @Test
    void todoControladorNuevoEstaEnLaListaDelCandadoDeSwagger() {
        Set<String> cubiertos = ConfiguracionSwagger.paquetesConCandado();

        ArchRule regla = classes()
                .that().areAnnotatedWith(RestController.class)
                .and().resideOutsideOfPackage(RAIZ + ".ai..")
                .should(new ArchCondition<>("estar en un paquete que ConfiguracionSwagger enumera") {
                    @Override
                    public void check(JavaClass clase, ConditionEvents eventos) {
                        String paquete = clase.getPackageName();
                        boolean cubierto = cubiertos.stream()
                                .anyMatch(c -> paquete.equals(c) || paquete.startsWith(c + "."));
                        if (!cubierto) {
                            eventos.add(SimpleConditionEvent.violated(clase, clase.getFullName()
                                    + " no está en la lista de ConfiguracionSwagger: sus endpoints"
                                    + " saldrían en Swagger sin candado"));
                        }
                    }
                })
                .because("la lista de ConfiguracionSwagger es la que pone el candado del token "
                        + "en Swagger, y un controlador fuera de ella publica endpoints que "
                        + "parecen abiertos");

        regla.check(codigo);
    }

    // ========================================================================
    // Higiene
    // ========================================================================

    @Test
    void nadieEscribeEnLaConsolaAPelo() {
        ArchRule regla = noClasses()
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .because("lo que se imprime así no aparece en el registro, y el registro es lo "
                        + "único que queda cuando algo falla en producción");

        regla.check(codigo);
    }
}
