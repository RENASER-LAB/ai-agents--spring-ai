package com.renaser.ai.ai_engine.postulacion.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.*;
import com.renaser.ai.ai_engine.archivo.repository.*;
import com.renaser.ai.ai_engine.archivo.service.*;
import com.renaser.ai.ai_engine.auditoria.entity.*;
import com.renaser.ai.ai_engine.auditoria.repository.*;
import com.renaser.ai.ai_engine.auditoria.service.*;
import com.renaser.ai.ai_engine.notificacion.entity.*;
import com.renaser.ai.ai_engine.notificacion.repository.*;
import com.renaser.ai.ai_engine.notificacion.service.*;
import com.renaser.ai.ai_engine.parametro.entity.*;
import com.renaser.ai.ai_engine.parametro.repository.*;
import com.renaser.ai.ai_engine.parametro.service.*;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.usuario.service.NombresDeUsuarios;
import com.renaser.ai.ai_engine.postulacion.service.ServicioPostulacionesPanel;

import com.renaser.ai.ai_engine.simulacion.service.ServicioDisponibilidadSimulacion;
import com.renaser.ai.ai_engine.validacion.service.ServicioValidacion;
import com.renaser.ai.ai_engine.postulacion.dto.DtosPostulacion.*;
import com.renaser.ai.ai_engine.postulacion.entity.*;
import com.renaser.ai.ai_engine.postulacion.repository.DatoCvRepository;
import com.renaser.ai.ai_engine.postulacion.repository.*;
import com.renaser.ai.ai_engine.postulacion.service.*;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.service.Remuneracion;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.vacante.service.VacanteArchivada;
import com.renaser.ai.ai_engine.vacante.service.VacanteEliminada;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServicioPostulacionesPanelImpl implements ServicioPostulacionesPanel {

    private static final Set<String> ESPERAS = Set.of("CANDIDATO", "SISTEMA", "TALENTO", "AREA");
    private static final String ETAPA_PRUEBA = "PRUEBA_PUESTO";
    private static final String ETAPA_VALIDACION = "VALIDACION";
    private static final String VALIDACION_POR_HABILITAR = "VALIDACION_POR_HABILITAR";

    private final PostulacionRepository postulaciones;
    private final EstadoPostulacionRepository estados;
    private final TransicionEstadoRepository transiciones;
    private final VacanteRepository vacantes;
    private final AlcanceSobreLaVacante alcanceVacante;
    private final UsuarioRepository usuarios;
    private final NombresDeUsuarios nombres;
    private final CvRepository cvs;
    private final EnlaceCvRepository enlaces;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final MaquinaEstados maquina;
    private final Permisos permisos;
    private final EntradaEtapaTecnica entradaTecnica;
    private final ServicioValidacion validacion;
    private final ServicioDisponibilidadSimulacion disponibilidad;
    private final DatoCvRepository datosCv;
    private final ServicioAuditoria auditoria;
    private final ServicioEnlaceAcceso enlacesDeAcceso;
    private final com.renaser.ai.ai_engine.decision.service.QuienPuedeContratar quienPuedeContratar;
    private final com.renaser.ai.ai_engine.colaborador.service.ContratacionEnLaFicha contratacion;

    @Override
    public List<FilaBandeja> bandeja(ContextoUsuario quien, String esperaA) {
        if (!ESPERAS.contains(esperaA)) {
            throw new IllegalArgumentException("espera_a tiene que ser CANDIDATO, SISTEMA, TALENTO o AREA");
        }
        FiltroAlcance alcance = permisos.alcanceDe("ver_candidatos");
        // PROPIO no alcanza a nadie aquí: en el panel ninguna postulación es de quien mira.
        // Sin esta línea la consulta recibiría un filtro nulo —responsableOFiltroNulo solo
        // distingue SUS_VACANTES— y enseñaría la bandeja entera, justo lo contrario. No era
        // alcanzable mientras el reparto se tocaba a mano en la base; desde que los permisos se
        // editan por el panel, basta un PUT sobre ver_candidatos.
        if (alcance.noAlcanzaANadieEnElPanel()) {
            return List.of();
        }
        Map<String, EstadoPostulacion> catalogo = catalogoEstados();
        List<Postulacion> filas = postulaciones.bandeja(
                quien.organizacionId(), esperaA, alcance.responsableOFiltroNulo());

        // Quién es cada candidato y a qué vacante se apuntó se resuelven para la tanda entera,
        // antes de mapear, y no una vez por fila.
        //
        // Preguntarlo dentro del map costaba tres viajes a la base por postulación. Ninguno es
        // lento —son búsquedas por clave primaria—, pero van en serie y contra Supabase cada
        // uno cuesta ~140 ms de ida y vuelta: con las 236 postulaciones de referencia salían
        // 709 consultas encadenadas y minuto y medio de espera. Peor que la espera: esa
        // petición retiene una conexión del pool todo ese rato, así que la bandeja no se
        // colgaba sola, se llevaba por delante al resto del panel.
        //
        // Así son cuatro consultas fijas, haya una fila o quinientas: dos las hace
        // NombresDeUsuarios por dentro, y sigue siendo por tanda.
        Map<Long, String> candidatos = nombres.porUsuario(idsDe(filas, Postulacion::getUsuarioId));
        Map<Long, Vacante> porVacante = porId(
                vacantes.findAllById(idsDe(filas, Postulacion::getVacanteId)), Vacante::getId);

        return filas.stream()
                .map(p -> filaBandeja(p, catalogo, candidatos, porVacante))
                .toList();
    }

    @Override
    public ConteoEmbudo embudo(ContextoUsuario quien, Long vacanteId) {
        vacanteVisible(quien, vacanteId, "ver_embudo");
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (Object[] fila : postulaciones.embudo(vacanteId)) {
            conteo.put((String) fila[0], (Long) fila[1]);
        }
        return new ConteoEmbudo(conteo);
    }

    @Override
    public FichaPostulacion ficha(ContextoUsuario quien, Long postulacionId) {
        Postulacion p = laVisible(quien, postulacionId, "abrir_ficha_candidato");
        Usuario usuario = usuarios.findById(p.getUsuarioId()).orElseThrow();
        String candidato = nombres.de(usuario.getId());
        Vacante laVacante = vacantes.findById(p.getVacanteId()).orElse(null);
        String vacante = laVacante == null ? "" : laVacante.getTitulo();
        EstadoPostulacion suEstado = estados.findById(p.getEstadoCodigo()).orElse(null);
        String nombreEstado = suEstado == null ? p.getEstadoCodigo() : suEstado.getNombre();

        Cv cv = cvs.findByPostulacionId(p.getId()).orElse(null);
        List<String> urls = cv == null ? List.of()
                : enlaces.findByCvId(cv.getId()).stream().map(EnlaceCv::getUrl).toList();

        // La pretensión, bajo el mismo permiso de siempre. Se resuelve en dos pasos —la cifra
        // y, si no la hay, por qué— porque un hueco a secas se lee como «no la declaró», que
        // es el único de los tres motivos que acusa al candidato.
        // Las dos llaves: el permiso, y que la vacante publique lo que paga. La segunda es
        // la misma regla del ranking y del perfil — si no enseñas lo que pagas, no ves lo que
        // piden. Aquí hoy sería redundante (una vacante oculta nunca guarda pretensión), pero
        // escribirla explícita es lo que impide que la regla se quede a medias mañana.
        boolean vePretension = quien.tiene("ver_pretension")
                && laVacante != null && Remuneracion.laEnsena(laVacante);
        String suPretension = vePretension
                ? Remuneracion.escribirPretension(p.getPretensionMonto(), p.getPretensionMoneda())
                : null;
        String porQueSin = porQueSinPretension(vePretension, p, laVacante);
        var vinculo = contratacion.de(quien, p);

        return new FichaPostulacion(p.getId(), p.getUuid().toString(), candidato, usuario.getCorreo(),
                vacante, p.getEstadoCodigo(), nombreEstado, p.getGrupoPrioridad(), p.getMotivoCierre(),
                cv == null ? null : cv.getResultadoOrgulloso(), urls,
                cv == null ? null : cv.getArchivoOriginalId(), p.getCreadoEn(), p.getMovidoEn(),
                quien.tiene("mover_postulacion"),
                p.getPretensionMonto() == null ? null : suPretension,
                porQueSin,
                // Contratar desde la ficha (V64): la decisión en verde, con el permiso de siempre.
                laVacante != null && laVacante.getEliminadaEn() == null
                        && quienPuedeContratar.puede(quien, p, suEstado != null && suEstado.isEsFinal()),
                vinculo.colaboradorId(), vinculo.puedeDarDeAlta(), vinculo.puedeVerColaborador());
    }

    /**
     * Por qué esta ficha no enseña ninguna pretensión.
     *
     * <p>Tres motivos y solo uno es verdad cada vez, y no se distinguen desde fuera: quien
     * pinta un hueco sin explicarlo está afirmando el tercero —«no quiso decirlo»—, que es el
     * único que acusa al candidato de algo.
     *
     * @return la frase, o {@code null} si sí hay pretensión que enseñar
     */
    private String porQueSinPretension(boolean vePretension, Postulacion p, Vacante vacante) {
        if (!vePretension) {
            return "Tu rol no puede ver la pretensión salarial: solo Dirección la ve, para "
                    + "que el sueldo no pese al calificar. El dato ni se consultó.";
        }
        if (p.getPretensionMonto() != null) {
            return null;
        }
        if (vacante != null && !Remuneracion.laEnsena(vacante)) {
            return "Esta vacante no publica su remuneración, así que no se le pidió la suya: "
                    + "quien no enseña lo que paga tampoco pregunta lo que piden.";
        }
        // Queda el caso de quien postuló ANTES de que la vacante publicara su sueldo —o antes
        // de que esto existiera—. El trato se juzga con las reglas del día que postuló, y por
        // eso tampoco se le puede acusar de reservado.
        return "No la declaró: cuando postuló, esta vacante todavía no publicaba su "
                + "remuneración.";
    }

    @Override
    public List<PasoHistorial> historial(ContextoUsuario quien, Long postulacionId) {
        laVisible(quien, postulacionId, "abrir_ficha_candidato");
        return transiciones.findByPostulacionIdOrderByOcurridaEnAsc(postulacionId).stream()
                .map(t -> new PasoHistorial(t.getEstadoAnteriorCodigo(), t.getEstadoNuevoCodigo(),
                        t.getUsuarioId(), t.isEsSistema(), t.isEsPorLote(), t.getMotivo(), t.getOcurridaEn()))
                .toList();
    }

    @Override
    @Transactional
    public void transicionar(ContextoUsuario quien, Long postulacionId, Transicionar datos) {
        Postulacion p = laVisible(quien, postulacionId, "mover_postulacion");
        exigirVacanteNoArchivada(p);
        // Si el destino es un cierre, hace falta decir de qué clase
        String motivoCierre = datos.motivoCierre();
        if ("CERRADA".equals(datos.estadoDestino()) && motivoCierre == null) {
            motivoCierre = "CIERRE_MANUAL";
        }
        if ("NO_CONTINUA".equals(datos.estadoDestino()) && motivoCierre == null) {
            motivoCierre = "DECISION_PERSONA";
        }
        // Nulo es avisar: quien no dijo nada quiere lo de siempre.
        boolean avisar = datos.avisar() == null || datos.avisar();
        // Moverla a mano a la prueba o a Validación desde otra etapa le crea lo que necesita
        // allí, igual que «Avanzar»: sin eso la prueba se quedaba en blanco con el correo ya
        // enviado, y el periodo de validación contestaba 404 a todo. El destino elegido se
        // respeta, salvo «Validación · por habilitar» con un periodo ya iniciado o cerrado.
        Destino destino = entrar(p, datos.estadoDestino(), datos.motivo());
        maquina.transicionar(p, destino.estado(), quien, destino.motivo(), false, false,
                motivoCierre, avisar);
    }

    @Override
    @Transactional
    public void confirmarAvance(ContextoUsuario quien, Long postulacionId, String motivo) {
        Postulacion p = laVisible(quien, postulacionId, "confirmar_avance");
        exigirVacanteNoArchivada(p);
        EstadoPostulacion siguiente = maquina.siguiente(p.getEstadoCodigo())
                .orElseThrow(() -> new IllegalStateException(
                        "Desde " + p.getEstadoCodigo() + " no hay un avance que calcular: "
                                + "usa una transición manual con motivo"));

        // Al entrar a la prueba o a validación se le crea lo que necesita allí —o se
        // reutiliza lo que ya tenía, si vuelve—. Ver `entrar`.
        Destino destino = entrar(p, siguiente.getCodigo(), motivo);
        maquina.transicionar(p, destino.estado(), quien, destino.motivo(), false, false, null);

        // Entrar a simulación no crea nada: la inscripción la elige el candidato. Lo que sí
        // hace falta es mirar si ya hay una sesión con cupo para su vacante, porque de eso
        // depende si se queda esperando o puede elegir ya.
        if ("SIMULACION_POR_HABILITAR".equals(destino.estado())) {
            disponibilidad.recalcularVacante(p.getOrganizacionId(), p.getVacanteId());
        }
    }

    /** El estado al que entra de verdad la postulación, y el motivo que queda escrito. */
    private record Destino(String estado, String motivo) {}

    /**
     * Lo que pasa al entrar a una etapa desde otra: «Avanzar» y el movimiento manual comparten
     * las reglas, y por eso viven en un solo sitio.
     *
     * <ul>
     *   <li><b>La prueba del puesto</b>: se crea lo que la vacante rinde —el intento o el
     *       cuestionario técnico— o se reutiliza el que ya tenía, con las reglas de
     *       {@link EntradaEtapaTecnica}. Sin prueba lista, 409 con el mismo mensaje que
     *       «Avanzar», y nada se mueve ni se avisa.
     *   <li><b>Validación</b>: se crea el periodo «por habilitar» o se reutiliza el que tenía
     *       sin tocarlo. Si se entra por «por habilitar», la persona va al paso que
     *       corresponde a ese periodo, con una coletilla en el motivo que lo explica. Con un
     *       movimiento manual a otro paso de Validación, ese paso se respeta.
     * </ul>
     *
     * <p>Moverse dentro de la misma etapa no crea ni redirige nada, y de un estado final no se
     * sale: esa negativa la da la máquina de estados con su mensaje, sin crear nada antes.
     *
     * <p><b>Todo o nada.</b> Corre en la transacción del llamador y antes de la transición:
     * si crear falla, no se guarda ni lo creado, ni la transición, ni sale el correo.
     */
    private Destino entrar(Postulacion p, String destino, String motivo) {
        EstadoPostulacion origen = estados.findById(p.getEstadoCodigo()).orElse(null);
        String etapaDestino = estados.findById(destino)
                .map(EstadoPostulacion::getEtapaCodigo).orElse(null);
        if (origen == null || origen.isEsFinal() || etapaDestino == null
                || etapaDestino.equals(origen.getEtapaCodigo())) {
            return new Destino(destino, motivo);
        }

        if (ETAPA_PRUEBA.equals(etapaDestino)) {
            exigirQueSigaDondeEstaba(p);
            // Lo comparte con el pase automático, que llega aquí sin usuario del que sacar la
            // organización: por eso la saca de la postulación. Ver EntradaEtapaTecnica.
            entradaTecnica.crearAlEntrar(p, laVacanteDe(p));
            return new Destino(destino, motivo);
        }

        if (ETAPA_VALIDACION.equals(etapaDestino)) {
            exigirQueSigaDondeEstaba(p);
            // El periodo nace en POR_HABILITAR: alguien tiene que decidir la modalidad —y, si
            // es trabajo real, registrar la figura contractual— antes de que empiece a correr.
            ServicioValidacion.Entrada entrada =
                    validacion.crearAlEntrar(p.getId(), p.getOrganizacionId());
            if (!VALIDACION_POR_HABILITAR.equals(destino)) {
                return new Destino(destino, motivo);
            }
            return new Destino(entrada.paso(), entrada.motivoCon(motivo));
        }

        return new Destino(destino, motivo);
    }

    /**
     * Que nadie la haya movido desde que se leyó, y que nadie la mueva hasta terminar.
     *
     * <p>Dos entradas a la vez —dos «Avanzar», o un «Avanzar» y un movimiento manual— leían
     * el mismo estado de partida. Si las dos creaban, la clave única paraba a una; pero si las
     * dos reutilizaban, las dos escribían su transición. Con la fila bloqueada, la segunda
     * espera a la primera, ve que el estado ya cambió y se planta sin escribir nada.
     */
    private void exigirQueSigaDondeEstaba(Postulacion p) {
        String guardado = postulaciones.estadoBloqueandoLaFila(p.getId());
        if (!Objects.equals(p.getEstadoCodigo(), guardado)) {
            throw new IllegalStateException("Esta postulación acaba de moverse a «" + guardado
                    + "» desde otra pantalla: vuelve a cargarla antes de seguir");
        }
    }

    /**
     * La vacante de una postulación que ya pasó por su guardián, por su organización: es por
     * fuerza de la misma empresa, y preguntarlo así no deja una búsqueda suelta por id.
     */
    private Vacante laVacanteDe(Postulacion p) {
        return vacantes.findByIdAndOrganizacionId(p.getVacanteId(), p.getOrganizacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "La vacante de esta postulación ya no existe"));
    }

    @Override
    public byte[] descargarArchivo(ContextoUsuario quien, Long archivoId, StringBuilder nombreSalida) {
        Archivo archivo = elVisible(quien, archivoId);
        nombreSalida.append(archivo.getNombreOriginal() == null ? "archivo" : archivo.getNombreOriginal());
        return almacen.leer(archivo);
    }

    @Override
    public EnlaceArchivo enlaceDeArchivo(ContextoUsuario quien, Long archivoId) {
        Archivo archivo = elVisible(quien, archivoId);
        var firmado = almacen.urlDeDescarga(archivo)
                .orElseThrow(() -> new IllegalStateException(
                        "El almacen de archivos de este entorno no reparte enlaces: usa la "
                                + "descarga de siempre"));
        return new EnlaceArchivo(firmado.url(), firmado.expira(),
                archivo.getNombreOriginal() == null ? "archivo" : archivo.getNombreOriginal());
    }

    /**
     * El enlace largo, el del Excel del ranking.
     *
     * <p>Pasa por {@link #elVisible} igual que los otros dos: el permiso se comprueba antes
     * de firmar, porque despues ya no hay a quien preguntarle.
     *
     * <p>Devuelve vacio —y no revienta— cuando el almacen no sabe firmar o el archivo ya no
     * esta: quien llama esta volcando una tanda entera, y una excepcion por una fila dejaria
     * sin hoja a las otras setenta y nueve.
     */
    @Override
    public java.util.Optional<EnlaceArchivo> enlaceDeVolcado(ContextoUsuario quien, Long archivoId) {
        Archivo archivo = elVisible(quien, archivoId);
        return almacen.urlDeVolcado(archivo)
                .map(firmado -> new EnlaceArchivo(firmado.url(), firmado.expira(),
                        archivo.getNombreOriginal() == null
                                ? "archivo" : archivo.getNombreOriginal()));
    }

    /**
     * El archivo, si quien pregunta puede verlo.
     *
     * <p>Lo comparten la descarga y el enlace <b>a proposito</b>: son dos formas de entregar
     * lo mismo, y si una comprobara el permiso y la otra no, la que no lo comprueba se
     * convierte en la puerta de atras.
     */
    private Archivo elVisible(ContextoUsuario quien, Long archivoId) {
        permisos.alcanceDe("descargar_entregables");
        return archivos.findByIdAndOrganizacionId(archivoId, quien.organizacionId())
                .orElseThrow(() -> new ResourceNotFoundException("Archivo", "id", archivoId));
    }

    // ============ ayudas ============

    private Map<String, EstadoPostulacion> catalogoEstados() {
        Map<String, EstadoPostulacion> mapa = new HashMap<>();
        estados.findAll().forEach(e -> mapa.put(e.getCodigo(), e));
        return mapa;
    }

    /**
     * Los ids no nulos y sin repetir de una tanda, listos para un {@code findAllById}.
     *
     * <p>Sin repetir porque en una bandeja se repiten mucho: veinte candidatos de la misma
     * vacante son veinte veces el mismo id, y pedirlo veinte veces es volver al problema.
     */
    private static <T> Set<Long> idsDe(Collection<T> cosas, Function<T, Long> id) {
        return cosas.stream().map(id).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> Map<Long, T> porId(Collection<T> cosas, Function<T, Long> id) {
        return cosas.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private FilaBandeja filaBandeja(Postulacion p, Map<String, EstadoPostulacion> catalogo,
                                    Map<Long, String> candidatos, Map<Long, Vacante> porVacante) {
        EstadoPostulacion estado = catalogo.get(p.getEstadoCodigo());
        // A quien ejerció su derecho al borrado se le sigue viendo la fila —la postulación
        // existió y el embudo tiene que cuadrar— pero no el nombre. De eso se ocupa
        // NombresDeUsuarios; el getOrDefault es solo por si la tanda cambió de tamaño.
        String candidato = candidatos.getOrDefault(p.getUsuarioId(), NombresDeUsuarios.ANONIMO);
        String vacante = Optional.ofNullable(porVacante.get(p.getVacanteId()))
                .map(Vacante::getTitulo).orElse("");
        return new FilaBandeja(p.getId(), p.getUuid().toString(), candidato, vacante,
                p.getEstadoCodigo(), estado == null ? "" : estado.getNombre(),
                estado == null ? "" : estado.getEsperaA(), p.getGrupoPrioridad(),
                Duration.between(p.getMovidoEn(), Instant.now()).toDays());
    }

    private Postulacion laVisible(ContextoUsuario quien, Long postulacionId, String permiso) {
        return alcanceVacante.laPostulacionVisible(quien, postulacionId, permiso);
    }

    @Override
    @Transactional
    public ServicioEnlaceAcceso.EnlaceGenerado enlaceDeAcceso(ContextoUsuario quien,
                                                            Long postulacionId) {
        Postulacion p = laVisible(quien, postulacionId, "mover_postulacion");
        // Un enlace para entrar a un proceso que ya no existe no lleva a ninguna parte (V60).
        alcanceVacante.exigirQueSuVacanteSigaExistiendo(p);
        return enlacesDeAcceso.generarEnlace(p.getId());
    }

    private void vacanteVisible(ContextoUsuario quien, Long vacanteId, String permiso) {
        alcanceVacante.laVacanteVisible(quien, vacanteId, permiso);
    }

    /**
     * Que la vacante de esta postulación no esté archivada.
     *
     * <p>Una archivada se lee entera —el ranking, las fichas, el historial, las descargas—
     * pero no se mueve: es la mitad de la regla que no puede sostenerse escondiendo botones,
     * porque el panel es un cliente más del API. Ver {@code VacanteArchivada}.
     *
     * <p>Se pregunta con un {@code exists} y no cargando la vacante: de ella hace falta saber
     * una sola cosa, y un {@code findById} suelto aquí sería justo la búsqueda que la regla de
     * arquitectura del aislamiento entre empresas vigila.
     *
     * <p>⚠️ <b>Esto no debería poder dispararse casi nunca</b>, y aun así está: archivar exige
     * que no quede nadie en carrera, así que en una archivada todas las postulaciones están
     * terminadas y la máquina de estados ya se planta sola. La guarda cubre el hueco entre las
     * dos reglas —un estado terminal que mañana admita volver atrás— y dice con palabras lo
     * que pasa, en vez de dejar un mensaje sobre estados que no menciona el archivo.
     */
    private void exigirVacanteNoArchivada(Postulacion p) {
        // La eliminada va PRIMERO y contesta distinto: 404 y no 409. Una archivada está ahí y
        // no se puede mover; una eliminada no existe, y decir «no puedes» sobre algo que
        // ninguna pantalla enseña ya sería contradecir al panel (V60).
        VacanteEliminada.exigirQueSigaExistiendo(
                vacantes.existsByIdAndEliminadaEnIsNotNull(p.getVacanteId()), p.getVacanteId());
        VacanteArchivada.exigirQueNoLoEste(
                vacantes.existsByIdAndArchivadaEnIsNotNull(p.getVacanteId()));
    }

    /**
     * Corrige el correo o el telefono que la IA leyo mal del curriculum.
     *
     * <p>Solo se toca lo que llega: casi siempre falla uno de los dos, y obligar a reescribir
     * el que estaba bien es una invitacion a estropearlo.
     *
     * <p>Queda auditado con el valor anterior y el motivo. Esto pisa un dato que vino del
     * curriculum de una persona; si mañana pregunta por que su correo dice otra cosa, la
     * respuesta tiene que estar escrita en algun sitio.
     */
    @Override
    @Transactional
    public ContactoDelCandidato corregirContacto(ContextoUsuario quien, Long postulacionId,
                                                 CorregirContacto datos) {
        Postulacion p = laVisible(quien, postulacionId, "corregir_contacto_candidato");
        // Una eliminada no existe: su contacto ya no se corrige (V60).
        alcanceVacante.exigirQueSuVacanteSigaExistiendo(p);

        if ((datos.email() == null || datos.email().isBlank())
                && (datos.telefono() == null || datos.telefono().isBlank())) {
            throw new IllegalArgumentException(
                    "No se manda ni correo ni telefono: no hay nada que corregir");
        }

        DatoCv ficha = datosCv.findByPostulacionId(postulacionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Ficha del curriculum", "postulacion", postulacionId));

        Map<String, String> antes = new LinkedHashMap<>();
        Map<String, String> despues = new LinkedHashMap<>();

        if (datos.email() != null && !datos.email().isBlank()) {
            antes.put("email", ficha.getEmail() == null ? "" : ficha.getEmail());
            ficha.setEmail(datos.email().trim());
            despues.put("email", ficha.getEmail());
        }
        if (datos.telefono() != null && !datos.telefono().isBlank()) {
            antes.put("telefono", ficha.getTelefono() == null ? "" : ficha.getTelefono());
            ficha.setTelefono(datos.telefono().trim());
            despues.put("telefono", ficha.getTelefono());
        }
        ficha.setActualizadoEn(Instant.now());
        datosCv.save(ficha);

        auditoria.registrar(p.getOrganizacionId(), quien, "corregir_contacto_candidato",
                "dato_cv", ficha.getId(), antes, despues, datos.motivo());

        return new ContactoDelCandidato(postulacionId, ficha.getNombre(),
                ficha.getEmail(), ficha.getTelefono());
    }

}
