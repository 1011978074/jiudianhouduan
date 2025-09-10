package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Reservation;
import org.example.springboot.entity.Room;
import org.example.springboot.entity.RoomType;
import org.example.springboot.entity.User;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.ReservationMapper;
import org.example.springboot.mapper.RoomMapper;
import org.example.springboot.mapper.RoomTypeMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.util.JwtTokenUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预订服务类
 */
@Service
public class ReservationService {
    @Resource
    private ReservationMapper reservationMapper;
    
    @Resource
    private RoomMapper roomMapper;
    
    @Resource
    private RoomTypeMapper roomTypeMapper;
    
    @Resource
    private UserMapper userMapper;

    @Resource
    private RoomService roomService;

    @Resource
    private BusinessSyncService businessSyncService;
    
    /**
     * 分页查询预订
     */
    public Page<Reservation> getReservationsByPage(Long userId, Long roomId, Integer status,
                                                Integer payStatus, LocalDate startDate, 
                                                LocalDate endDate, Integer currentPage, Integer size) {
        LambdaQueryWrapper<Reservation> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        if (userId != null) {
            queryWrapper.eq(Reservation::getUserId, userId);
        }
        if (roomId != null) {
            queryWrapper.eq(Reservation::getRoomId, roomId);
        }
        if (status != null) {
            queryWrapper.eq(Reservation::getStatus, status);
        }
        if (payStatus != null) {
            queryWrapper.eq(Reservation::getPayStatus, payStatus);
        }
        if (startDate != null) {
            queryWrapper.ge(Reservation::getStartDate, startDate);
        }
        if (endDate != null) {
            queryWrapper.le(Reservation::getEndDate, endDate);
        }
        
        // 排序
        queryWrapper.orderByDesc(Reservation::getCreateTime);
        
        // 分页查询
        Page<Reservation> page = reservationMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
        
        // 查询关联信息
        for (Reservation reservation : page.getRecords()) {
            // 查询用户信息
            User user = userMapper.selectById(reservation.getUserId());
            reservation.setUser(user);
            
            // 查询房间信息
            Room room = roomMapper.selectById(reservation.getRoomId());
            if (room != null) {
                // 查询房间类型信息
                RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
                room.setRoomType(roomType);
                reservation.setRoom(room);
            }
        }
        
        return page;
    }
    
    /**
     * 根据ID获取预订
     */
    public Reservation getReservationById(Long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }
        
        // 查询用户信息
        User user = userMapper.selectById(reservation.getUserId());
        reservation.setUser(user);
        
        // 查询房间信息
        Room room = roomMapper.selectById(reservation.getRoomId());
        if (room != null) {
            // 查询房间类型信息
            RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
            room.setRoomType(roomType);
            reservation.setRoom(room);
        }
        
