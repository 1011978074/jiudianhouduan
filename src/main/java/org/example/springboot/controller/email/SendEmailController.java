package org.example.springboot.controller.email;



import jakarta.annotation.Resource;
import org.example.springboot.common.Result;
import org.example.springboot.entity.User;
import org.example.springboot.exception.ServiceException;
import org.example.springboot.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.security.GeneralSecurityException;
import java.util.Random;

@RestController
@CrossOrigin(origins = "*", maxAge = 3600)
@RequestMapping("/email")
public class SendEmailController {
    private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailController.class);

    @Resource
    JavaMailSender javaMailSender;
    @Value("${user.fromEmail}")
    private String FROM_EMAIL;
    @Resource
    UserService userService;

    @Resource
    private org.example.springboot.service.UserCacheService userCacheService;


    @GetMapping("/code/{email}")
    public Result<?> sendCode(@PathVariable String email) {
        // 检查邮箱是否已注册
        try {
            if (userService.getByEmail(email) != null) {
                throw new ServiceException("邮箱已被注册");
            }
        } catch (ServiceException e) {
            if (!"邮箱不存在".equals(e.getMessage())) {
                throw e;
            }
            // 邮箱不存在是正常的，可以发送验证码
        }

        try {
            int code = generateAndSendCode(email);
            // 将验证码存储到Redis中
            userCacheService.cacheEmailCode(email, String.valueOf(code));
            return Result.success("验证码发送成功");
        } catch (Exception e) {
            throw new ServiceException("验证码发送失败：" + e.getMessage());
        }
    }

    private int generateAndSendCode(String email) {
        Random random = new Random();
        int code = random.nextInt(899999) + 100000;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM_EMAIL);
        message.setTo(email);
        message.setSubject("注册验证码");
        message.setText("邮箱验证码为：" + code + ",请勿转发给他人");

        javaMailSender.send(message);
        LOGGER.info("邮件已发送：" + message.getText());
        
        return code;
    }

    @GetMapping("/findByEmail/{email}")
    public Result<?> findByEmail(@PathVariable String email) {
        LOGGER.info("FIND BY EMAIL:" + email );


                User user = userService.getByEmail(email);
                if (user == null) return Result.error("-1", "邮箱不存在");



        // 生成随机验证码
        Random random = new Random();
        int code = random.nextInt(899999) + 100000;

        // 构建邮件消息
        SimpleMailMessage simpleMailMessage = new SimpleMailMessage();
        simpleMailMessage.setFrom(FROM_EMAIL);
        simpleMailMessage.setTo(email);
        simpleMailMessage.setSubject("找回密码验证码");
        simpleMailMessage.setText("您的找回密码验证码为：" + code + "，有效期为5分钟，请勿泄露给他人。");

        try {
            // 发送邮件
            javaMailSender.send(simpleMailMessage);
            LOGGER.info("找回密码邮件已发送：" + simpleMailMessage.getText());
            // 将验证码存储到Redis中，设置5分钟过期时间
            userCacheService.cacheEmailCode(email, String.valueOf(code));
            return Result.success("找回密码验证码发送成功");
        } catch (Exception e) {
            LOGGER.error("找回密码邮件发送异常：" + e.getMessage());
            return Result.error("-1", "邮件发送异常，请联系管理员。");
        }
    }

    /**
     * 验证邮箱验证码
     * @param email 邮箱
     * @param code 验证码
     * @return 验证结果
     */
    @PostMapping("/verify")
    public Result<?> verifyEmailCode(@RequestBody java.util.Map<String, String> params) {
        String email = params.get("email");
        String code = params.get("code");

        if (email == null || code == null) {
            return Result.error("邮箱和验证码不能为空");
        }

        String cachedCode = userCacheService.getEmailCode(email);
        if (cachedCode == null) {
            return Result.error("验证码已过期或不存在");
        }

        if (!cachedCode.equals(code)) {
            return Result.error("验证码错误");
        }

        // 验证成功后删除验证码
        userCacheService.removeEmailCode(email);
        return Result.success("验证码验证成功");
    }

}
