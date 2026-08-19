package com.renaser.ai.ai_engine.ai.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_run")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class AgentRun
{
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private AgentType agentType;

    // Correlaciona todas las corridas disparadas por una misma consulta inicial. Junto con
    // parentRunId permite reconstruir el árbol completo del flujo (quién disparó a quién),
    // que entityId por sí solo no distingue si se consulta la misma entidad dos veces.
    private UUID flowId;
    private UUID parentRunId;
    private int depth;

    private String entityId;

    // Texto libre del usuario: con el varchar(255) por defecto, un objetivo algo largo
    // reventaba la inserción con un error de base de datos.
    @Column(length = 4000)
    private String objective;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String outputJson;

    // info|attention|risk|critical — viene del envelope, null mientras el run está pendiente
    private String severity;

    // derivado de humanGate.required() del envelope (Human Gate es por acción, no por agente,
    // pero a nivel de fila seguimos necesitando saber si ESTE run tiene algo pendiente de aprobar)
    private boolean requiresHumanApproval;
    private boolean approved;

    // nextAgent (columna única) ya no existe: el V2 usa routing[] con varios destinos posibles
    // en paralelo. El fan-out se lee directo del outputJson al momento del handoff, no se
    // persiste como columna — ver AgentExecutionServiceImpl.

    private Instant createdAt;

    // Null mientras la corrida está en cola o ejecutándose. Junto con createdAt da la
    // duración real de cada paso, que es lo que hay que mirar para decidir infraestructura.
    private Instant finishedAt;

    // Una corrida que falla debe quedar marcada como fallida, no pendiente para siempre:
    // sin esto un error del modelo era indistinguible de "todavía procesando".
    @Column(length = 2000)
    private String errorMessage;

    private String version;
}
