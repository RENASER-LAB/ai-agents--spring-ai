package com.renaser.ai.ai_engine.administracion.service.impl;

import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.AreaPanel;
import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.BorrarArea;
import com.renaser.ai.ai_engine.administracion.dto.DtosAdministracion.ImpactoDeBorrarArea;
import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.auditoria.service.ServicioAuditoria;
import com.renaser.ai.ai_engine.organizacion.entity.Area;
import com.renaser.ai.ai_engine.organizacion.repository.AreaRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.solicitud.entity.SolicitudTalento;
import com.renaser.ai.ai_engine.solicitud.repository.SolicitudTalentoRepository;
import com.renaser.ai.ai_engine.usuario.entity.Usuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las áreas: la estructura de la organización, y lo que cuesta tocarla.
 *
 * <p>Un área es la pieza más pequeña del sistema con las consecuencias más grandes: sin una no
 * se puede registrar una Solicitud de Talento, y dos tablas la referencian —
 * {@code solicitud_talento.area_id} (NOT NULL) y {@code usuario.area_id} (admite NULL)—.
 * <b>Ninguna de las dos declara {@code ON DELETE}</b>, así que Postgres aplica NO ACTION y un
 * DELETE falla mientras quede una sola fila apuntando, también contra la que admite nulo.
 *
 * <p>Lo que se prueba aquí no es que la fila se guarde: es lo que impide dejar la organización
 * sin estructura, perder el rastro de a dónde fue el trabajo de un área borrada, o esconder un
 * área donde nadie pueda volver a encontrarla.
 *
 * <p><b>Lo que estas pruebas NO pueden ver</b>, dicho para que nadie lo suponga cubierto: la
 * clave ajena de verdad. Con dobles no hay base que la haga fallar, así que lo que se comprueba
 * es que el guardián corta antes —{@code never().delete(...)}—, que es lo que hace que el
 * usuario jamás vea el error crudo. La otra mitad la darían las de integración.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Las áreas de la organización")
class AreasDeLaOrganizacionTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long AREA = 7L;
    private static final Long DESTINO = 8L;

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            10L, 20L, ORGANIZACION, "EQUIPO", List.of(5L), Map.of());

    @Mock private AreaRepository areas;
    @Mock private SolicitudTalentoRepository solicitudes;
    @Mock private UsuarioRepository usuarios;
    @Mock private ServicioAuditoria auditoria;

    @InjectMocks private ServicioAdministracionImpl servicio;

    private static Area area(Long id, String nombre, boolean activa) {
        return Area.builder().id(id).organizacionId(ORGANIZACION).nombre(nombre)
                .esActiva(activa).build();
    }

    // ---------- Las dos listas ----------

    @Nested
    @DisplayName("Listarlas")
    class Listarlas {

        @Test
        @DisplayName("La lista normal solo trae las activas: alimenta el desplegable de la solicitud")
        void laNormalSoloTraeLasActivas() {
            when(areas.findByOrganizacionIdAndEsActivaTrueOrderByNombre(ORGANIZACION))
                    .thenReturn(List.of(area(AREA, "Operaciones", true)));

            assertThat(servicio.areas(QUIEN)).extracting(AreaPanel::nombre)
                    .containsExactly("Operaciones");
        }

        @Test
        @DisplayName("La de todas trae también las retiradas, o desactivar no tendría vuelta atrás")
        void laDeTodasTraeLasRetiradas() {
            when(areas.findByOrganizacionIdOrderByNombre(ORGANIZACION)).thenReturn(List.of(
                    area(AREA, "Operaciones", true),
                    area(9L, "Logística", false)));

            List<AreaPanel> todas = servicio.todasLasAreas(QUIEN);

            // Si esta lista filtrara igual que la otra, un área desactivada saldría de la
            // pantalla sin ninguna forma de volver a encenderla: el «esActiva» del DTO sería
            // siempre true y nadie sabría siquiera que existe.
            assertThat(todas).extracting(AreaPanel::nombre)
                    .containsExactly("Operaciones", "Logística");
            assertThat(todas.get(1).esActiva()).isFalse();
        }
    }

    // ---------- Crear y renombrar ----------

    @Nested
    @DisplayName("Crear y renombrar")
    class CrearYRenombrar {

        @Test
        @DisplayName("Crear guarda el nombre recortado y lo deja auditado")
        void crearDejaRastro() {
            when(areas.existsByOrganizacionIdAndNombre(ORGANIZACION, "Operaciones")).thenReturn(false);
            when(areas.save(any())).thenReturn(area(AREA, "Operaciones", true));

            assertThat(servicio.crearArea(QUIEN, "  Operaciones  ")).isEqualTo(AREA);

            ArgumentCaptor<Area> guardada = ArgumentCaptor.captor();
            verify(areas).save(guardada.capture());
            assertThat(guardada.getValue().getNombre()).isEqualTo("Operaciones");
            assertThat(guardada.getValue().isEsActiva()).isTrue();
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("crear_area"),
                    eq("area"), eq(AREA), any(), any(), any());
        }

        @Test
        @DisplayName("Un nombre repetido se rechaza aquí, no en la clave única de la base")
        void elNombreRepetidoSeRechazaAntes() {
            when(areas.existsByOrganizacionIdAndNombre(ORGANIZACION, "Operaciones")).thenReturn(true);

            assertThatThrownBy(() -> servicio.crearArea(QUIEN, "Operaciones"))
                    .as("dejarlo llegar al UNIQUE de la V2 da un mensaje con el nombre de una "
                            + "restricción dentro, y lo que pasa es que ese área ya existe")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Operaciones");

            verify(areas, never()).save(any());
        }

        @Test
        @DisplayName("Renombrar guarda el nombre viejo en la auditoría: es el único sitio donde queda")
        void renombrarGuardaElNombreViejo() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            when(areas.existsByOrganizacionIdAndNombre(ORGANIZACION, "Operaciones y Logística"))
                    .thenReturn(false);

            servicio.renombrarArea(QUIEN, AREA, "Operaciones y Logística");

            ArgumentCaptor<Area> guardada = ArgumentCaptor.captor();
            verify(areas).save(guardada.capture());
            assertThat(guardada.getValue().getNombre()).isEqualTo("Operaciones y Logística");

            // Las solicitudes guardan el id del área, no su texto: sin esta fila nadie puede
            // reconstruir con qué nombre se registraron.
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("renombrar_area"),
                    eq("area"), eq(AREA), eq(Map.of("nombre", "Operaciones")),
                    eq(Map.of("nombre", "Operaciones y Logística")), any());
        }

        @Test
        @DisplayName("Renombrarla al nombre que ya tenía no escribe nada")
        void elMismoNombreNoEsUnCambio() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));

            servicio.renombrarArea(QUIEN, AREA, "  Operaciones  ");

            // Un panel que reenvía la fila entera al guardar mandaría esto en cada pulsación.
            verify(areas, never()).save(any());
            verify(auditoria, never()).registrar(any(), any(), anyString(), anyString(), any(),
                    any(), any(), any());
        }

        @Test
        @DisplayName("Renombrarla al nombre de otra área choca antes de tocar la base")
        void renombrarAUnoOcupadoChoca() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            when(areas.existsByOrganizacionIdAndNombre(ORGANIZACION, "Logística")).thenReturn(true);

            assertThatThrownBy(() -> servicio.renombrarArea(QUIEN, AREA, "Logística"))
                    .isInstanceOf(IllegalStateException.class);

            verify(areas, never()).save(any());
        }

        @Test
        @DisplayName("Un área de otra organización no existe para quien pregunta")
        void laDeOtraOrganizacionNoExiste() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.renombrarArea(QUIEN, AREA, "Lo que sea"))
                    .as("lo ajeno es un 404, no un 403: decir que existe ya es contar algo")
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ---------- Apagar y encender ----------

    @Nested
    @DisplayName("Desactivar y reactivar")
    class DesactivarYReactivar {

        @Test
        @DisplayName("Desactivar apaga la bandera y no toca nada de lo que colgaba del área")
        void desactivarNoSeLlevaNadaPorDelante() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            // Hay más de una encendida: la última no se puede apagar, y eso lo prueba su
            // propio test. Aquí lo que se mira es que apagar una cualquiera no arrastre nada.
            when(areas.countByOrganizacionIdAndEsActivaTrue(ORGANIZACION)).thenReturn(3L);

            servicio.desactivarArea(QUIEN, AREA);

            ArgumentCaptor<Area> guardada = ArgumentCaptor.captor();
            verify(areas).save(guardada.capture());
            assertThat(guardada.getValue().isEsActiva()).isFalse();

            // Desactivar es lo contrario de borrar: las solicitudes viejas conservan su área.
            verify(solicitudes, never()).save(any());
            verify(usuarios, never()).save(any());
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("desactivar_area"),
                    eq("area"), eq(AREA), any(), any(), any());
        }

        @Test
        @DisplayName("Reactivar la vuelve a encender")
        void reactivarLaEnciende() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Logística", false)));

            servicio.reactivarArea(QUIEN, AREA);

            ArgumentCaptor<Area> guardada = ArgumentCaptor.captor();
            verify(areas).save(guardada.capture());
            assertThat(guardada.getValue().isEsActiva()).isTrue();
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("reactivar_area"),
                    eq("area"), eq(AREA), any(), any(), any());
        }

        @Test
        @DisplayName("Desactivar una ya desactivada no escribe nada")
        void apagarLoApagadoNoEsUnCambio() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Logística", false)));

            servicio.desactivarArea(QUIEN, AREA);

            verify(areas, never()).save(any());
            verify(auditoria, never()).registrar(any(), any(), anyString(), anyString(), any(),
                    any(), any(), any());
        }
    }

    // ---------- Borrar de verdad ----------

    @Nested
    @DisplayName("Borrarla de verdad")
    class Borrarla {

        @Test
        @DisplayName("El impacto se cuenta antes, para poder enseñarlo antes de confirmar")
        void elImpactoSeCuentaAntes() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            // Sin filtrar por organización: el impacto cuenta lo mismo que ve la clave ajena,
            // que es lo único que puede impedir el borrado.
            when(solicitudes.countByAreaId(AREA)).thenReturn(3L);
            when(usuarios.countByAreaId(AREA)).thenReturn(2L);

            ImpactoDeBorrarArea impacto = servicio.impactoDeBorrar(QUIEN, AREA);

            assertThat(impacto.solicitudes()).isEqualTo(3L);
            assertThat(impacto.usuarios()).isEqualTo(2L);
            assertThat(impacto.nombre()).isEqualTo("Operaciones");
        }

        @Test
        @DisplayName("Sin destino y con filas colgando: se rechaza con los dos números y NO se borra")
        void sinDestinoYConFilasSeRechaza() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            when(solicitudes.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(3L);
            when(usuarios.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(2L);

            assertThatThrownBy(() -> servicio.borrarArea(QUIEN, AREA, new BorrarArea(null, "sobra")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("3")
                    .hasMessageContaining("2")
                    .hasMessageContaining("Operaciones");

            // Aquí está la razón de ser de la guarda: sin ella la llamada llegaría al DELETE y
            // la clave ajena —NO ACTION en las dos tablas— devolvería un error de restricción
            // en la cara de quien administra, que no dice qué hacer a continuación.
            verify(areas, never()).delete(any());
            verify(solicitudes, never()).save(any());
            verify(usuarios, never()).save(any());
        }

        @Test
        @DisplayName("Basta UNA persona para bloquear el borrado: su clave ajena también es NO ACTION")
        void unUsuarioSoloTambienLoBloquea() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            when(solicitudes.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(0L);
            when(usuarios.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(1L);

            // `usuario.area_id` admite NULL, y de ahí sale la idea de que no estorba. Estorba:
            // la clave ajena no declara ON DELETE y Postgres rechaza el borrado igual.
            assertThatThrownBy(() -> servicio.borrarArea(QUIEN, AREA, new BorrarArea(null, "sobra")))
                    .isInstanceOf(IllegalStateException.class);

            verify(areas, never()).delete(any());
        }

        @Test
        @DisplayName("Vacía y sin destino: se borra, y la auditoría guarda el nombre que se pierde")
        void laVaciaSeBorraSinDestino() {
            Area laQueSeVa = area(AREA, "Área creada por error", true);
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION)).thenReturn(Optional.of(laQueSeVa));
            when(solicitudes.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(0L);
            when(usuarios.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(0L);

            servicio.borrarArea(QUIEN, AREA, new BorrarArea(null, "se creó dos veces"));

            verify(areas).delete(laQueSeVa);
            verify(auditoria).registrar(eq(ORGANIZACION), eq(QUIEN), eq("borrar_area"),
                    eq("area"), eq(AREA),
                    eq(Map.of("nombre", "Área creada por error", "solicitudes", 0L, "usuarios", 0L)),
                    eq(null), eq("se creó dos veces"));
        }

        @Test
        @DisplayName("Con destino: primero se mueve todo, después se borra")
        void conDestinoSeMueveYLuegoSeBorra() {
            Area laQueSeVa = area(AREA, "Operaciones", true);
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION)).thenReturn(Optional.of(laQueSeVa));
            when(areas.findByIdAndOrganizacionId(DESTINO, ORGANIZACION))
                    .thenReturn(Optional.of(area(DESTINO, "Operaciones y Logística", true)));
            when(solicitudes.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(1L);
            when(usuarios.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(1L);
            when(solicitudes.findByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(List.of(
                    SolicitudTalento.builder().id(100L).organizacionId(ORGANIZACION).areaId(AREA).build()));
            when(usuarios.findByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(List.of(
                    Usuario.builder().id(200L).organizacionId(ORGANIZACION).areaId(AREA).build()));

            servicio.borrarArea(QUIEN, AREA, new BorrarArea(DESTINO, "se fusionan las dos áreas"));

            ArgumentCaptor<SolicitudTalento> mudada = ArgumentCaptor.captor();
            verify(solicitudes).save(mudada.capture());
            assertThat(mudada.getValue().getAreaId()).isEqualTo(DESTINO);

            // Y NO se vacía: `usuario.area_id` admite NULL, así que ponerlo a nulo contentaría
            // a Postgres y perdería el dato. Quien borra dice a dónde va la gente.
            ArgumentCaptor<Usuario> movido = ArgumentCaptor.captor();
            verify(usuarios).save(movido.capture());
            assertThat(movido.getValue().getAreaId()).isEqualTo(DESTINO);

            verify(areas).delete(laQueSeVa);
        }

        @Test
        @DisplayName("El destino no puede ser la que se está borrando")
        void elDestinoNoEsLaQueSeVa() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            when(solicitudes.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(1L);
            when(usuarios.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(0L);

            assertThatThrownBy(() ->
                    servicio.borrarArea(QUIEN, AREA, new BorrarArea(AREA, "a sí misma")))
                    .as("mover a la que se borra deja las filas justo donde estaban y el DELETE "
                            + "falla igual, pero después de haber tocado la base")
                    .isInstanceOf(IllegalArgumentException.class);

            verify(areas, never()).delete(any());
        }

        @Test
        @DisplayName("El destino tiene que estar activo: mover a una retirada esconde el trabajo dos veces")
        void elDestinoTieneQueEstarActivo() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            when(areas.findByIdAndOrganizacionId(DESTINO, ORGANIZACION))
                    .thenReturn(Optional.of(area(DESTINO, "Logística", false)));
            when(solicitudes.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(1L);
            when(usuarios.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(0L);

            assertThatThrownBy(() ->
                    servicio.borrarArea(QUIEN, AREA, new BorrarArea(DESTINO, "se fusionan")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Logística");

            verify(areas, never()).delete(any());
        }

        @Test
        @DisplayName("Un destino de otra organización no existe")
        void elDestinoAjenoNoExiste() {
            when(areas.findByIdAndOrganizacionId(AREA, ORGANIZACION))
                    .thenReturn(Optional.of(area(AREA, "Operaciones", true)));
            when(areas.findByIdAndOrganizacionId(DESTINO, ORGANIZACION)).thenReturn(Optional.empty());
            when(solicitudes.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(1L);
            when(usuarios.countByOrganizacionIdAndAreaId(ORGANIZACION, AREA)).thenReturn(0L);

            assertThatThrownBy(() ->
                    servicio.borrarArea(QUIEN, AREA, new BorrarArea(DESTINO, "se fusionan")))
                    .as("si no se comprobara, esto movería las solicitudes de una empresa al "
                            + "área de otra")
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(areas, never()).delete(any());
        }
    }
}
