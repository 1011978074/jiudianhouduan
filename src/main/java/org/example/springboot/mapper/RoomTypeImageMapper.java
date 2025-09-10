package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.RoomTypeImage;

/**
 * 房间类型图片数据访问接口
 */
@Mapper
public interface RoomTypeImageMapper extends BaseMapper<RoomTypeImage> {
} 