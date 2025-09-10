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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间类型实体类
 */
@Data
@TableName("room_type")
@Schema(description = "房间类型实体类")
public class RoomType {
    @TableId(type = IdType.AUTO)
    @Schema(description = "房型ID")
    private Long id;
    
    @Schema(description = "房型名称")
    @NotBlank(message = "房型名称不能为空")
    @Size(max = 50, message = "房型名称长度不能超过50个字符")
    private String name;
    
    @Schema(description = "房型价格")
    @NotNull(message = "房型价格不能为空")
    @Min(value = 0, message = "房型价格不能小于0")
    private BigDecimal price;
    
    @Schema(description = "最大入住人数")
    @NotNull(message = "最大入住人数不能为空")
    @Min(value = 1, message = "最大入住人数不能小于1")
    private Integer maxPeople;
    
    @Schema(description = "床型描述")
    @NotBlank(message = "床型描述不能为空")
    @Size(max = 50, message = "床型描述长度不能超过50个字符")
    private String bedType;
    
    @Schema(description = "设施描述")
    private String facilities;
    
    @Schema(description = "房型详细描述")
    private String description;
    
    @Schema(description = "房型主图片URL")
    private String image;
    
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    
    @TableField(exist = false)
    @Schema(description = "该房型的房间数量，非数据库字段")
    private Integer roomCount;
    
    @TableField(exist = false)
    @Schema(description = "该房型的可用房间数量，非数据库字段")
    private Integer availableRoomCount;
    
    @TableField(exist = false)
    @Schema(description = "房型图片列表，非数据库字段")
    private List<RoomTypeImage> images;
} 