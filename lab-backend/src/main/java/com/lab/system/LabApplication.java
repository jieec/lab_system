package com.lab.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 高校实验室预约与设备运维系统 启动类
 */
@SpringBootApplication
@MapperScan("com.lab.system.mapper")
@EnableScheduling
public class LabApplication {

    public static void main(String[] args) {
        SpringApplication.run(LabApplication.class, args);
    }
}
