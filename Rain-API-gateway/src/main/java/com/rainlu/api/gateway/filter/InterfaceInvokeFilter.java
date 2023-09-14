package com.rainlu.api.gateway.filter;


import com.rainlu.api.common.model.entity.InterfaceInfo;
import com.rainlu.api.common.model.entity.User;
import com.rainlu.api.common.model.vo.UserInterfaceInfoMessage;
import com.rainlu.api.common.service.ApiBackendService;
import com.rainlu.api.common.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static com.rainlu.api.common.constant.RabbitMQConstant.EXCHANGE_INTERFACE_CONSISTENT;
import static com.rainlu.api.common.constant.RabbitMQConstant.ROUTING_KEY_INTERFACE_CONSISTENT;

/**
 * @description `用户 || SDK`对系统中的接口进行调用时的过滤器
 * @author Jun Lu
 * @date 2023-09-11 22:25:37
 */
@Component
@Slf4j
public class InterfaceInvokeFilter implements GatewayFilter, Ordered {

    private static final String INTERFACE_HOST = "http://localhost:8123";

    @DubboReference
    private ApiBackendService apiBackendService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        /*
            1.记录请求日志
            2.黑白名单放行(可做可不做)
            3.用户鉴权(API签名认证)
                - 校验**客户端调用传递过来的签名**与**服务端根据同样签名生成规则生成的签名**是否相同
                - 为避免**接口重放**，还需要校验`nonce`（随机的单次令牌）是否已经存在于Redis中
            4.远程调用接口服务，获取当前被调用接口的详细信息。根据是否获取到接口信息以判断当前调用的接口是否存在
            5.判断接口是否还有调用次数（根据 `接口id` & `当前用户的id` 查询 `用户调用接口` 的关系表）
                - 如果没有调用次数了，则直接拒绝本次调用
                - 如果还有调用次数，则：将接口的调用次数 + 1 & 将接口的剩余调用次数 - 1
                    > 使用**乐观锁**解决接口统计时的并发安全问题 {
                         - 为避免次数统计的并发安全问题（类似于商品超卖问题），本系统采用乐观锁来解决。
                         - 因为本系统的并发量并不是特别大，所以出现冲突的概率较低。又由于使用乐观锁，对性能的影响较小，所以才使用乐观锁
                      }
            6.在网关中通过一系列“验证”后，开始进行接口调用。预期是等待模拟接口调用完成后再进行后续的校验操作，
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
            7.获取接口调用的响应结果，打上响应日志
                 - 使用ServerHttpResponseDecorator完成操作
         */

        //1.打上请求日志
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        String path = INTERFACE_HOST + request.getPath().value();
        String method = request.getMethod().toString();

        log.info("请求id:" + request.getId());
        log.info("请求URI:" + request.getURI());
        log.info("请求PATH:" + request.getPath());
        log.info("请求参数:" + request.getQueryParams());
        log.info("本地请求地址:" + request.getLocalAddress());
        log.info("请求地址：" + request.getRemoteAddress());

        //2.黑白名单(可做可不做)
//        InetSocketAddress localAddress = request.getLocalAddress();
//        if (!"127.0.0.1".equals(localAddress.getHostString())){
//            response.setStatusCode(HttpStatus.FORBIDDEN);
//            return response.setComplete();
//        }

        //3.用户鉴权(API签名认证)
        HttpHeaders headers = request.getHeaders();

        String accessKey = headers.getFirst("accessKey");
        String body = headers.getFirst("body");
        String sign = headers.getFirst("sign");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");


        // 根据accessKey获取用户信息（当前的主要目的：一方面是判断accessKey是否合法，另一方面是获取到secretKey）
        User invokeUser = null;
        try {
            invokeUser = apiBackendService.getInvokeUser(accessKey);
        } catch (Exception e) {
            log.error("远程调用获取调用接口用户的信息失败");
            e.printStackTrace();
        }

        if (invokeUser == null) {
            return handleNoAuth(response);
        }

