package com.rainlu.thirdparty.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.rainlu.api.common.constant.RabbitMQConstant.*;

// TODO 与订单超时取消所配置的消息队列的作用区别
/**
 * RabbitMQ配置
 */
@Slf4j
@Configuration
public class AliPayRabbitMQConfig {

    //声明交换机
    @Bean
    public Exchange alipayExchange() {
        return new DirectExchange(ORDER_EXCHANGE_NAME, true, false);
    }

    /**
     * 普通队列
     *
     * @return
     */
    @Bean
    public Queue alipayQueue() {
        return new Queue(ORDER_SUCCESS_QUEUE_NAME, true, false, false, null);
    }


    /**
     * 交换机和普通队列绑定
     *
     * @return
     */
    @Bean
    public Binding alipayBinding() {
        return new Binding(ORDER_SUCCESS_QUEUE_NAME,
                Binding.DestinationType.QUEUE, ORDER_EXCHANGE_NAME,
                ORDER_SUCCESS_EXCHANGE_ROUTING_KEY, null);
    }

}
