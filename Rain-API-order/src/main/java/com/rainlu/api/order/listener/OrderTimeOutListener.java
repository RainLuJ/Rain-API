package com.rainlu.api.order.listener;


import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.rabbitmq.client.Channel;
import com.rainlu.api.common.model.entity.Order;
import com.rainlu.api.common.service.ApiBackendService;
import com.rainlu.api.order.service.TOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

import static com.rainlu.api.order.config.RabbitMQConfig.QUEUE_ORDER_DLX_QUEUE;
import static com.rainlu.api.order.enums.OrderStatusEnum.ORDER_NOT_PAY_STATUS;
import static com.rainlu.api.order.enums.OrderStatusEnum.ORDER_PAY_TIMEOUT_STATUS;

@Component
@Slf4j
public class OrderTimeOutListener {

    @DubboReference
    private ApiBackendService apiBackendService;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    @Resource
    private TOrderService orderService;


    //监听queue_order_dlx_queue死信队列，实现支付超时的回滚功能
    //生产者是懒加载机制，消费者是饿汉加载机制，二者机制不对应，所以消费者要自行创建队列并加载，否则会报错
    @RabbitListener(queuesToDeclare = {@Queue(QUEUE_ORDER_DLX_QUEUE)})
    public void receiveOrderMsg(Order order, Message message, Channel channel) throws IOException {

        log.info("监听到消息啦，内容是：" + order.toString());
        Long orderId = order.getId();
        Order dbOrder = orderService.getById(orderId);

        /* 根据订单状态判断订单是否支付成功 */
        // 只负责处理状态为`未支付`的超时订单
        if (dbOrder.getStatus().equals(ORDER_NOT_PAY_STATUS.getValue())) {
            // TODO 1. 向支付平台发起请求，确认这些状态为没有支付成功的订单是否真的没有被支付，还是由于网络抖动而导致回调接口的异常
            /* if (向支付平台发起请求，发现此订单确实没有支付) {
                    // TODO 2. 向支付平台发请求：先关闭本次交易，
                    再执行 **库存回滚逻辑** 与 **状态修改逻辑**
               }
             */
            Long interfaceId = dbOrder.getInterfaceId();
            Integer count = order.getCount();
            try {
                /* 回滚库存 */
                boolean success = apiBackendService.recoverInterfaceStock(interfaceId, count);
                if (!success) {
                    log.error("回滚库存失败!!!");
                    channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
                }

                /* 更新订单状态为：已取消 */
                UpdateWrapper<Order> updateWrapper = new UpdateWrapper<>();
                updateWrapper.set("status", ORDER_PAY_TIMEOUT_STATUS.getValue());
                updateWrapper.eq("id", dbOrder.getId());
                orderService.update(updateWrapper);

            } catch (Exception e) {
                log.error("回滚库存失败!!!");
                e.printStackTrace();
                channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);

                return;
            }
        }


        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }


}