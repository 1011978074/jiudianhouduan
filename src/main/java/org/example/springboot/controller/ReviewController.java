package org.example.springboot.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.Review;
import org.example.springboot.entity.User;
import org.example.springboot.service.ReviewService;
import org.example.springboot.util.JwtTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name="评价管理接口")
@RestController
@RequestMapping("/review")
public class ReviewController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewController.class);
    
    @Resource
    private ReviewService reviewService;
    
    @Operation(summary = "分页查询评价")
    @GetMapping("/page")
    public Result<?> getReviewsByPage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long roomTypeId,
            @RequestParam(required = false) Long reservationId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        
        // 非管理员只能查询公开的评价或自己的评价
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.success(null);
        }
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            if (userId == null || !userId.equals(currentUser.getId())) {
                status = 1; // 只能查看显示状态的评价
            }
        }
        
        Page<Review> page = reviewService.getReviewsByPage(userId, roomTypeId, reservationId, status, currentPage, size);
        return Result.success(page);
    }
    
    @Operation(summary = "根据id获取评价")
    @GetMapping("/{id}")
    public Result<?> getReviewById(@PathVariable Long id) {
        Review review = reviewService.getReviewById(id);
        
        // 非管理员只能查询公开的评价或自己的评价
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode()) && 
            review.getStatus() != 1 && 
            !review.getUserId().equals(currentUser.getId())) {
            return Result.error("无权查看此评价");
        }
        
        return Result.success(review);
    }
    
    @Operation(summary = "查询预订的评价")
    @GetMapping("/reservation/{reservationId}")
    public Result<?> getReviewByReservationId(@PathVariable Long reservationId) {
        Review review = reviewService.getReviewByReservationId(reservationId);
        if (review == null) {
            return Result.success(null);
        }
        
        // 非管理员只能查询公开的评价或自己的评价
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode()) && 
            review.getStatus() != 1 && 
            !review.getUserId().equals(currentUser.getId())) {
            return Result.error("无权查看此评价");
        }
        
        return Result.success(review);
    }
    
    @Operation(summary = "创建评价")
    @PostMapping("/create")
    public Result<?> createReview(@RequestBody Review review) {
        Review createdReview = reviewService.createReview(review);
        return Result.success(createdReview);
    }
    
    @Operation(summary = "更新评价")
    @PutMapping("/{id}")
    public Result<?> updateReview(@PathVariable Long id, @RequestBody Review review) {
        // 权限检查
        Review existingReview = reviewService.getReviewById(id);

        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !existingReview.getUserId().equals(currentUser.getId())) {
            return Result.error("无权修改此评价");
        }
        
        reviewService.updateReview(id, review);
        return Result.success();
    }
    
    @Operation(summary = "回复评价")
    @PutMapping("/reply/{id}")
    public Result<?> replyReview(@PathVariable Long id, @RequestBody Map<String, String> params) {
        // 权限检查
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权回复评价");
        }
        
        String reply = params.get("reply");
        if (reply == null || reply.isEmpty()) {
            return Result.error("回复内容不能为空");
        }
        
        reviewService.replyReview(id, reply);
        return Result.success();
    }
    
    @Operation(summary = "更新评价状态")
    @PutMapping("/status/{id}")
    public Result<?> updateReviewStatus(@PathVariable Long id, @RequestParam Integer status) {
        // 权限检查
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权更新评价状态");
        }
        
        reviewService.updateReviewStatus(id, status);
        return Result.success();
    }
    
    @Operation(summary = "删除评价")
    @DeleteMapping("/{id}")
    public Result<?> deleteReview(@PathVariable Long id) {
        // 权限检查
        Review existingReview = reviewService.getReviewById(id);
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        if (!"ADMIN".equals(currentUser.getRoleCode()) && !existingReview.getUserId().equals(currentUser.getId())) {
            return Result.error("无权删除此评价");
        }
        
        reviewService.deleteReview(id);
        return Result.success();
    }
    
    @Operation(summary = "获取当前用户的评价列表")
    @GetMapping("/user")
    public Result<?> getUserReviews() {
        User currentUser = JwtTokenUtils.getCurrentUser();
        if(currentUser == null) {
            return Result.error("-1","用户未登录！");
        }
        List<Review> reviews = reviewService.getUserReviews(currentUser.getId());
        return Result.success(reviews);
    }
    
    @Operation(summary = "获取房型的评价列表")
    @GetMapping("/roomType/{roomTypeId}")
    public Result<?> getRoomTypeReviews(@PathVariable Long roomTypeId) {
        List<Review> reviews = reviewService.getRoomTypeReviews(roomTypeId);
        return Result.success(reviews);
    }
    
    @Operation(summary = "计算房型的平均评分")
    @GetMapping("/score/{roomTypeId}")
    public Result<?> calculateRoomAverageScore(@PathVariable Long roomTypeId) {
        double score = reviewService.calculateRoomTypeAverageScore(roomTypeId);
        return Result.success(score);
    }

    @Operation(summary = "获取评价统计")
    @GetMapping("/stats")
    public Result<?> getReviewStats() {
        // 只有管理员可以查看统计数据
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (!"ADMIN".equals(currentUser.getRoleCode())) {
            return Result.error("无权查看统计数据");
        }

        Map<String, Object> statistics = reviewService.getReviewStatistics();
        return Result.success(statistics);
    }
}