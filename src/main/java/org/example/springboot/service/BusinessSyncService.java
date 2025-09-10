package org.example.springboot.service;

import jakarta.annotation.Resource;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Reservation;
import org.example.springboot.entity.Room;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ReservationMapper;
import org.example.springboot.mapper.RoomMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 业务流程同步服务 - 处理异步操作补偿和状态同步
 */
@Service
public class BusinessSyncService {
    
    @Resource
    private OrderMapper orderMapper;
    
    @Resource
    private ReservationMapper reservationMapper;
    
    @Resource
    private RoomMapper roomMapper;
    
    /**
     * 同步订单和预订状态
     */
//    @Transactional
    public void syncOrderAndReservationStatus(Long orderId, Long reservationId) {
        try {
            Order order = orderMapper.selectById(orderId);
            Reservation reservation = reservationMapper.selectById(reservationId);
            
            if (order == null || reservation == null) {
                throw new ServiceException("订单或预订不存在");
            }
            
            // 检查状态一致性
            if (order.getStatus() == 1 && reservation.getPayStatus() != 1) {
                // 订单已支付但预订未标记为已支付，同步状态
                reservation.setPayStatus(1);
                reservation.setUpdateTime(LocalDateTime.now());
                reservationMapper.updateById(reservation);
            } else if (order.getStatus() == 3 && reservation.getPayStatus() != 2) {
                // 订单已退款但预订未标记为已退款，同步状态
                reservation.setPayStatus(2);
                reservation.setStatus(2); // 同时取消预订
                reservation.setUpdateTime(LocalDateTime.now());
                reservationMapper.updateById(reservation);
            }
            
        } catch (Exception e) {
            // 记录同步失败的情况，可以后续重试
            System.err.println("状态同步失败: " + e.getMessage());
            throw new ServiceException("状态同步失败: " + e.getMessage());
        }
    }
    
    /**
     * 支付成功后的补偿处理
     */
    @Transactional
    public void handlePaymentSuccessCompensation(Long orderId) {
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new ServiceException("订单不存在");
            }
            
            // 检查订单状态
            if (order.getStatus() != 1) {
                return; // 订单未支付，无需处理
            }
            
            // 更新关联预订的支付状态
            Reservation reservation = reservationMapper.selectById(order.getReservationId());
            if (reservation != null && reservation.getPayStatus() != 1) {
                reservation.setPayStatus(1);
                reservation.setUpdateTime(LocalDateTime.now());
                
                // 如果预订状态还是待确认，可以考虑自动确认
                if (reservation.getStatus() == 0) {
                    reservation.setStatus(1); // 自动确认
                }
                
                reservationMapper.updateById(reservation);
            }
            
        } catch (Exception e) {
            System.err.println("支付补偿处理失败: " + e.getMessage());
            // 可以记录到补偿任务表，后续重试
        }
    }
    
    /**
     * 预订取消后的补偿处理
     */
