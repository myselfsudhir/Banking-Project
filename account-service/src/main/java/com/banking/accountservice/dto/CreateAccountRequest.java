package com.banking.accountservice.dto;

import com.banking.accountservice.entity.AccountType;
import jakarta.validation.constraints.Email;
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
public class CreateAccountRequest {
    @NotBlank(message = "Account Holder Name is Required")
    private String accountHolderName;

    @NotBlank(message = "email is Required")
    @Email(message = "Invalid Email format")
    private String email;

    @NotBlank(message = "Phone is Required")
    private String phone;

    @NotNull(message = "Account Type is Required")
    private AccountType accountType;

    @NotNull(message = "Initial Deposit is Required")
    @Positive(message = "Initial Deposit must be positive")
    private BigDecimal initialDeposit;

}
