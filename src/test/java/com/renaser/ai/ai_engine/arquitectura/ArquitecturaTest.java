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
     * <p>El motor de agentes ({@code ai/}) queda fuera de esta comprobación. Desde el
     * 24/08/2026 sus rutas piden token de equipo, y {@code ConfiguracionSwagger} les pone el
     * candado reconociéndolas por el <b>nombre del paquete</b> {@code ai.controller} — no por
     * clase, porque importarlas allí rompería la regla de la frontera de aquí arriba.
     *
     * <p><b>Ese reconocimiento no lo comprueba nadie</b>, y conviene decirlo en vez de
     * suponerlo: un controlador nuevo del motor colocado fuera de {@code ai.controller} se
     * quedaría sin candado en Swagger sin que falle ninguna prueba. Se acepta a sabiendas
     * porque ahí no nacen controladores casi nunca; si eso cambia, esta regla tendrá que
     * cubrir también {@code ai/}.
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

    // ========================================================================
    // El aislamiento entre empresas (pieza B del multiempresa)
    // ========================================================================

    /**
     * Los repositorios de agregados «con dueño»: sus filas pertenecen a una organización,
     * directamente o a través de su padre. Buscar en ellos por id suelto desde un servicio
     * del panel es la fuga típica entre empresas — invisible mientras solo exista una.
     */
    private static final Set<String> REPOSITORIOS_CON_DUENO = Set.of(
            RAIZ + ".vacante.repository.VacanteRepository",
            RAIZ + ".vacante.repository.PuestoRepository",
            RAIZ + ".postulacion.repository.PostulacionRepository",
            RAIZ + ".solicitud.repository.SolicitudTalentoRepository",
            RAIZ + ".pesos.repository.VersionPesosRepository",
            RAIZ + ".perfilintegral.repository.PlantillaEvaluacionRepository",
            RAIZ + ".prueba.repository.PlantillaPruebaRepository",
            RAIZ + ".prueba.repository.VersionPlantillaPruebaRepository",
            RAIZ + ".archivo.repository.ArchivoRepository",
            RAIZ + ".simulacion.repository.SesionSimulacionRepository",
            RAIZ + ".simulacion.repository.InscripcionSesionRepository",
            RAIZ + ".decision.repository.BarreraCriticaRepository",
            RAIZ + ".consentimiento.repository.SolicitudBorradoRepository",
            RAIZ + ".usuario.repository.InvitacionRepository");

    /**
     * Las llamadas {@code findById} sobre esos repositorios que SÍ están bien, una por una
     * y con su porqué. La lista es la aduana: una llamada nueva falla esta prueba hasta que
     * alguien la escriba aquí, y escribirla obliga a mirar si de verdad no filtra.
     *
     * <p>Casi todas se salvan por el mismo patrón — <b>derivar al padre</b>: la fila se
     * busca por id suelto, pero su id no vino del cliente sino de una fila ya validada
     * (la postulación de la organización, la inscripción de una sesión propia), o la
     * organización se comprueba acto seguido a través del padre. Lo ajeno termina en 404
     * igual. Los otros dos motivos, señalados en su grupo: el portal filtra por persona,
     * no por organización; y el borrado 29733 es de la plataforma y ya pasó su aduana.
     */
    private static final Set<String> LLAMADAS_SIN_DUENO_ACORDADAS = Set.of(
            // El guardián compartido, al que están migrando los laVisible de cada panel: la
            // postulación ya se resolvió con findByIdAndOrganizacionId y su vacante se pide por
            // id solo para saber de quién es. Es el mismo caso de los de abajo, en un solo
            // sitio. Ojo al escribirlo: tiene que ser vacantes.findById(...) literal, porque
            // esta regla recorre las LLAMADAS y una referencia a método (vacantes::findById)
            // se le escapa sin que nadie lo haya decidido.
            "AlcanceSobreLaVacante#alcanzaA",
            // El guardián laVisible de cada panel: la postulación ya se resolvió con
            // findByIdAndOrganizacionId; su vacante se pide por id solo para comprobar el
            // alcance SUS_VACANTES. Derivar al padre en su forma más pura.
            // Llegó con el desglose de la evaluación (PR #38, en paralelo al multiempresa):
            // mismo patrón exacto que los de arriba. La regla lo denunció al fusionar — que
            // es justo para lo que existe — y la lectura confirmó que deriva del padre.
            // Derivan de una postulación o vacante ya validada por su guardián: la
            // vacante de la postulación, el puesto de la vacante, la plantilla que la
            // vacante tiene asignada (y que se validó contra el dueño al asignarla).
            "ServicioPostulacionesPanelImpl#confirmarAvance",
            "ServicioPostulacionesPanelImpl#ficha",
            "ServicioPerfilIntegralPanelImpl#pesosDe",
            "ServicioPerfilIntegralPanelImpl#ranking",
            "ServicioDecisionImpl#calcular",
            "ServicioDecisionImpl#decidir",
            "ServicioEvaluacionImpl#crearAlPostular",
            "ServicioEvaluacionImpl#pintar",
            "ServicioVacantesPanelImpl#asignarPlantillaEvaluacion",
            "ServicioVacantesPanelImpl#definirCierrePrueba",
            "ServicioVacantesPanelImpl#fechaDeCierreDe",
            "ServicioPruebaImpl#laVersion",
            "CalificacionPorCriterio#calcularNotaEtapa",
            "CalificacionPorCriterio#maximosDe",
            "MaquinaEstados#avisarAlCandidato",
            "MaquinaEstados#loDeLaPrueba",
            // Buscan por id suelto Y COMPRUEBAN al dueño en la línea siguiente: son la
            // implementación misma del patrón, no una excepción a él.
            "ServicioSimulacionImpl#laInscripcion",
            "ServicioSimulacionImpl#inscribirse",
            "ServicioSimulacionImpl#marcarAsistencia",
            "ServicioSimulacionImpl#miSesion",
            "ServicioPlantillaPruebaImpl#laVersionVisible",
            "ServicioPlantillaPruebaImpl#laVersionEnBorrador",
            "ServicioVacantesPanelImpl#asignarPlantillaPrueba",
            "ServicioDecisionImpl#registrarBarreraDetectada",
            // El portal: el candidato es de la plataforma y sus cosas se filtran por
            // persona, no por organización. Y el tablón de vacantes publicadas es LA
            // excepción deliberada del spec B: se ve el de todas las empresas.
            //
            // Ojo: desde el corte del portal las dos entradas del tablón son INERTES —
            // la regla solo inspecciona clases con algún método que reciba
            // ContextoUsuario, y el tablón (público, sin token) no tiene ninguno. Se
            // quedan como constancia de la excepción deliberada, y por si el tablón
            // algún día recibe contexto y vuelve bajo el aduanero.
            "ServicioTablonPortalImpl#vacante",
            "ServicioPostulacionPortalImpl#postular",
            "ServicioPostulacionPortalImpl#comoResumen",
            // El texto legal de la empresa de una vacante PUBLICADA: es del tablón, la
            // misma excepción deliberada que #vacante, y con el mismo filtro de estado.
            "ServicioTablonPortalImpl#consentimientoDeVacante",
            // El borrado 29733 es de la plataforma: exigirPlataforma ya cerró la puerta
            // antes de estas búsquedas, y la solicitud cruza empresas a propósito.
            "ServicioBorradoDatosImpl#ejecutarBorrado");

    /**
     * Ningún servicio del panel busca por id suelto en un agregado con dueño.
     *
     * <p>La regla de la pieza B: toda consulta del panel entra por la organización de quien
     * pregunta ({@code findByIdAndOrganizacionId} o un guardián que derive al padre), y lo
     * ajeno responde 404. Esta prueba convierte el olvido en compilación rota: con una sola
     * empresa una consulta sin filtrar funciona idéntico, y nadie lo nota hasta que la
     * segunda empresa ve los datos de la primera.
     *
     * <p>Se vigilan los servicios que reciben {@link com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario}:
     * ahí hay un usuario de una organización concreta preguntando. Los procesos sin nadie
     * conectado (cola, barridos) toman la organización de cada trabajo y quedan fuera.
     *
     * <p><b>Lo que esta regla no ve</b>, dicho para que nadie lo suponga cubierto: escribir
     * una clave foránea ajena sin buscarla antes (no hay llamada que inspeccionar), y las
     * consultas derivadas con nombre distinto de {@code findById}. La prueba de las dos
     * empresas ({@code FlujoDosEmpresasIT}) es la otra mitad del vigilante.
     */
    @Test
    void ningunServicioDelPanelBuscaPorIdSueltoEnUnAgregadoConDueno() {
        ArchRule regla = classes()
                .that().resideInAPackage("..service..")
                .should(new ArchCondition<>("pasar por el guardián de organización al buscar por id") {
                    @Override
                    public void check(JavaClass clase, ConditionEvents eventos) {
                        boolean recibeContexto = clase.getMethods().stream()
                                .anyMatch(m -> m.getRawParameterTypes().stream()
                                        .anyMatch(t -> t.getName()
                                                .equals(RAIZ + ".seguridad.dto.ContextoUsuario")));
                        if (!recibeContexto) {
                            return;
                        }
                        for (var llamada : clase.getMethodCallsFromSelf()) {
                            // Se compara el dueño de la llamada porque findById vive en
                            // CrudRepository: lo que identifica al repositorio es el tipo
                            // sobre el que se invoca, no dónde está declarado el método.
                            if (!"findById".equals(llamada.getName())
                                    || !REPOSITORIOS_CON_DUENO.contains(
                                            llamada.getTargetOwner().getName())) {
                                continue;
                            }
                            String donde = clase.getSimpleName() + "#"
                                    + sinEnvoltorioDeLambda(llamada.getOrigin().getName());
                            if (LLAMADAS_SIN_DUENO_ACORDADAS.contains(donde)) {
                                continue;
                            }
                            eventos.add(SimpleConditionEvent.violated(clase, donde
                                    + " llama a " + llamada.getTargetOwner().getSimpleName()
                                    + ".findById sin pasar por la organización: con dos "
                                    + "empresas, eso lee datos de la otra"));
                        }
                    }
                })
                .because("toda consulta del panel entra por la organización de quien pregunta, "
                        + "y lo que no es suyo responde 404; el findById suelto es la fuga que "
                        + "una sola empresa jamás delata");

        regla.check(codigo);
    }

    /** Una llamada dentro de una lambda llega como {@code lambda$metodo$0}: se firma con el método. */
    private static String sinEnvoltorioDeLambda(String nombreDeOrigen) {
        if (nombreDeOrigen.startsWith("lambda$")) {
            return nombreDeOrigen.substring("lambda$".length(), nombreDeOrigen.lastIndexOf('$'));
        }
        return nombreDeOrigen;
    }
}
