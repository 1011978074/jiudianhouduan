package org.example.springboot.service;

import jakarta.annotation.Resource;
import org.example.springboot.entity.User;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 用户缓存服务类
 * 专门处理用户相关的缓存操作
 */
@Service
public class UserCacheService {

    @Resource
    private RedisService redisService;

    // 缓存键前缀
    private static final String USER_INFO_PREFIX = "user:info:";
    private static final String USER_TOKEN_PREFIX = "user:token:";
    private static final String LOGIN_FAIL_PREFIX = "user:login:fail:";
    private static final String EMAIL_CODE_PREFIX = "user:email:code:";

    // 缓存过期时间
    private static final long USER_INFO_EXPIRE = 30; // 用户信息缓存30分钟
    private static final long TOKEN_EXPIRE = 24 * 60; // Token缓存24小时
    private static final long LOGIN_FAIL_EXPIRE = 15; // 登录失败记录15分钟
    private static final long EMAIL_CODE_EXPIRE = 5; // 邮箱验证码5分钟

    /**
     * 缓存用户信息（暂时禁用，避免序列化问题）
     * @param user 用户信息
     */
    public void cacheUserInfo(User user) {
        // 暂时禁用用户信息缓存，避免序列化问题
        // 等Redis配置完全稳定后再启用
        /*
        if (user != null && user.getId() != null) {
            String key = USER_INFO_PREFIX + user.getId();
            redisService.set(key, user, USER_INFO_EXPIRE, TimeUnit.MINUTES);
        }
        */
    }

    /**
     * 获取缓存的用户信息（暂时禁用）
     * @param userId 用户ID
     * @return 用户信息
     */
    public User getCachedUserInfo(Long userId) {
        // 暂时禁用用户信息缓存，直接返回null让系统从数据库查询
        return null;
    }

    /**
     * 删除用户信息缓存
     * @param userId 用户ID
     */
    public void removeCachedUserInfo(Long userId) {
        if (userId != null) {
            String key = USER_INFO_PREFIX + userId;
            redisService.delete(key);
        }
    }

    /**
     * 缓存用户Token
     * @param userId 用户ID
     * @param token Token值
     */
    public void cacheUserToken(Long userId, String token) {
        if (userId != null && token != null) {
            String key = USER_TOKEN_PREFIX + userId;
            redisService.set(key, token, TOKEN_EXPIRE, TimeUnit.MINUTES);
        }
    }

    /**
     * 获取缓存的用户Token
     * @param userId 用户ID
     * @return Token值
     */
    public String getCachedUserToken(Long userId) {
        if (userId == null) {
            return null;
        }
        String key = USER_TOKEN_PREFIX + userId;
        try {
            Object tokenObj = redisService.get(key);
            if (tokenObj instanceof String) {
                return (String) tokenObj;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除用户Token缓存
     * @param userId 用户ID
     */
    public void removeCachedUserToken(Long userId) {
        if (userId != null) {
            String key = USER_TOKEN_PREFIX + userId;
            redisService.delete(key);
        }
    }

    /**
     * 记录登录失败次数
     * @param username 用户名
     * @return 当前失败次数
     */
    public Long recordLoginFailure(String username) {
        if (username == null) {
            return 0L;
        }
        String key = LOGIN_FAIL_PREFIX + username;
        Long failCount = redisService.increment(key, 1);
        // 设置过期时间
        redisService.expire(key, LOGIN_FAIL_EXPIRE, TimeUnit.MINUTES);
        return failCount;
    }

    /**
     * 获取登录失败次数
     * @param username 用户名
     * @return 失败次数
     */
    public Long getLoginFailureCount(String username) {
        if (username == null) {
            return 0L;
        }
        String key = LOGIN_FAIL_PREFIX + username;
        Object count = redisService.get(key);
        return count != null ? Long.valueOf(count.toString()) : 0L;
    }

    /**
     * 清除登录失败记录
     * @param username 用户名
     */
    public void clearLoginFailure(String username) {
        if (username != null) {
            String key = LOGIN_FAIL_PREFIX + username;
            redisService.delete(key);
        }
    }

    /**
     * 缓存邮箱验证码
     * @param email 邮箱
     * @param code 验证码
     */
    public void cacheEmailCode(String email, String code) {
        if (email != null && code != null) {
            String key = EMAIL_CODE_PREFIX + email;
            redisService.set(key, code, EMAIL_CODE_EXPIRE, TimeUnit.MINUTES);
        }
    }

    /**
     * 获取邮箱验证码
     * @param email 邮箱
     * @return 验证码
     */
    public String getEmailCode(String email) {
        if (email == null) {
            return null;
        }
        String key = EMAIL_CODE_PREFIX + email;
        try {
            Object codeObj = redisService.get(key);
            if (codeObj instanceof String) {
                return (String) codeObj;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除邮箱验证码
     * @param email 邮箱
     */
    public void removeEmailCode(String email) {
        if (email != null) {
            String key = EMAIL_CODE_PREFIX + email;
            redisService.delete(key);
        }
    }

    /**
     * 检查是否被锁定（登录失败次数过多）
     * @param username 用户名
     * @param maxFailCount 最大失败次数
     * @return 是否被锁定
     */
    public boolean isLocked(String username, int maxFailCount) {
        return getLoginFailureCount(username) >= maxFailCount;
    }
}
