package org.example.springboot.task;

import jakarta.annotation.Resource;
import org.example.springboot.service.BusinessSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 业务同步定时任务
 */
@Component
public class BusinessSyncTask {
    
    @Resource
    private BusinessSyncService businessSyncService;
    
    /**
     * 每小时执行一次状态同步检查
     */
    @Scheduled(fixedRate = 3600000) // 1小时 = 3600000毫秒
    public void syncStatusCheck() {
        try {
            System.out.println("开始执行业务状态同步检查...");
            businessSyncService.asyncStatusSyncCheck();
            System.out.println("业务状态同步检查完成");
        } catch (Exception e) {
            System.err.println("业务状态同步检查失败: " + e.getMessage());
        }
    }
    
    /**
     * 每天凌晨2点执行数据一致性全面检查
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyConsistencyCheck() {
        try {
            System.out.println("开始执行每日数据一致性检查...");
            // 这里可以添加更全面的数据一致性检查逻辑
            businessSyncService.asyncStatusSyncCheck();
            System.out.println("每日数据一致性检查完成");
        } catch (Exception e) {
            System.err.println("每日数据一致性检查失败: " + e.getMessage());
        }
    }
}
