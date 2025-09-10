package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.RoomType;

/**
 * 房间类型数据访问接口
 */
@Mapper
public interface RoomTypeMapper extends BaseMapper<RoomType> {
} 