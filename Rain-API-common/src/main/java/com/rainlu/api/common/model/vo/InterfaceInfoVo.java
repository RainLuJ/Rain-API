package com.rainlu.api.common.model.vo;


import com.rainlu.api.common.model.entity.InterfaceInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class InterfaceInfoVo extends InterfaceInfo {

    /**
     * 统计每个接口被用户调用的总数
     */
    private Integer totalNum;


    /**
     * 付费接口的计费规则（元/条）
     */
    private Double charging;

    /**
     * 接口计费信息的Id
     */
    private Long chargingId;

    /**
     * 接口剩余的可调用次数
     *      - 在用户想查看自己所拥有的接口信息时才会被查询出来
     *      - 在主页显示所有的被管理的接口信息时，不会被查询出来
     */
    private String availablePieces;

}
