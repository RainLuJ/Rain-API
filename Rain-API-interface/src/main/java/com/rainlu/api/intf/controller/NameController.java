package com.rainlu.api.intf.controller;


import com.rainlu.api.intf.entity.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/name")
public class NameController {

    @PostMapping("/user")
    public String getName(@RequestBody User user) {
        return "你的名字是" + user.getName();
    }
}
