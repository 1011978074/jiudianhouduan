package org.example.springboot.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.User;
import org.example.springboot.DTO.UserPasswordUpdateDTO;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.service.UserService;
import org.example.springboot.util.JwtTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name="用户管理接口")
@RestController
@RequestMapping("/user")
public class UserController {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);
    
    @Resource
    private UserService userService;
    
    @Resource
    private UserMapper userMapper;

    @Resource
    private org.example.springboot.service.UserCacheService userCacheService;

    @Operation(summary = "根据id获取用户信息")
    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        // 如果用户不存在会抛出异常，由全局异常处理器处理
        User user = userService.getUserById(id);
        return Result.success(user);
    }

    @Operation(summary = "根据username获取用户信息")
    @GetMapping("/username/{username}")
    public Result<?> getUserByUsername(@PathVariable String username) {
        // 不存在的用户会抛出异常
        User user = userService.getByUsername(username);
        return Result.success(user);
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<?> login(@RequestBody User user) {
        User loginUser = userService.login(user);
        return Result.success(loginUser);
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, Object> params) {
        // 获取用户信息和验证码
        String email = (String) params.get("email");
        String code = (String) params.get("code");

        if (email == null || code == null) {
            return Result.error("邮箱和验证码不能为空");
        }

        // 验证邮箱验证码
        String cachedCode = userCacheService.getEmailCode(email);
        if (cachedCode == null) {
            return Result.error("验证码已过期或不存在");
        }
        if (!cachedCode.equals(code)) {
            return Result.error("验证码错误");
        }

        // 验证成功后删除验证码
        userCacheService.removeEmailCode(email);

        // 构建用户对象
        User user = new User();
        user.setUsername((String) params.get("username"));
        user.setPassword((String) params.get("password"));
        user.setEmail(email);
        user.setPhone((String) params.get("phone"));
        user.setName((String) params.get("name"));
        user.setRoleCode("USER"); // 默认注册为普通用户

        userService.createUser(user);
        return Result.success("注册成功");
    }



    @Operation(summary = "用户退出登录")
    @PostMapping("/logout")
    public Result<?> logout(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        if (userId != null) {
            // 清除Redis中的Token和用户信息缓存
            userCacheService.removeCachedUserToken(userId);
            userCacheService.removeCachedUserInfo(userId);
        }
        return Result.success("退出登录成功");
    }

    @Operation(summary = "密码修改")
    @PutMapping("/password/{id}")
    public Result<?> updatePassword(@PathVariable Long id, @RequestBody UserPasswordUpdateDTO userPasswordUpdate) {
        // 密码修改失败会抛出异常
        userService.updatePassword(id, userPasswordUpdate);
        return Result.success("密码修改成功");
    }

    @Operation(summary = "忘记密码")
    @PostMapping("/forget")
    public Result<?> forgetPassword(@RequestBody Map<String, String> params) {
        String email = params.get("email");
        String newPassword = params.get("newPassword");
        String code = params.get("code");

        if (email == null || newPassword == null || code == null) {
            return Result.error("邮箱、新密码和验证码不能为空");
        }

        // 验证邮箱验证码
        String cachedCode = userCacheService.getEmailCode(email);
        if (cachedCode == null) {
            return Result.error("验证码已过期或不存在");
        }
        if (!cachedCode.equals(code)) {
            return Result.error("验证码错误");
        }

        // 验证成功后删除验证码
        userCacheService.removeEmailCode(email);

        // 密码重置失败会抛出异常
        userService.forgetPassword(email, newPassword);
        return Result.success("密码重置成功");
    }

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<?> getUsersByPage(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String roleCode,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer size) {
        Page<User> page = userService.getUsersByPage(username, name, roleCode, currentPage, size);
        return Result.success(page);
    }

    @Operation(summary = "根据角色获取用户列表")
    @GetMapping("/role/{roleCode}")
    public Result<?> getUserByRole(@PathVariable String roleCode) {
        List<User> users = userService.getUserByRole(roleCode);
        return Result.success(users);
    }

    @Operation(summary = "批量删除用户")
    @DeleteMapping("/deleteBatch")
    public Result<?> deleteBatch(@RequestBody List<Integer> ids) {
        userService.deleteBatch(ids);
        return Result.success("批量删除成功");
    }

    @Operation(summary = "获取所有用户")
    @GetMapping("/all")
    public Result<?> getUserList() {
        List<User> list = userService.getUserList();
        return Result.success(list);
    }

    @Operation(summary = "创建新用户")
    @PostMapping("/add")
    public Result<?> createUser(@RequestBody User user) {
        userService.createUser(user);
        return Result.success("创建成功");
    }

    @Operation(summary = "更新用户信息")
    @PutMapping("/{id}")
    public Result<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        // 更新失败会抛出具体原因的异常
        userService.updateUser(id, user);
        return Result.success("更新成功");
    }

    @Operation(summary = "根据id删除用户")
    @DeleteMapping("/delete/{id}")
    public Result<?> deleteUserById(@PathVariable Long id) {
        // 删除失败会抛出异常
        userService.deleteUserById(id);
        return Result.success("删除成功");
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/current")
    public Result<?> getCurrentUser() {
        User currentUser = JwtTokenUtils.getCurrentUser();
        if (currentUser == null) {
            return Result.error("获取当前用户信息失败，请重新登录");
        }
        // 获取完整的用户信息
        currentUser = userService.getUserById(currentUser.getId());
        return Result.success(currentUser);
    }
    
    @Operation(summary = "修改用户状态")
    @PutMapping("/status/{userId}")
    public Result<?> updateStatus(@PathVariable Long userId, @RequestParam Integer status) {
        userService.updateUserStatus(userId, status);
        return Result.success("状态修改成功");
    }
    
    @Operation(summary = "获取用户数量统计")
    @GetMapping("/stats")
    public Result<?> getUserStats() {
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
        
        Map<String, Integer> stats = Map.of(
            "totalUsers", totalUsers,
            "normalUsers", normalUsers,
            "adminUsers", adminUsers
        );
        
        return Result.success(stats);
    }
}
