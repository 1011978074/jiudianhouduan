package org.example.springboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 房间类型图片实体类
 */
@Data
@TableName("room_type_image")
@Schema(description = "房间类型图片实体类")
public class RoomTypeImage {
    @TableId(type = IdType.AUTO)
    @Schema(description = "图片ID")
    private Long id;
    
    @Schema(description = "房间类型ID")
    @NotNull(message = "房间类型ID不能为空")
    private Long roomTypeId;
    
    @Schema(description = "图片URL")
    @NotBlank(message = "图片URL不能为空")
    private String imageUrl;
    
    @Schema(description = "排序顺序")
    private Integer sortOrder;
    
    @Schema(description = "是否主图(0-否,1-是)")
    private Integer isMain;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
} 