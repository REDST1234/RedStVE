package com.bytedance.aivideo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_REMOTION = "remotion.exchange";
    public static final String QUEUE_RENDER_TASK = "remotion.render.queue";
    public static final String ROUTING_KEY_RENDER = "remotion.render.routing";

    @Bean
    public DirectExchange remotionExchange() {
        return new DirectExchange(EXCHANGE_REMOTION);
    }

    @Bean
    public Queue renderTaskQueue() {
        return new Queue(QUEUE_RENDER_TASK, true); // durable
    }

    @Bean
    public Binding renderTaskBinding(Queue renderTaskQueue, DirectExchange remotionExchange) {
        return BindingBuilder.bind(renderTaskQueue).to(remotionExchange).with(ROUTING_KEY_RENDER);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
