package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类
 */
@Data
@TableName("orders")
@Schema(description = "订单实体类")
public class Order {
    @TableId(type = IdType.AUTO)
    @Schema(description = "订单ID")
    private Long id;
    
    @Schema(description = "订单编号")
    private String orderNo;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "预订ID")
    private Long reservationId;
    
    @Schema(description = "订单金额")
    private BigDecimal amount;
    
    @Schema(description = "订单状态(0-未支付，1-已支付，2-已取消，3-已退款)")
    private Integer status;
    
    @Schema(description = "支付时间")
    private LocalDateTime payTime;
    
    @Schema(description = "支付方式")
    private String payMethod;
    
    @Schema(description = "支付流水号")
    private String payNo;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    @Schema(description = "用户信息")
    private User user;
    
    @TableField(exist = false)
    @Schema(description = "预订信息")
    private Reservation reservation;
    
    @TableField(exist = false)
    @Schema(description = "状态名称")
    private String statusName;
    
    /**
     * 获取订单状态名称
     */
    public String getStatusName() {
        if (status == null) return "";
        switch (status) {
            case 0: return "未支付";
            case 1: return "已支付";
            case 2: return "已取消";
            case 3: return "已退款";
            default: return "未知";
        }
    }
} 