package com.banking.accountservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountEventConsumer {


    private final AccountService accountService;

    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(@Payload Map<String, Object> payload){
        try {
            log.info("Received payload: {}", payload);
            String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
            Object amountValue = payload.get("amount");
            BigDecimal amount = new BigDecimal(amountValue.toString());
            log.info("Crediting for account number {} with amount {}", receiverAccountNumber, amount);
            accountService.creditBalance(receiverAccountNumber, amount);
        }catch (Exception e){
            log.error("Error while crediting balance {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String, Object> payload){
        try {
            String accountNumber = (String) payload.get("accountNumber");
            log.info("Fraud Detected Blocking Account Number {}", accountNumber);
            accountService.blockAccount(accountNumber);

        }catch (Exception e){
            log.error("Error while blocking account {}", e.getMessage());
        }
    }
}
