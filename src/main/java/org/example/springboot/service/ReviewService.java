package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.springboot.entity.Reservation;
import org.example.springboot.entity.Review;
import org.example.springboot.entity.Room;
import org.example.springboot.entity.User;
import org.example.springboot.entity.RoomType;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.ReservationMapper;
import org.example.springboot.mapper.ReviewMapper;
import org.example.springboot.mapper.RoomMapper;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.mapper.RoomTypeMapper;
import org.example.springboot.util.JwtTokenUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价服务类
 */
@Service
public class ReviewService {
    @Resource
    private ReviewMapper reviewMapper;
    
    @Resource
    private UserMapper userMapper;
    
    @Resource
    private RoomMapper roomMapper;
    
    @Resource
    private ReservationMapper reservationMapper;
    
    @Resource
    private RoomTypeMapper roomTypeMapper;
    
    /**
     * 分页查询评价
     */
    public Page<Review> getReviewsByPage(Long userId, Long roomTypeId, Long reservationId, Integer status,
                                      Integer currentPage, Integer size) {
        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        if (userId != null) {
            queryWrapper.eq(Review::getUserId, userId);
        }
        if (roomTypeId != null) {
            queryWrapper.eq(Review::getRoomTypeId, roomTypeId);
        }
        if (reservationId != null) {
            queryWrapper.eq(Review::getReservationId, reservationId);
        }
        if (status != null) {
            queryWrapper.eq(Review::getStatus, status);
        }
        
        // 按创建时间降序排序
        queryWrapper.orderByDesc(Review::getCreateTime);
        
        Page<Review> page = reviewMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
        
        // 查询关联数据
        for (Review review : page.getRecords()) {
            loadReviewAssociations(review);
        }
        
        return page;
    }
    
    /**
     * 根据ID获取评价
     */
    public Review getReviewById(Long id) {
        Review review = reviewMapper.selectById(id);
        if (review == null) {
            throw new ServiceException("评价不存在");
        }
        
        // 查询关联数据
        loadReviewAssociations(review);
        
        return review;
    }
    
    /**
     * 查询预订的评价
     */
    public Review getReviewByReservationId(Long reservationId) {
        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Review::getReservationId, reservationId);
        
        Review review = reviewMapper.selectOne(queryWrapper);
        if (review != null) {
            // 查询关联数据
            loadReviewAssociations(review);
        }
        
