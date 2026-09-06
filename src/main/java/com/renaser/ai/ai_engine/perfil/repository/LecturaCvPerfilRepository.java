package com.renaser.ai.ai_engine.perfil.repository;

import com.renaser.ai.ai_engine.perfil.entity.LecturaCvPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LecturaCvPerfilRepository extends JpaRepository<LecturaCvPerfil, Long> {

    /**
     * La lectura de ESE archivo, que es la única que describe lo que hay ahora en el perfil.
     *
     * <p>⚠️ <b>Por el archivo y no por «la última».</b> Las lecturas no se borran nunca —son
     * el recibo de lo ya pagado (RF-161)—, así que quien quitó su currículum sigue teniendo
     * filas {@code LISTA} de uno que ya no está. Preguntar por la más reciente diría «listo»
     * sobre un currículum ausente.
     */
    Optional<LecturaCvPerfil> findFirstByPersonaIdAndArchivoIdOrderByIdDesc(
            Long personaId, Long archivoId);

    /** La que esté corriendo, para cerrarla antes de arrancar otra. */
    Optional<LecturaCvPerfil> findByPersonaIdAndEstado(Long personaId, String estado);

    /**
     * Lecturas de esta persona sobre un archivo con el mismo contenido, ya terminadas bien.
     *
     * <p>Es lo que evita pagar dos veces la misma lectura (RF-161): la huella del archivo
     * dice que ese PDF ya se leyó.
     */
    List<LecturaCvPerfil> findByPersonaIdAndArchivoIdInAndEstado(
            Long personaId, List<Long> archivoIds, String estado);
}
