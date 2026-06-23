package com.bytedance.aivideo.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_REMOTION = "remotion.exchange";
    public static final String QUEUE_RENDER_TASK = "remotion.render.queue";
    public static final String ROUTING_KEY_RENDER = "remotion.render.routing";

    // --- 死信队列配置常量 ---
    public static final String EXCHANGE_DLX = "remotion.dlx.exchange";
    public static final String QUEUE_RENDER_DLQ = "remotion.render.dlq";
    public static final String ROUTING_KEY_DLQ = "remotion.render.dlq.routing";

    @Bean
    public DirectExchange remotionExchange() {
        return new DirectExchange(EXCHANGE_REMOTION);
    }

    @Bean
    public Queue renderTaskQueue() {
        // 改造主队列：当消息被拒绝 (nack) 且 requeue=false 时，自动路由到死信交换机
        return QueueBuilder.durable(QUEUE_RENDER_TASK)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_DLQ)
                // 混沌工程增强 1：超时宕机兜底，消息最多存活 60 秒 (60000ms)，没人收就送进死信队列退钱
                .withArgument("x-message-ttl", 60000)
                // 混沌工程增强 2：内存雪崩兜底，队列最多只能堆积 1000 个任务，超出的直接送进死信队列退钱
                .withArgument("x-max-length", 1000)
                .build();
    }

    @Bean
    public Binding renderTaskBinding(Queue renderTaskQueue, DirectExchange remotionExchange) {
        return BindingBuilder.bind(renderTaskQueue).to(remotionExchange).with(ROUTING_KEY_RENDER);
    }

    // ==========================================
    // 死信架构配置 (Dead Letter Architecture)
    // ==========================================

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EXCHANGE_DLX);
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(QUEUE_RENDER_DLQ, true);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY_DLQ);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
