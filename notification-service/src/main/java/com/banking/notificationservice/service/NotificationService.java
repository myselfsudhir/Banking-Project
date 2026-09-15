package com.banking.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    @KafkaListener(topics = "transaction.otp.generated")
    public void consumeOtpGenerated(@Payload Map<String,Object> event)
    {
        try {
            log.info("OTP generated in Notification with payload {}", event);
            String accountNumber = (String) event.get("accountNumber");
            String otp = (String) event.get("otp");
            String transactionId = (String) event.get("transactionId");
            Object amountValue = event.get("amount");

            BigDecimal amount = new BigDecimal(amountValue.toString());
            String reason = (String) event.get("reason");

            sendAlert(accountNumber,"TRANSACTION_VERIFICATION_REQUIRED",String.format(
                    "Suspicious Activity Detected on Account"+
                            "Reason :%s"+
                            "A Transaction of %s is pending verification"+
                            "Your Otp is: %s and valid for 5 mins"+
                            "If this was not you - then ignore the message",
                    reason,amount,otp
            ));
        }catch (Exception ex){
            log.error("Error Sending OTP notification: {}",ex.getMessage());
        }
    }

    @KafkaListener(topics ="transaction.completed" )
    public void consumeTransactionCompleted(@Payload Map<String,Object> payload)
    {
        try {
            log.info("Transaction Completed Event with payload {}", payload);
            String senderAccountNumber = (String) payload.get("senderAccountNumber");
            String receiverAccountNumber = (String) payload.get("receiverAccountNumber");
            Object amountValue = payload.get("amount");

            BigDecimal amount = new BigDecimal(amountValue.toString());

            //Debit Alert
            sendAlert(senderAccountNumber,"Debit Alert",
                    String.format("%s has been successfully debited from account %s", amount,senderAccountNumber));

            //Credit Alert
            sendAlert(senderAccountNumber,"Credit Alert",
                    String.format("%s has been successfully Credited from account %s", amount,receiverAccountNumber));


        }catch (Exception ex){
            log.error("Error Sending Transaction notification: {}",ex.getMessage());
        }
    }

    @KafkaListener(topics = "fraud.detected")
    public void consumeFraudDetected(@Payload Map<String,Object> payload){
        try {
            log.info("Fraud detected with payload {}", payload);
            String accountNumber = (String) payload.get("accountNumber");
            String reason = payload.get("reason").toString();

            sendAlert(accountNumber,"SUSPICIOUS_ACTIVITY_DETECTED",String.format(
                    "Your account %s has been blocked "+
                            "Reason: %s."+
                            "Please constact your bank immediately",accountNumber,reason
            ));

        }catch (Exception ex){
            log.error("Error sending Fraud Alert {}",ex.getMessage());
        }

    }

    @KafkaListener(topics = "transaction.refunded")
    public void consumeTransactionRefunded(@Payload Map<String,Object> payload)
    {
        try {
            log.info("Received Transaction refund Notification with payload {}", payload);
            String senderAccountNumber = (String) payload.get("senderAccountNumber");
            Object amountValue = payload.get("amount");
            BigDecimal amount = new BigDecimal(amountValue.toString());
            String reason = payload.get("reason").toString();

            sendAlert(senderAccountNumber,"REFUND PROCESSED",String.format(
                    "Your Transaction of %s was cancelled "+
                            "Reason: %s "+
                            " %s has been refunded to account %s. ",amount,reason,amount,senderAccountNumber
            ));

        }catch (Exception ex){
            log.error("Error Sending Refund notification: {}",ex.getMessage());
        }
    }

    @KafkaListener(topics = "payment.completed")
    public void consumePaymentCompleted(@Payload Map<String,Object> payload)
    {
        try {
            log.info("Received Payment Notification for completed Transaction with payload {}", payload);
            String accountNumber = (String) payload.get("accountNumber");
            Object amountValue = payload.get("amount");

            BigDecimal amount = new BigDecimal(amountValue.toString());

            sendAlert(accountNumber,"PAYMENT SUCCESSFUL",String.format(
                    "Payment of %s is completed. "+
                            "RazorPay ID: %s",amount,payload.get("razorpayPaymentId")
            ));

        } catch (Exception e) {
            log.error("Error Sending Payment notification: {}",e.getMessage());
        }
    }

    @KafkaListener(topics = "payment.failed")
    public void consumePaymentFailed(@Payload Map<String,Object> payload)
    {
        try {
            log.info("Payment Failed Notification with payload {}", payload);
            String accountNumber = (String) payload.get("accountNumber");
            String amount = (String) payload.get("amount");

            sendAlert(accountNumber,"PAYMENT FAILED",String.format(
                    "Your payment of %s can not be completed "+
                            "Please Try again or contact support.",amount
            ));
        }catch (Exception ex){
            log.error("Error Sending Payment Failure notification: {}",ex.getMessage());
        }
    }

    private void sendAlert(String accountNumber,String subject, String message) {
        log.info("-----------------------------------------");
        log.info("Account: {}",accountNumber);
        log.info("Subject: {}",subject);
        log.info("Message: {}",message);
        log.info("------------------------------------------");
    }

}
