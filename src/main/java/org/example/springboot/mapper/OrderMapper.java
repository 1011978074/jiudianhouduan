package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.Order;

/**
 * 订单数据访问接口
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
} 