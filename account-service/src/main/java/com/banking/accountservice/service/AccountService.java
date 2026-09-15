package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.entity.AccountStatus;
import com.banking.accountservice.entity.AccountType;
import com.banking.accountservice.respository.AccountRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    private static SecureRandom random = new SecureRandom();


    public AccountResponse createRequest(@Valid CreateAccountRequest request) {
        log.info("Creating account for request {}", request.getEmail());
        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("account already exists for email " + request.getEmail());
        }
        Account account = new Account();
        account.setEmail(request.getEmail());
        account.setAccountHolderName(request.getAccountHolderName());
        account.setPhone(request.getPhone());
        account.setAccountType(request.getAccountType());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setBalance(request.getInitialDeposit());
        account.setAccountNumber(generateAccountNumber());
        account.setDailyTransactionLimit(
                request.getAccountType() == AccountType.SAVINGS?new BigDecimal("100000"):new BigDecimal("500000")
        );
        Account savedAccount = accountRepository.save(account);
        log.info("Saved account created for  {}", savedAccount.getAccountNumber());

        return mapToResponse(savedAccount);
    }

    //Generate 12 digit Account Number
    private String generateAccountNumber() {
        String accountNumber;
        do{
            long number = random.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d", number);
        }while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountResponse mapToResponse(Account savedAccount) {
        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(String.valueOf(savedAccount.getId()));
        accountResponse.setEmail(savedAccount.getEmail());
        accountResponse.setAccountHolderName(savedAccount.getAccountHolderName());
        accountResponse.setPhone(savedAccount.getPhone());
        accountResponse.setAccountType(savedAccount.getAccountType());
        accountResponse.setAccountStatus(savedAccount.getAccountStatus());
        accountResponse.setBalance(savedAccount.getBalance());
        accountResponse.setDailyTransactionLimit(savedAccount.getDailyTransactionLimit());
        accountResponse.setAccountNumber(savedAccount.getAccountNumber());
        accountResponse.setCreatedAt(savedAccount.getCreatedAt());
        return accountResponse;
    }

    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.getAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
        );
        return mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.getAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
        );
        return account.getBalance();
    }

    /*
    * Block during fraud detection by fraud detection service through kafka
    * */
    public void blockAccount(String accountNumber) {
        log.info("Blocking account for account number {}", accountNumber);
        Account account = accountRepository.getAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
        );
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account blocked for account number {}", accountNumber);
    }


    public void deductBalance(String accountNumber, BigDecimal amount) {
        log.info("Deducting account for account number {} with balance {} ", accountNumber,amount);
        Account account = accountRepository.getAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
        );

        if (!account.getAccountStatus().equals(AccountStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account status not active");
        }

        if (account.getBalance().compareTo(amount) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account balance not enough");
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Account deducted for account number {}", accountNumber);
        log.info("Balance Updated. new Balance is {}", account.getBalance());
    }

    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Credit account number {} with balance {} ", accountNumber,amount);
        Account account = accountRepository.getAccountByAccountNumber(accountNumber).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found")
        );
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        log.info("{} credit for account number {} New balance is {}", amount, accountNumber,account.getBalance());

    }
}