        return reservation;
    }
    
    /**
     * 创建预订 - 使用分布式锁防止竞态条件
     */
    @Transactional
    public Reservation createReservation(Reservation reservation) {
        // 获取当前登录用户
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser==null){
            throw new ServiceException("用户未登录");
        }
        reservation.setUserId(currentUser.getId());

        // 验证预订信息
        validateReservation(reservation);

        // 使用分布式锁防止并发预订同一房间
        String lockKey = "room_reservation_" + reservation.getRoomId() + "_" +
                        reservation.getStartDate() + "_" + reservation.getEndDate();

        // 模拟分布式锁（最好使用Redis分布式锁）
        synchronized (lockKey.intern()) {
            // 在锁内重新检查房间可用性
            checkRoomAvailabilityWithLock(reservation.getRoomId(), reservation.getStartDate(), reservation.getEndDate());

            // 设置预订状态
            reservation.setStatus(0); // 待确认
            reservation.setPayStatus(0); // 未支付

            // 计算总价
            Room room = roomMapper.selectById(reservation.getRoomId());
            if (room == null) {
                throw new ServiceException("房间不存在");
            }

            RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
            if (roomType == null) {
                throw new ServiceException("房间类型不存在");
            }

            // 计算入住天数
            long days = ChronoUnit.DAYS.between(reservation.getStartDate(), reservation.getEndDate());
            if (days <= 0) {
                throw new ServiceException("入住天数必须大于0");
            }

            // 计算总价
            BigDecimal totalPrice = roomType.getPrice().multiply(new BigDecimal(days));
            reservation.setPrice(totalPrice);

            // 设置创建时间和更新时间
            reservation.setCreateTime(LocalDateTime.now());
            reservation.setUpdateTime(LocalDateTime.now());

            // 保存预订
            if (reservationMapper.insert(reservation) <= 0) {
                throw new ServiceException("创建预订失败");
            }

            return reservation;
        }
    }
    
    /**
     * 更新预订
     */
    @Transactional
    public void updateReservation(Long id, Reservation reservation) {
        // 检查预订是否存在
        Reservation existingReservation = reservationMapper.selectById(id);
        if (existingReservation == null) {
            throw new ServiceException("要更新的预订不存在");
        }
        
        // 检查是否可以更新
        if (existingReservation.getStatus() == 2 || existingReservation.getStatus() == 3) {
            throw new ServiceException("已取消或已完成的预订不能修改");
        }
        
        // 验证预订信息
        if (reservation.getRoomId() != null && !reservation.getRoomId().equals(existingReservation.getRoomId()) ||
            reservation.getStartDate() != null && !reservation.getStartDate().equals(existingReservation.getStartDate()) ||
            reservation.getEndDate() != null && !reservation.getEndDate().equals(existingReservation.getEndDate())) {
            // 如果修改了房间或日期，需要重新检查可用性
            Long roomId = reservation.getRoomId() != null ? reservation.getRoomId() : existingReservation.getRoomId();
            LocalDate startDate = reservation.getStartDate() != null ? reservation.getStartDate() : existingReservation.getStartDate();
            LocalDate endDate = reservation.getEndDate() != null ? reservation.getEndDate() : existingReservation.getEndDate();
            
            checkRoomAvailability(roomId, startDate, endDate);
            
            // 重新计算总价
            Room room = roomMapper.selectById(roomId);
            if (room == null) {
                throw new ServiceException("房间不存在");
            }
            
            RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
            if (roomType == null) {
                throw new ServiceException("房间类型不存在");
            }
            
            // 计算入住天数
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days <= 0) {
                throw new ServiceException("入住天数必须大于0");
            }
            
            // 计算总价
            BigDecimal totalPrice = roomType.getPrice().multiply(new BigDecimal(days));
            reservation.setPrice(totalPrice);
        }
        
        reservation.setId(id);
        if (reservationMapper.updateById(reservation) <= 0) {
            throw new ServiceException("更新预订失败");
        }
    }
    
    /**
     * 取消预订 - 增强版本，包含取消规则验证
     */
    @Transactional
    public void cancelReservation(Long id) {
        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new ServiceException("要取消的预订不存在");
        }

        // 获取当前用户
        User currentUser = JwtTokenUtils.getCurrentUser();

        // 验证取消权限
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ServiceException("无权取消此预订");
        }

        // 检查是否可以取消
        if (reservation.getStatus() == 2 || reservation.getStatus() == 3) {
            throw new ServiceException("已取消或已完成的预订不能再次取消");
        }

        // 验证取消时间规则
        validateCancellationRules(reservation, currentUser.getRoleCode());

        // 更新预订状态
        reservation.setStatus(2); // 已取消
        reservation.setUpdateTime(LocalDateTime.now());

        // 如果已支付，需要处理退款
        if (reservation.getPayStatus() == 1) {
            // 这里应该调用退款服务，暂时只更新状态
            reservation.setPayStatus(2); // 已退款
        }

        if (reservationMapper.updateById(reservation) <= 0) {
            throw new ServiceException("取消预订失败");
        }

        // 异步执行预订取消补偿处理
        try {
            businessSyncService.handleReservationCancelCompensation(id);
        } catch (Exception e) {
            System.err.println("预订取消补偿处理失败: " + e.getMessage());
            // 补偿失败不影响主流程，会由定时任务重试
        }
    }

    /**
     * 验证取消规则
     */
    private void validateCancellationRules(Reservation reservation, String userRole) {
        LocalDate now = LocalDate.now();
        LocalDate startDate = reservation.getStartDate();

        // 管理员可以无条件取消
        if ("ADMIN".equals(userRole)) {
            return;
        }

        // 普通用户取消规则
        if (startDate.isBefore(now)) {
            throw new ServiceException("已开始的预订不能取消");
        }

        // 入住前24小时内不能取消（除非是管理员）
        if (startDate.minusDays(1).isBefore(now)) {
            throw new ServiceException("入住前24小时内不能取消预订");
        }
    }
    
    /**
     * 更新预订状态 - 增强版本，包含状态转换验证
     */
    @Transactional
    public void updateReservationStatus(Long id, Integer status) {
        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }

        // 验证状态转换的合法性
        validateStatusTransition(reservation.getStatus(), status);

        // 更新状态
        reservation.setStatus(status);
        reservation.setUpdateTime(LocalDateTime.now());

        // 如果状态变为已完成，记录完成时间
        if (status == 3) {
            // 可以添加完成时间字段
        }

        if (reservationMapper.updateById(reservation) <= 0) {
            throw new ServiceException("更新预订状态失败");
        }
    }

    /**
     * 验证预订状态转换的合法性
     */
    private void validateStatusTransition(Integer currentStatus, Integer newStatus) {
        if (currentStatus == null || newStatus == null) {
            throw new ServiceException("状态不能为空");
        }

        // 定义合法的状态转换
        switch (currentStatus) {
            case 0: // 待确认
                if (newStatus != 1 && newStatus != 2) {
                    throw new ServiceException("待确认状态只能转换为已确认或已取消");
                }
                break;
            case 1: // 已确认
                if (newStatus != 2 && newStatus != 3) {
                    throw new ServiceException("已确认状态只能转换为已取消或已完成");
                }
                break;
            case 2: // 已取消
                throw new ServiceException("已取消的预订不能再次修改状态");
            case 3: // 已完成
                throw new ServiceException("已完成的预订不能再次修改状态");
            default:
                throw new ServiceException("未知的预订状态");
        }
    }
    
    /**
     * 更新支付状态
     */
    @Transactional
    public void updatePayStatus(Long id, Integer payStatus) {
        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }
        
        // 更新支付状态
        reservation.setPayStatus(payStatus);
        
        if (reservationMapper.updateById(reservation) <= 0) {
            throw new ServiceException("更新支付状态失败");
        }
    }
    
    /**
     * 查询用户的预订记录
     */
    public List<Reservation> getUserReservations(Long userId) {
        LambdaQueryWrapper<Reservation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Reservation::getUserId, userId);
        queryWrapper.orderByDesc(Reservation::getCreateTime);
        
        List<Reservation> reservations = reservationMapper.selectList(queryWrapper);
        
        // 查询关联信息
        for (Reservation reservation : reservations) {
            // 查询房间信息
            Room room = roomMapper.selectById(reservation.getRoomId());
            if (room != null) {
                // 查询房间类型信息
                RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
                room.setRoomType(roomType);
                reservation.setRoom(room);
            }
        }
        
        return reservations;
    }
    
    /**
     * 查询用户指定状态的预订记录
     */
    public List<Reservation> getUserReservationsByStatus(Long userId, Integer status) {
        LambdaQueryWrapper<Reservation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Reservation::getUserId, userId);
        if (status != null) {
            queryWrapper.eq(Reservation::getStatus, status);
        }
        queryWrapper.orderByDesc(Reservation::getCreateTime);
        
        List<Reservation> reservations = reservationMapper.selectList(queryWrapper);
        
        // 查询关联信息
        for (Reservation reservation : reservations) {
            // 查询房间信息
            Room room = roomMapper.selectById(reservation.getRoomId());
            if (room != null) {
                // 查询房间类型信息
                RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
                room.setRoomType(roomType);
                reservation.setRoom(room);
            }
        }
        
        return reservations;
    }
    
    /**
     * 验证预订信息
     */
    private void validateReservation(Reservation reservation) {
        if (reservation.getRoomId() == null) {
            throw new ServiceException("房间ID不能为空");
        }
        
        if (reservation.getStartDate() == null) {
            throw new ServiceException("入住日期不能为空");
        }
        
        if (reservation.getEndDate() == null) {
            throw new ServiceException("退房日期不能为空");
        }
        
        if (reservation.getStartDate().isAfter(reservation.getEndDate())) {
            throw new ServiceException("入住日期不能晚于退房日期");
        }
        
        if (reservation.getStartDate().isBefore(LocalDate.now())) {
            throw new ServiceException("入住日期不能早于今天");
        }
        
        if (reservation.getGuestCount() == null || reservation.getGuestCount() <= 0) {
            throw new ServiceException("入住人数必须大于0");
        }
        
        if (StringUtils.isBlank(reservation.getGuestName())) {
            throw new ServiceException("入住人姓名不能为空");
        }
        
        if (StringUtils.isBlank(reservation.getGuestPhone())) {
            throw new ServiceException("入住人电话不能为空");
        }
    }
    
    /**
     * 检查房间是否可用
     */
    private void checkRoomAvailability(Long roomId, LocalDate startDate, LocalDate endDate) {
        // 检查房间是否存在且可用
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new ServiceException("房间不存在");
        }
        
        if (room.getStatus() != 1) {
            throw new ServiceException("房间不可用");
        }
        
        // 检查该房间在所选日期范围内是否已被预订
        LambdaQueryWrapper<Reservation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Reservation::getRoomId, roomId)
            .and(wrapper -> wrapper
                // 预订已确认或待确认
                .in(Reservation::getStatus, 0, 1)
                // 预订时间与查询时间有交集
                .and(timeWrapper -> timeWrapper
                    // 开始日期在查询范围内
                    .between(Reservation::getStartDate, startDate, endDate.minusDays(1))
                    .or()
                    // 结束日期在查询范围内
                    .between(Reservation::getEndDate, startDate.plusDays(1), endDate)
                    .or()
                    // 查询范围完全被预订范围包含
                    .and(containWrapper -> containWrapper
                        .le(Reservation::getStartDate, startDate)
                        .ge(Reservation::getEndDate, endDate)
                    )
                )
            );
        
        int count = Math.toIntExact(reservationMapper.selectCount(queryWrapper));
        if (count > 0) {
            throw new ServiceException("所选日期该房间已被预订，请选择其他日期或房间");
        }
    }

    /**
     * 带锁的房间可用性检查 - 防止并发预订
     */
    private void checkRoomAvailabilityWithLock(Long roomId, LocalDate startDate, LocalDate endDate) {
        // 基础验证
        if (roomId == null || startDate == null || endDate == null) {
            throw new ServiceException("房间ID和日期不能为空");
        }

        if (startDate.isAfter(endDate)) {
            throw new ServiceException("入住日期不能晚于退房日期");
        }

        if (startDate.isBefore(LocalDate.now())) {
            throw new ServiceException("入住日期不能早于今天");
        }

        // 检查房间状态
        Room room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new ServiceException("房间不存在");
        }

        if (room.getStatus() != 1) {
            throw new ServiceException("房间当前不可用（维护中）");
        }

        // 更严格的时间冲突检查
        LambdaQueryWrapper<Reservation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Reservation::getRoomId, roomId)
            .in(Reservation::getStatus, 0, 1) // 待确认或已确认
            .and(wrapper -> wrapper
                // 情况1: 新预订开始时间在现有预订期间内
                .and(w1 -> w1.le(Reservation::getStartDate, startDate).gt(Reservation::getEndDate, startDate))
                .or()
                // 情况2: 新预订结束时间在现有预订期间内
                .and(w2 -> w2.lt(Reservation::getStartDate, endDate).ge(Reservation::getEndDate, endDate))
                .or()
                // 情况3: 新预订完全包含现有预订
                .and(w3 -> w3.ge(Reservation::getStartDate, startDate).le(Reservation::getEndDate, endDate))
                .or()
                // 情况4: 现有预订完全包含新预订
                .and(w4 -> w4.le(Reservation::getStartDate, startDate).ge(Reservation::getEndDate, endDate))
            );

        long conflictCount = reservationMapper.selectCount(queryWrapper);
        if (conflictCount > 0) {
            throw new ServiceException("所选日期该房间已被预订，请选择其他日期或房间");
        }
    }

    /**
     * 办理入住手续
     */
    @Transactional
    public void checkIn(Long reservationId, String guestIdCard, String notes) {
        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }

        // 验证入住条件
        validateCheckInConditions(reservation);

        // 验证身份证号
        if (guestIdCard == null || guestIdCard.trim().isEmpty()) {
            throw new ServiceException("入住需要提供身份证号");
        }

        // 更新预订状态和入住信息
        Reservation updateReservation = new Reservation();
        updateReservation.setId(reservationId);
        updateReservation.setStatus(1); // 已确认（入住中）

        // 可以扩展字段记录入住信息
        String currentNotes = reservation.getNotes() != null ? reservation.getNotes() : "";
        String checkInInfo = currentNotes + " [入住时间: " + LocalDateTime.now() + ", 身份证: " + guestIdCard + "]";
        if (notes != null && !notes.trim().isEmpty()) {
            checkInInfo += " [入住备注: " + notes + "]";
        }
        updateReservation.setNotes(checkInInfo);
        updateReservation.setUpdateTime(LocalDateTime.now());

        if (reservationMapper.updateById(updateReservation) <= 0) {
            throw new ServiceException("办理入住失败");
        }
    }

    /**
     * 办理退房手续
     */
    @Transactional
    public void checkOut(Long reservationId, BigDecimal additionalFee, String notes) {
        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }

        // 验证退房条件
        validateCheckOutConditions(reservation);

        // 更新预订状态
        Reservation updateReservation = new Reservation();
        updateReservation.setId(reservationId);
        updateReservation.setStatus(3); // 已完成

        // 处理额外费用
        if (additionalFee != null && additionalFee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newPrice = reservation.getPrice().add(additionalFee);
            updateReservation.setPrice(newPrice);
        }

        // 记录退房信息
        String currentNotes = reservation.getNotes() != null ? reservation.getNotes() : "";
        String checkOutInfo = currentNotes + " [退房时间: " + LocalDateTime.now() + "]";
        if (additionalFee != null && additionalFee.compareTo(BigDecimal.ZERO) > 0) {
            checkOutInfo += " [额外费用: " + additionalFee + "]";
        }
        if (notes != null && !notes.trim().isEmpty()) {
            checkOutInfo += " [退房备注: " + notes + "]";
        }
        updateReservation.setNotes(checkOutInfo);
        updateReservation.setUpdateTime(LocalDateTime.now());

        if (reservationMapper.updateById(updateReservation) <= 0) {
            throw new ServiceException("办理退房失败");
        }

        // 更新房间清洁状态（如果有房间服务）
        try {
            roomService.updateRoomCleaningStatus(reservation.getRoomId(), "待清洁");
        } catch (Exception e) {
            // 房间清洁状态更新失败不影响退房流程
            System.err.println("更新房间清洁状态失败: " + e.getMessage());
        }
    }

    /**
     * 验证入住条件
     */
    private void validateCheckInConditions(Reservation reservation) {
        LocalDate today = LocalDate.now();

        // 检查预订状态
        if (reservation.getStatus() != 1) {
            throw new ServiceException("只有已确认的预订才能办理入住");
        }

        // 检查支付状态
        if (reservation.getPayStatus() != 1) {
            throw new ServiceException("预订未支付，不能办理入住");
        }

        // 检查入住日期
        if (reservation.getStartDate().isAfter(today)) {
            throw new ServiceException("还未到入住日期");
        }

        // 检查是否已过期（入住日期后3天内有效）
        if (reservation.getStartDate().plusDays(3).isBefore(today)) {
            throw new ServiceException("预订已过期，无法办理入住");
        }
    }

    /**
     * 验证退房条件
     */
    private void validateCheckOutConditions(Reservation reservation) {
        LocalDate today = LocalDate.now();

        // 检查预订状态
        if (reservation.getStatus() != 1) {
            throw new ServiceException("只有入住中的预订才能办理退房");
        }

        // 检查是否已入住
        if (reservation.getStartDate().isAfter(today)) {
            throw new ServiceException("预订还未入住，不能办理退房");
        }
    }

    /**
     * 延长入住时间
     */
    @Transactional
    public void extendStay(Long reservationId, LocalDate newEndDate, String reason) {
        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }

        // 验证延长条件
        validateExtensionConditions(reservation, newEndDate);

        // 检查延长期间房间是否可用
        checkRoomAvailabilityWithLock(reservation.getRoomId(), reservation.getEndDate().plusDays(1), newEndDate);

        // 计算额外费用
        Room room = roomMapper.selectById(reservation.getRoomId());
        RoomType roomType = roomTypeMapper.selectById(room.getRoomTypeId());
        long extraDays = ChronoUnit.DAYS.between(reservation.getEndDate(), newEndDate);
        BigDecimal extraFee = roomType.getPrice().multiply(new BigDecimal(extraDays));

        // 更新预订信息
        Reservation updateReservation = new Reservation();
        updateReservation.setId(reservationId);
        updateReservation.setEndDate(newEndDate);
        updateReservation.setPrice(reservation.getPrice().add(extraFee));

        // 记录延长信息
        String currentNotes = reservation.getNotes() != null ? reservation.getNotes() : "";
        String extensionInfo = currentNotes + " [延长入住: " + LocalDateTime.now() +
                              ", 新退房日期: " + newEndDate +
                              ", 额外费用: " + extraFee +
                              ", 原因: " + (reason != null ? reason : "无") + "]";
        updateReservation.setNotes(extensionInfo);
        updateReservation.setUpdateTime(LocalDateTime.now());

        if (reservationMapper.updateById(updateReservation) <= 0) {
            throw new ServiceException("延长入住失败");
        }
    }

    /**
     * 验证延长入住条件
     */
    private void validateExtensionConditions(Reservation reservation, LocalDate newEndDate) {
        LocalDate today = LocalDate.now();

        // 检查预订状态
        if (reservation.getStatus() != 1) {
            throw new ServiceException("只有入住中的预订才能延长");
        }

        // 检查新结束日期
        if (newEndDate == null || newEndDate.isBefore(reservation.getEndDate())) {
            throw new ServiceException("新退房日期必须晚于原退房日期");
        }

        // 限制延长时间（最多延长30天）
        if (ChronoUnit.DAYS.between(reservation.getEndDate(), newEndDate) > 30) {
            throw new ServiceException("延长时间不能超过30天");
        }
    }

    /**
     * 获取预订统计
     */
    public Map<String, Object> getReservationStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        // 总预订数
        long totalReservations = reservationMapper.selectCount(null);
        statistics.put("totalReservations", totalReservations);

        // 待确认预订数
        long pendingReservations = reservationMapper.selectCount(
            new LambdaQueryWrapper<Reservation>().eq(Reservation::getStatus, 0)
        );
        statistics.put("pendingReservations", pendingReservations);

        // 已确认预订数
        long confirmedReservations = reservationMapper.selectCount(
            new LambdaQueryWrapper<Reservation>().eq(Reservation::getStatus, 1)
        );
        statistics.put("confirmedReservations", confirmedReservations);

        // 已完成预订数
        long completedReservations = reservationMapper.selectCount(
            new LambdaQueryWrapper<Reservation>().eq(Reservation::getStatus, 3)
        );
        statistics.put("completedReservations", completedReservations);

        // 已取消预订数
        long cancelledReservations = reservationMapper.selectCount(
            new LambdaQueryWrapper<Reservation>().eq(Reservation::getStatus, 2)
        );
        statistics.put("cancelledReservations", cancelledReservations);

        // 今日入住预订数
        LocalDate today = LocalDate.now();
        long todayCheckIn = reservationMapper.selectCount(
            new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStartDate, today)
                .in(Reservation::getStatus, 1, 3) // 已确认或已完成
        );
        statistics.put("todayCheckIn", todayCheckIn);

        // 今日退房预订数
        long todayCheckOut = reservationMapper.selectCount(
            new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getEndDate, today)
                .in(Reservation::getStatus, 1, 3) // 已确认或已完成
        );
        statistics.put("todayCheckOut", todayCheckOut);

        return statistics;
    }
}