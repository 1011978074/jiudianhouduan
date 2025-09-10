package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.User;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.service.*;
import org.example.springboot.util.JwtTokenUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "后台管理首页接口")
@RestController
@RequestMapping("/dashboard")
public class DashboardController {
    
    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RoomService roomService;
    
    @Resource
    private ReservationService reservationService;
    
    @Resource
    private OrderService orderService;
    
    @Resource
    private ReviewService reviewService;
    
    @Operation(summary = "获取后台管理首页统计数据")
    @GetMapping("/stats")
    public Result<?> getDashboardStats() {
        // 只有管理员可以查看统计数据
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权查看统计数据");
        }
        
        Map<String, Object> dashboardStats = new HashMap<>();
        
        try {
            // 用户统计
            Map<String, Integer> userStats = getUserStats();
            dashboardStats.put("userStats", userStats);
            
            // 房间统计
            Map<String, Object> roomStats = roomService.getRoomUsageStatistics();
            dashboardStats.put("roomStats", roomStats);
            
            // 预订统计
            Map<String, Object> reservationStats = reservationService.getReservationStatistics();
            dashboardStats.put("reservationStats", reservationStats);
            
            // 订单统计
            Map<String, Object> orderStats = orderService.getOrderStatistics();
            dashboardStats.put("orderStats", orderStats);
            
            // 评价统计
            Map<String, Object> reviewStats = reviewService.getReviewStatistics();
            dashboardStats.put("reviewStats", reviewStats);
            
            return Result.success(dashboardStats);
        } catch (Exception e) {
            return Result.error("获取统计数据失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取用户统计数据
     */
    private Map<String, Integer> getUserStats() {
        Map<String, Integer> stats = new HashMap<>();

        // 获取总用户数
        int totalUsers = Math.toIntExact(userMapper.selectCount(null));

        // 获取普通用户数
        int normalUsers = Math.toIntExact(userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRoleCode, "USER")
        ));

        // 获取管理员数
        int adminUsers = Math.toIntExact(userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRoleCode, "ADMIN")
        ));

        stats.put("totalUsers", totalUsers);
        stats.put("normalUsers", normalUsers);
        stats.put("adminUsers", adminUsers);

        return stats;
    }
}
