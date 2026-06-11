package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class testcontroll {

    @GetMapping("/test")
    public String hello()
    {
        return "Hello world";
    }
}
