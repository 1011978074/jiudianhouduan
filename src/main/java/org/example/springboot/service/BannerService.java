package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.example.springboot.entity.Banner;
import org.example.springboot.mapper.BannerMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 轮播图服务类
 */
@Service
public class BannerService {
    
    @Resource
    private BannerMapper bannerMapper;
    
    /**
     * 获取轮播图列表（分页）
     * @param title 标题（可选）
     * @param status 状态（可选）
     * @param currentPage 当前页
     * @param size 每页大小
     * @return 分页结果
     */
    public Page<Banner> getBannersByPage(String title, Integer status, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Banner> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        if (StringUtils.isNotBlank(title)) {
            queryWrapper.like(Banner::getTitle, title);
        }
        if (status != null) {
            queryWrapper.eq(Banner::getStatus, status);
        }
        
        // 按排序字段升序
        queryWrapper.orderByAsc(Banner::getSortOrder);
        
        return bannerMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
    }
    
    /**
     * 获取启用的轮播图列表
     * @return 轮播图列表
     */
    public List<Banner> getEnabledBanners() {
        LambdaQueryWrapper<Banner> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Banner::getStatus, 1)  // 1表示启用
                   .orderByAsc(Banner::getSortOrder);
        
        return bannerMapper.selectList(queryWrapper);
    }
    
    /**
     * 保存轮播图
     * @param banner 轮播图对象
     * @return 是否成功
     */
    public boolean saveBanner(Banner banner) {
        if (banner.getId() == null) {
            // 新增
            return bannerMapper.insert(banner) > 0;
        } else {
            // 更新
            return bannerMapper.updateById(banner) > 0;
        }
    }
    
    /**
     * 删除轮播图
     * @param id 轮播图ID
     * @return 是否成功
     */
    public boolean deleteBanner(Long id) {
        return bannerMapper.deleteById(id) > 0;
    }
    
    /**
     * 更新轮播图状态
     * @param id 轮播图ID
     * @param status 状态
     * @return 是否成功
     */
    public boolean updateStatus(Long id, Integer status) {
        Banner banner = new Banner();
        banner.setId(id);
        banner.setStatus(status);
        return bannerMapper.updateById(banner) > 0;
    }
    
    /**
     * 获取轮播图详情
     * @param id 轮播图ID
     * @return 轮播图对象
     */
    public Banner getBannerById(Long id) {
        return bannerMapper.selectById(id);
    }
} 