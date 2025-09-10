package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Future;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预订实体类
 */
@Data
@TableName("reservation")
@Schema(description = "预订实体类")
public class Reservation {
    @TableId(type = IdType.AUTO)
    @Schema(description = "预订ID")
    private Long id;
    
    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    @Schema(description = "房间ID")
    @NotNull(message = "房间ID不能为空")
    private Long roomId;
    
    @Schema(description = "入住日期")
    @NotNull(message = "入住日期不能为空")
    private LocalDate startDate;
    
    @Schema(description = "退房日期")
    @NotNull(message = "退房日期不能为空")
    private LocalDate endDate;
    
    @Schema(description = "预订状态(0:待确认,1:已确认,2:已取消,3:已完成)")
    private Integer status;
    
    @Schema(description = "预订总价")
    private BigDecimal price;
    
    @Schema(description = "支付状态(0:未支付,1:已支付,2:已退款)")
    private Integer payStatus;
    
    @Schema(description = "入住人数")
    @NotNull(message = "入住人数不能为空")
    @Min(value = 1, message = "入住人数不能小于1")
    private Integer guestCount;
    
    @Schema(description = "入住人姓名")
    @NotBlank(message = "入住人姓名不能为空")
    private String guestName;
    
    @Schema(description = "入住人电话")
    @NotBlank(message = "入住人电话不能为空")
    private String guestPhone;
    
    @Schema(description = "备注信息")
    private String notes;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    @Schema(description = "用户信息，非数据库字段")
    private User user;
    
    @TableField(exist = false)
    @Schema(description = "房间信息，非数据库字段")
    private Room room;
    
    @TableField(exist = false)
    @Schema(description = "订单信息，非数据库字段")
    private Order order;
    
    /**
     * 获取预订状态名称
     */
    public String getStatusName() {
        if (status == null) return "";
        switch (status) {
            case 0:
                return "待确认";
            case 1:
                return "已确认";
            case 2:
                return "已取消";
            case 3:
                return "已完成";
            default:
                return "未知状态";
        }
    }
    
    public String getPayStatusName() {
        if (payStatus == null) return "";
        switch (payStatus) {
            case 0:
                return "未支付";
            case 1:
                return "已支付";
            case 2:
                return "已退款";
            default:
                return "未知状态";
        }
    }

    public String getFullStatusName() {
        if (status == null || payStatus == null) return "";
        
        // 待确认 + 已支付 = 待管理员确认
        if (status == 0 && payStatus == 1) {
            return "已支付待确认";
        }
        
        return getStatusName();
    }
} 