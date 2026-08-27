package com.renaser.ai.ai_engine.vacante.service;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.postulacion.repository.PostulacionRepository;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.seguridad.dto.FiltroAlcance;
import com.renaser.ai.ai_engine.seguridad.service.Permisos;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Quién alcanza qué filas del panel.
 *
 * <p>Esto guarda la puerta de casi todo lo que enseña datos de un candidato, y cuando se
 * equivoca <b>no se rompe nada a la vista</b>: la pantalla carga, la lista sale, las notas se
 * guardan. Solo que las está viendo alguien a quien no le tocaba. Un fallo aquí no se
 * descubre por un error, se descubre porque alguien comenta lo que no debería saber.
 *
 * <p>Por eso lo que se prueba son sobre todo negativas y silencios:
 *
 * <ul>
 *   <li><b>Lo que no se alcanza responde igual que lo que no existe.</b> Si un id ajeno diera
 *       403 y uno inventado 404, la diferencia entre las dos respuestas sería un mapa de qué
 *       ids hay al otro lado.
 *   <li><b>El alcance sale del permiso que llega, no de uno fijo.</b> Es lo que impide que un
 *       rol con un permiso acotado y otro libre use el segundo para saltarse el primero.
 *   <li><b>Con TODO no se pregunta por la vacante.</b> No es una optimización: es la prueba de
 *       que el camino ancho no paga una consulta por fila cuando esto se usa sobre una tanda.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Qué filas del panel alcanza quien pregunta")
class AlcanceSobreLaVacanteTest {

    private static final Long ORGANIZACION = 1L;
    private static final Long QUIEN_MIRA = 10L;
    private static final Long OTRO = 99L;
    private static final Long POSTULACION = 55L;
    private static final Long VACANTE = 7L;
    private static final String PERMISO = "ajustar_nota";

    private static final ContextoUsuario QUIEN = new ContextoUsuario(
            QUIEN_MIRA, 3L, ORGANIZACION, "EQUIPO", List.of(2L), Map.of());

    @Mock private PostulacionRepository postulaciones;
    @Mock private VacanteRepository vacantes;
    @Mock private Permisos permisos;

    @InjectMocks
    private AlcanceSobreLaVacante alcance;

