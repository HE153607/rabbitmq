package com.example.rabbitmq.controller;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StringSerializer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;

@RestController
@RequestMapping("/api/v1")
@Slf4j
public class RabbitController {


    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("/publish")
    public void sendMessage(@RequestBody User message){
        rabbitTemplate.convertAndSend("web_exchange", "web_key", message, message1 -> {
            log.warn("message -> {}", new String(message1.getBody()));
            return message1;
        }, new CorrelationData("anh01"));
    }
}
@Data
class User implements Serializable {
    private int id;
    private String name;
}
