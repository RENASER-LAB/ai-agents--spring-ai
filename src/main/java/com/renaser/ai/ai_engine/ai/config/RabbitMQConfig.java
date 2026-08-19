package com.renaser.ai.ai_engine.ai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String AGENT_EXCHANGE = "agent.exchange";
    public static final String AGENT_HANDOFF_QUEUE = "agent.handoff.queue";
    public static final String AGENT_HANDOFF_ROUTING_KEY = "agent.handoff.*";
    public static final String AGENT_EXECUTION_QUEUE = "agent.execution.queue";
    public static final String AGENT_EXECUTION_ROUTING_KEY = "agent.execute";

    // La cola del hito 2 de selección: los tres agentes que califican una postulación.
    // Va aparte de agent.execution.queue a propósito. Son mecanismos distintos: aquélla
    // ejecuta los agentes genéricos de RENASER OS con su envelope común, y ésta atiende
    // filas de trabajo_ia, que se reintentan por su cuenta y escriben sobre las tablas de
    // selección. Compartir cola mezclaría dos formatos de mensaje en el mismo consumidor.
    public static final String SELECCION_CALIFICACION_QUEUE = "seleccion.calificacion.queue";
    public static final String SELECCION_CALIFICACION_ROUTING_KEY = "seleccion.calificar";

    // Destino de los mensajes que no se pudieron procesar. Nada se pierde en silencio:
    // queda aquí para inspección en vez de reintentarse indefinidamente.
    public static final String AGENT_DLX = "agent.dlx";
    public static final String AGENT_DLQ = "agent.dlq";
    public static final String AGENT_DLQ_ROUTING_KEY = "agent.dead";

    /**
     * Tope de entregas de un mismo mensaje. Es la única protección real contra el bucle que
     * provoca el consumer_timeout de RabbitMQ: si la inferencia excede ese tiempo, el broker
     * reencola el mensaje por su cuenta y sin este límite el trabajo se repetiría para
     * siempre, quemando CPU. Una corrida medida hoy tardó 31 min y cruzó ese umbral.
     */
    private static final int MAX_DELIVERIES = 3;

    @Bean
    public TopicExchange agentExchange() {
        return new TopicExchange(AGENT_EXCHANGE);
    }

    @Bean
    public DirectExchange agentDeadLetterExchange() {
        return new DirectExchange(AGENT_DLX);
    }

    @Bean
    public Queue agentDeadLetterQueue() {
        return QueueBuilder.durable(AGENT_DLQ).build();
    }

    @Bean
    public Binding agentDeadLetterBinding(Queue agentDeadLetterQueue, DirectExchange agentDeadLetterExchange) {
        return BindingBuilder.bind(agentDeadLetterQueue).to(agentDeadLetterExchange).with(AGENT_DLQ_ROUTING_KEY);
    }

    // Colas quorum: las clásicas no soportan x-delivery-limit, que es justo lo que acota
    // los reintentos automáticos del broker.
    @Bean
    public Queue agentHandoffQueue() {
        return protectedQueue(AGENT_HANDOFF_QUEUE);
    }

    @Bean
    public Queue agentExecutionQueue() {
        return protectedQueue(AGENT_EXECUTION_QUEUE);
    }

    private Queue protectedQueue(String name) {
        return QueueBuilder.durable(name)
                .quorum()
                .deliveryLimit(MAX_DELIVERIES)
                .deadLetterExchange(AGENT_DLX)
                .deadLetterRoutingKey(AGENT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding agentHandoffBinding(Queue agentHandoffQueue, TopicExchange agentExchange) {
        return BindingBuilder.bind(agentHandoffQueue).to(agentExchange).with(AGENT_HANDOFF_ROUTING_KEY);
    }

    @Bean
    public Binding agentExecutionBinding(Queue agentExecutionQueue, TopicExchange agentExchange) {
        return BindingBuilder.bind(agentExecutionQueue).to(agentExchange).with(AGENT_EXECUTION_ROUTING_KEY);
    }

    @Bean
    public Queue seleccionCalificacionQueue() {
        return protectedQueue(SELECCION_CALIFICACION_QUEUE);
    }

    @Bean
    public Binding seleccionCalificacionBinding(Queue seleccionCalificacionQueue,
                                                TopicExchange agentExchange) {
        return BindingBuilder.bind(seleccionCalificacionQueue).to(agentExchange)
                .with(SELECCION_CALIFICACION_ROUTING_KEY);
    }

    /**
     * defaultRequeueRejected=false: un mensaje que falla va directo a la DLQ en vez de volver
     * a la cola. Sin esto, el comportamiento por defecto de Spring AMQP es reencolar en bucle
     * — que es exactamente lo que pasó dos veces hoy con mensajes de formato viejo, y obligó
     * a purgar las colas a mano.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
