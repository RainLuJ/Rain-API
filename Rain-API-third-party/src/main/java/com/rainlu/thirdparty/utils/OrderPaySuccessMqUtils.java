package com.rainlu.thirdparty.utils;


import cn.hutool.core.lang.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static com.rainlu.api.common.constant.RabbitMQConstant.ORDER_EXCHANGE_NAME;
import static com.rainlu.api.common.constant.RabbitMQConstant.ORDER_SUCCESS_EXCHANGE_ROUTING_KEY;
import static com.rainlu.api.common.constant.RedisConstant.SEND_ORDER_PAY_SUCCESS_INFO;

/**
 * 订单支付成功之后，回调接口被支付平台调用。
 * 在回调接口中，持久化成功支付的订单信息，并将订单id传递给消息队列。
 *
 * 监听此队列的消费者
 */
@Component
@Slf4j
public class OrderPaySuccessMqUtils {
    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * @param outTradeNo 我们自己的订单号
     */
    public void sendOrderPaySuccess(String outTradeNo) {

        /* 保证发送端的消息发送可靠性 */

        // 向Redis中记录当前发送的订单[id]消息，如果发送端的消息发送失败，则利用Redis中的消息重新
        redisTemplate.opsForValue().set(SEND_ORDER_PAY_SUCCESS_INFO + outTradeNo, outTradeNo);

        String finalMessageId = UUID.randomUUID().toString();
        rabbitTemplate.convertAndSend(ORDER_EXCHANGE_NAME, ORDER_SUCCESS_EXCHANGE_ROUTING_KEY, outTradeNo, message -> {
            MessageProperties messageProperties = message.getMessageProperties();
            //生成全局唯一id
            messageProperties.setMessageId(finalMessageId);
            messageProperties.setContentEncoding("utf-8");
            return message;
        });
        log.info("消息队列给订单服务发送支付成功消息，订单号：" + outTradeNo);
    }

}