        String secretKey = invokeUser.getSecretKey();
        // 校验逻辑：根据当前用户的`秘钥`与`请求参数`生成签名，再与用户请求系统时携带的签名进行比对
        String serverSign = SignUtils.generateSign(body, secretKey);

        if (sign == null || !sign.equals(serverSign)) {
            log.error("签名校验失败!!!!");
            return handleNoAuth(response);
        }

        //3.1防止请求重放，使用redis存储请求的唯一标识，随机时间，并定时淘汰，那使用什么redis结构来实现嗯？
        //既然是单个数据，这样用string结构实现即可
        // setnx nonce 1
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(nonce, "1", 5, TimeUnit.MINUTES);
        if (success == null) {
            log.error("随机令牌存储失败!!!!");
            return handleNoAuth(response);
        }

        //4.远程调用判断接口是否存在以及获取调用接口信息
        InterfaceInfo interFaceInfo = null;
        try {
            interFaceInfo = apiBackendService.getInterFaceInfo(path, method);
        } catch (Exception e) {
            log.info("远程调用获取被调用接口信息失败");
            e.printStackTrace();
        }


        if (interFaceInfo == null) {
            log.error("接口不存在！！！！");
            return handleNoAuth(response);
        }


        // 5.判断接口是否还有调用次数，并统计接口调用次数。
        /*
            类似于商品超卖问题。只不过这里是：`判断接口是否还有调用次数` & `将接口的被调用次数 + 1` 是一个非原子性操作。
            这会产生接口**超额调用**的问题。
            解决方式：
                - 乐观锁
                - 本地锁
                - 分布式锁
                    > 本质都是保证这两个操作的原子性
         */
        boolean result = false;
        try {
            result = apiBackendService.invokeCount(invokeUser.getId(), interFaceInfo.getId());
        } catch (Exception e) {
            log.error("统计接口出现问题或者用户恶意调用不存在的接口");
            e.printStackTrace();
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }

        if (!result) {
            log.error("接口剩余次数不足");
            return handleNoAuth(response);
        }

        //6.发起接口调用，网关路由实现
        Mono<Void> filter = chain.filter(exchange);

        return handleResponse(exchange, chain, interFaceInfo.getId(), invokeUser.getId());

    }

    /**
     * 统一处理无权限调用异常：设置状态码 & 结束调用
     */
    @NotNull
    private Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }

    /**
     * @description
     */
    public Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain,
                                     long interfaceInfoId, long userId) {
        try {
            ServerHttpResponse originalResponse = exchange.getResponse();

            //缓存数据
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            //响应状态码
            HttpStatus statusCode = originalResponse.getStatusCode();

            if (statusCode == HttpStatus.OK) {
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {

                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        log.info("body instanceof Flux: {}", (body instanceof Flux));
                        if (body instanceof Flux) {
                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                            return super.writeWith(fluxBody.map(dataBuffer -> {
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                DataBufferUtils.release(dataBuffer);//释放掉内存

                                //7.获取响应结果，打上响应日志
                                // 构建日志
                                log.info("接口调用响应状态码：" + originalResponse.getStatusCode());
                                //responseBody
                                String responseBody = new String(content, StandardCharsets.UTF_8);

                                //8.接口调用失败，利用消息队列实现接口统计数据的回滚；因为消息队列的可靠性所以我们选择消息队列而不是远程调用来实现
                                if (!(originalResponse.getStatusCode() == HttpStatus.OK)) {
                                    log.error("接口异常调用-响应体:" + responseBody);
                                    UserInterfaceInfoMessage msg = new UserInterfaceInfoMessage(userId, interfaceInfoId);
                                    rabbitTemplate.convertAndSend(EXCHANGE_INTERFACE_CONSISTENT, ROUTING_KEY_INTERFACE_CONSISTENT, msg);
                                }

                                return bufferFactory.wrap(content);
                            }));
                        } else {
                            log.error("<--- {} 响应code异常", getStatusCode());
                        }
                        return super.writeWith(body);
                    }
                };
                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            return chain.filter(exchange);//降级处理返回数据
        } catch (Exception e) {
            log.error("gateway log exception.\n" + e);
            return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -2;
    }
}