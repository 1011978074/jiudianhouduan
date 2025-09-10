package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.User;
import org.example.springboot.service.RedisService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Redis测试控制器
 * 用于测试Redis连接和基本功能
 */
@Tag(name = "Redis测试接口")
@RestController
@RequestMapping("/redis")
public class RedisTestController {

    @Resource
    private RedisService redisService;

    @Operation(summary = "测试Redis连接")
    @GetMapping("/test")
    public Result<?> testRedis() {
        try {
            // 测试基本的set和get操作
            String testKey = "test:connection";
            String testValue = "Redis连接测试成功";
            
            redisService.set(testKey, testValue, 60, TimeUnit.SECONDS);
            String result = redisService.get(testKey, String.class);
            
            if (testValue.equals(result)) {
                return Result.success("Redis连接测试成功：" + result);
            } else {
                return Result.error("Redis连接测试失败：值不匹配");
            }
        } catch (Exception e) {
            return Result.error("Redis连接测试失败：" + e.getMessage());
        }
    }

    @Operation(summary = "设置缓存")
    @PostMapping("/set")
    public Result<?> setCache(@RequestParam String key, @RequestParam String value, @RequestParam(defaultValue = "60") long seconds) {
        try {
            redisService.set(key, value, seconds, TimeUnit.SECONDS);
            return Result.success("缓存设置成功");
        } catch (Exception e) {
            return Result.error("缓存设置失败：" + e.getMessage());
        }
    }

    @Operation(summary = "获取缓存")
    @GetMapping("/get/{key}")
    public Result<?> getCache(@PathVariable String key) {
        try {
            Object value = redisService.get(key);
            return Result.success(value);
        } catch (Exception e) {
            return Result.error("获取缓存失败：" + e.getMessage());
        }
    }

    @Operation(summary = "删除缓存")
    @DeleteMapping("/delete/{key}")
    public Result<?> deleteCache(@PathVariable String key) {
        try {
            Boolean result = redisService.delete(key);
            return Result.success("删除结果：" + result);
        } catch (Exception e) {
            return Result.error("删除缓存失败：" + e.getMessage());
        }
    }

    @Operation(summary = "测试用户对象序列化")
    @GetMapping("/test-user")
    public Result<?> testUserSerialization() {
        try {
            // 创建一个测试用户对象
            User testUser = new User();
            testUser.setId(999L);
            testUser.setUsername("testuser");
            testUser.setEmail("test@example.com");
            testUser.setCreateTime(LocalDateTime.now());
            testUser.setUpdateTime(LocalDateTime.now());

            String testKey = "test:user:999";

            // 测试序列化和反序列化
            redisService.set(testKey, testUser, 60, TimeUnit.SECONDS);
            User retrievedUser = redisService.get(testKey, User.class);

            if (retrievedUser != null && testUser.getUsername().equals(retrievedUser.getUsername())) {
                return Result.success("用户对象序列化测试成功：" + retrievedUser.getUsername() + ", 创建时间：" + retrievedUser.getCreateTime());
            } else {
                return Result.error("用户对象序列化测试失败：对象不匹配");
            }
        } catch (Exception e) {
            return Result.error("用户对象序列化测试失败：" + e.getMessage());
        }
    }
}
