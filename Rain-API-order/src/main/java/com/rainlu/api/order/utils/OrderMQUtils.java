package com.rainlu.api.order.utils;


import cn.hutool.core.util.IdUtil;
import com.rainlu.api.common.model.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import static com.rainlu.api.order.config.RabbitMQConfig.EXCHANGE_ORDER_PAY;
import static com.rainlu.api.order.config.RabbitMQConfig.ROUTINGKEY_ORDER_PAY;

/**
 * @description 添加|创建订单成功后，将创建好的订单发送至延时队列，便于后续进行超时取消逻辑
 * @author Jun Lu
 * @date 2023-09-14 20:22:16
 */
@Slf4j
@Component
public class OrderMQUtils implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {


    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 订单创建成功后，向MQ发送订单消息
     *
     * @param order
     */
    public void sendOrderSnInfo(Order order) {
        String finalMessageId = IdUtil.simpleUUID();
        rabbitTemplate.convertAndSend(EXCHANGE_ORDER_PAY, ROUTINGKEY_ORDER_PAY, order, message -> {
            MessageProperties messageProperties = message.getMessageProperties();
            //生成全局唯一id
            messageProperties.setMessageId(finalMessageId);
            //设置消息的有效时间
//            message.getMessageProperties().setExpiration("1000*60");
            messageProperties.setContentEncoding("utf-8");
            return message;
        });
    }

    /**
     * @description 消息生产者发送消息至交换机时触发，用于判断交换机是否成功收到消息
     * @param correlationData 当前消息的唯一关联数据（消息的唯一id）
     * @param success         消息是否成功收到
     * @param cause           失败的原因
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean success, String cause) {
        if (!success) {
            log.error("订单--消息投递到交换机失败：{}---->{}", correlationData, cause);
        }
    }


    @PostConstruct
    public void init() {
        // 设置交换机处理失败消息的模式。true表示消息由交换机 到达不了队列时，会将消息重新返回给生产者
        // 如果不设置这个指令，则交换机向队列推送消息失败后，不会触发 setReturnsCallback
        rabbitTemplate.setMandatory(true);

        // 消息消费者确认收到消息后，手动ACK回执
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnsCallback(this);
    }

    /**
     * @description 在交换机未将数据丢入指定的队列中时，会触发`returnedMessage`回调方法
     * @param message   消息对象
     * @param replyCode 错误码
     * @param replyText 错误信息
     * @param exchange 交换机
     * @param routingKey 路由键
     */
    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        log.error("发生异常，返回消息回调:{}", message);
    }

    /**
     * @param returned 封装了一系列的参数，简化方法体
     * @description
     * @author Jun Lu
     * @date 2023-09-14 20:04:19
     */
    @Override
    public void returnedMessage(ReturnedMessage returned) {
        log.error("发生异常，返回消息回调:{}", returned);
    }
}
