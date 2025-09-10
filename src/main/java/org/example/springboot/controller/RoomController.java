package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Room;
import org.example.springboot.entity.User;
import org.example.springboot.service.RoomService;

import org.example.springboot.util.JwtTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name="房间管理接口")
@RestController
@RequestMapping("/room")
public class RoomController {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomController.class);
    
    @Resource
    private RoomService roomService;
    
    @Operation(summary = "分页查询房间")
    @GetMapping("/page")
    public Result<?> getRoomsByPage(
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) Long roomTypeId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer floor,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<Room> page = roomService.getRoomsByPage(roomNumber, roomTypeId, status, floor, currentPage, size);
        return Result.success(page);
    }
    
    @Operation(summary = "查询可预订的房间（仅供展示，不用于业务逻辑）")
    @GetMapping("/available")
    public Result<?> getAvailableRooms(
            @RequestParam(required = false) Long roomTypeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        // 注意：此接口仅用于前端展示可用房间信息，不应用于预订业务逻辑
        // 实际的房间可用性检查和分配在预订创建时由后端统一处理
        List<Room> rooms = roomService.getAvailableRooms(roomTypeId, startDate, endDate);
        return Result.success(rooms);
    }

    @Operation(summary = "检查房型可用性（仅供展示）")
    @GetMapping("/availability/{roomTypeId}")
    public Result<?> checkRoomTypeAvailability(
            @PathVariable Long roomTypeId,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        try {
            List<Room> availableRooms = roomService.getAvailableRooms(roomTypeId, startDate, endDate);

            Map<String, Object> result = new HashMap<>();
            result.put("available", !availableRooms.isEmpty());
            result.put("availableCount", availableRooms.size());
            result.put("message", availableRooms.isEmpty() ?
                "该时间段内所选房型暂无可用房间" :
                "该时间段内有 " + availableRooms.size() + " 间可用房间");

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("检查房型可用性失败：" + e.getMessage());
        }
    }
    
    @Operation(summary = "根据id获取房间")
    @GetMapping("/{id}")
    public Result<?> getRoomById(@PathVariable Long id) {
        Room room = roomService.getRoomById(id);
        return Result.success(room);
    }
    
    @Operation(summary = "创建房间")
    @PostMapping("/add")
    public Result<?> createRoom(@RequestBody Room room) {
        roomService.createRoom(room);
        return Result.success("创建成功");
    }
    
    @Operation(summary = "更新房间")
    @PutMapping("/{id}")
    public Result<?> updateRoom(@PathVariable Long id, @RequestBody Room room) {
        roomService.updateRoom(id, room);
        return Result.success("更新成功");
    }
    
    @Operation(summary = "删除房间")
    @DeleteMapping("/{id}")
    public Result<?> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return Result.success("删除成功");
    }
    
    @Operation(summary = "更新房间状态")
    @PutMapping("/status/{id}")
    public Result<?> updateRoomStatus(@PathVariable Long id, @RequestParam Integer status) {
        roomService.updateRoomStatus(id, status);
        return Result.success("状态更新成功");
    }
    
    @Operation(summary = "批量创建房间")
    @PostMapping("/batchAdd")
    public Result<?> batchCreateRooms(@RequestBody Map<String, Object> params) {
        Long roomTypeId = Long.valueOf(params.get("roomTypeId").toString());
        Integer floor = Integer.valueOf(params.get("floor").toString());
        String roomNumberPrefix = params.get("roomNumberPrefix").toString();
        Integer startNumber = Integer.valueOf(params.get("startNumber").toString());
        Integer count = Integer.valueOf(params.get("count").toString());
        
        roomService.batchCreateRooms(roomTypeId, floor, roomNumberPrefix, startNumber, count);
        return Result.success("批量创建成功");
    }

    @Operation(summary = "获取房间使用统计")
    @GetMapping("/stats")
    public Result<?> getRoomUsageStatistics() {
        // 只有管理员可以查看统计数据
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权查看统计数据");
        }

        Map<String, Object> statistics = roomService.getRoomUsageStatistics();
        return Result.success(statistics);
    }
}