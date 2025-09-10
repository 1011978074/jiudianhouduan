package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价实体类
 */
@Data
@TableName("review")
@Schema(description = "评价实体类")
public class Review {
    
    @TableId(type = IdType.AUTO)
    @Schema(description = "评价ID")
    private Long id;
    
    @Schema(description = "用户ID")
    private Long userId;
    
    @Schema(description = "房型ID")
    private Long roomTypeId;
    
    @Schema(description = "预订ID")
    private Long reservationId;
    
    @Schema(description = "评价内容")
    private String content;
    
    @Schema(description = "评分(1-5)")
    private Integer score;
    
    @Schema(description = "评价图片URL，多个以逗号分隔")
    private String images;
    
    @Schema(description = "回复内容")
    private String reply;
    
    @Schema(description = "回复时间")
    private LocalDateTime replyTime;
    
    @Schema(description = "状态(0-隐藏，1-显示)")
    private Integer status;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    @Schema(description = "用户信息")
    private User user;
    
    @TableField(exist = false)
    @Schema(description = "房型信息")
    private RoomType roomType;

    @TableField(exist = false)
    @Schema(description = "房间信息")
    private Room room;
    
    @TableField(exist = false)
    @Schema(description = "预订信息")
    private Reservation reservation;
    
    @TableField(exist = false)
    @Schema(description = "状态名称")
    private String statusName;
    
    public String getStatusName() {
        if (status == null) return "";
        switch (status) {
            case 0: return "隐藏";
            case 1: return "显示";
            default: return "未知";
        }
    }
} 