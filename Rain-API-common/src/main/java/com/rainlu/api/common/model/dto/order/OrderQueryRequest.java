package com.rainlu.api.common.model.dto.order;


import com.rainlu.api.common.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
public class OrderQueryRequest extends PageRequest implements Serializable {
    // 待支付 & 已支付 & 已过期/失效
    private String type;
}
