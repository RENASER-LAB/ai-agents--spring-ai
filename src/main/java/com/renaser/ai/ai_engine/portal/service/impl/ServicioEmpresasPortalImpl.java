package com.renaser.ai.ai_engine.portal.service.impl;

import com.renaser.ai.ai_engine.ai.exception.ResourceNotFoundException;
import com.renaser.ai.ai_engine.archivo.entity.Archivo;
import com.renaser.ai.ai_engine.archivo.repository.ArchivoRepository;
import com.renaser.ai.ai_engine.archivo.service.AlmacenArchivos;
import com.renaser.ai.ai_engine.organizacion.entity.Organizacion;
import com.renaser.ai.ai_engine.organizacion.entity.OrganizacionBeneficio;
import com.renaser.ai.ai_engine.organizacion.entity.Sede;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionBeneficioRepository;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.organizacion.repository.SedeRepository;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.ImagenesDeLaEmpresa.Cual;
import com.renaser.ai.ai_engine.organizacion.service.SedesDeLaEmpresa;
import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure;
import com.renaser.ai.ai_engine.organizacion.service.ServicioBrochure.ArchivoDelBrochure;
import com.renaser.ai.ai_engine.organizacion.service.TamanoDeEmpresa;
import com.renaser.ai.ai_engine.perfil.dto.DtosPerfil.OpcionUbigeo;
import com.renaser.ai.ai_engine.perfil.service.CatalogosDelPerfil;
import com.renaser.ai.ai_engine.portal.dto.DtosPortal.PerfilDeEmpresa;
import com.renaser.ai.ai_engine.portal.service.PresentacionEnElPortal;
import com.renaser.ai.ai_engine.portal.service.ServicioEmpresasPortal;
import com.renaser.ai.ai_engine.portal.service.ServicioTablonPortal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El perfil público de una empresa: ver {@link ServicioEmpresasPortal}. Solo lectura y sin
 * token, como el tablón: aquí no cuelga nada de ningún candidato.
 */
@Service
@RequiredArgsConstructor
public class ServicioEmpresasPortalImpl implements ServicioEmpresasPortal {

    /** Lo único que se sirve como imagen pública: lo que deja el re-codificado. */
    private static final Set<String> TIPOS_DE_IMAGEN = Set.of("image/png", "image/jpeg");

    private final OrganizacionRepository organizaciones;
    private final OrganizacionBeneficioRepository beneficios;
    private final SedeRepository sedes;
    private final CatalogosDelPerfil catalogos;
    private final ServicioTablonPortal tablon;
    private final PresentacionEnElPortal presentacion;
    private final ArchivoRepository archivos;
    private final AlmacenArchivos almacen;
    private final ServicioBrochure brochures;

    @Override
    @Transactional(readOnly = true)
    public PerfilDeEmpresa perfil(String codigo) {
        Organizacion empresa = laActiva(codigo);
        List<Sede> activas = sedes.findByOrganizacionIdAndEsActivaTrueOrderByNombre(empresa.getId());
        Map<String, OpcionUbigeo> provincias = SedesDeLaEmpresa.algunaConProvincia(activas)
                ? catalogos.ciudadesPorCodigo() : Map.of();
        return new PerfilDeEmpresa(empresa.getCodigo(), empresa.getNombre(),
                SedesDeLaEmpresa.ciudad(activas, provincias),
                ImagenesDeLaEmpresa.direccionPublica(empresa.getCodigo(), Cual.LOGO,
                        empresa.getLogoArchivoId()),
                ImagenesDeLaEmpresa.direccionPublica(empresa.getCodigo(), Cual.PORTADA,
                        empresa.getPortadaArchivoId()),
                empresa.getSector(), empresa.getTamano(),
                TamanoDeEmpresa.textoDe(empresa.getTamano()), empresa.getAnioFundacion(),
                SedesDeLaEmpresa.provincias(activas, provincias), empresa.getQuienesSomos(),
                empresa.getPropositoEmpresa(),
                PresentacionEnElPortal.videoDe(empresa.getVideoPresentacion()),
                beneficios.findByOrganizacionIdOrderByOrden(empresa.getId()).stream()
                        .map(OrganizacionBeneficio::getTexto).toList(),
                empresa.getPaginaWeb(), presentacion.redesDe(empresa),
                tablon.vacantesPublicadasDe(empresa), empresa.getMision(), empresa.getVision(),
                ServicioBrochure.tieneLoMinimo(empresa)
                        ? "/api/v1/portal/empresas/"
                        + ImagenesDeLaEmpresa.codigoEnLaDireccion(empresa.getCodigo()) + "/brochure"
                        : null);
    }

    @Override
    @Transactional(readOnly = true)
    public ArchivoDelBrochure brochure(String codigo) {
        Organizacion empresa = laActiva(codigo);
        return brochures.dePublica(empresa)
                .orElseThrow(() -> new ResourceNotFoundException("Brochure", "codigo", codigo));
    }

    @Override
    @Transactional(readOnly = true)
    public Imagen imagen(String codigo, String cual) {
        Organizacion empresa = laActiva(codigo);
        Cual cualEs = Cual.deTexto(cual)
                .orElseThrow(() -> new ResourceNotFoundException("Imagen", "nombre", cual));
        Long archivoId = cualEs == Cual.LOGO ? empresa.getLogoArchivoId()
                : empresa.getPortadaArchivoId();
        if (archivoId == null) {
            throw new ResourceNotFoundException("Imagen", "nombre", cual);
        }
        // Con la organización: aunque el id saliera de otro sitio, solo se entrega un archivo de
        // esta empresa, vivo, y que sea una de las dos imágenes que se re-codificaron al subir.
        Archivo archivo = archivos.findByIdAndOrganizacionId(archivoId, empresa.getId())
                .filter(a -> a.getBorradoEn() == null && a.getRuta() != null)
                .filter(a -> TIPOS_DE_IMAGEN.contains(a.getTipo()))
                .orElseThrow(() -> new ResourceNotFoundException("Imagen", "nombre", cual));
        return new Imagen(almacen.leer(archivo), archivo.getTipo());
    }

    /**
     * La empresa ACTIVA con ese código, sin mirar mayúsculas. Una suspendida, un código que no
     * existe —o dos que solo se distinguen por las mayúsculas— dan el mismo 404.
     */
    private Organizacion laActiva(String codigo) {
        List<Organizacion> conEse = codigo == null ? List.of()
                : organizaciones.conElCodigo(codigo.strip());
        if (conEse.size() != 1 || !conEse.get(0).isEsActiva()) {
            throw new ResourceNotFoundException("Empresa", "codigo", codigo);
        }
        return conEse.get(0);
    }
}
