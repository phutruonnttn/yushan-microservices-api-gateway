package com.yushan.gateway.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Feign Client Configuration
 * 
 * Provides HttpMessageConverters bean required by Feign Client in Gateway (reactive environment).
 * This is needed because Spring Cloud Gateway uses WebFlux (reactive) by default,
 * but Feign Client requires HttpMessageConverters from Spring MVC.
 */
@Configuration
public class FeignConfig {

    @Bean
    public HttpMessageConverters httpMessageConverters() {
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new MappingJackson2HttpMessageConverter());
        return new HttpMessageConverters(converters);
    }
}

