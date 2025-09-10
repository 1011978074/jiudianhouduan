package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.RoomTypeImage;
import org.example.springboot.service.RoomTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name="房间类型图片管理接口")
@RestController
@RequestMapping("/roomTypeImage")
public class RoomTypeImageController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTypeImageController.class);
    
    @Resource
    private RoomTypeService roomTypeService;
    
    @Operation(summary = "添加房型图片")
    @PostMapping("/add")
    public Result<?> addRoomTypeImage(@RequestBody RoomTypeImage image) {
        roomTypeService.addRoomTypeImage(image.getRoomTypeId(), image);
        return Result.success("添加成功");
    }
    
    @Operation(summary = "删除房型图片")
    @DeleteMapping("/{id}")
    public Result<?> deleteRoomTypeImage(@PathVariable Long id) {
        roomTypeService.deleteRoomTypeImage(id);
        return Result.success("删除成功");
    }
    
    @Operation(summary = "设置主图")
    @PutMapping("/setMain/{id}")
    public Result<?> setMainImage(@PathVariable Long id) {
        roomTypeService.setMainImage(id);
        return Result.success("设置成功");
    }
} 