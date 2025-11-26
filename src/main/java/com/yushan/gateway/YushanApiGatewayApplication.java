package com.yushan.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients  // Enable Feign clients for inter-service calls
@EnableAsync  // Enable async support for background tasks (bootstrap service)
public class YushanApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(YushanApiGatewayApplication.class, args);
    }
}
