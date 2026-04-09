package com.szu.rag;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.szu.rag.**.mapper")
public class SzRagApplication {
    public static void main(String[] args) {
        SpringApplication.run(SzRagApplication.class, args);
    }
}
