package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Reservation;
import org.example.springboot.entity.Room;
import org.example.springboot.entity.User;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.service.ReservationService;
import org.example.springboot.service.RoomService;
import org.example.springboot.util.JwtTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name="预订管理接口")
@RestController
@RequestMapping("/reservation")
public class ReservationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReservationController.class);
    
    @Resource
    private ReservationService reservationService;
    
    @Resource
    private RoomService roomService;
    
    @Operation(summary = "分页查询预订")
    @GetMapping("/page")
    public Result<?> getReservationsByPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String roomNumber,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer payStatus,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        
        // 非管理员只能查询自己的预订
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            throw new RuntimeException("用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            userId = currentUser.getId();
        }
        
        // 如果提供了房间号，根据房间号查询roomId
        if (roomNumber != null && !roomNumber.isEmpty() && roomId == null) {
            Room room = roomService.getRoomByNumber(roomNumber);
            if (room != null) {
                roomId = room.getId();
            }else{
                return Result.success(new Page<>(currentPage, size));
            }
        }
        
        Page<Reservation> page = reservationService.getReservationsByPage(
            userId, roomId, status, payStatus, startDate, endDate, currentPage, size);
        return Result.success(page);
    }
    
    @Operation(summary = "根据id获取预订")
    @GetMapping("/{id}")
    public Result<?> getReservationById(@PathVariable Long id) {
        Reservation reservation = reservationService.getReservationById(id);
        
        // 权限检查
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !reservation.getUserId().equals(currentUser.getId())) {
            return Result.error("无权查看此预订");
        }
        
        return Result.success(reservation);
    }
    
    @Operation(summary = "创建预订")
    @PostMapping("/create")
    public Result<?> createReservation(@RequestBody Map<String, Object> params) {
        try {
            // 从参数中提取数据
            Long roomTypeId = params.containsKey("roomTypeId") ? Long.valueOf(params.get("roomTypeId").toString()) : null;
            LocalDate startDate = params.containsKey("startDate") ? LocalDate.parse(params.get("startDate").toString()) : null;
            LocalDate endDate = params.containsKey("endDate") ? LocalDate.parse(params.get("endDate").toString()) : null;
            Integer guestCount = params.containsKey("guestCount") ? Integer.valueOf(params.get("guestCount").toString()) : null;
            String guestName = params.containsKey("guestName") ? params.get("guestName").toString() : null;
            String guestPhone = params.containsKey("guestPhone") ? params.get("guestPhone").toString() : null;
            String notes = params.containsKey("notes") ? params.get("notes").toString() : null;

            // 创建预订对象，让Service层处理房间分配和可用性检查
            Reservation reservation = new Reservation();
            reservation.setStartDate(startDate);
            reservation.setEndDate(endDate);
            reservation.setGuestCount(guestCount);
            reservation.setGuestName(guestName);
            reservation.setGuestPhone(guestPhone);
            reservation.setNotes(notes);

            // 使用Service层的智能房间分配功能
            Room allocatedRoom = roomService.allocateRoom(roomTypeId, startDate, endDate, guestCount);
            reservation.setRoomId(allocatedRoom.getId());

            // 创建预订（Service层会进行最终的可用性检查）
            Reservation createdReservation = reservationService.createReservation(reservation);

            return Result.success(createdReservation);

        } catch (ServiceException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("创建预订失败：" + e.getMessage());
        }
    }
    
    @Operation(summary = "更新预订")
    @PutMapping("/{id}")
    public Result<?> updateReservation(@PathVariable Long id, @RequestBody Reservation reservation) {
        // 权限检查
        Reservation existingReservation = reservationService.getReservationById(id);
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !existingReservation.getUserId().equals(currentUser.getId())) {
            return Result.error("无权修改此预订");
        }
        
        reservationService.updateReservation(id, reservation);
        return Result.success("更新成功");
    }
    
    @Operation(summary = "取消预订")
    @PutMapping("/cancel/{id}")
    public Result<?> cancelReservation(@PathVariable Long id) {
        // 权限检查
        Reservation existingReservation = reservationService.getReservationById(id);
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !existingReservation.getUserId().equals(currentUser.getId())) {
            return Result.error("无权取消此预订");
        }
        
        reservationService.cancelReservation(id);
        return Result.success("取消成功");
    }
    
    @Operation(summary = "更新预订状态")
    @PutMapping("/status/{id}")
    public Result<?> updateReservationStatus(@PathVariable Long id, @RequestParam Integer status) {
        // 权限检查
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权更新预订状态");
        }
        
        reservationService.updateReservationStatus(id, status);
        return Result.success("状态更新成功");
    }
    
    @Operation(summary = "更新支付状态")
    @PutMapping("/payStatus/{id}")
    public Result<?> updatePayStatus(@PathVariable Long id, @RequestParam Integer payStatus) {
        // 权限检查
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        reservationService.updatePayStatus(id, payStatus);
        return Result.success("支付状态更新成功");
    }
    
    @Operation(summary = "获取当前用户的预订记录")
    @GetMapping("/user")
    public Result<?> getUserReservations() {
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.success(null);
        }
        List<Reservation> reservations = reservationService.getUserReservations(currentUser.getId());
        return Result.success(reservations);
    }

    @Operation(summary = "获取当前用户已完成的预订记录")
    @GetMapping("/user/completed")
    public Result<?> getUserCompletedReservations() {
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.success(null);
        }
        List<Reservation> reservations = reservationService.getUserReservationsByStatus(currentUser.getId(), 3);
        return Result.success(reservations);
    }

    @Operation(summary = "获取预订统计")
    @GetMapping("/stats")
    public Result<?> getReservationStats() {
        // 只有管理员可以查看统计数据
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权查看统计数据");
        }

        Map<String, Object> statistics = reservationService.getReservationStatistics();
        return Result.success(statistics);
    }
}