package com.banking.accountservice.respository;

import com.banking.accountservice.entity.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account,String> {

    boolean existsByEmail(@Valid @NotBlank String email);

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> getAccountByAccountNumber(String accountNumber);
}
