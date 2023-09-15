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
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;

import static com.rainlu.api.common.constant.RabbitMQConstant.ORDER_SUCCESS_QUEUE_NAME;
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

        /* 修改订单状态 */
        UpdateWrapper<Order> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("status", ORDER_PAY_SUCCESS_STATUS);
        updateWrapper.eq("orderSn", outTradeNo);
        boolean updateOrderStatus = orderService.update(updateWrapper);

        /* 给用户分配购买的接口调用次数 */
        Long userId = order.getUserId();
        Long interfaceId = order.getInterfaceId();
        Integer count = order.getCount();
        boolean updateInvokeCount = apiBackendService.updateUserInterfaceInvokeCount(userId, interfaceId, count);

        // 有任意操作执行失败，则拒绝消息，并将此条消息重新入队
        if (!updateOrderStatus || !updateInvokeCount) {
            channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
            return;
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }


}