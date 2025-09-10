package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.Reservation;

/**
 * 预订数据访问接口
 */
@Mapper
public interface ReservationMapper extends BaseMapper<Reservation> {
} 