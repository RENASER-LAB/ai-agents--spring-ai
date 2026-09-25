package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.consentimiento.service.TextosDeConsentimiento;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.CiudadPublica;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.ConsentimientoDeVacante;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.RequisitoPublico;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.VacantePublica;
import com.renaser.ai.ai_engine.portal.service.ServicioTablonPortal;
import com.renaser.ai.ai_engine.vacante.entity.RequisitoObjetivo;
import com.renaser.ai.ai_engine.vacante.entity.Vacante;
import com.renaser.ai.ai_engine.vacante.repository.RequisitoObjetivoRepository;
import com.renaser.ai.ai_engine.vacante.repository.VacanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * El tablón público: ver {@link ServicioTablonPortal}. Solo lectura y sin token — de
 * aquí no cuelga nada del candidato, y por eso este servicio no conoce su cuenta ni
 * sus postulaciones.
 */
@Service
@RequiredArgsConstructor
public class ServicioTablonPortalImpl implements ServicioTablonPortal {

    private final VacanteRepository vacantes;
    private final OrganizacionRepository organizaciones;
    private final RequisitoObjetivoRepository requisitos;
    private final TextosDeConsentimiento textos;
    // Pone nombre y departamento a la ciudad de cada vacante (V62). El mismo catálogo del
    // registro, con las desactivadas incluidas: lo que una vacante tiene guardado se enseña.
    private final CatalogosDelPerfil catalogos;

    @Override
    public List<VacantePublica> vacantesPublicadas() {
        // Las publicadas de TODAS las empresas juntas: la excepción deliberada de la
        // pieza B, con nombre y apellido — el tablón es lo que hace plataforma a la
        // plataforma. Cada vacante dice de qué empresa es, porque el candidato tiene que
        // saber a quién le manda su currículum.
        //
        // De todas las ACTIVAS: una empresa suspendida no puede responder, y nadie debe
        // postularle mientras tanto (pieza F). Sus vacantes siguen PUBLICADAS en la base
        // a propósito — reactivar es volver a verlas, no volver a publicarlas.
        //
        // Y de las que siguen existiendo: una eliminada se retira del tablón aunque su fila
        // siga diciendo PUBLICADA (V60). El estado no se toca al eliminar —cómo iba la
        // convocatoria cuando se retiró es parte de lo que hay que conservar—, así que el
        // corte lo hace la fecha de eliminación y no el estado.
        List<Vacante> todas = vacantes
                .findByEstadoAndEliminadaEnIsNullOrderByPublicadaEnDesc("PUBLICADA");
        Map<Long, Organizacion> organizacionesActivas = organizacionesActivasDe(todas);
        List<Vacante> publicadas = todas.stream()
                .filter(v -> organizacionesActivas.containsKey(v.getOrganizacionId()))
                .toList();

        // Los requisitos de todas de una vez. Este es el tablón de empleo: la única pantalla
        // que se sirve sin haber entrado y, por eso, la que más veces se pide. Una consulta
        // por vacante aquí no la paga un candidato, la paga cada visita.
        Map<Long, List<RequisitoObjetivo>> porVacante = requisitosDe(
                publicadas.stream().map(Vacante::getId).toList());
        // Y el catálogo de ciudades una sola vez, por lo mismo; y ninguna si ninguna la tiene.
        Map<String, OpcionUbigeo> ciudades = ciudadesDe(publicadas);

        return publicadas.stream()
                .map(v -> comoPublica(v, porVacante.getOrDefault(v.getId(), List.of()),
                        organizacionesActivas.get(v.getOrganizacionId()).getNombre(),
                        ciudades))
                .toList();
    }

