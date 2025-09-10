package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.User;
import org.example.springboot.service.OrderService;
import org.example.springboot.util.JwtTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name="订单管理接口")
@RestController
@RequestMapping("/order")
public class OrderController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);
    
    @Resource
    private OrderService orderService;
    
    @Operation(summary = "分页查询订单")
    @GetMapping("/page")
    public Result<?> getOrdersByPage(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        
        // 非管理员只能查询自己的订单
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            userId = currentUser.getId();
        }
        
        Page<Order> page = orderService.getOrdersByPage(orderNo, userId, status, currentPage, size);
        return Result.success(page);
    }
    
    @Operation(summary = "根据id获取订单")
    @GetMapping("/{id}")
    public Result<?> getOrderById(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        
        // 非管理员只能查询自己的订单
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode()) && 
            !order.getUserId().equals(currentUser.getId())) {
            return Result.error("无权查看此订单");
        }
        
        return Result.success(order);
    }
    
    @Operation(summary = "根据订单号获取订单")
    @GetMapping("/no/{orderNo}")
    public Result<?> getOrderByOrderNo(@PathVariable String orderNo) {
        Order order = orderService.getOrderByOrderNo(orderNo);
        
        // 非管理员只能查询自己的订单
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode()) && 
            !order.getUserId().equals(currentUser.getId())) {
            return Result.error("无权查看此订单");
        }
        
        return Result.success(order);
    }
    
    @Operation(summary = "查询预订的订单")
    @GetMapping("/reservation/{reservationId}")
    public Result<?> getOrderByReservationId(@PathVariable Long reservationId) {
        Order order = orderService.getOrderByReservationId(reservationId);
        if (order == null) {
            return Result.success(null);
        }
        
        // 非管理员只能查询自己的订单
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode()) && 
            !order.getUserId().equals(currentUser.getId())) {
            return Result.error("无权查看此订单");
        }
        
        return Result.success(order);
    }
    
    @Operation(summary = "创建订单")
    @PostMapping("/create/{reservationId}")
    public Result<?> createOrder(@PathVariable Long reservationId) {
        Order order = orderService.createOrder(reservationId);
        return Result.success(order);
    }
    
    @Operation(summary = "支付订单")
    @PostMapping("/pay/{id}")
    public Result<?> payOrder(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String payMethod = params.get("payMethod");
        String payNo = params.get("payNo");
        
        if (payMethod == null || payMethod.isEmpty()) {
            return Result.error("支付方式不能为空");
        }
        
        orderService.payOrder(id, payMethod, payNo);
        return Result.success();
    }
    
    @Operation(summary = "取消订单")
    @PostMapping("/cancel/{id}")
    public Result<?> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return Result.success();
    }
    
    @Operation(summary = "退款")
    @PostMapping("/refund/{id}")
    public Result<?> refundOrder(@PathVariable Long id) {
        orderService.refundOrder(id);
        return Result.success();
    }
    
    @Operation(summary = "更新订单状态")
    @PutMapping("/status/{id}")
    public Result<?> updateOrderStatus(@PathVariable Long id, @RequestParam Integer status) {
        orderService.updateOrderStatus(id, status);
        return Result.success();
    }
    
    @Operation(summary = "获取当前用户的订单列表")
    @GetMapping("/user")
    public Result<?> getUserOrders() {
        User currentUser = JwtTokenUtils.getCurrentUser();
        List<Order> orders = orderService.getUserOrders(currentUser.getId());
        return Result.success(orders);
    }
    
    @Operation(summary = "统计订单总金额")
    @GetMapping("/stats/amount")
    public Result<?> calculateTotalAmount(@RequestParam(required = false) Integer status) {
        // 只有管理员可以查看统计数据
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权查看统计数据");
        }
        
        BigDecimal totalAmount = orderService.calculateTotalAmount(status);
        return Result.success(totalAmount);
    }

    @Operation(summary = "获取订单统计")
    @GetMapping("/stats")
    public Result<?> getOrderStats() {
        // 只有管理员可以查看统计数据
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权查看统计数据");
        }

        Map<String, Object> statistics = orderService.getOrderStatistics();
        return Result.success(statistics);
    }
}