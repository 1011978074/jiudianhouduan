package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Order;
import org.example.springboot.entity.Reservation;
import org.example.springboot.entity.Room;
import org.example.springboot.entity.RoomType;
import org.example.springboot.entity.User;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.OrderMapper;
import org.example.springboot.mapper.ReservationMapper;
import org.example.springboot.mapper.RoomMapper;
import org.example.springboot.mapper.RoomTypeMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.util.JwtTokenUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 订单服务类
 */
@Service
public class OrderService {
    @Resource
    private OrderMapper orderMapper;
    
    @Resource
    private ReservationMapper reservationMapper;
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private RoomMapper roomMapper;
    
    @Resource
    private RoomTypeMapper roomTypeMapper;

    @Resource
    private BusinessSyncService businessSyncService;
    
    /**
     * 分页查询订单
     */
    public Page<Order> getOrdersByPage(String orderNo, Long userId, Integer status, 
                                    Integer currentPage, Integer size) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        if (StringUtils.isNotBlank(orderNo)) {
            queryWrapper.like(Order::getOrderNo, orderNo);
        }
        if (userId != null) {
            queryWrapper.eq(Order::getUserId, userId);
        }
        if (status != null) {
            queryWrapper.eq(Order::getStatus, status);
        }
        
        // 按创建时间降序排序
        queryWrapper.orderByDesc(Order::getCreateTime);
        
        Page<Order> page = orderMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
        
        // 查询关联数据
        for (Order order : page.getRecords()) {
            loadOrderAssociations(order);
        }
        
