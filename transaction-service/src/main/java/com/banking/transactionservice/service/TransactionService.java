package com.banking.transactionservice.service;

import com.banking.transactionservice.Client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.entity.TransactionStatus;
import com.banking.transactionservice.entity.TransactionType;
import com.banking.transactionservice.event.TransactionCompletedEvent;
import com.banking.transactionservice.event.TransactionInitiatedEvent;
import com.banking.transactionservice.repository.TransactionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {


    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private static final String TRANSACTION_INITIATED_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final RedisTemplate<String, String> redisTemplate;

    /*
    * SAGA-1 , initiate transfer
    * deducts from sender via feign
    * Saves Transaction as Processing
    * then publish event to kafka for fraud check
    * @Param request
    * */
    public TransactionResponse transfer(@Valid TransferRequest transferRequest) {
        log.info("SAGA Start -> Transfer {} -> {} amount {}",transferRequest.getSenderAccountNumber(),
                transferRequest.getReceiverAccountNumber(), transferRequest.getAmount());

        //SAGA- Step -1 deduct from sender
        accountServiceClient.deductBalance(transferRequest.getSenderAccountNumber(), transferRequest.getAmount());
        Transaction transaction = new Transaction();
        transaction.setSenderAccountNumber(transferRequest.getSenderAccountNumber());
        transaction.setReceiverAccountNumber(transferRequest.getReceiverAccountNumber());
        transaction.setAmount(transferRequest.getAmount());
        transaction.setType(TransactionType.TRANSFER);
        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction.setDescription(transferRequest.getDescription());
        transaction.setReferenceNumber(UUID.randomUUID().toString());
        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as processing {}", savedTransaction.getId());
        TransactionInitiatedEvent event = new TransactionInitiatedEvent(
                savedTransaction.getId(),
                savedTransaction.getSenderAccountNumber(),
                savedTransaction.getReceiverAccountNumber(),
                savedTransaction.getAmount(),
                savedTransaction.getDescription()
        );
        kafkaTemplate.send(TRANSACTION_INITIATED_TOPIC, savedTransaction.getId(),event);
        log.info("Our SAGA Step 2-> Transaction initiated event published {}",savedTransaction.getId());
        return mapToResponse(savedTransaction);
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        TransactionResponse transactionResponse = new TransactionResponse();
        transactionResponse.setId(transaction.getId());
        transactionResponse.setDescription(transaction.getDescription());
        transactionResponse.setAmount(transaction.getAmount());
        transactionResponse.setSenderAccountNumber(transaction.getSenderAccountNumber());
        transactionResponse.setReceiverAccountNumber(transaction.getReceiverAccountNumber());
        transactionResponse.setType(transaction.getType());
        transactionResponse.setStatus(transaction.getStatus());
        transactionResponse.setReferenceNumber(transaction.getReferenceNumber());
        transactionResponse.setFailureReason(transaction.getFailureReason());
        transactionResponse.setCreatedAt(transaction.getCreatedAt());
        transactionResponse.setCompletedAt(transaction.getCompletedAt());
        return transactionResponse;
    }

    public TransactionResponse getTransaction(String id) {
        Optional<Transaction> transaction = transactionRepository.findById(id);
        return transaction.map(this::mapToResponse).orElseThrow(()->new RuntimeException(
                "Transaction with id " + id + " not found"
        ));
    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        return transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public TransactionResponse verifyOTP(String transactionId, String otp) {
        log.info("Verifying OTP for transaction id {}", transactionId);
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(()->new RuntimeException(
                "Transaction with id " + transactionId + " not found"
        ));

        String otpKey = "verification:otp"+transactionId;
        String storedOTP = redisTemplate.opsForValue().get(otpKey);

        if (storedOTP == null) {
            //OTP Expired
            log.warn("OTP for transaction id {} has expired", transactionId);
            compensateTransaction(transaction,"OTP-Expired - transaction cancelled and amount refunded");
            return mapToResponse(transaction);
        }
        if (!otp.equals(storedOTP)) {
            //block account and refund
            log.warn("Wrong OTP for transaction id {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,"Wrong OTP Entered - transaction cancelled "+
                    "Account blocked for security ");
            return mapToResponse(transaction);
        }
        //OTP Correct -comp[lete transaction
        log.info("OTP Verified for transaction id {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return mapToResponse(transaction);
    }

    private void completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);
        TransactionCompletedEvent event = new TransactionCompletedEvent(
                transaction.getId(),transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),transaction.getAmount(),transaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId(),event);
        log.info("SAGA- complete for  transaction id {}", transaction.getId());
    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        //publish fraud.detected event -> account service will block account
        Map<String,Object> fraudEvent =  new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", reason);

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC,transaction.getSenderAccountNumber(),fraudEvent);
        log.warn("fraud.detected published -> and account {} wiil be blocked .Kindly contact your bank",
                transaction.getSenderAccountNumber());
        //SAGA Compensation: refund Sender
        compensateTransaction(transaction,reason);

    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA Compensation refunding: {} amount: {}",
                transaction.getSenderAccountNumber(),transaction.getAmount());

        //credit money back to sender synchronously
        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(), transaction.getAmount());
        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason +" SAGA Compensation executed , amount refunded at "+ LocalDateTime.now());
        transactionRepository.save(transaction);

        //Publish Refund Event - Notification Service will notify user
        Map<String, Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", reason);

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC, transaction.getId(),refundEvent);
        log.info("SAGA Compensation refunded for amount {} refunded to {}",transaction.getAmount(),transaction.getSenderAccountNumber());
    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(()->new RuntimeException(
                "Transaction with id " + transactionId + " not found"
        ));
        if (transaction.getStatus()!= TransactionStatus.PROCESSING){
            log.warn("Transaction: {} not PROCESSING -> skipping",transactionId);
            return;
        }
        completeTransaction(transaction);
    }
}
