package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.entity.PaymentStatus;
import com.banking.paymentservice.respository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {


    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    @Value("${razorpay.key-id}")
    private String keyId;
    @Value("${razorpay.key-secret}")
    private String keySecret;

    /*
    * Create RazorPay payment order
    *
    * FLOW:
    * 1.Crete order in RazorPay
    * 2.save Payment Record in DB
    * 3.Return order details to frontend
    * 4.Frontend show RazorPay checkOut
    * 5.User pays
    * 6. RazorPay calls webhook
    *
    * */


    public PaymentOrderResponse createPaymentOrder(@Valid CreatePaymentRequest request) throws RazorpayException {
        log.info("Received request to Create Payment Order for account: {}", request.getAccountNumber());

        RazorpayClient razorpayClient = new RazorpayClient(keyId,keySecret);
        int convertedAmount = request.getAmount().multiply(BigDecimal.valueOf(100)).intValue();

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", convertedAmount);
        orderRequest.put("currency","USD/INR");
        orderRequest.put("receipt","rcpt_"+ System.currentTimeMillis()+UUID.randomUUID().toString()
                .replace("-","").substring(0,10));

        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        log.info("Razorpay Order Created: {}", razorpayOrder.get("id").toString());
        //save payment record
        Payment payment = new Payment();
        payment.setRazorpayOrderId(razorpayOrder.get("id").toString());
        payment.setAccountNumber(request.getAccountNumber());
        payment.setAmount(request.getAmount());
        payment.setCurrency("USD/INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setDescription(request.getDescription());
        Payment savedPayment = paymentRepository.save(payment);
        return new PaymentOrderResponse(
                savedPayment.getId(),razorpayOrder.get("id").toString(),
                request.getAmount(),"USD/INR","CREATED",keyId
        );


    }

    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received Razorpay Webhook Payload: {}", payload.get("event"));
        String eventName = (String) payload.get("event");

        if("payment.captured".equals(eventName)){
            handlePaymentSuccess(payload);
        }
        if("payment.failed".equals(eventName)){
            handlePaymentFailure(payload);
        }

    }

    private void handlePaymentFailure(Map<String, Object> payload) {
        try{
            Map<String, Object> paymentRequest = extractPaymentData(payload);
            String orderId = (String) paymentRequest.get("order_id");
            String paymentId = (String) paymentRequest.get("id");
            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment Not Found for order: "+orderId));

            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed by razorPay");
            paymentRepository.save(payment);
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("reason", payment.getFailureReason());

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC,payment.getId() ,event);
            log.info("Payment Failed Event: {}", payment.getId());
        }
        catch (Exception e){
            log.error("payment failed: {}", e.getMessage());
        }
    }

    private void handlePaymentSuccess(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentRequest = extractPaymentData(payload);
            String orderId = (String) paymentRequest.get("order_id");
            String paymentId = (String) paymentRequest.get("id");

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment Not Found for order: "+orderId));
            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            //publish payment completed event to kafka
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorpayPaymentId", paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC,payment.getId() ,event);
            log.info("Payment Completed Successfully{}",payment.getId());
        }catch (Exception e){
            log.error("Error handling payment Successfully{}",e.getMessage());
        }
    }

    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String, Object> entity = (Map<String, Object>) payload.get("payload");

        Map<String, Object> paymentWrapper = (Map<String, Object>) entity.get("payment");

        return (Map<String, Object>)paymentWrapper.get("entity");

    }
}
