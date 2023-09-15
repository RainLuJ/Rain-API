package com.rainlu.api.order.listener;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.rainlu.api.common.common.ErrorCode;
import com.rainlu.api.common.exception.BusinessException;
import com.rainlu.api.common.model.entity.Order;
import com.rainlu.api.common.service.ApiBackendService;
import com.rainlu.api.order.service.TOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.rainlu.api.common.constant.RabbitMQConstant.ORDER_SUCCESS_QUEUE_NAME;
import static com.rainlu.api.common.constant.RedisConstant.*;
import static com.rainlu.api.order.enums.OrderStatusEnum.ORDER_PAY_SUCCESS_STATUS;
import static com.rainlu.api.order.enums.OrderStatusEnum.ORDER_PAY_TIMEOUT_STATUS;

/**
 * @description 订单支付成功后，会将成功的订单id信息发送给MQ，MQ接收到此条消息后会对订单进行处理
 *                  - 修改订单状态
 *                  - 给用户分配接口的调用次数
 */
@Component
@Slf4j
public class OrderPaySuccessListener {


    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private TOrderService orderService;

    @Resource
    private ApiBackendService apiBackendService;


    //监听order.pay.success.queue订单交易成功队列，实现订单状态的修改以及给用户分配购买的接口调用次数
    //生产者是懒加载机制，消费者是饿汉加载机制，二者机制不对应，所以消费者要自行创建队列并加载，否则会报错
    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queuesToDeclare = {@Queue(ORDER_SUCCESS_QUEUE_NAME)})
    public void receiveOrderMsg(String outTradeNo, Message message, Channel channel) throws IOException {

        /*
            1.保证消息发送的可靠性(消息生产者处的消息可靠性保障机制)：如果消息成功被监听到说明消息已经成功由`生产者`将消息发送到`队列`中，那就不需要生产者重新发送消息了。
              所以，删掉Redis中对于消息的记录。
        */
        redisTemplate.delete(SEND_ORDER_PAY_SUCCESS_INFO + outTradeNo);

        /*
            2.消费端的`消息幂等性`问题(消费端的消息可靠机制)：因为消费端开启了手动确认机制，这会产生消息重复消费的问题。
              解决方案：这里使用Redis记录已经成功处理的订单来解决。
                - 如果Redis中已经有了记录，说明已经被处理过
                - 如果订单状态为超时已取消，则不用再处理了
         */
        String orderSn = redisTemplate.opsForValue().get(CONSUME_ORDER_PAY_SUCCESS_INFO + outTradeNo);

        // 如果消息(订单)已经被处理了，则直接应答ACK
        if (StringUtils.isNoneBlank(orderSn)) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("orderSn", outTradeNo);
        Order order = orderService.getOne(queryWrapper);
        if (order == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "订单号不存在");
        }
        // 如果订单已经被[超时]取消，则不用再继续处理了，直接应答ACK
        if (order.getStatus().equals(ORDER_PAY_TIMEOUT_STATUS.getValue())) {
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            return;
        }

        /* 3.修改订单状态 */
        UpdateWrapper<Order> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("status", ORDER_PAY_SUCCESS_STATUS);
        updateWrapper.eq("orderSn", outTradeNo);
        boolean updateOrderStatus = orderService.update(updateWrapper);

        /* 4.给用户分配购买的接口调用次数 */
        Long userId = order.getUserId();
        Long interfaceId = order.getInterfaceId();
        Integer count = order.getCount();
        boolean updateInvokeCount = apiBackendService.updateUserInterfaceInvokeCount(userId, interfaceId, count);

        // 有任意操作执行失败，则拒绝消息，并将此条消息重新入队
        if (!updateOrderStatus || !updateInvokeCount) {
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            return;
        }

        // 5.为解决消费端的消息幂等性问题，这里需要记录已经被成功处理的消息。
        // 30分钟后订单会被取消，在订单被取消之前，这条记录需要一直存在
        // 被取消后的订单就算再次被发送到此类中进行消费，但由于已经过期，所以不会再执行重复的业务处理
        redisTemplate.opsForValue().set(CONSUME_ORDER_PAY_SUCCESS_INFO + outTradeNo, EXIST_KEY_VALUE, 30, TimeUnit.MINUTES);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }


}