import java.util.*;
import java.time.*;
import com.renaser.ai.ai_engine.ai.messaging.*;
import com.renaser.ai.ai_engine.ai.dto.*;
import com.renaser.ai.ai_engine.ai.model.*;
import com.renaser.ai.ai_engine.ai.repository.*;
import com.renaser.ai.ai_engine.ai.mapper.*;
import com.renaser.ai.ai_engine.ai.service.*;
import com.renaser.ai.ai_engine.ai.service.impl.*;
import com.renaser.ai.ai_engine.perfilintegral.service.PuenteCalificacionIa;
import com.renaser.ai.ai_engine.organizacion.repository.OrganizacionRepository;
import com.renaser.ai.ai_engine.postulacion.service.impl.AnonimizadorCv;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class AuditoriaRnf {
  static void check(boolean result, String message) {
    if (!result) throw new AssertionError(message);
    System.out.println("REPRODUCIDO: " + message);
  }
  public static void main(String[] args) {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    List<AgentHandoffMessage> sent = new ArrayList<>();
    doAnswer(inv -> {sent.add(inv.getArgument(2)); return null;})
      .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));
    AgentHandoffPublisher pub = new AgentHandoffPublisher(rabbit);
    UUID id = UUID.randomUUID();
    pub.publishFanOut(id,id,"test","test",List.of(new RoutingItem("AG-05","test",1,List.of())),0,1);
    check(sent.isEmpty(), "routing AG-05 del contrato publica 0 mensajes");
    pub.publishFanOut(id,id,"test","test",List.of(new RoutingItem("CEO","test",1,List.of("pendiente"))),0,1);
    check(sent.size()==1, "dependsOn pendiente no impide publicar al agente dependiente");
    sent.clear();
    List<RoutingItem> routes = List.of("CEO","AUDITOR","FINANCE","EVENT","GROWTH").stream()
       .map(s -> new RoutingItem(s,"test",1,List.of())).toList();
    pub.publishFanOut(id,id,"test","test",routes,0,1);
    int cursor=0;
    while(cursor<sent.size()) {
      var m=sent.get(cursor++);
      pub.publishFanOut(id,id,"test","test",routes,m.depth(),m.totalRuns());
    }
    check(sent.size()+1>AgentChainLimits.MAX_AGENT_RUNS,
      "un flujo permite " +(sent.size()+1)+" corridas con MAX_AGENT_RUNS="+AgentChainLimits.MAX_AGENT_RUNS);

    AgentInvoker invoker=mock(AgentInvoker.class);
    AgentRunRepository repo=mock(AgentRunRepository.class);
    AgentRunMapper mapper=mock(AgentRunMapper.class);
    AgentHandoffPublisher handoff=mock(AgentHandoffPublisher.class);
    AgentExecutionRequestPublisher executionPub=mock(AgentExecutionRequestPublisher.class);
    var service=new AgentExecutionServiceImpl(invoker,repo,mapper,handoff,executionPub);
    AgentRun run=AgentRun.builder().id(id).flowId(id).entityId("test").objective("test").build();
    AgentResponse<Object> response=new AgentResponse<>(Severity.INFO,List.of(),List.of(),null,
       new HumanGateBlock(true,"aprobar","test","direccion"),List.of(),routes,null);
    when(invoker.ask(any())).thenAnswer(call -> response);
    doAnswer(call -> {AgentRun r=call.getArgument(0); r.setRequiresHumanApproval(true);
      r.setOutputJson("{}"); r.setFinishedAt(Instant.now()); return null;})
      .when(invoker).applyResult(any(),any());
    when(mapper.toAgenteRun(any())).thenReturn(run);
    when(repo.save(any())).thenAnswer(call -> call.getArgument(0));
    when(repo.findById(id)).thenReturn(Optional.of(run));
    service.execute(new AgentRunRequest(AgentType.CEO,"test","test"));
    verify(handoff).publishFanOut(any(),any(),any(),any(),any(),anyInt(),anyInt());
    check(run.isRequiresHumanApproval()&&!run.isApproved(),
      "se publica fan-out aunque humanGate.required=true y approved=false (no prueba escrituras externas)");
    clearInvocations(invoker);
    var msg=new AgentExecutionMessage(id,id,null,AgentType.CEO,"test","test",0,1);
    service.completeExecution(msg);
    service.completeExecution(msg);
    verify(invoker,times(2)).ask(any());
    check(true,"redelivery de un run terminado vuelve a invocar al modelo dos veces");

    String cv=new AnonimizadorCv().anonimizar("Tengo 10 años de experiencia en Java. Con 2 hijos.");
    check(!cv.contains("10 años")&&cv.contains("2 hijos"),
      "anonimización elimina años de experiencia y conserva 'Con 2 hijos': "+cv);

    TrabajoIaRepository trabajos=mock(TrabajoIaRepository.class);
    var terminado=TrabajoIa.builder().id(7L).postulacionId(1L).agenteCodigo("EVALUADOR")
      .modo("FINA").estado("TERMINADO").build();
    when(trabajos.findFirstByPostulacionIdAndAgenteCodigoAndModoOrderByIdDesc(1L,"EVALUADOR","FINA"))
       .thenReturn(Optional.of(terminado));
    var puente=mock(PuenteCalificacionIa.class);
    when(puente.tieneEvaluacionEntregada(1L)).thenReturn(true);
    when(puente.organizacionDe(1L)).thenReturn(1L);
    var cola=new ColaCalificacionIaImpl(trabajos,new RegistroTrabajosIa(trabajos),
      mock(TrabajoIaPublisher.class),puente,mock(TopeMensualIa.class),
      mock(OrganizacionRepository.class),List.of(),true,3,15);
    check(!cola.reencolarEvaluador(1L),"reencolarEvaluador devuelve false para EVALUADOR terminado");
  }
}