        return review;
    }
    
    /**
     * 创建评价 - 增强版本，包含严格权限控制
     */
    @Transactional
    public Review createReview(Review review) {
        // 获取当前登录用户
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (currentUser == null) {
            throw new ServiceException("用户未登录！");
        }
        review.setUserId(currentUser.getId());

        // 验证评价信息
        validateReview(review);

        // 检查预订是否存在
        Reservation reservation = reservationMapper.selectById(review.getReservationId());
        if (reservation == null) {
            throw new ServiceException("预订不存在");
        }

        // 检查预订是否属于当前用户
        if (!reservation.getUserId().equals(currentUser.getId())) {
            throw new ServiceException("无权为此预订创建评价");
        }

        // 严格验证评价权限
        validateReviewPermission(reservation);

        // 检查是否已经评价
        Review existingReview = getReviewByReservationId(review.getReservationId());
        if (existingReview != null) {
            throw new ServiceException("已经评价过此预订");
        }

        // 修复数据关联 - 通过预订获取房间信息
        Room room = roomMapper.selectById(reservation.getRoomId());
        if (room == null) {
            throw new ServiceException("房间信息不存在");
        }

        // 评价关联房间类型，不关联具体房间
        review.setRoomTypeId(room.getRoomTypeId());
//        review.setRoomId(null); // 不关联具体房间

        // 设置默认值
        review.setStatus(1); // 显示
        review.setCreateTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());

        // 保存评价
        if (reviewMapper.insert(review) <= 0) {
            throw new ServiceException("创建评价失败");
        }

        return review;
    }

    /**
     * 严格验证评价权限
     */
    private void validateReviewPermission(Reservation reservation) {
        LocalDate now = LocalDate.now();

        // 检查预订状态是否为已完成
        if (reservation.getStatus() != 3) {
            throw new ServiceException("只能为已完成的预订创建评价");
        }

        // 检查是否真实入住（退房日期必须已过）
        if (reservation.getEndDate().isAfter(now)) {
            throw new ServiceException("预订尚未结束，无法评价");
        }

        // 检查评价时间限制（退房后30天内）
        if (reservation.getEndDate().plusDays(30).isBefore(now)) {
            throw new ServiceException("评价时间已过期（退房后30天内有效）");
        }

        // 检查支付状态
        if (reservation.getPayStatus() != 1) {
            throw new ServiceException("未支付的预订无法评价");
        }
    }
    
    /**
     * 更新评价
     */
    @Transactional
    public void updateReview(Long id, Review review) {
        // 检查评价是否存在
        Review existingReview = reviewMapper.selectById(id);
        if (existingReview == null) {
            throw new ServiceException("评价不存在");
        }
        
        // 设置ID和不允许修改的字段
        review.setId(id);
        review.setUserId(existingReview.getUserId());
        review.setRoomTypeId(existingReview.getRoomTypeId());
        review.setReservationId(existingReview.getReservationId());
        review.setCreateTime(existingReview.getCreateTime());
        review.setUpdateTime(LocalDateTime.now());
        
        if (reviewMapper.updateById(review) <= 0) {
            throw new ServiceException("更新评价失败");
        }
    }
    
    /**
     * 回复评价
     */
    @Transactional
    public void replyReview(Long id, String reply) {
        // 检查评价是否存在
        Review existingReview = reviewMapper.selectById(id);
        if (existingReview == null) {
            throw new ServiceException("评价不存在");
        }
        
        // 更新回复信息
        Review review = new Review();
        review.setId(id);
        review.setReply(reply);
        review.setReplyTime(LocalDateTime.now());
        review.setUpdateTime(LocalDateTime.now());
        
        if (reviewMapper.updateById(review) <= 0) {
            throw new ServiceException("回复评价失败");
        }
    }
    
    /**
     * 更改评价状态
     */
    @Transactional
    public void updateReviewStatus(Long id, Integer status) {
        // 检查评价是否存在
        Review existingReview = reviewMapper.selectById(id);
        if (existingReview == null) {
            throw new ServiceException("评价不存在");
        }
        
        // 更新状态
        Review review = new Review();
        review.setId(id);
        review.setStatus(status);
        review.setUpdateTime(LocalDateTime.now());
        
        if (reviewMapper.updateById(review) <= 0) {
            throw new ServiceException("更新评价状态失败");
        }
    }
    
    /**
     * 删除评价
     */
    @Transactional
    public void deleteReview(Long id) {
        // 检查评价是否存在
        Review existingReview = reviewMapper.selectById(id);
        if (existingReview == null) {
            throw new ServiceException("评价不存在");
        }
        
        if (reviewMapper.deleteById(id) <= 0) {
            throw new ServiceException("删除评价失败");
        }
    }
    
    /**
     * 获取用户的评价列表
     */
    public List<Review> getUserReviews(Long userId) {
        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Review::getUserId, userId);
        queryWrapper.eq(Review::getStatus, 1); // 只查询显示的评价
        queryWrapper.orderByDesc(Review::getCreateTime);
        
        List<Review> reviews = reviewMapper.selectList(queryWrapper);
        
        // 查询关联数据
        for (Review review : reviews) {
            loadReviewAssociations(review);
        }
        
        return reviews;
    }
    
    /**
     * 获取房间类型的评价列表 - 修复数据关联错误
     */
    public List<Review> getRoomTypeReviews(Long roomTypeId) {
        RoomType roomType = roomTypeMapper.selectById(roomTypeId);
        if (roomType == null) {
            throw new ServiceException("房型信息不存在");
        }

        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Review::getRoomTypeId, roomTypeId); // 修复：使用正确的字段名
        queryWrapper.eq(Review::getStatus, 1); // 只查询显示的评价
        queryWrapper.orderByDesc(Review::getCreateTime);

        List<Review> reviews = reviewMapper.selectList(queryWrapper);

        // 查询关联数据
        for (Review review : reviews) {
            loadReviewAssociations(review);
        }

        return reviews;
    }
    
    /**
     * 计算房间的平均评分
     */
    public double calculateRoomTypeAverageScore(Long roomTypeId) {
        LambdaQueryWrapper<Review> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Review::getRoomTypeId, roomTypeId);
        queryWrapper.eq(Review::getStatus, 1); // 只查询显示的评价
        
        List<Review> reviews = reviewMapper.selectList(queryWrapper);
        
        if (reviews.isEmpty()) {
            return 0;
        }
        
        int totalScore = 0;
        for (Review review : reviews) {
            totalScore += review.getScore();
        }
        
        return (double) totalScore / reviews.size();
    }
    
    /**
     * 查询关联数据
     */
    private void loadReviewAssociations(Review review) {
        // 查询用户
        User user = userMapper.selectById(review.getUserId());
        review.setUser(user);
        

            // 查询房间类型
            RoomType roomType = roomTypeMapper.selectById(review.getRoomTypeId());


        review.setRoomType(roomType);
        
        // 查询预订
        Reservation reservation = reservationMapper.selectById(review.getReservationId());
        if(reservation != null) {
            Long roomId = reservation.getRoomId();
            Room room = roomMapper.selectById(roomId);
            review.setRoom(room);
        }
        review.setReservation(reservation);
    }
    
    /**
     * 验证评价内容
     */
    private void validateReview(Review review) {
        if (review.getReservationId() == null) {
            throw new ServiceException("预订ID不能为空");
        }
        
        if (StringUtils.isBlank(review.getContent())) {
            throw new ServiceException("评价内容不能为空");
        }
        
        if (review.getScore() == null) {
            throw new ServiceException("评分不能为空");
        }
        
        if (review.getScore() < 1 || review.getScore() > 5) {
            throw new ServiceException("评分必须在1-5之间");
        }
    }

    /**
     * 获取评价统计
     */
    public Map<String, Object> getReviewStatistics() {
        Map<String, Object> statistics = new HashMap<>();

        // 总评价数
        long totalReviews = reviewMapper.selectCount(null);
        statistics.put("totalReviews", totalReviews);

        // 显示的评价数
        long visibleReviews = reviewMapper.selectCount(
            new LambdaQueryWrapper<Review>().eq(Review::getStatus, 1)
        );
        statistics.put("visibleReviews", visibleReviews);

        // 隐藏的评价数
        long hiddenReviews = reviewMapper.selectCount(
            new LambdaQueryWrapper<Review>().eq(Review::getStatus, 0)
        );
        statistics.put("hiddenReviews", hiddenReviews);

        // 平均评分
        List<Review> allReviews = reviewMapper.selectList(
            new LambdaQueryWrapper<Review>().eq(Review::getStatus, 1)
        );
        double averageScore = 0;
        if (!allReviews.isEmpty()) {
            int totalScore = allReviews.stream().mapToInt(Review::getScore).sum();
            averageScore = (double) totalScore / allReviews.size();
        }
        statistics.put("averageScore", Math.round(averageScore * 100.0) / 100.0);

        // 各评分等级统计
        Map<String, Long> scoreDistribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            final int score = i;
            long count = allReviews.stream().filter(r -> r.getScore() == score).count();
            scoreDistribution.put(score + "星", count);
        }
        statistics.put("scoreDistribution", scoreDistribution);

        return statistics;
    }
}