        return page;
    }
    
    /**
     * 根据ID获取订单
     */
    public Order getOrderById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        
        // 查询关联数据
        loadOrderAssociations(order);
        
        return order;
    }
    
    /**
     * 根据订单号获取订单
     */
    public Order getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getOrderNo, orderNo);
        
        Order order = orderMapper.selectOne(queryWrapper);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        
        // 查询关联数据
        loadOrderAssociations(order);
        
        return order;
    }
    
    /**
     * 查询预订的订单
     */
    public Order getOrderByReservationId(Long reservationId) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getReservationId, reservationId);
        
        Order order = orderMapper.selectOne(queryWrapper);
        if (order != null) {
            // 查询关联数据
            loadOrderAssociations(order);
        }
        
        return order;
    }
    
    /**
     * 创建订单
     */
    @Transactional
    public Order createOrder(Long reservationId) {
        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }
        
        // 获取当前登录用户
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser==null){
            throw new ServiceException("用户未登录");
        }
        // 检查预订是否属于当前用户
        if (!reservation.getUserId().equals(currentUser.getId())) {
            throw new ServiceException("无权为此预订创建订单");
        }
        
        // 检查是否已有订单
        Order existingOrder = getOrderByReservationId(reservationId);
        if (existingOrder != null) {
            return existingOrder;
        }
        
        // 创建订单
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(currentUser.getId());
        order.setReservationId(reservationId);
        order.setAmount(reservation.getPrice());
        order.setStatus(0); // 未支付
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        
        if (orderMapper.insert(order) <= 0) {
            throw new ServiceException("创建订单失败");
        }
        
        return order;
    }
    
    /**
     * 更新订单状态
     */
    @Transactional
    public void updateOrderStatus(Long id, Integer status) {
        // 检查订单是否存在
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        
        // 获取当前登录用户
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser==null){
            throw new ServiceException("用户未登录");
        }
        
        // 非管理员只能操作自己的订单
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !order.getUserId().equals(currentUser.getId())) {
            throw new ServiceException("无权操作此订单");
        }
        
        // 更新订单状态
        Order updateOrder = new Order();
        updateOrder.setId(id);
        updateOrder.setStatus(status);
        updateOrder.setUpdateTime(LocalDateTime.now());
        
        // 如果是支付状态，设置支付时间
        if (status == 1) {
            updateOrder.setPayTime(LocalDateTime.now());
        }
        
        if (orderMapper.updateById(updateOrder) <= 0) {
            throw new ServiceException("更新订单状态失败");
        }
        
        // 同时更新预订的支付状态
        updateReservationPayStatus(order.getReservationId(), status);
    }
    
    /**
     * 支付订单 - 增强版本，包含支付验证
     */
    @Transactional
    public void payOrder(Long id, String payMethod, String payNo) {
        // 检查订单是否存在
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }

        // 获取当前登录用户
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser==null){
            throw new ServiceException("用户未登录");
        }

        // 检查订单是否属于当前用户
        if (!order.getUserId().equals(currentUser.getId())) {
            throw new ServiceException("无权操作此订单");
        }

        // 检查订单状态
        if (order.getStatus() != 0) {
            throw new ServiceException("订单状态不允许支付");
        }

        // 验证支付参数
        validatePaymentParams(payMethod, payNo, order.getAmount());

        // 检查订单是否过期（24小时内有效）
        if (order.getCreateTime().isBefore(LocalDateTime.now().minusHours(24))) {
            order.setStatus(2);
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);
            throw new ServiceException("订单已过期，请重新下单");
        }

        // 模拟支付验证（实际项目中应调用第三方支付接口验证）
        boolean paymentValid = validatePaymentWithThirdParty(payMethod, payNo, order.getAmount());
        if (!paymentValid) {
            throw new ServiceException("支付验证失败，请检查支付信息");
        }

        // 更新订单信息
        Order updateOrder = new Order();
        updateOrder.setId(id);
        updateOrder.setStatus(1); // 已支付
        updateOrder.setPayMethod(payMethod);
        updateOrder.setPayNo(payNo);
        updateOrder.setPayTime(LocalDateTime.now());
        updateOrder.setUpdateTime(LocalDateTime.now());

        if (orderMapper.updateById(updateOrder) <= 0) {
            throw new ServiceException("支付订单失败");
        }

        // 同时更新预订的支付状态
        updateReservationPayStatus(order.getReservationId(), 1);

        // 异步执行支付成功补偿处理
        try {
            businessSyncService.handlePaymentSuccessCompensation(id);
        } catch (Exception e) {
            System.err.println("支付成功补偿处理失败: " + e.getMessage());
            // 补偿失败不影响主流程，会由定时任务重试
        }
    }
    
    /**
     * 取消订单
     */
    @Transactional
    public void cancelOrder(Long id) {
        // 检查订单是否存在
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        
        // 获取当前登录用户
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ServiceException("用户未登录");
        }
        // 非管理员只能取消自己的订单
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !order.getUserId().equals(currentUser.getId())) {
            throw new ServiceException("无权取消此订单");
        }
        
        // 检查订单状态
        if (order.getStatus() != 0) {
            throw new ServiceException("只能取消未支付的订单");
        }
        
        // 更新订单状态
        Order updateOrder = new Order();
        updateOrder.setId(id);
        updateOrder.setStatus(2); // 已取消
        updateOrder.setUpdateTime(LocalDateTime.now());
        
        if (orderMapper.updateById(updateOrder) <= 0) {
            throw new ServiceException("取消订单失败");
        }
        
        // 同时更新预订的支付状态
        Reservation reservation = reservationMapper.selectById(order.getReservationId());
        if (reservation != null) {
            Reservation updateReservation = new Reservation();
            updateReservation.setId(reservation.getId());
            updateReservation.setStatus(2); // 已取消
            updateReservation.setPayStatus(2); // 已取消
            updateReservation.setUpdateTime(LocalDateTime.now());
            Room room = roomMapper.selectById(reservation.getRoomId());
            if(room!=null){
                room.setStatus(1);//释放房间占用状态
                roomMapper.updateById(room);

            }

            if (reservationMapper.updateById(updateReservation) <= 0) {
                throw new ServiceException("更新预订状态失败");
            }
        }
    }
    
    /**
     * 退款 - 增强版本，包含退款规则验证
     */
    @Transactional
    public void refundOrder(Long id) {
        // 检查订单是否存在
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new ServiceException("订单不存在");
        }

        // 获取当前登录用户
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ServiceException("用户未登录");
        }

        // 非管理员只能退款自己的订单
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !order.getUserId().equals(currentUser.getId())) {
            throw new ServiceException("无权退款此订单");
        }

        // 检查订单状态
        if (order.getStatus() != 1) {
            throw new ServiceException("只能对已支付的订单进行退款");
        }

        // 获取关联的预订信息
        Reservation reservation = reservationMapper.selectById(order.getReservationId());
        if (reservation == null) {
            throw new ServiceException("关联的预订不存在");
        }

        // 验证退款规则
        validateRefundRules(reservation, currentUser.getRoleCode());

        // 计算退款金额（可能扣除手续费）
        BigDecimal refundAmount = calculateRefundAmount(order.getAmount(), reservation.getStartDate());

        // 模拟调用第三方支付平台退款接口
        boolean refundSuccess = processThirdPartyRefund(order.getPayNo(), refundAmount);
        if (!refundSuccess) {
            throw new ServiceException("第三方退款处理失败，请稍后重试");
        }

        // 更新订单状态
        Order updateOrder = new Order();
        updateOrder.setId(id);
        updateOrder.setStatus(3); // 已退款
        updateOrder.setUpdateTime(LocalDateTime.now());

        if (orderMapper.updateById(updateOrder) <= 0) {
            throw new ServiceException("退款操作失败");
        }

        // 更新预订状态
        Reservation updateReservation = new Reservation();
        updateReservation.setId(reservation.getId());
        updateReservation.setPayStatus(2); // 已退款
        Room room = roomMapper.selectById(reservation.getRoomId());
        if (room != null) {
            room.setStatus(1);
            roomMapper.updateById(room);//释放房间
        }

        // 如果预订还未开始，自动取消预订
        if (reservation.getStartDate().isAfter(LocalDate.now())) {
            updateReservation.setStatus(2); // 已取消
        }
        updateReservation.setUpdateTime(LocalDateTime.now());

        if (reservationMapper.updateById(updateReservation) <= 0) {
            throw new ServiceException("更新预订状态失败");
        }
    }
    
    /**
     * 获取用户的订单列表
     */
    public List<Order> getUserOrders(Long userId) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getUserId, userId);
        queryWrapper.orderByDesc(Order::getCreateTime);
        
        List<Order> orders = orderMapper.selectList(queryWrapper);
        
        // 查询关联数据
        for (Order order : orders) {
            loadOrderAssociations(order);
        }
        
        return orders;
    }
    
    /**
     * 统计订单
     */
    public BigDecimal calculateTotalAmount(Integer status) {
        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            queryWrapper.eq(Order::getStatus, status);
        }
        
        List<Order> orders = orderMapper.selectList(queryWrapper);
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Order order : orders) {
            if (order.getAmount() != null) {
                totalAmount = totalAmount.add(order.getAmount());
            }
        }
        
        return totalAmount;
    }
    
    /**
     * 查询关联数据
     */
    private void loadOrderAssociations(Order order) {
        // 查询用户
        User user = userMapper.selectById(order.getUserId());
        order.setUser(user);
        
        // 查询预订
        Reservation reservation = reservationMapper.selectById(order.getReservationId());
        order.setReservation(reservation);
        
        // 加载预订关联的房间和房型信息
        if (reservation != null) {
            // 查询房间信息
            Room room = roomMapper.selectById(reservation.getRoomId());
            if (room != null) {
                // 查询房间类型信息
                RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
                room.setRoomType(roomType);
                reservation.setRoom(room);
            }
        }
    }
    
    /**
     * 更新预订的支付状态
     */
    private void updateReservationPayStatus(Long reservationId, Integer status) {
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation != null) {
            Reservation updateReservation = new Reservation();
            updateReservation.setId(reservationId);
            
            // 状态映射: 0-未支付，1-已支付，2-已退款
            if (status == 0) {
                updateReservation.setPayStatus(0); // 未支付
            } else if (status == 1) {
                updateReservation.setPayStatus(1); // 已支付
                // 支付成功后不自动确认预订，需要管理员手动确认
            } else if (status == 2) {
                // 订单取消时不改变预订状态，只在取消订单方法中处理
            } else if (status == 3) {
                updateReservation.setPayStatus(2); // 已退款
            }
            
            updateReservation.setUpdateTime(LocalDateTime.now());
            
            reservationMapper.updateById(updateReservation);
        }
    }
    
    /**
     * 生成订单编号
     */
    private String generateOrderNo() {
        // 生成格式为: 时间戳(14位) + UUID(8位)
        String timestamp = LocalDateTime.now().toString().replaceAll("[^0-9]", "").substring(0, 14);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
        return timestamp + uuid;
    }

    /**
     * 验证支付参数
     */
    private void validatePaymentParams(String payMethod, String payNo, BigDecimal amount) {
        if (payMethod == null || payMethod.trim().isEmpty()) {
            throw new ServiceException("支付方式不能为空");
        }

//        if (payNo == null || payNo.trim().isEmpty()) {
//            throw new ServiceException("支付流水号不能为空");
//        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("支付金额必须大于0");
        }

