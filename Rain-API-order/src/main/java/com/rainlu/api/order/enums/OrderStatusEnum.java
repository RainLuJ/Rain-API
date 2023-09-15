package com.rainlu.api.order.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单状态枚举类
 */
public enum OrderStatusEnum {

    ORDER_NOT_PAY_STATUS("未支付", 0),
    ORDER_PAY_SUCCESS_STATUS("已支付", 1),
    ORDER_PAY_TIMEOUT_STATUS("支付超时", 2);

    private final String text;

    private final Integer value;

    OrderStatusEnum(String text, Integer value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    public Integer getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