    @Override
    public VacantePublica vacante(Long id) {
        // Sin filtro de organización a propósito: el tablón es de todas las empresas.
        // Lo que sí se exige es que esté PUBLICADA — un borrador no existe para nadie —
        // y que su empresa esté activa: la vacante de una suspendida tampoco existe para
        // el tablón (pieza F).
        // El tercer colador es el de la V60: una vacante eliminada no existe para el portal
        // ni con el id en la mano —el enlace que alguien guardó ayer, el resultado viejo de
        // un buscador—. El portal lo traduce a «esta vacante ya no está disponible».
        Vacante vacante = vacantes.findById(id)
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .filter(v -> v.getEliminadaEn() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
        Organizacion empresa = organizaciones.findById(vacante.getOrganizacionId())
                .filter(Organizacion::isEsActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", id));
        return comoPublica(vacante, requisitos.findByVacanteIdAndEsActivoTrue(vacante.getId()),
                empresa.getNombre(), ciudadesDe(List.of(vacante)));
    }

    @Override
    public ConsentimientoDeVacante consentimientoDeVacante(Long vacanteId) {
        // Mismo guardián que el detalle público: una vacante sin publicar, eliminada —o de
        // una empresa suspendida— no existe para nadie, y su texto legal tampoco.
        Vacante vacante = vacantes.findById(vacanteId)
                .filter(v -> "PUBLICADA".equals(v.getEstado()))
                .filter(v -> v.getEliminadaEn() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        Organizacion empresa = organizaciones.findById(vacante.getOrganizacionId())
                .filter(Organizacion::isEsActiva)
                .orElseThrow(() -> new ResourceNotFoundException("Vacante", "id", vacanteId));
        // Ya compuesto con el nombre de la empresa: lo que se devuelve es exactamente lo
        // que el candidato va a leer, y exactamente lo que se guardará al firmar.
        var texto = textos.procesoDe(empresa.getNombre());
        return new ConsentimientoDeVacante(empresa.getNombre(),
                texto.fuente().getVersion(), texto.texto());
    }

    /** Las organizaciones ACTIVAS de un lote de vacantes: el colador del tablón. */
    private Map<Long, Organizacion> organizacionesActivasDe(List<Vacante> deVacantes) {
        List<Long> ids = deVacantes.stream().map(Vacante::getOrganizacionId).distinct().toList();
        return organizaciones.findAllById(ids).stream()
                .filter(Organizacion::isEsActiva)
                .collect(Collectors.toMap(Organizacion::getId, Function.identity()));
    }

    /**
     * Los requisitos activos de un lote de vacantes, agrupados por la suya.
     *
     * <p>Ordenados por id porque la consulta no lo pedía y la base no lo promete: al traerlos
     * en bloque llegan mezclados de todas las vacantes, y sin un orden explícito la misma
     * vacante podría enseñar sus requisitos en distinto orden en dos visitas seguidas.
     */
    private Map<Long, List<RequisitoObjetivo>> requisitosDe(List<Long> vacanteIds) {
        if (vacanteIds.isEmpty()) {
            return Map.of();
        }
        return requisitos.findByVacanteIdInAndEsActivoTrue(vacanteIds).stream()
                .sorted(Comparator.comparing(RequisitoObjetivo::getId))
                .collect(Collectors.groupingBy(RequisitoObjetivo::getVacanteId));
    }

    /** Las ciudades del catálogo por código, solo si alguna vacante del lote tiene una. */
    private Map<String, OpcionUbigeo> ciudadesDe(List<Vacante> deVacantes) {
        boolean hayAlguna = deVacantes.stream().anyMatch(v -> v.getCiudadUbigeo() != null);
        return hayAlguna ? catalogos.ciudadesPorCodigo() : Map.of();
    }

    /**
     * La ciudad de una vacante, con su nombre, o nulo si no tiene.
     *
     * <p>Si el código no está en el catálogo —no debería pasar: hay clave foránea— sale con el
     * código como nombre antes que perderse: un hueco donde iba la ciudad se lee como un
     * fallo de carga.
     */
    private static CiudadPublica ciudadDe(Vacante v, Map<String, OpcionUbigeo> ciudades) {
        if (v.getCiudadUbigeo() == null) {
            return null;
        }
        OpcionUbigeo opcion = ciudades.get(v.getCiudadUbigeo());
        return opcion == null
                ? new CiudadPublica(v.getCiudadUbigeo(), v.getCiudadUbigeo(), null)
                : new CiudadPublica(opcion.codigo(), opcion.nombre(), opcion.departamento());
    }

    private VacantePublica comoPublica(Vacante v, List<RequisitoObjetivo> suyos,
                                       String nombreEmpresa, Map<String, OpcionUbigeo> ciudades) {
        List<RequisitoPublico> reqs = suyos.stream()
                .map(r -> new RequisitoPublico(r.getId(), r.getDescripcion()))
                .toList();
        return new VacantePublica(v.getId(), v.getTitulo(), nombreEmpresa, v.getDescripcion(),
                v.getProposito(), v.getResponsabilidades(), v.getRequisitos(), v.getModalidad(),
                v.getHorario(), v.getUbicacion(), ciudadDe(v, ciudades), v.getPublicadaEn(),
                RemuneracionQueVeElCandidato.de(v), reqs);
    }
}
