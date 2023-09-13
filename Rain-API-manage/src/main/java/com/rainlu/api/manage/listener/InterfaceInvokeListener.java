package com.rainlu.api.manage.listener;


import com.rabbitmq.client.Channel;
import com.rainlu.api.common.model.vo.UserInterfaceInfoMessage;
import com.rainlu.api.manage.service.UserInterfaceInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

import static com.rainlu.api.common.constant.RabbitMQConstant.QUEUE_INTERFACE_CONSISTENT;

/**
 * 当接口调用失败时，被调用失败的 `接口的id` 和 `当前用户的id` 会被存入队列。当前类就是监听这个队列的消费者。
 * 即：如果接口调用失败，负责回滚数据库中的的接口统计数据(`user_interface_info`)
 */
@Component
@Slf4j
public class InterfaceInvokeListener {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserInterfaceInfoService userInterfaceInfoService;


    //监听queue_interface_consistent队列，实现接口统计功能
    //生产者是懒加载机制，消费者是饿汉加载机制，二者机制不对应，所以消费者要自行创建队列并加载，否则会报错
    @RabbitListener(queuesToDeclare = {@Queue(QUEUE_INTERFACE_CONSISTENT)})
    public void receiveSms(UserInterfaceInfoMessage userInterfaceInfoMessage, Message message, Channel channel) throws IOException {
        log.info("监听到消息啦，内容是：" + userInterfaceInfoMessage);

        Long userId = userInterfaceInfoMessage.getUserId();
        Long interfaceInfoId = userInterfaceInfoMessage.getInterfaceInfoId();

        boolean result = false;
        try {
            // 回滚 user_interface_info 表中对应的记录
            result = userInterfaceInfoService.recoverInvokeCount(userId, interfaceInfoId);
        } catch (Exception e) {
            log.error("接口统计数据回滚失败！！！");
            e.printStackTrace();
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            return;
        }

        if (!result) {
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }


}