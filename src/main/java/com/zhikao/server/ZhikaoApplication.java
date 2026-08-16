package com.zhikao.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 知考公考学习 App 后端服务启动类。
 *
 * <p>T2.1 空工程阶段：数据库尚未建库，临时排除 DataSource 自动配置保证工程可启动；
 * T2.3 建表完成后移除该排除项。
 *
 * @author 2004TZK
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class ZhikaoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhikaoApplication.class, args);
    }
}
