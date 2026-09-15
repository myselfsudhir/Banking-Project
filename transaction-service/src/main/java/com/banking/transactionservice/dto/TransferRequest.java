package com.banking.transactionservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "sender account number is required")
    private String senderAccountNumber;
    @NotBlank(message = "receiver account number is required")
    private String receiverAccountNumber;
    @NotNull(message = "Amount is Required")
    @Positive(message = "amount cannot be negative")
    private BigDecimal amount;
    private String description;
}