//        // 验证支付方式是否支持
//        if (!"ALIPAY".equals(payMethod) && !"WECHAT".equals(payMethod) && !"BANK_CARD".equals(payMethod)) {
//            throw new ServiceException("不支持的支付方式");
//        }
//
//        // 验证支付流水号格式（简单验证）
//        if (payNo.length() < 10) {
//            throw new ServiceException("支付流水号格式不正确");
//        }
    }

    /**
     * 模拟第三方支付验证
     */
    private boolean validatePaymentWithThirdParty(String payMethod, String payNo, BigDecimal amount) {
        // 实际项目中应该调用第三方支付平台的查询接口验证支付结果
//        // 这里模拟验证逻辑
//
//        // 简单的格式验证
//        if (payNo.startsWith("FAKE_")) {
//            return false; // 明显的假支付流水号
//        }
//
//        // 模拟网络调用延迟
//        try {
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
            return true;
        // 模拟90%的成功率
//        return Math.random() > 0.1;
    }

    /**
     * 验证退款规则
     */
    private void validateRefundRules(Reservation reservation, String userRole) {
        LocalDate now = LocalDate.now();
        LocalDate startDate = reservation.getStartDate();

        // 管理员可以无条件退款
        if ("ADMIN".equals(userRole)) {
            return;
        }

        // 普通用户退款规则
        if (startDate.isBefore(now)) {
            throw new ServiceException("已开始的预订不能退款");
        }

        // 入住前24小时内不能退款
        if (startDate.minusDays(1).isBefore(now)) {
            throw new ServiceException("入住前24小时内不能退款");
        }

        // 检查预订状态
        if (reservation.getStatus() == 2) {
            throw new ServiceException("已取消的预订不能退款");
        }

        if (reservation.getStatus() == 3) {
            throw new ServiceException("已完成的预订不能退款");
        }
    }

    /**
     * 计算退款金额
     */
    private BigDecimal calculateRefundAmount(BigDecimal originalAmount, LocalDate startDate) {
        LocalDate now = LocalDate.now();
        long daysUntilStart = ChronoUnit.DAYS.between(now, startDate);

        // 根据退款时间计算手续费
        BigDecimal refundRate;
        if (daysUntilStart >= 7) {
            refundRate = new BigDecimal("1.00"); // 7天前退款，全额退款
        } else if (daysUntilStart >= 3) {
            refundRate = new BigDecimal("0.90"); // 3-7天前退款，扣除10%手续费
        } else if (daysUntilStart >= 1) {
            refundRate = new BigDecimal("0.80"); // 1-3天前退款，扣除20%手续费
        } else {
            refundRate = new BigDecimal("0.50"); // 24小时内退款，扣除50%手续费
        }

        return originalAmount.multiply(refundRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 模拟第三方退款处理
     */
    private boolean processThirdPartyRefund(String payNo, BigDecimal refundAmount) {
        // 实际项目中应该调用第三方支付平台的退款接口
        // 这里模拟退款处理
//
//        if (payNo == null || refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
//            return false;
//        }
//
//        // 模拟网络调用延迟
//        try {
//            Thread.sleep(200);
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//
//        // 模拟95%的成功率
//        return Math.random() > 0.05;

        return true;
    }

    /**
     * 获取订单统计
     */
    public Map<String, Object> getOrderStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        // 总订单数
        long totalOrders = orderMapper.selectCount(null);
        statistics.put("totalOrders", totalOrders);

        // 待支付订单数
        long pendingOrders = orderMapper.selectCount(
            new LambdaQueryWrapper<Order>().eq(Order::getStatus, 0)
        );
        statistics.put("pendingOrders", pendingOrders);

        // 已支付订单数
        long paidOrders = orderMapper.selectCount(
            new LambdaQueryWrapper<Order>().eq(Order::getStatus, 1)
        );
        statistics.put("paidOrders", paidOrders);

        // 已取消订单数
        long cancelledOrders = orderMapper.selectCount(
            new LambdaQueryWrapper<Order>().eq(Order::getStatus, 2)
        );
        statistics.put("cancelledOrders", cancelledOrders);

        // 已退款订单数
        long refundedOrders = orderMapper.selectCount(
            new LambdaQueryWrapper<Order>().eq(Order::getStatus, 3)
        );
        statistics.put("refundedOrders", refundedOrders);

        // 总金额统计
        BigDecimal totalAmount = calculateTotalAmount(null);
        statistics.put("totalAmount", totalAmount);

        // 已支付金额
        BigDecimal paidAmount = calculateTotalAmount(1);
        statistics.put("paidAmount", paidAmount);

        // 已退款金额
        BigDecimal refundedAmount = calculateTotalAmount(3);
        statistics.put("refundedAmount", refundedAmount);

        return statistics;
    }
}