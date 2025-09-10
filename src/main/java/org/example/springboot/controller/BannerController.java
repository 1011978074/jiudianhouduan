package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Banner;
import org.example.springboot.service.BannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 轮播图控制器
 */
@Tag(name = "轮播图管理接口")
@RestController
@RequestMapping("/banner")
public class BannerController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BannerController.class);
    
    @Resource
    private BannerService bannerService;
    
    @Operation(summary = "分页查询轮播图")
    @GetMapping("/page")
    public Result<?> getBannersByPage(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        LOGGER.info("分页查询轮播图: title={}, status={}, currentPage={}, size={}", 
                title, status, currentPage, size);
        return Result.success(bannerService.getBannersByPage(title, status, currentPage, size));
    }
    
    @Operation(summary = "获取启用的轮播图")
    @GetMapping("/enabled")
    public Result<?> getEnabledBanners() {
        LOGGER.info("获取启用的轮播图");
        List<Banner> bannerList = bannerService.getEnabledBanners();
        return Result.success(bannerList);
    }
    
    @Operation(summary = "保存轮播图")
    @PostMapping
    public Result<?> saveBanner(@RequestBody Banner banner) {
        LOGGER.info("保存轮播图: {}", banner);
        if (bannerService.saveBanner(banner)) {
            return Result.success();
        }
        return Result.error("保存失败");
    }
    
    @Operation(summary = "获取轮播图详情")
    @GetMapping("/{id}")
    public Result<?> getBannerById(@PathVariable Long id) {
        LOGGER.info("获取轮播图详情: id={}", id);
        Banner banner = bannerService.getBannerById(id);
        if (banner == null) {
            return Result.error("轮播图不存在");
        }
        return Result.success(banner);
    }
    
    @Operation(summary = "更新轮播图")
    @PutMapping("/{id}")
    public Result<?> updateBanner(@PathVariable Long id, @RequestBody Banner banner) {
        LOGGER.info("更新轮播图: id={}, banner={}", id, banner);
        banner.setId(id);
        if (bannerService.saveBanner(banner)) {
            return Result.success();
        }
        return Result.error("更新失败");
    }
    
    @Operation(summary = "删除轮播图")
    @DeleteMapping("/{id}")
    public Result<?> deleteBanner(@PathVariable Long id) {
        LOGGER.info("删除轮播图: id={}", id);
        if (bannerService.deleteBanner(id)) {
            return Result.success();
        }
        return Result.error("删除失败");
    }
    
    @Operation(summary = "更新轮播图状态")
    @PutMapping("/{id}/status/{status}")
    public Result<?> updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        LOGGER.info("更新轮播图状态: id={}, status={}", id, status);
        if (bannerService.updateStatus(id, status)) {
            return Result.success();
        }
        return Result.error("状态更新失败");
    }
} 