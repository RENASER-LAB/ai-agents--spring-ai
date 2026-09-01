package com.renaser.ai.ai_engine.prueba.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.service.DuenoDelInstrumento;
import com.renaser.ai.ai_engine.perfilintegral.entity.Criterio;
import com.renaser.ai.ai_engine.perfilintegral.repository.CriterioRepository;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.ConsignaResponse;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.CrearCriterioRubrica;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.CrearEntregableRequerido;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.CrearVariante;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.CrearVersion;
import com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.ReordenarElementos;
import com.renaser.ai.ai_engine.prueba.entity.EntregableRequerido;
import com.renaser.ai.ai_engine.prueba.entity.PlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.entity.PreguntaVersionPlantilla;
import com.renaser.ai.ai_engine.prueba.entity.VarianteCambio;
import com.renaser.ai.ai_engine.prueba.entity.VersionPlantillaPrueba;
import com.renaser.ai.ai_engine.prueba.repository.EntregableRequeridoRepository;
import com.renaser.ai.ai_engine.prueba.repository.PlantillaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaPruebaRepository;
import com.renaser.ai.ai_engine.prueba.repository.PreguntaVersionPlantillaRepository;
import com.renaser.ai.ai_engine.prueba.repository.VarianteCambioRepository;
import com.renaser.ai.ai_engine.prueba.repository.VersionPlantillaPruebaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.renaser.ai.ai_engine.prueba.dto.DtosPlantillaPrueba.MAXIMO_GUIA_CALIFICACION;
import static java.nio.charset.StandardCharsets.UTF_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Componer una versión de prueba equivocándose.
 *
 * <p>Hasta ahora una versión solo sabía crecer: no había forma de quitar una pregunta, un
 * entregable ni un criterio. Junto con la regla de publicar —la rúbrica suma 100 exactos—
 * eso era un callejón sin salida: un criterio de 40 puntos puesto de más dejaba la rúbrica
 * en 140, y esa versión no se podía publicar nunca más. Lo que se protege aquí:
 *
 * <ul>
 *   <li>que el callejón tenga salida: quitar el criterio de más devuelve la rúbrica a 100
 *       y la versión se publica;
 *   <li>que quitar una pregunta de una versión <b>no</b> la borre del catálogo, que es
 *       global y lo comparten varias versiones;
 *   <li>que nada de esto funcione sobre una versión ya PUBLICADA;
 *   <li>que el {@code orden} siga siendo válido después de un borrado — el punto donde
 *       {@code size()+1} chocaba contra el UNIQUE (versión, orden) de la V15;
 *   <li>y que el borrado de un criterio no alcance a los criterios <b>globales</b>, que
 *       comparten tabla con la rúbrica de la prueba.
 * </ul>
 *
 * <p>⚠️ Lo que estas pruebas <b>no</b> comprueban, dicho para que nadie lo suponga: son de
 * dobles, así que ningún repositorio de aquí levanta el UNIQUE de la base ni ejecuta un
 * {@code flush} de verdad. Lo que sí fijan es la decisión que evita el choque —de qué
 * número sale la fila siguiente, y que el reordenado escribe dos tandas de números que no
 * se pisan— que es lo único que un doble puede sostener.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Corregir una versión de prueba en borrador")
class ServicioPlantillaPruebaEdicionTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long PLANTILLA = 6L;
    private static final Long VERSION = 61L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            12L, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private PlantillaPruebaRepository plantillas;
    @Mock private VersionPlantillaPruebaRepository versiones;
    @Mock private VarianteCambioRepository variantes;
    @Mock private PreguntaPruebaRepository preguntasCatalogo;
    @Mock private PreguntaVersionPlantillaRepository preguntasElegidas;
    @Mock private EntregableRequeridoRepository entregablesRequeridos;
    @Mock private CriterioRepository criterios;
    @Mock private ServicioAuditoria auditoria;
    @Mock private DuenoDelInstrumento dueno;

    @Mock private AlmacenArchivos almacen;

    private ServicioPlantillaPruebaImpl servicio;

    @BeforeEach
    void crearElServicio() {
        servicio = new ServicioPlantillaPruebaImpl(plantillas, versiones, variantes,
                preguntasCatalogo, preguntasElegidas, entregablesRequeridos, criterios,
                auditoria, dueno, almacen);
    }

    // ============ Montaje ============

    private VersionPlantillaPrueba laVersion(String estado) {
        VersionPlantillaPrueba version = VersionPlantillaPrueba.builder()
                .id(VERSION).plantillaPruebaId(PLANTILLA).version(1)
                .enunciado("Resuelve el caso").modalidad("CRONOMETRADA").duracionMinutos(90)
                .estado(estado)
                .build();
        when(versiones.findById(VERSION)).thenReturn(Optional.of(version));
        when(plantillas.findByIdAndOrganizacionId(PLANTILLA, ORGANIZACION))
                .thenReturn(Optional.of(PlantillaPrueba.builder()
                        .id(PLANTILLA).organizacionId(ORGANIZACION).build()));
        return version;
    }

    private VersionPlantillaPrueba enBorrador() {
        return laVersion("BORRADOR");
    }

    private VersionPlantillaPrueba yaPublicada() {
        return laVersion("PUBLICADA");
    }

    private EntregableRequerido unEntregable(long id, int orden) {
        return EntregableRequerido.builder()
                .id(id).versionPlantillaPruebaId(VERSION)
                .nombre("Entregable " + orden).detalle("máx. 5 minutos")
                .formato("ARCHIVO").esObligatorio(true).orden(orden)
                .build();
    }

    private VarianteCambio unaVariante(long id, int orden) {
        return VarianteCambio.builder()
                .id(id).versionPlantillaPruebaId(VERSION).texto("Variante " + orden).orden(orden)
                .build();
    }

    private Criterio unCriterio(long id, String codigo, double puntos, int orden) {
        return Criterio.builder()
                .id(id).codigo(codigo).nombre("Criterio " + codigo)
                .etapaCodigo("PRUEBA_PUESTO").versionPlantillaPruebaId(VERSION)
                .puntos(BigDecimal.valueOf(puntos)).metodoVerificacion("PERSONA").orden(orden)
                .build();
    }

    // ============ Quitar una pregunta elegida ============

    @Nested
    @DisplayName("Quitar una pregunta de la versión")
    class QuitarPregunta {

        @Test
        @DisplayName("borra la elección y deja la pregunta en el catálogo, que es de todos")
        void noTocaElCatalogo() {
            enBorrador();
            PreguntaVersionPlantilla elegida = PreguntaVersionPlantilla.builder()
                    .versionPlantillaPruebaId(VERSION).preguntaPruebaId(77L).orden(2).build();
            when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(
                            PreguntaVersionPlantilla.builder()
                                    .versionPlantillaPruebaId(VERSION).preguntaPruebaId(76L)
                                    .orden(1).build(),
                            elegida));

            servicio.quitarPregunta(QUIEN, VERSION, 77L);

            verify(preguntasElegidas).delete(elegida);
            // El catálogo es global y la misma pregunta la puede tener elegida otra versión:
            // esta llamada no puede tocarlo ni para leerlo.
            verifyNoInteractions(preguntasCatalogo);
        }

        @Test
        @DisplayName("una pregunta que esta versión no eligió responde 404")
        void laQueNoEstaNoSeQuita() {
            enBorrador();
            when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> servicio.quitarPregunta(QUIEN, VERSION, 77L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("sobre una versión ya publicada se rechaza")
        void enPublicadaNoSeQuita() {
            yaPublicada();

            assertThatThrownBy(() -> servicio.quitarPregunta(QUIEN, VERSION, 77L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PUBLICADA");
            verify(preguntasElegidas, never()).delete(any());
        }
    }

    // ============ Entregables ============

    @Nested
    @DisplayName("Los entregables de un borrador")
    class Entregables {

        @Test
        @DisplayName("se quitan")
        void seQuitan() {
            EntregableRequerido e = unEntregable(31L, 2);
            when(entregablesRequeridos.findById(31L)).thenReturn(Optional.of(e));
            enBorrador();

            servicio.quitarEntregableRequerido(QUIEN, 31L);

            verify(entregablesRequeridos).delete(e);
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN),
                    eq("eliminar_entregable_requerido"), eq("entregable_requerido"), eq(31L),
                    any(), eq(null), eq(null));
        }

        @Test
        @DisplayName("no se quitan si la versión ya se publicó")
        void enPublicadaNo() {
            when(entregablesRequeridos.findById(31L)).thenReturn(Optional.of(unEntregable(31L, 2)));
            yaPublicada();

            assertThatThrownBy(() -> servicio.quitarEntregableRequerido(QUIEN, 31L))
                    .isInstanceOf(IllegalStateException.class);
            verify(entregablesRequeridos, never()).delete(any());
        }

        @Test
        @DisplayName("se corrigen sin tener que borrarlos y volverlos a crear")
        void seCorrigen() {
            EntregableRequerido e = unEntregable(31L, 2);
            when(entregablesRequeridos.findById(31L)).thenReturn(Optional.of(e));
            enBorrador();

            servicio.actualizarEntregableRequerido(QUIEN, 31L, new CrearEntregableRequerido(
                    "El video de la sustentación", "máx. 5 minutos", "ENLACE", false));

            assertThat(e.getNombre()).isEqualTo("El video de la sustentación");
            assertThat(e.getFormato()).isEqualTo("ENLACE");
            assertThat(e.isEsObligatorio()).isFalse();
            // El orden y la versión no son datos del entregable: no se tocan al corregirlo
            assertThat(e.getOrden()).isEqualTo(2);
            assertThat(e.getVersionPlantillaPruebaId()).isEqualTo(VERSION);
        }

        /**
         * El punto exacto del choque: con {@code size()+1} el entregable nuevo pediría el 3,
         * que sigue ocupado por el tercero, y el UNIQUE (versión, orden) de la V15 lo
         * rechazaría. Con {@code max(orden)+1} pide el 4 y el hueco del 2 se queda vacío.
         */
        @Test
        @DisplayName("tras quitar el del medio, el siguiente que se agrega no reclama su hueco")
        void elOrdenNoChocaTrasBorrar() {
            enBorrador();
            when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(unEntregable(30L, 1), unEntregable(32L, 3)));
            when(entregablesRequeridos.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.agregarEntregableRequerido(QUIEN, VERSION, new CrearEntregableRequerido(
                    "El informe", "máx. 10 páginas", "ARCHIVO", true));

            ArgumentCaptor<EntregableRequerido> guardado =
                    ArgumentCaptor.forClass(EntregableRequerido.class);
            verify(entregablesRequeridos).save(guardado.capture());
            assertThat(guardado.getValue().getOrden()).isEqualTo(4);
        }
    }

    // ============ La rúbrica ============

    @Nested
    @DisplayName("La rúbrica de un borrador")
    class Rubrica {

        @Test
        @DisplayName("una rúbrica de 140 vuelve a 100 quitando el criterio de más, y se publica")
        void elCallejonTieneSalida() {
            enBorrador();
            Criterio deMas = unCriterio(43L, "SOBRA", 40, 3);
            when(criterios.findById(43L)).thenReturn(Optional.of(deMas));

            servicio.quitarCriterioRubrica(QUIEN, 43L);
            verify(criterios).delete(deMas);

            // Y con eso la versión ya se puede publicar: la rúbrica suma 100 otra vez
            when(criterios.findByVersionPlantillaPruebaId(VERSION)).thenReturn(List.of(
                    unCriterio(41L, "CAJA", 60, 1), unCriterio(42L, "SEDES", 40, 2)));
            when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(PreguntaVersionPlantilla.builder()
                            .versionPlantillaPruebaId(VERSION).preguntaPruebaId(1L).orden(1)
                            .build()));
            when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of());

            servicio.publicarVersion(QUIEN, VERSION);
        }

        /**
         * La trampa de compartir tabla: {@code criterio} guarda también los criterios
         * GLOBALES —los del currículum, los de la simulación, las métricas de la
         * validación—, que se reconocen porque no tienen versión de prueba. Sin la guarda,
         * este endpoint sería la forma de borrarlos para toda la plataforma.
         */
        @Test
        @DisplayName("un criterio global no se toca desde aquí: por esta puerta no existe")
        void losGlobalesNoSeTocan() {
            when(criterios.findById(9L)).thenReturn(Optional.of(Criterio.builder()
                    .id(9L).codigo("INT").nombre("Integridad").etapaCodigo("PERFIL_INTEGRAL")
                    .versionPlantillaPruebaId(null).metodoVerificacion("PERSONA").orden(1)
                    .build()));

            assertThatThrownBy(() -> servicio.quitarCriterioRubrica(QUIEN, 9L))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(criterios, never()).delete(any());
            // Ni siquiera llegó a mirar de qué versión era: no hay ninguna
            verifyNoInteractions(versiones);
        }

        @Test
        @DisplayName("no se corrige un criterio de una versión ya publicada")
        void enPublicadaNo() {
            when(criterios.findById(43L)).thenReturn(Optional.of(unCriterio(43L, "SOBRA", 40, 3)));
            yaPublicada();

            assertThatThrownBy(() -> servicio.actualizarCriterioRubrica(QUIEN, 43L,
                    new CrearCriterioRubrica("SOBRA", "Otro nombre", null, 10.0, "PERSONA")))
                    .isInstanceOf(IllegalStateException.class);
            verify(criterios, never()).save(any());
        }

        /**
         * La V10 pone un UNIQUE (codigo, versión) sobre la rúbrica, y su error no nombra el
         * campo culpable porque la clave es de dos columnas. Corregir una errata en el código
         * es justo cuando se choca con el de al lado, así que el aviso se da aquí.
         */
        @Test
        @DisplayName("dos criterios de la misma rúbrica no comparten código")
        void elCodigoNoSeRepite() {
            Criterio c = unCriterio(43L, "SOBRA", 40, 3);
            when(criterios.findById(43L)).thenReturn(Optional.of(c));
            enBorrador();
            when(criterios.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(unCriterio(41L, "CAJA", 60, 1), c));

            assertThatThrownBy(() -> servicio.actualizarCriterioRubrica(QUIEN, 43L,
                    new CrearCriterioRubrica("CAJA", "Otro nombre", null, 10.0, "PERSONA")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CAJA");
            // Y renombrarse a sí mismo no es chocar
            servicio.actualizarCriterioRubrica(QUIEN, 43L,
                    new CrearCriterioRubrica("SOBRA", "Nombre corregido", null, 40.0, "PERSONA"));
            assertThat(c.getNombre()).isEqualTo("Nombre corregido");
        }

        @Test
        @DisplayName("se corrigen los puntos de un criterio sin borrarlo")
        void seCorrigenLosPuntos() {
            Criterio c = unCriterio(43L, "SOBRA", 40, 3);
            when(criterios.findById(43L)).thenReturn(Optional.of(c));
            enBorrador();

            servicio.actualizarCriterioRubrica(QUIEN, 43L,
                    new CrearCriterioRubrica("ORDEN", "Orden y limpieza", "Deja la caja cuadrada",
                            15.0, "AGENTE"));

            assertThat(c.getCodigo()).isEqualTo("ORDEN");
            assertThat(c.getPuntos()).isEqualByComparingTo(BigDecimal.valueOf(15.0));
            assertThat(c.getMetodoVerificacion()).isEqualTo("AGENTE");
            // Lo que lo hace un criterio de ESTA rúbrica y no uno global se queda quieto
            assertThat(c.getVersionPlantillaPruebaId()).isEqualTo(VERSION);
            assertThat(c.getEtapaCodigo()).isEqualTo("PRUEBA_PUESTO");
        }
    }

    // ============ Variantes ============

    @Nested
    @DisplayName("Las variantes del cambio inesperado")
    class Variantes {

        @Test
        @DisplayName("se quitan y se corrigen en borrador")
        void seQuitanYSeCorrigen() {
            VarianteCambio v = unaVariante(21L, 2);
            when(variantes.findById(21L)).thenReturn(Optional.of(v));
            enBorrador();

            servicio.actualizarVariante(QUIEN, 21L, new CrearVariante("Se cae el sistema"));
            assertThat(v.getTexto()).isEqualTo("Se cae el sistema");

            servicio.quitarVariante(QUIEN, 21L);
            verify(variantes).delete(v);
        }

        @Test
        @DisplayName("tras quitar una, la siguiente tampoco reclama el hueco")
        void elOrdenNoChocaTrasBorrar() {
            enBorrador();
            when(variantes.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(unaVariante(20L, 1), unaVariante(22L, 3)));
            when(variantes.save(any())).thenAnswer(i -> i.getArgument(0));

            servicio.agregarVariante(QUIEN, VERSION, new CrearVariante("Llega un pedido urgente"));

            ArgumentCaptor<VarianteCambio> guardada = ArgumentCaptor.forClass(VarianteCambio.class);
            verify(variantes).save(guardada.capture());
            assertThat(guardada.getValue().getOrden()).isEqualTo(4);
        }
    }

    // ============ Los datos de la versión ============

    @Nested
    @DisplayName("Los datos de la versión")
    class DatosDeLaVersion {

        @Test
        @DisplayName("se reemplazan en borrador, sin tocar el número, el estado ni la publicación")
        void seReemplazan() {
            VersionPlantillaPrueba version = enBorrador();

            servicio.actualizarVersion(QUIEN, VERSION, new CrearVersion(
                    "Arma el cierre de caja del día", "Los comprobantes del turno",
                    "Calculadora y hoja de cálculo", "PLAZO_ABIERTO", null, 3, 20, 40, 10, null));

            assertThat(version.getEnunciado()).isEqualTo("Arma el cierre de caja del día");
            assertThat(version.getModalidad()).isEqualTo("PLAZO_ABIERTO");
            assertThat(version.getPlazoDias()).isEqualTo(3);
            assertThat(version.getDuracionMinutos()).isNull();
            assertThat(version.getMinutoCambioMin()).isEqualTo(20);
            assertThat(version.getVersion()).isEqualTo(1);
            assertThat(version.getEstado()).isEqualTo("BORRADOR");
            assertThat(version.getPublicadaEn()).isNull();
        }

        @Test
        @DisplayName("no se reemplazan si ya se publicó")
        void enPublicadaNo() {
            VersionPlantillaPrueba version = yaPublicada();

            assertThatThrownBy(() -> servicio.actualizarVersion(QUIEN, VERSION, new CrearVersion(
                    "Otro enunciado", null, null, "CRONOMETRADA", 90, null, null, null, null, null)))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(version.getEnunciado()).isEqualTo("Resuelve el caso");
        }

        @Test
        @DisplayName("cronometrada sin duración se rechaza con el nombre del campo que falta")
        void cronometradaSinDuracion() {
            enBorrador();

            assertThatThrownBy(() -> servicio.actualizarVersion(QUIEN, VERSION, new CrearVersion(
                    "Un enunciado", null, null, "CRONOMETRADA", null, null, null, null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("duracionMinutos");
        }

        @Test
        @DisplayName("el minuto del cambio no puede ir de mayor a menor")
        void elCambioAlReves() {
            enBorrador();

            assertThatThrownBy(() -> servicio.actualizarVersion(QUIEN, VERSION, new CrearVersion(
                    "Un enunciado", null, null, "CRONOMETRADA", 90, null, 50, 30, null, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minutoCambioMin");
        }
    }

    @Nested
    @DisplayName("La guía de calificación")
    class LaGuiaDeCalificacion {

        @Test
        @DisplayName("se guarda con la versión y se puede quitar mandándola vacía")
        void seGuardaYSeQuita() {
            VersionPlantillaPrueba version = enBorrador();
            version.setGuiaCalificacion("Fíjate en si el cierre de caja cuadra al céntimo");

            servicio.actualizarVersion(QUIEN, VERSION, conGuia(null));

            assertThat(version.getGuiaCalificacion()).isNull();
        }

        @Test
        @DisplayName("una guía que se pasa de largo se rechaza diciendo el tope y lo que trae")
        void unaGuiaLarguisimaSeRechaza() {
            // ⚠️ El texto acaba dentro del `system` de un modelo que pone notas. Sin tope,
            // cualquiera con permiso para editar plantillas puede mandar cincuenta mil
            // caracteres que tapen por volumen la instrucción del agente — y de paso pagarlos
            // en cada calificación.
            enBorrador();
            String larguisima = "a".repeat(MAXIMO_GUIA_CALIFICACION + 1);

            assertThatThrownBy(() -> servicio.actualizarVersion(QUIEN, VERSION, conGuia(larguisima)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(MAXIMO_GUIA_CALIFICACION));

            verifyNoInteractions(auditoria);
        }

        @Test
        @DisplayName("una que llega justo en el tope pasa")
        void enElTopeJustoPasa() {
            VersionPlantillaPrueba version = enBorrador();
            String justa = "a".repeat(MAXIMO_GUIA_CALIFICACION);

            servicio.actualizarVersion(QUIEN, VERSION, conGuia(justa));

            assertThat(version.getGuiaCalificacion()).hasSize(MAXIMO_GUIA_CALIFICACION);
        }

        @Test
        @DisplayName("en la auditoría va su longitud, no el texto")
        void enLaAuditoriaVaSuLongitud() {
            // El texto que de verdad importa —el que estaba en vigor al calificar— vive en
            // `EjecucionIa.envio`, que guarda el `system` entero de cada llamada al modelo.
            enBorrador();

            servicio.actualizarVersion(QUIEN, VERSION, conGuia("Mira el margen de error"));

            ArgumentCaptor<Map<String, Object>> despues = ArgumentCaptor.captor();
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN),
                    eq("editar_version_plantilla_prueba"), eq("version_plantilla_prueba"),
                    eq(VERSION), any(), despues.capture(), any());
            assertThat(despues.getValue()).containsEntry("largoGuiaCalificacion", "23");
            assertThat(despues.getValue().values()).doesNotContain("Mira el margen de error");
        }

        private CrearVersion conGuia(String guia) {
            return new CrearVersion("Resuelve el caso", null, null, "CRONOMETRADA", 90,
                    null, null, null, null, guia);
        }
    }

    @Nested
    @DisplayName("El enunciado subido como archivo")
    class ElEnunciadoSubido {

        private final MockMultipartFile pdf = new MockMultipartFile(
                "archivo", "enunciado.pdf", "application/pdf", "el enunciado".getBytes(UTF_8));

        @Test
        @DisplayName("se guarda y deja en la versión el enlace que después pega el correo")
        void seGuardaYDejaElEnlace() {
            VersionPlantillaPrueba version = enBorrador();
            Archivo guardado = Archivo.builder().id(9L).nombreOriginal("enunciado.pdf").build();
            when(almacen.guardar(ORGANIZACION, pdf)).thenReturn(guardado);
            Instant expira = Instant.now().plus(Duration.ofDays(180));
            when(almacen.urlDeConsigna(guardado)).thenReturn(
                    Optional.of(new AlmacenArchivos.EnlaceFirmado("https://bucket/enunciado", expira)));

            ConsignaResponse salida = servicio.subirConsigna(QUIEN, VERSION, pdf);

            assertThat(version.getUrlConsigna()).isEqualTo("https://bucket/enunciado");
            assertThat(salida.archivoId()).isEqualTo(9L);
            assertThat(salida.expira()).isEqualTo(expira);
        }

        @Test
        @DisplayName("subir el enunciado NO monta la prueba: no toca preguntas, entregables ni rúbrica")
        void subirNoMontaLaPrueba() {
            // ⚠️ Lo que se sube es el papel que lee el candidato. De un PDF no sale ninguna
            // nota, así que publicar sigue exigiendo lo mismo que antes. Quien crea que con
            // el archivo ya tiene la prueba montada se topará con que no se puede publicar.
            enBorrador();
            Archivo guardado = Archivo.builder().id(9L).nombreOriginal("enunciado.pdf").build();
            when(almacen.guardar(ORGANIZACION, pdf)).thenReturn(guardado);
            when(almacen.urlDeConsigna(guardado)).thenReturn(Optional.of(
                    new AlmacenArchivos.EnlaceFirmado("https://bucket/enunciado", Instant.now())));

            servicio.subirConsigna(QUIEN, VERSION, pdf);

            verifyNoInteractions(preguntasElegidas, entregablesRequeridos, criterios);
        }

        @Test
        @DisplayName("sobre una versión ya publicada se rechaza, y el archivo ni se sube")
        void enPublicadaNiSeSube() {
            // Que se niegue ANTES de tocar el almacén importa: si no, cada intento dejaría un
            // archivo huérfano en el bucket que nadie va a borrar nunca.
            VersionPlantillaPrueba version = yaPublicada();

            assertThatThrownBy(() -> servicio.subirConsigna(QUIEN, VERSION, pdf))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PUBLICADA");

            assertThat(version.getUrlConsigna()).isNull();
            verifyNoInteractions(almacen);
        }

        @Test
        @DisplayName("si el almacén no reparte enlaces, se dice, y la versión no queda a medias")
        void sinEnlaceNoSeGuardaNada() {
            VersionPlantillaPrueba version = enBorrador();
            Archivo guardado = Archivo.builder().id(9L).build();
            when(almacen.guardar(ORGANIZACION, pdf)).thenReturn(guardado);
            when(almacen.urlDeConsigna(guardado)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.subirConsigna(QUIEN, VERSION, pdf))
                    .isInstanceOf(IllegalStateException.class);

            assertThat(version.getUrlConsigna()).isNull();
        }
    }

    // ============ Reordenar ============

    @Nested
    @DisplayName("Reordenar una lista de la versión")
    class Reordenar {

        /**
         * ⚠️ Lo que se fija aquí es que el renumerado va en dos tandas cuyos números no se
         * pisan: la primera aparca por encima del máximo actual y la segunda escribe el
         * 1..n. Contra el UNIQUE (versión, orden) de la V15 una sola tanda fallaría según
         * en qué orden la base ejecutara los UPDATE — o sea, unas veces sí y otras no.
         */
        @Test
        @DisplayName("aparca por encima del máximo antes de escribir el 1..n")
        void dosPasadasQueNoSePisan() {
            enBorrador();
            when(entregablesRequeridos.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(unEntregable(30L, 1), unEntregable(31L, 2),
                            unEntregable(32L, 3)));
            List<List<Integer>> pasadas = new ArrayList<>();
            doAnswer(invocacion -> {
                List<EntregableRequerido> lista = invocacion.getArgument(0);
                pasadas.add(lista.stream().map(EntregableRequerido::getOrden).toList());
                return lista;
            }).when(entregablesRequeridos).saveAll(anyList());

            servicio.reordenarEntregables(QUIEN, VERSION, new ReordenarElementos(List.of(32L, 30L, 31L)));

            assertThat(pasadas).hasSize(2);
            // Primera tanda: todos por encima del 3, que era el máximo
            assertThat(pasadas.get(0)).containsExactly(4, 5, 6);
            // Segunda: el orden pedido, ya compacto
            assertThat(pasadas.get(1)).containsExactly(1, 2, 3);
            // Y el flush entre tandas es lo que de verdad las separa
            verify(entregablesRequeridos, times(2)).flush();
        }

        @Test
        @DisplayName("una lista a medias no reordena nada")
        void laListaVaEntera() {
            enBorrador();
            when(variantes.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(unaVariante(20L, 1), unaVariante(21L, 2)));

            assertThatThrownBy(() -> servicio.reordenarVariantes(QUIEN, VERSION,
                    new ReordenarElementos(List.of(21L))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 ids");
            verify(variantes, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("las preguntas se ordenan por su id del catálogo")
        void lasPreguntasPorSuIdDelCatalogo() {
            enBorrador();
            when(preguntasElegidas.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(
                            PreguntaVersionPlantilla.builder().versionPlantillaPruebaId(VERSION)
                                    .preguntaPruebaId(76L).orden(1).build(),
                            PreguntaVersionPlantilla.builder().versionPlantillaPruebaId(VERSION)
                                    .preguntaPruebaId(77L).orden(2).build()));
            List<List<Integer>> pasadas = new ArrayList<>();
            doAnswer(invocacion -> {
                List<PreguntaVersionPlantilla> lista = invocacion.getArgument(0);
                pasadas.add(lista.stream().map(PreguntaVersionPlantilla::getOrden).toList());
                return lista;
            }).when(preguntasElegidas).saveAll(anyList());

            servicio.reordenarPreguntas(QUIEN, VERSION, new ReordenarElementos(List.of(77L, 76L)));

            assertThat(pasadas.get(1)).containsExactly(1, 2);
            verifyNoInteractions(preguntasCatalogo);
        }

        /** La otra tabla con UNIQUE (versión, orden) de la V15: la misma cautela le toca. */
        @Test
        @DisplayName("las variantes también se recolocan en dos tandas")
        void lasVariantes() {
            enBorrador();
            when(variantes.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(unaVariante(20L, 1), unaVariante(21L, 2)));
            List<List<Integer>> pasadas = new ArrayList<>();
            doAnswer(invocacion -> {
                List<VarianteCambio> lista = invocacion.getArgument(0);
                pasadas.add(lista.stream().map(VarianteCambio::getOrden).toList());
                return lista;
            }).when(variantes).saveAll(anyList());

            servicio.reordenarVariantes(QUIEN, VERSION, new ReordenarElementos(List.of(21L, 20L)));

            assertThat(pasadas.get(0)).containsExactly(3, 4);
            assertThat(pasadas.get(1)).containsExactly(1, 2);
            verify(variantes, times(2)).flush();
        }

        @Test
        @DisplayName("la rúbrica se reordena sin tocar los puntos de nadie")
        void laRubrica() {
            enBorrador();
            Criterio uno = unCriterio(41L, "CAJA", 60, 1);
            Criterio dos = unCriterio(42L, "SEDES", 40, 2);
            when(criterios.findByVersionPlantillaPruebaIdOrderByOrden(VERSION))
                    .thenReturn(List.of(uno, dos));
            List<List<Integer>> pasadas = new ArrayList<>();
            doAnswer(invocacion -> {
                List<Criterio> lista = invocacion.getArgument(0);
                pasadas.add(lista.stream().map(Criterio::getOrden).toList());
                return lista;
            }).when(criterios).saveAll(anyList());

            servicio.reordenarRubrica(QUIEN, VERSION, new ReordenarElementos(List.of(42L, 41L)));

            assertThat(pasadas.get(1)).containsExactly(1, 2);
            assertThat(dos.getOrden()).isEqualTo(1);
            assertThat(uno.getOrden()).isEqualTo(2);
            // Reordenar es reordenar: la rúbrica sigue sumando lo mismo
            assertThat(uno.getPuntos()).isEqualByComparingTo(BigDecimal.valueOf(60));
            assertThat(dos.getPuntos()).isEqualByComparingTo(BigDecimal.valueOf(40));
        }

        @Test
        @DisplayName("no se reordena una versión ya publicada")
        void enPublicadaNo() {
            yaPublicada();

            assertThatThrownBy(() -> servicio.reordenarEntregables(QUIEN, VERSION,
                    new ReordenarElementos(List.of(30L))))
                    .isInstanceOf(IllegalStateException.class);
            verify(entregablesRequeridos, never()).saveAll(anyList());
        }
    }
}
