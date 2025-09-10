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
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 房间实体类
 */
@Data
@TableName("room")
@Schema(description = "房间实体类")
public class Room {
    @TableId(type = IdType.AUTO)
    @Schema(description = "房间ID")
    private Long id;
    
    @Schema(description = "房间号")
    @NotBlank(message = "房间号不能为空")
    @Size(max = 20, message = "房间号长度不能超过20个字符")
    private String roomNumber;
    
    @Schema(description = "房间类型ID")
    @NotNull(message = "房间类型不能为空")
    private Long roomTypeId;
    
    @Schema(description = "房间状态(0:维护中,1:可用)")
    private Integer status;
    
    @Schema(description = "楼层")
    @NotNull(message = "楼层不能为空")
    @Min(value = 1, message = "楼层不能小于1")
    private Integer floor;
    
    @Schema(description = "房间描述")
    private String description;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    @Schema(description = "房间类型，非数据库字段")
    private RoomType roomType;
    
    @TableField(exist = false)
    @Schema(description = "是否已被预订，非数据库字段")
    private Boolean isReserved;
    
    @TableField(exist = false)
    @Schema(description = "房间价格(来自房型)，非数据库字段")
    private java.math.BigDecimal price;
    
    /**
     * 获取房间状态名称
     */

    @Schema(description = "房间状态名称，非数据库字段")
    public String getStatusName() {
        if (status == null) return "";
        switch (status) {
            case 0:
                return "维护中";
            case 1:
                return "可用";
            default:
                return "未知状态";
        }
    }
} 