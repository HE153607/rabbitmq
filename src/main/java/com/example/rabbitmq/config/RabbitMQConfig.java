package com.example.rabbitmq.config;

import com.rabbitmq.client.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.SendTo;

import java.io.Serializable;

@Configuration
@Slf4j
public class RabbitMQConfig {

    @Bean
    ConnectionFactory connectionFactory() {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
        cachingConnectionFactory.setPassword("guest");
        cachingConnectionFactory.setUsername("guest");
        cachingConnectionFactory.setHost("localhost");
        cachingConnectionFactory.setPort(5672);
        try(Connection connection = cachingConnectionFactory.createConnection()){
            Channel channel = connection.createChannel(false);
            channel.exchangeDeclare("EXCHANGE_NAME", BuiltinExchangeType.DIRECT, true);

            // Create queue - (queueName, durable, exclusive, autoDelete, arguments)
            channel.queueDeclare("QUEUE_NAME", true, false, false, null);

            // Bind queue to exchange - (queue, exchange, routingKey)
            channel.queueBind("QUEUE_NAME", "EXCHANGE_NAME", "ROUTING_KEY");
            AMQP.Confirm.SelectOk confirmed = channel.confirmSelect();
            System.out.println("Enabled published confirm: " + confirmed);
            channel.addConfirmListener((sequenceNumber, multiple) -> {
                System.out.println("[Confirmed - multiple] " + multiple);
                System.out.println("[Confirmed - sequenceNumber] " + sequenceNumber);
            }, (sequenceNumber, multiple) -> {
                System.out.println("Not-Acknowledging for message with id " + sequenceNumber);
            });
            channel.basicPublish("EXCHANGE_NAME", "ROUTING_KEY", null, "message channel".getBytes());

            DeliverCallback deliverCallback = (consumerTag, message) -> {
                System.out.println("[Received] : " + new String(message.getBody()));
            };
            CancelCallback cancelCallback = consumerTag -> {
                System.out.println("[Canceled]" + consumerTag);
            };
            channel.close();
            // basicConsume - ( queue, autoAck, deliverCallback, cancelCallback)
            //channel.basicConsume("QUEUE_NAME", true, deliverCallback, cancelCallback);
        }catch (Exception e){
            e.printStackTrace();
        }
        cachingConnectionFactory.setConnectionLimit(2);
        //cachingConnectionFactory.setPublisherConfirms(true);
        cachingConnectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);//enable type correlated to receive confirm from broker
        cachingConnectionFactory.setPublisherReturns(true);//enable return messaage when publisher cannot send to queue in broker
        return cachingConnectionFactory;
    }

    @Bean
    public MessageConverter converter(){
        Jackson2JsonMessageConverter messageConverter = new Jackson2JsonMessageConverter();
        messageConverter.setDefaultCharset("UTF-8");
        return messageConverter;
    }


    @Bean
    public RabbitAdmin rabbitAdmin(){
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory());
        TopicExchange topicExchange = new TopicExchange("web_exchange");
        Queue queue = new Queue("web", false);

        rabbitAdmin.declareExchange(topicExchange);
        rabbitAdmin.declareQueue(queue);

        rabbitAdmin.declareBinding(BindingBuilder
                .bind(queue)
                .to(topicExchange)
                .with("web_key"));

        return rabbitAdmin;
    }
    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory());
        rabbitTemplate.setReplyTimeout(300);
        rabbitTemplate.setMessageConverter(converter());
        //rabbitTemplate.setChannelTransacted(true);
        rabbitTemplate.setMandatory(true);// true : mean send to any queue doesn't exist but message still can confirm success to broker
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("Received acknowledgment for correlation data: " + correlationData);
            } else {
                System.out.println("Received negative acknowledgment for correlation data: " + correlationData + ", Cause: " + cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returnedMessage -> {
            System.out.println("message missed: "+new String(returnedMessage.getMessage().getBody()));
        });
        return rabbitTemplate;
    }

//    @RabbitListener(queues = "web")
//    //@SendTo("web2")
//    public void getMessage(Message user){
//        log.info("message received ->{},{}", user.getMessageProperties().getCorrelationId(),
//                new String(user.getBody()));
//        //return user.toString();
//    }
}
@Data
class User implements Serializable {
    private int id;
    private String name;
}