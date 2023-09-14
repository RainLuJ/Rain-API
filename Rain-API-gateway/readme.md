# 网关服务


## 网关限流



## 统一鉴权

请求中是否携带Token？Token是否合法？


## API签名校验 

1. 校验**客户端调用传递过来的签名**与**服务端根据同样签名生成规则生成的签名**是否相同（API签名校验）
2. 为避免**接口重放**，还需要校验`nonce`（随机生成的单次令牌）是否已经存在于Redis中
  > nonce被存储在Redis中，并设置短的过期时间（5min）。如果设置成功，则没有产生接口重放，反之相反。 


## 统一日志处理

请求日志 & 响应日志


## 接口[调用次数]统计

判断接口是否还有调用次数（根据 `接口id` & `当前用户的id` 查询 `用户调用接口关系表`）
- 如果没有调用次数了，则直接拒绝本次调用
- 如果还有调用次数，则：将接口的调用次数 + 1 & 将接口的剩余调用次数 - 1

> 使用**乐观锁**解决接口统计时的并发安全问题 {
>     为避免次数统计的并发安全问题（类似于商品超卖问题），本系统采用乐观锁来解决。 
>     因为本系统的并发量并不是特别大，所以出现冲突的概率较低。又由于使用乐观锁，对性能的影响较小，所以才使用乐观锁
> }

类似于商品超卖问题。只不过这里是：`判断接口是否还有调用次数` & `将接口的被调用次数 + 1` 是一个非原子性操作。
这会产生接口**超额调用**的问题。
解决方式：
    - 乐观锁
    - 本地锁
    - 分布式锁
        > 本质都是保证这两个操作的原子性


## 接口数据一致性处理

在网关中通过一系列“验证”后，开始进行接口调用。预期是等待模拟接口调用完成后再进行后续的校验操作，
但现实是：`chain.filter(exchange)`方法被调用后会立刻返回，直到被return后才会调用模拟接口。
所以目前的问题是：
> 怎样才能让`接口`被调用后再进行后续的校验呢？{
    使用响应对象的装饰器对象：ServerHttpResponseDecorator
}

> 由于在第5步中就已经进行了接口次数的统计，那如果后续接口调用失败了，阁下将如何应对呢？ {
    由于RabbitMQ具有消息可靠性保障机制，所以才使用RabbitMQ完成接口调用失败后的接口调用次数的回滚。
    具体实现：将失败调用的 `接口id` & `当前用户id`封装为消息，传送进队列，消费者进行调用次数 & 剩余次数的回滚
}

> 为什么不在接口调用次数完毕后再进行次数的统计？这样不是就不用回滚了吗？{
    确实是不用回滚了。但如果接口没有剩余次数了，这样做就会仍然被调用，这会对实际业务造成影响，
    不说影响性能了，这本质上就和免费再给用户一次调用机会一样，只是没有获取到调用结果而已。而这是不被允许的！！！
}


### 消息队列回滚调用次数

```java
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
    public void receiveSms(UserInterfaceInfoMessage userInterfaceInfoMessage, Message message, Channel channel) 
            throws IOException {
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
```
