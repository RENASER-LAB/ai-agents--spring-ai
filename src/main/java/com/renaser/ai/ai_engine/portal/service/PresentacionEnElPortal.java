package com.renaser.ai.ai_engine.portal.service;

import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionRedSocial;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRedSocialRepository;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.RedSocial;
import com.renaser.ai.ai_engine.organizacion.service.VideoDePresentacion;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.EmpresaDeLaVacante;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.RedPublica;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.VideoPublico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lo que el portal enseña de la presentación de una empresa (V81), armado en un solo sitio: el
 * tablón, el detalle de la vacante y el perfil público dicen lo mismo.
 *
 * <p>⚠️ <b>El tablón es la pantalla más pedida y se sirve sin cuenta.</b> Las redes de todas las
 * empresas del lote se cargan en UNA consulta ({@link #deLas}); las empresas ya vienen de la
 * consulta del colador del tablón, con su presentación en la misma fila. Nunca una consulta por
 * vacante.
 */
@Component
@RequiredArgsConstructor
public class PresentacionEnElPortal {

    private final OrganizacionRedSocialRepository redes;

    /** La presentación de cada empresa del lote, por su id; las que no tienen nada no están. */
    public Map<Long, EmpresaDeLaVacante> deLas(Collection<Organizacion> empresas) {
        if (empresas.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = empresas.stream().map(Organizacion::getId).distinct().toList();
        Map<Long, List<OrganizacionRedSocial>> porEmpresa = redes.findByOrganizacionIdIn(ids)
                .stream().collect(Collectors.groupingBy(OrganizacionRedSocial::getOrganizacionId));
        Map<Long, EmpresaDeLaVacante> resultado = new HashMap<>();
        for (Organizacion empresa : empresas) {
            EmpresaDeLaVacante suya = deUna(empresa,
                    porEmpresa.getOrDefault(empresa.getId(), List.of()));
            if (suya != null) {
                resultado.put(empresa.getId(), suya);
            }
        }
        return resultado;
    }

    /** La de una sola empresa: el detalle de una vacante. */
    public EmpresaDeLaVacante deUna(Organizacion empresa) {
        return deUna(empresa, redes.findByOrganizacionId(empresa.getId()));
    }

    /** Las redes de una empresa como las lee el portal, en el orden de siempre. */
    public List<RedPublica> redesDe(Organizacion empresa) {
        return comoPublicas(redes.findByOrganizacionId(empresa.getId()));
    }

    /**
     * Lo de una empresa, o nulo si no llenó nada de lo que se ve junto a la vacante: el
     * propósito, el video, la web, las redes y el logo.
     */
    private static EmpresaDeLaVacante deUna(Organizacion empresa, List<OrganizacionRedSocial> suyas) {
        List<RedPublica> publicas = comoPublicas(suyas);
        VideoPublico video = videoDe(empresa.getVideoPresentacion());
        String logo = ImagenesDeLaEmpresa.direccionPublica(empresa.getCodigo(),
                ImagenesDeLaEmpresa.Cual.LOGO, empresa.getLogoArchivoId());
        if (empresa.getPropositoEmpresa() == null && video == null
                && empresa.getPaginaWeb() == null && publicas.isEmpty() && logo == null) {
            return null;
        }
        return new EmpresaDeLaVacante(empresa.getPropositoEmpresa(), video,
                empresa.getPaginaWeb(), publicas, logo);
    }

    private static List<RedPublica> comoPublicas(List<OrganizacionRedSocial> filas) {
        return filas.stream()
                .filter(r -> RedSocial.deCodigo(r.getRed()).isPresent())
                .sorted(Comparator.comparingInt(r -> RedSocial.deCodigo(r.getRed())
                        .map(Enum::ordinal).orElse(Integer.MAX_VALUE)))
                .map(r -> new RedPublica(r.getRed(), r.getEnlace()))
                .toList();
    }

    /**
     * Un enlace de video guardado, reducido a servicio, identificador y clave. Nulo si no hay, o
     * si lo guardado ya no se reconoce: antes que pintar un recuadro que no se puede reproducir.
     */
    public static VideoPublico videoDe(String enlace) {
        if (enlace == null) {
            return null;
        }
        return VideoDePresentacion.leer(enlace)
                .map(v -> new VideoPublico(v.servicio(), v.identificador(), v.clave(), enlace))
                .orElse(null);
    }
}
