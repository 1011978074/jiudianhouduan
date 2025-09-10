package org.example.springboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.springboot.entity.Banner;

/**
 * 轮播图Mapper接口
 */
@Mapper
public interface BannerMapper extends BaseMapper<Banner> {
    // 继承BaseMapper，无需编写额外方法
} 