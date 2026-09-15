package com.banking.transactionservice.service;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";

    private final RedisTemplate<String,String> redisTemplate;

    private static final long OTP_EXPIRY_MINUTES = 5;

    @KafkaListener(topics = "verification.required")
    public void consumeVerificationRequired(@Payload Map<String,Object> event){
        try{
            String transactionId = (String) event.get("transactionId");
            String accountNumber = (String) event.get("accountNumber");
            String reason = (String) event.get("reason");

            log.info("Verification Required - transaction: {}, reason: {}",transactionId,reason);
            Transaction transaction = transactionRepository.findById(transactionId).
                    orElseThrow(()->new RuntimeException(
                            "Transaction not found with id: "+transactionId
                    ));
            if (transaction.getStatus()!= TransactionStatus.PROCESSING){
                log.warn("Transaction: {} not PROCESSING -> skipping",transactionId);
                return;
            }

            //generate otp
            String OTP = String.format("%06d",(int)(Math.random()*900000)+100000);

            String otpKey = "verification:otp"+transactionId;

            redisTemplate.opsForValue().set(otpKey,OTP,OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

            //update status
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);

            log.info("OTP generated for transaction: {} and expires in {} minutes",transactionId,OTP_EXPIRY_MINUTES);

            //Notify User
            Map<String, Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("accountNumber",accountNumber);
            otpEvent.put("reason",reason);
            otpEvent.put("otp",OTP);
            otpEvent.put("amount",event.get("amount"));


            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId,otpEvent);

        }catch(Exception e){
            log.error("Error Handling Verification required: {}",e.getMessage());
        }

    }

    @KafkaListener(topics = "fraud.check.clean")
    public void consumeFraudCheckClean(@Payload Map<String,Object> payLoad){
        try{
            String transactionId = (String) payLoad.get("transactionId");
            String accountNumber = (String) payLoad.get("accountNumber");
            String reason = (String) payLoad.get("reason");
            transactionService.processCleanResult(transactionId);
        } catch (Exception e) {
            log.error("Error processing fraud check result: {}",e.getMessage());
            throw new RuntimeException(e);
        }

    }
}
