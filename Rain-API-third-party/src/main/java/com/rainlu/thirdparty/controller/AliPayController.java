package com.rainlu.thirdparty.controller;


import cn.hutool.core.date.DateUtil;
import cn.hutool.extra.qrcode.QrCodeUtil;
import cn.hutool.extra.qrcode.QrConfig;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.rainlu.api.common.common.BaseResponse;
import com.rainlu.api.common.utils.ResultUtils;
import com.rainlu.thirdparty.config.AliPayConfig;
import com.rainlu.thirdparty.model.dto.AlipayRequest;
import com.rainlu.thirdparty.model.entity.AlipayInfo;
import com.rainlu.thirdparty.service.AlipayInfoService;
import com.rainlu.thirdparty.utils.OrderPaySuccessMqUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.rainlu.api.common.constant.RedisConstant.ALIPAY_TRADE_SUCCESS_RECORD;
import static com.rainlu.api.common.constant.RedisConstant.EXIST_KEY_VALUE;

/**
 * 支付宝沙箱支付
 */
@Slf4j
@RestController
@RequestMapping("/alipay")
public class AliPayController {


    @Resource
    private AliPayConfig aliPayConfig;

    @Resource
    private AlipayInfoService alipayInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private OrderPaySuccessMqUtils orderPaySuccessMqUtils;

    /**
     * @description 前端：用户点击“去支付”的时候，调用此接口
     * 支付预请求  ---》  向支付宝发起请求，获取支付短链接[二维码码串]
     * - 获取向支付宝发起请求的`客户端对象`
     * - 构造`预请求对象`和`请求参数模型`
     * - 使用`客户端对象`携带`预请求对象`向支付宝平台发起请求，获取到`支付响应对象`
     * - 解析`支付响应对象`中的支付短链接二维码码串，响应给前端，让用户扫码付款
     * @author Jun Lu
     * @date 2023-09-13 14:57:11
     */
    @PostMapping("/payCode")
    public BaseResponse<String> payCode(@RequestBody AlipayRequest alipayRequest) throws AlipayApiException {

        // 订单编号：是自己生成的，不是支付宝平台的。自己生成，然后给支付宝平台，当做本次订单的id
        String outTradeNo = alipayRequest.getTraceNo();
        // 订单描述
        String subject = alipayRequest.getSubject();
        // 订单总金额
        double totalAmount = alipayRequest.getTotalAmount();

        // 根据注册的商家信息创建支付宝交易客户端
        AlipayClient alipayClient = new DefaultAlipayClient(aliPayConfig.getGatewayUrl(),
                aliPayConfig.getAppId(),
                aliPayConfig.getPrivateKey(),
                "json", aliPayConfig.getCharset(),
                aliPayConfig.getPublicKey(),
                aliPayConfig.getSignType());

        // 创建支付宝交易请求对象
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        // 创建请求对象的参数模型
        AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
        // 设置支付成功之后的【异步回调接口】
        //request.setNotifyUrl("http://www.chen-code.work:9000/api/third/alipay/notify");
        request.setNotifyUrl(aliPayConfig.getNotifyUrl());
        // 封装请求对象的参数模型
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount(String.valueOf(totalAmount));
        model.setSubject(subject);
        request.setBizModel(model);

        // 使用支付宝交易客户端携带请求参数向支付宝平台发起请求，并获取到响应对象。
        // 主要目的是：获取响应对象中的“支付短链接(平台已经创建好了对应的二维码的码串)”
        AlipayTradePrecreateResponse response = alipayClient.execute(request);
        log.info("响应支付二维码详情：" + response.getBody());

        // 根据原生的`二维码码串`生成base64格式的字符串，便于二维码的转换
        String base64 = QrCodeUtil.generateAsBase64(response.getQrCode(), new QrConfig(300, 300), "png");

        return ResultUtils.success(base64);
    }


    /**
     * @description 支付成功的回调接口，注意这里必须是POST接口
     * @author Jun Lu
     * @date 2023-09-13 15:00:43
     */
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/notify")
    public synchronized void payNotify(HttpServletRequest request) throws Exception {

        if (request.getParameter("trade_status").equals("TRADE_SUCCESS")) {
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (String name : requestParams.keySet()) {
                params.put(name, request.getParameter(name));
            }

            // TODO 幂等性保证：判断该订单号是否被处理过，解决因为多次重复收到阿里的回调通知导致的订单重复处理的问题
            Object outTradeNo = stringRedisTemplate.opsForValue().get(ALIPAY_TRADE_SUCCESS_RECORD + params.get("out_trade_no"));
            if (null == outTradeNo) {
                // 支付宝验签
                if (AlipaySignature.rsaCheckV1(params, aliPayConfig.getPublicKey(),
                        aliPayConfig.getCharset(), aliPayConfig.getSignType())) {
                    //验证成功
                    log.info("支付成功:{}", params);
                    // 验签通过，将订单信息存入数据库
                    AlipayInfo alipayInfo = new AlipayInfo();
                    alipayInfo.setSubject(params.get("subject"));
                    alipayInfo.setTradeStatus(params.get("trade_status"));
                    alipayInfo.setTradeNo(params.get("trade_no"));
                    alipayInfo.setOrderNumber(params.get("out_trade_no"));
                    alipayInfo.setTotalAmount(Double.valueOf(params.get("total_amount")));
                    alipayInfo.setBuyerId(params.get("buyer_id"));
                    alipayInfo.setGmtPayment(DateUtil.parse(params.get("gmt_payment")));
                    alipayInfo.setBuyerPayAmount(Double.valueOf(params.get("buyer_pay_amount")));
                    alipayInfoService.save(alipayInfo);

                    // 记录处理成功的订单到Redis中，实现订单幂等性
                    stringRedisTemplate.opsForValue().set(ALIPAY_TRADE_SUCCESS_RECORD + alipayInfo.getOrderNumber(), EXIST_KEY_VALUE, 30, TimeUnit.MINUTES);

                    // 修改数据库，完成整个订单功能
                    orderPaySuccessMqUtils.sendOrderPaySuccess(params.get("out_trade_no"));
                }
            }
        }
    }
}
