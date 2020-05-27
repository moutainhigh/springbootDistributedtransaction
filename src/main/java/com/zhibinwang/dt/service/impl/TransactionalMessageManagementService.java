package com.zhibinwang.dt.service.impl;

import com.zhibinwang.dt.enump.TxMessageStatus;
import com.zhibinwang.dt.mapper.TransactionMessageMapper;
import com.zhibinwang.dt.mapper.TransactionalMessageContentMapper;
import com.zhibinwang.dt.model.TransactionalMessage;
import com.zhibinwang.dt.model.TransactionalMessageContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @author zhibin.wang
 * @desc 事务消息管理服务
 **/
@Service
@Slf4j
public class TransactionalMessageManagementService {

    @Autowired
    private  TransactionalMessageContentMapper transactionalMessageContentMapper;

    @Autowired
    private TransactionMessageMapper transactionMessageMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private static final LocalDateTime END = LocalDateTime.of(2999, 1, 1, 0, 0, 0);
    private static final long DEFAULT_INIT_BACKOFF = 10L;
    private static final int DEFAULT_BACKOFF_FACTOR = 2;
    private static final int DEFAULT_MAX_RETRY_TIMES = 5;
    private static final int LIMIT = 100;

    public void  saveTransctionMessage(TransactionalMessage transactionalMessage,String content){
        transactionalMessage.setCreateTime(LocalDateTime.now());
        transactionalMessage.setEditTime(LocalDateTime.now());
        transactionalMessage.setCreator("admin");
        transactionalMessage.setCreator("admin");

        // 计算洗一次执行时间
        transactionalMessage.setNextScheduleTime(calculateNextScheduleTime(LocalDateTime.now(), DEFAULT_INIT_BACKOFF,
                DEFAULT_BACKOFF_FACTOR, 0));

        transactionalMessage.setBackoffFactor(DEFAULT_BACKOFF_FACTOR);
        transactionalMessage.setCurrentRetryTimes(0);
        transactionalMessage.setMaxRetryTimes(DEFAULT_MAX_RETRY_TIMES);
        transactionalMessage.setMessageStatus(TxMessageStatus.PENDING.getStatus());
        // 插入数据库 TODO

        TransactionalMessageContent transactionalMessageContent = new TransactionalMessageContent();
        transactionalMessageContent.setContent(content);
        transactionalMessageContent.setMessageId(transactionalMessage.getId());

        //插入数据库 TODO


    }

    /**
     * 发送数据到mq
     * @param transactionalMessage
     * @param content
     */
    public void sendMessageToMq(TransactionalMessage transactionalMessage,String content){

       try {

           rabbitTemplate.convertAndSend(transactionalMessage.getExchangeName(), transactionalMessage.getRoutingKey(),content);
           //没有报错说明发送成功
           makeSuccess(transactionalMessage);
       }catch (Exception e){
           // 报错说明发送失败
           makeFail(transactionalMessage, e);
       }
    }

    private void  makeSuccess(TransactionalMessage transactionalMessage){
        // 设置下次重发时间为最大重发时间
        transactionalMessage.setNextScheduleTime(END);

        transactionalMessage.setMessageStatus(TxMessageStatus.SUCCESS.getStatus());
        transactionalMessage.setEditTime(LocalDateTime.now());
        //更新数据库，当前重发次数
        transactionalMessage.setCurrentRetryTimes(transactionalMessage.getCurrentRetryTimes().compareTo(transactionalMessage.getMaxRetryTimes()) >= 0 ?
                transactionalMessage.getMaxRetryTimes() : transactionalMessage.getCurrentRetryTimes() + 1);

        // 更新数据库 id已经赋值并传进去了，所以可以根据id进行更新 TODO
    }

    private void makeFail(TransactionalMessage transactionalMessage,Exception e){
        log.error("发送队列{}消息失败", transactionalMessage.getQueueName(),e);

        transactionalMessage.setEditTime(LocalDateTime.now());

        transactionalMessage.setMessageStatus(TxMessageStatus.FAIL.getStatus());
       Integer retryTimes =  transactionalMessage.getCurrentRetryTimes().compareTo(transactionalMessage.getMaxRetryTimes()) >= 0 ?
                transactionalMessage.getMaxRetryTimes() : transactionalMessage.getCurrentRetryTimes() + 1;
       transactionalMessage.setCurrentRetryTimes(retryTimes);
       // 达到最大重发次数
       if (retryTimes == transactionalMessage.getMaxRetryTimes()){
           // 设置下次重发时间为最大重发时间
           transactionalMessage.setNextScheduleTime(END);
       }else {
           transactionalMessage.setNextScheduleTime(calculateNextScheduleTime(transactionalMessage.getNextScheduleTime(), Long.valueOf(retryTimes)));
       }

       // 更新数据库 TODO
    }

    /**
     * 进行业务补偿
     */
    public void processPendingCompensationRecords (){

        // 查询需要进行业务补偿的数据
        // 查询状态为失败，
    }

    /**
     *
     * @param base 基准时间
     * @param initBackoff 退避基准值
     * @param backoffFactor 退避指数
     * @param round 轮数
     * @return
     */
    public static LocalDateTime calculateNextScheduleTime(LocalDateTime base,
                                                    long initBackoff,
                                                    long backoffFactor,
                                                    long round) {
        double delta = initBackoff * Math.pow(backoffFactor, round);
        return base.plusSeconds((long) delta);
    }


    private LocalDateTime calculateNextScheduleTime(LocalDateTime base,Long round){
        return calculateNextScheduleTime(base, DEFAULT_INIT_BACKOFF,
                DEFAULT_BACKOFF_FACTOR, round);
    }


    public static void main(String[] args) {
        LocalDateTime localDateTime1 = LocalDateTime.now();
        LocalDateTime localDateTime2 = calculateNextScheduleTime(localDateTime1, DEFAULT_INIT_BACKOFF,
                DEFAULT_BACKOFF_FACTOR, 0);
        System.out.println(localDateTime2);

        LocalDateTime localDateTime3 = calculateNextScheduleTime(localDateTime1, DEFAULT_INIT_BACKOFF,
                DEFAULT_BACKOFF_FACTOR, 1);
        System.out.println(localDateTime3);

        LocalDateTime localDateTime4 = calculateNextScheduleTime(localDateTime1, DEFAULT_INIT_BACKOFF,
                DEFAULT_BACKOFF_FACTOR, 2);

        System.out.println(localDateTime4);
    }
}