//    @Transactional
    public void handleReservationCancelCompensation(Long reservationId) {
        try {
            Reservation reservation = reservationMapper.selectById(reservationId);
            if (reservation == null || reservation.getStatus() != 2) {
                return; // 预订不存在或未取消，无需处理
            }
            
            // 释放房间资源（如果有房间锁定机制）
            Room room = roomMapper.selectById(reservation.getRoomId());
            if (room != null) {
                // 这里实现房间资源释放逻辑
                room.setStatus(1);
                room.setUpdateTime(LocalDateTime.now());
                roomMapper.updateById(room);


            }
            
            // 处理关联订单
            List<Order> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                    .eq(Order::getReservationId, reservationId)
                    .in(Order::getStatus, 0, 1) // 未支付或已支付
            );
            
            for (Order order : orders) {
                if (order.getStatus() == 1) {
                    // 已支付订单需要退款
                    order.setStatus(3); // 已退款
                    order.setUpdateTime(LocalDateTime.now());
                    orderMapper.updateById(order);
                } else if (order.getStatus() == 0) {
                    // 未支付订单直接取消
                    order.setStatus(2); // 已取消
                    order.setUpdateTime(LocalDateTime.now());
                    orderMapper.updateById(order);
                }
            }
            
        } catch (Exception e) {
            System.err.println("预订取消补偿处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 异步状态同步检查
     */
    public void asyncStatusSyncCheck() {
        CompletableFuture.runAsync(() -> {
            try {
                // 检查支付状态不一致的情况
                checkPaymentStatusInconsistency();
                
                // 检查预订状态不一致的情况
                checkReservationStatusInconsistency();
                
                // 检查过期未处理的订单
                checkExpiredOrders();
                
            } catch (Exception e) {
                System.err.println("异步状态检查失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 检查支付状态不一致
     */
    private void checkPaymentStatusInconsistency() {
        // 查找订单已支付但预订未标记为已支付的情况
        List<Order> paidOrders = orderMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 1) // 已支付
        );
        
        for (Order order : paidOrders) {
            Reservation reservation = reservationMapper.selectById(order.getReservationId());
            if (reservation != null && reservation.getPayStatus() != 1) {
                // 发现不一致，进行修复
                try {
                    syncOrderAndReservationStatus(order.getId(), reservation.getId());
                } catch (Exception e) {
                    System.err.println("修复支付状态不一致失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 检查预订状态不一致
     */
    private void checkReservationStatusInconsistency() {
        // 查找已取消的预订但关联订单未处理的情况
        List<Reservation> cancelledReservations = reservationMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, 2) // 已取消
        );
        
        for (Reservation reservation : cancelledReservations) {
            List<Order> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                    .eq(Order::getReservationId, reservation.getId())
                    .in(Order::getStatus, 0, 1) // 未支付或已支付
            );
            
            if (!orders.isEmpty()) {
                // 发现不一致，进行修复
                try {
                    handleReservationCancelCompensation(reservation.getId());
                } catch (Exception e) {
                    System.err.println("修复预订状态不一致失败: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * 检查过期订单
     */
    private void checkExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusHours(24);
        
        // 查找24小时前创建但仍未支付的订单
        List<Order> expiredOrders = orderMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, 0) // 未支付
                .lt(Order::getCreateTime, expireTime)
        );
        
        for (Order order : expiredOrders) {
            try {
                // 自动取消过期订单
                order.setStatus(2); // 已取消
                order.setUpdateTime(LocalDateTime.now());
                orderMapper.updateById(order);
                
                // 同时取消关联的预订
                Reservation reservation = reservationMapper.selectById(order.getReservationId());
                if (reservation != null && reservation.getStatus() == 0) {
                    reservation.setStatus(2); // 已取消
                    reservation.setUpdateTime(LocalDateTime.now());
                    handleReservationCancelCompensation(reservation.getId());
                    reservationMapper.updateById(reservation);
                }
                
            } catch (Exception e) {
                System.err.println("处理过期订单失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 数据一致性验证
     */
    public boolean validateDataConsistency(Long orderId, Long reservationId) {
        try {
            Order order = orderMapper.selectById(orderId);
            Reservation reservation = reservationMapper.selectById(reservationId);
            
            if (order == null || reservation == null) {
                return false;
            }
            
            // 检查关联关系
            if (!order.getReservationId().equals(reservationId)) {
                return false;
            }
            
            // 检查状态一致性
            if (order.getStatus() == 1 && reservation.getPayStatus() != 1) {
                return false; // 订单已支付但预订未标记为已支付
            }
            
            if (order.getStatus() == 3 && reservation.getPayStatus() != 2) {
                return false; // 订单已退款但预订未标记为已退款
            }
            
            if (reservation.getStatus() == 2 && order.getStatus() != 2 && order.getStatus() != 3) {
                return false; // 预订已取消但订单状态未同步
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("数据一致性验证失败: " + e.getMessage());
            return false;
        }
    }
}
