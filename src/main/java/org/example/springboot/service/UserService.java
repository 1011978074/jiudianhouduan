package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;

import org.example.springboot.entity.Reservation;
import org.example.springboot.entity.User;
import org.example.springboot.DTO.UserPasswordUpdateDTO;
import org.example.springboot.enumClass.AccountStatus;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.mapper.UserMapper;
import org.example.springboot.mapper.ReservationMapper;
import org.example.springboot.util.JwtTokenUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    @Resource
    private UserMapper userMapper;

    @Resource
    private ReservationMapper reservationMapper;

    @Value("${user.defaultPassword:123456}")
    private String DEFAULT_PWD;

    @Resource
    private PasswordEncoder bCryptPasswordEncoder;

    @Resource
    private UserCacheService userCacheService;

    /**
     * 根据邮箱获取用户信息
     */
    public User getByEmail(String email) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new ServiceException("邮箱不存在");
        }
        return user;
    }

    /**
     * 用户登录
     */
    public User login(User user) {
        // 登录失败次数限制
        if (userCacheService.isLocked(user.getUsername(), 5)) {
            throw new ServiceException("登录失败次数过多，账号已被锁定15分钟");
        }


        User dbUser = getByUsername(user.getUsername());
        // 用户存在性检查已经在 getByUsername 中处理
        if (dbUser.getStatus() != null && dbUser.getStatus().equals(AccountStatus.DISABLED.getValue())) {
            throw new ServiceException("账号已被禁用，请联系管理员");
        }

        if (!bCryptPasswordEncoder.matches(user.getPassword(), dbUser.getPassword())) {
            // 记录登录失败次数
            userCacheService.recordLoginFailure(user.getUsername());
            throw new ServiceException("用户名或密码错误");
        }

        // 登录成功，清除失败记录
        userCacheService.clearLoginFailure(user.getUsername());

        String token = JwtTokenUtils.genToken(String.valueOf(dbUser.getId()), dbUser.getPassword());

        // 缓存用户Token
        userCacheService.cacheUserToken(dbUser.getId(), token);

        // 暂时注释掉用户信息缓存，避免序列化问题
        // userCacheService.cacheUserInfo(dbUser);

        // 设置用户角色名称
        dbUser.setRoleName(dbUser.getRoleName());
        dbUser.setToken(token);
        return dbUser;
    }

    /**
     * 根据角色获取用户列表
     */
    public List<User> getUserByRole(String roleCode) {
        List<User> users = userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .eq(User::getRoleCode, roleCode)
        );
        return users;
    }

    /**
     * 创建新用户
     */
    @Transactional
    public void createUser(User user) {
        // 检查用户名是否存在
        if (userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, user.getUsername())
            ) != null) {
            throw new ServiceException("用户名已存在");
        }
        
        // 检查邮箱是否被使用
        if (userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, user.getEmail())
            ) != null) {
            throw new ServiceException("邮箱已被使用");
        }

        // 设置默认值
        if (user.getRoleCode() == null) {
            user.setRoleCode("USER");
        }
        if (user.getStatus() == null) {
            user.setStatus(AccountStatus.NORMAL.getValue());
        }

        user.setPassword(StringUtils.isNotBlank(user.getPassword()) ? user.getPassword() : DEFAULT_PWD);
        user.setPassword(bCryptPasswordEncoder.encode(user.getPassword()));
        
        if (userMapper.insert(user) <= 0) {
            throw new ServiceException("用户创建失败");
        }
    }

    /**
     * 更新用户信息
     */
    @Transactional
    public void updateUser(Long id, User user) {
        // 检查用户是否存在
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new ServiceException("要更新的用户不存在");
        }
        
        // 检查新用户名是否与其他用户重复
        if (user.getUsername() != null && !user.getUsername().equals(existingUser.getUsername())) {
            User duplicateUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, user.getUsername())
            );
            if (duplicateUser != null && !duplicateUser.getId().equals(id)) {
                throw new ServiceException("新用户名已被使用");
            }
        }
        
        // 检查新邮箱是否与其他用户重复
        if (user.getEmail() != null && !user.getEmail().equals(existingUser.getEmail())) {
            User duplicateUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, user.getEmail())
            );
            if (duplicateUser != null && !duplicateUser.getId().equals(id)) {
                throw new ServiceException("新邮箱已被使用");
            }
        }
        
        user.setId(id);
        // 不更新密码字段
        user.setPassword(null);
        
        if (userMapper.updateById(user) <= 0) {
            throw new ServiceException("用户更新失败");
        }

        // 更新成功后，清除缓存
        userCacheService.removeCachedUserInfo(id);
    }

    /**
     * 根据用户名获取用户
     */
    public User getByUsername(String username) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
        );
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        return user;
    }

    /**
     * 批量删除用户
     */
    @Transactional
    public void deleteBatch(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("未选择要删除的用户");
        }
        
        for (Integer id : ids) {
            userMapper.deleteById(id);
        }
    }

    /**
     * 获取所有用户列表
     */
    public List<User> getUserList() {
        return userMapper.selectList(new LambdaQueryWrapper<>());
    }

    /**
     * 根据ID获取用户
     */
    public User getUserById(Long id) {
        // 先从缓存中获取
        User cachedUser = userCacheService.getCachedUserInfo(id);
        if (cachedUser != null) {
            // 获取用户的预订数量（这个信息变化频繁，不缓存）
            Integer reservationCount = Math.toIntExact(reservationMapper.selectCount(
                    new LambdaQueryWrapper<Reservation>()
                            .eq(Reservation::getUserId, id)
            ));
            cachedUser.setReservationCount(reservationCount);
            return cachedUser;
        }

        // 缓存中没有，从数据库获取
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }

        // 设置用户角色名称
        user.setRoleName(user.getRoleName());

        // 获取用户的预订数量
        Integer reservationCount = Math.toIntExact(reservationMapper.selectCount(
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, id)
        ));
        user.setReservationCount(reservationCount);

        // 缓存用户信息
        userCacheService.cacheUserInfo(user);

        return user;
    }

    /**
     * 分页查询用户
     */
    public Page<User> getUsersByPage(String username, String name, String roleCode, Integer currentPage, Integer size) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        
        // 添加查询条件
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like(User::getUsername, username);
        }
        if (StringUtils.isNotBlank(name)) {
            queryWrapper.like(User::getName, name);
        }
        if (StringUtils.isNotBlank(roleCode)) {
            queryWrapper.eq(User::getRoleCode, roleCode);
        }
        
        Page<User> page = userMapper.selectPage(new Page<>(currentPage, size), queryWrapper);
        
        // 为每个用户设置角色名称
        for (User user : page.getRecords()) {
            user.setRoleName(user.getRoleName());
        }
        
        return page;
    }

    /**
     * 更新用户密码
     */
    @Transactional
    public void updatePassword(Long id, UserPasswordUpdateDTO update) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        
        // 验证旧密码
        if (!bCryptPasswordEncoder.matches(update.getOldPassword(), user.getPassword())) {
            throw new ServiceException("原密码错误");
        }
        
        // 更新新密码
        user.setPassword(bCryptPasswordEncoder.encode(update.getNewPassword()));
        if (userMapper.updateById(user) <= 0) {
            throw new ServiceException("密码修改失败");
        }

        // 密码修改后，清除缓存和Token
        userCacheService.removeCachedUserInfo(id);
        userCacheService.removeCachedUserToken(id);
    }

    /**
     * 重置密码
     */
    @Transactional
    public void forgetPassword(String email, String newPassword) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        );
        if (user == null) {
            throw new ServiceException("邮箱不存在");
        }
        
        user.setPassword(bCryptPasswordEncoder.encode(newPassword));
        if (userMapper.updateById(user) <= 0) {
            throw new ServiceException("密码重置失败");
        }

        // 密码重置后，清除缓存和Token
        userCacheService.removeCachedUserInfo(user.getId());
        userCacheService.removeCachedUserToken(user.getId());
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUserById(Long id) {
        // 检查是否为管理员账号
        User user = userMapper.selectById(id);
        if (user != null && "ADMIN".equals(user.getRoleCode())) {
            throw new ServiceException("不能删除管理员账号");
        }
        
        if (userMapper.deleteById(id) <= 0) {
            throw new ServiceException("删除失败");
        }

        // 删除用户后，清除所有相关缓存
        userCacheService.removeCachedUserInfo(id);
        userCacheService.removeCachedUserToken(id);
    }
    
    /**
     * 更新用户状态
     */
    @Transactional
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        
        // 检查是否为管理员账号
        if ("ADMIN".equals(user.getRoleCode()) && !status.equals(AccountStatus.NORMAL.getValue())) {
            throw new ServiceException("不能禁用管理员账号");
        }
        
        user.setStatus(status);
        if (userMapper.updateById(user) <= 0) {
            throw new ServiceException("状态更新失败");
        }

        // 状态更新后，清除缓存
        userCacheService.removeCachedUserInfo(id);
        // 如果是禁用用户，也清除Token
        if (!status.equals(AccountStatus.NORMAL.getValue())) {
            userCacheService.removeCachedUserToken(id);
        }
    }
}
