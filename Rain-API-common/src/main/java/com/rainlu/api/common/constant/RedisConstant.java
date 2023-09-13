package com.rainlu.api.common.constant;

public interface RedisConstant {


    //表示redis中key对应的value存在。值任意就行
    String EXIST_KEY_VALUE = "1";

    // 消息生产者处的消息可靠性保证：通过记录发送端成功发送的消息内容实现消息队列发送端的可靠性机制，保障发送端的消息成功路由到队列中
    String SEND_ORDER_PAY_SUCCESS_INFO = "rabbitmq:send:order:paySuccess:message:";

    // 消息消费者处的消息可靠性保证：
    String CONSUME_ORDER_PAY_SUCCESS_INFO = "rabbitmq:consume:order:paySuccess:message:";

    // 回调接口中对订单重复消息的幂等性保证：成功支付宝订单成功交易的记录，解决因为网络故障而多次重复收到阿里的回调通知导致的订单重复处理的问题
    String ALIPAY_TRADE_SUCCESS_RECORD = "alipay:trade:success:record:";

    //短信登录key
    String LOGIN_CODE_PRE = "user::email::register::";

}