    private Postulacion laPostulacion() {
        return Postulacion.builder()
                .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE).build();
    }

    private void hayPostulacion() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(laPostulacion()));
    }

    private void conAlcance(FiltroAlcance.Tipo tipo) {
        when(permisos.alcanceDe(PERMISO)).thenReturn(new FiltroAlcance(tipo, QUIEN_MIRA));
    }

    private void laVacanteEsDe(Long responsable) {
        when(vacantes.findById(VACANTE)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                        .responsableUsuarioId(responsable).build()));
    }

    // ============ La postulación ============

    @Test
    @DisplayName("Con TODO sale la postulación, y no se pregunta de quién es la vacante")
    void conTodoNiSePreguntaPorLaVacante() {
        hayPostulacion();
        conAlcance(FiltroAlcance.Tipo.TODO);

        assertThat(alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO).getId())
                .isEqualTo(POSTULACION);

        // Sobre una tanda, preguntar aquí sería una consulta por fila para nada.
        verifyNoInteractions(vacantes);
    }

    @Test
    @DisplayName("Con SUS_VACANTES sale si la vacante es suya")
    void conSusVacantesSaleLaSuya() {
        hayPostulacion();
        conAlcance(FiltroAlcance.Tipo.SUS_VACANTES);
        laVacanteEsDe(QUIEN_MIRA);

        assertThat(alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO).getId())
                .isEqualTo(POSTULACION);

        verify(vacantes, times(1)).findById(VACANTE);
    }

    @Test
    @DisplayName("Con SUS_VACANTES, una de vacante ajena responde igual que una que no existe")
    void conSusVacantesLaAjenaEs404() {
        hayPostulacion();
        conAlcance(FiltroAlcance.Tipo.SUS_VACANTES);
        laVacanteEsDe(OTRO);

        assertThatThrownBy(() -> alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO))
                .as("un 403 confirmaría que esa postulación existe")
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Postulación");
    }

    @Test
    @DisplayName("Si la vacante ya no está, no se alcanza: en la duda no se enseña")
    void sinVacanteNoSeAlcanza() {
        hayPostulacion();
        conAlcance(FiltroAlcance.Tipo.SUS_VACANTES);
        when(vacantes.findById(VACANTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Una postulación de otra empresa no llega a mirarse el alcance")
    void laDeOtraEmpresaNiSeMira() {
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO))
                .isInstanceOf(ResourceNotFoundException.class);

        // Ni el permiso se resuelve: la empresa se comprueba antes que nada.
        verifyNoInteractions(permisos, vacantes);
    }

    @Test
    @DisplayName("Sin el permiso, el 403 sale tal cual y no se disfraza de 404")
    void sinElPermisoSaleEl403() {
        hayPostulacion();
        when(permisos.alcanceDe(PERMISO)).thenThrow(new AccessDeniedException("no lo tienes"));

        // No tener el permiso y no alcanzar la fila son cosas distintas: la primera se dice
        // con un 403, porque no revela nada que quien pregunta no supiera ya.
        assertThatThrownBy(() -> alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Se mira el alcance del permiso que llega, no el de uno fijo")
    void elAlcanceEsElDelPermisoQueLlega() {
        // Un servicio guarda varias acciones con permisos distintos. Si esto mirara siempre el
        // mismo, un rol con ajustar_nota acotado pero ver_embudo libre ajustaría notas ajenas.
        hayPostulacion();
        conAlcance(FiltroAlcance.Tipo.TODO);

        alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO);

        verify(permisos).alcanceDe(PERMISO);
        verify(permisos, never()).alcanceDe("ver_embudo");
    }

    @Test
    @DisplayName("Con PROPIO no se alcanza nada, y ni se pregunta de quién es la vacante")
    void conPropioNoSeAlcanzaNada() {
        // PROPIO quiere decir «lo tuyo», y en el panel nada de esto es de quien mira: son
        // candidatos, y /panel/** exige un token de equipo. Los catorce guardianes de los que
        // salió esta clase lo dejaban pasar —solo probaban «es SUS_VACANTES»— y le daban
        // acceso completo justo a quien menos alcance tiene. Mientras el reparto se editaba a
        // mano en la base no era alcanzable; desde que los permisos se cambian por el panel,
        // basta un PUT sobre cualquiera de esos permisos.
        hayPostulacion();
        conAlcance(FiltroAlcance.Tipo.PROPIO);

        assertThatThrownBy(() -> alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(vacantes);
    }

    @Test
    @DisplayName("Ni siquiera la postulación de quien pregunta: en el panel no se mira a sí mismo")
    void conPropioNiLaDeUnoMismo() {
        // El caso que un lector espera que pase, y no pasa a propósito: aunque la postulación
        // fuera del propio usuario, esto es el panel. Un candidato mirando lo suyo entra por
        // el portal, que tiene su propia comprobación contra el usuario de la fila.
        when(postulaciones.findByIdAndOrganizacionId(POSTULACION, ORGANIZACION))
                .thenReturn(Optional.of(Postulacion.builder()
                        .id(POSTULACION).organizacionId(ORGANIZACION).vacanteId(VACANTE)
                        .usuarioId(QUIEN_MIRA).build()));
        conAlcance(FiltroAlcance.Tipo.PROPIO);

        assertThatThrownBy(() -> alcance.laPostulacionVisible(QUIEN, POSTULACION, PERMISO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ============ La vacante ============

    @Test
    @DisplayName("Con SUS_VACANTES sale la vacante suya y no la ajena")
    void laVacanteSuyaSiYLaAjenaNo() {
        when(permisos.alcanceDe(PERMISO))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, QUIEN_MIRA));
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                        .responsableUsuarioId(QUIEN_MIRA).build()));

        assertThat(alcance.laVacanteVisible(QUIEN, VACANTE, PERMISO).getId()).isEqualTo(VACANTE);
    }

    @Test
    @DisplayName("Una vacante que no dirige responde 404, con su propio nombre de recurso")
    void laVacanteAjenaEs404() {
        when(permisos.alcanceDe(PERMISO))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, QUIEN_MIRA));
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                        .responsableUsuarioId(OTRO).build()));

        assertThatThrownBy(() -> alcance.laVacanteVisible(QUIEN, VACANTE, PERMISO))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vacante");
    }

    // ============ El lote ============

    @Test
    @DisplayName("Con PROPIO tampoco se alcanza una vacante")
    void conPropioTampocoLaVacante() {
        when(permisos.alcanceDe(PERMISO))
                .thenReturn(new FiltroAlcance(FiltroAlcance.Tipo.PROPIO, QUIEN_MIRA));
        when(vacantes.findByIdAndOrganizacionId(VACANTE, ORGANIZACION)).thenReturn(Optional.of(
                Vacante.builder().id(VACANTE).organizacionId(ORGANIZACION)
                        .responsableUsuarioId(QUIEN_MIRA).build()));

        assertThatThrownBy(() -> alcance.laVacanteVisible(QUIEN, VACANTE, PERMISO))
                .as("ni la que dirige: PROPIO no habla de vacantes, habla de filas suyas")
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Sobre una tanda la vacante sale del mapa ya cargado, sin ir a la base")
    void enLaTandaNoSeVaALaBase() {
        Vacante suya = Vacante.builder().id(VACANTE).responsableUsuarioId(QUIEN_MIRA).build();
        Map<Long, Vacante> porVacante = Map.of(VACANTE, suya);

        boolean llega = alcance.alcanzaA(QUIEN,
                new FiltroAlcance(FiltroAlcance.Tipo.SUS_VACANTES, QUIEN_MIRA),
                laPostulacion(), id -> Optional.ofNullable(porVacante.get(id)));

        assertThat(llega).isTrue();
        verifyNoInteractions(vacantes, postulaciones);
    }

    @Test
    @DisplayName("Una postulación que no está no la alcanza nadie")
    void laPostulacionNulaNoLaAlcanzaNadie() {
        assertThat(alcance.alcanzaA(QUIEN,
                new FiltroAlcance(FiltroAlcance.Tipo.TODO, QUIEN_MIRA), null, id -> Optional.empty()))
                .isFalse();
    }
}
