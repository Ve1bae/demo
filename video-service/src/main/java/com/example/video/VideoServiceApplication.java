package com.example.video;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.video.mapper")
public class VideoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VideoServiceApplication.class, args);
    }
}
