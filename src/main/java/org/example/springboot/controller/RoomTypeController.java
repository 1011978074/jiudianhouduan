package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.RoomType;
import org.example.springboot.service.RoomTypeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name="房间类型管理接口")
@RestController
@RequestMapping("/roomType")
public class RoomTypeController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTypeController.class);
    
    @Resource
    private RoomTypeService roomTypeService;
    
    @Operation(summary = "分页查询房间类型")
    @GetMapping("/page")
    public Result<?> getRoomTypesByPage(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice,
            @RequestParam(required = false) Integer maxPeople,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<RoomType> page = roomTypeService.getRoomTypesByPage(name, minPrice, maxPrice, maxPeople, currentPage, size);
        return Result.success(page);
    }
    
    @Operation(summary = "获取所有房间类型")
    @GetMapping("/all")
    public Result<?> getAllRoomTypes() {
        List<RoomType> roomTypes = roomTypeService.getAllRoomTypes();
        return Result.success(roomTypes);
    }
    
    @Operation(summary = "根据id获取房间类型")
    @GetMapping("/{id}")
    public Result<?> getRoomTypeById(@PathVariable Long id) {
        RoomType roomType = roomTypeService.getRoomTypeById(id);
        return Result.success(roomType);
    }
    
    @Operation(summary = "创建房间类型")
    @PostMapping("/add")
    public Result<?> createRoomType(@RequestBody RoomType roomType) {
        roomTypeService.createRoomType(roomType);
        return Result.success("创建成功");
    }
    
    @Operation(summary = "更新房间类型")
    @PutMapping("/{id}")
    public Result<?> updateRoomType(@PathVariable Long id, @RequestBody RoomType roomType) {
        roomTypeService.updateRoomType(id, roomType);
        return Result.success("更新成功");
    }
    
    @Operation(summary = "删除房间类型")
    @DeleteMapping("/{id}")
    public Result<?> deleteRoomType(@PathVariable Long id) {
        roomTypeService.deleteRoomType(id);
        return Result.success("删除成功");
    }
} 