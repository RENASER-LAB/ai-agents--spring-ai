package com.renaser.ai.ai_engine.perfil.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.PerfilCompleto;
import com.renaser.ai.ai_engine.perfil.service.ServicioPerfilPanel;
import com.renaser.ai.ai_engine.postulacion.entity.Postulacion;
import com.renaser.ai.ai_engine.seguridad.dto.ContextoUsuario;
import com.renaser.ai.ai_engine.usuario.repository.UsuarioRepository;
import com.renaser.ai.ai_engine.vacante.service.AlcanceSobreLaVacante;
import com.renaser.ai.ai_engine.vacante.service.Remuneracion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ServicioPerfilPanelImpl implements ServicioPerfilPanel {

    private final AlcanceSobreLaVacante alcance;
    private final UsuarioRepository usuarios;
    private final PintorDePerfil pintor;
    // Solo para preguntar si la vacante de esta postulación publica lo que paga: es la
    // segunda llave de la pretensión, y sin ella la reciprocidad tendría una puerta trasera.
    private final com.renaser.ai.ai_engine.vacante.repository.VacanteRepository vacantes;

    /**
     * El perfil de un candidato, si de verdad se puede ver.
     *
     * <p>El filtro por empresa no basta: un rol puede tener {@code ver_perfil_candidato}
     * limitado a sus vacantes, y sin comprobarlo leería la trayectoria —y la pretensión
     * salarial— de candidatos de convocatorias ajenas. Quién alcanza qué lo decide
     * {@link AlcanceSobreLaVacante}, que es el mismo guardián que usa el resto del panel.
     */
    @Override
    public PerfilCompleto verDePostulacion(ContextoUsuario quien, Long postulacionId) {
        Postulacion postulacion =
                alcance.laPostulacionVisible(quien, postulacionId, "ver_perfil_candidato");
        Long personaId = usuarios.findById(postulacion.getUsuarioId())
                .map(u -> u.getPersonaId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Postulación", "id", postulacionId));

        // ⚠️ Lo primero que se quita: la foto, la portada, el curriculum del perfil y los
        // diplomas. Son del candidato y aqui mira quien decide. Ver `sinLoDelCandidato`.
        PerfilCompleto completo = pintor.sinLoDelCandidato(pintor.pintar(personaId));
        /*
         * La pretension pide DOS llaves, las mismas que el ranking.
         *
         * La primera es su permiso — y sin el no viaja ni el nombre del campo (JsonInclude
         * NON_NULL en el DTO).
         *
         * ⚠️ **La segunda es que la vacante DE ESTA POSTULACION publique lo que paga.** Esta
         * banda vive en `perfil_candidato`, que es de la plataforma y no de ninguna empresa:
         * sin esta linea, la empresa que esconde su sueldo —y que por eso no le pidio nada al
         * candidato— leia aqui lo que esa persona escribio en su perfil, o lo que la V54 le
         * guardo cuando declaro su cifra a OTRA empresa. Cobrar por un lado lo que no se paga
         * por el otro, dando un rodeo.
         *
         * Es la misma regla del sistema: si no enseñas lo que pagas, no ves lo que piden.
         */
        // Por la organización DE LA POSTULACIÓN, que el guardián de arriba ya validó, y no
        // con un `findById` suelto: la regla de arquitectura de la casa lo prohíbe en los
        // servicios del panel, y tiene razón — el findById suelto es la fuga entre empresas
        // que una sola empresa jamás delata. Aquí la organización es la misma por diseño (la
        // postulación nace en la de la vacante), así que el filtro no quita nada y cierra la
        // puerta por si algún día dejaran de coincidir.
        boolean laVacanteEnsenaSuSueldo = vacantes
                .findByIdAndOrganizacionId(postulacion.getVacanteId(),
                        postulacion.getOrganizacionId())
                .map(Remuneracion::laEnsena)
                .orElse(false);
        return quien.tiene("ver_pretension") && laVacanteEnsenaSuSueldo
                ? completo
                : pintor.sinPretension(completo);
    }
}
