package com.fryrank.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = "com.fryrank")
public class AspectConfig {
    // This configuration enables AspectJ auto-proxying and scans the package for components and aspects.
}