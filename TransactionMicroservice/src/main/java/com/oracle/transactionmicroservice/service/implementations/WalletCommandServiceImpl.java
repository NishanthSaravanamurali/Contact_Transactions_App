package com.oracle.transactionmicroservice.service.implementations;
import com.oracle.transactionmicroservice.dto.response.WalletResponse;
import org.springframework.transaction.annotation.Transactional;
import com.oracle.transactionmicroservice.dto.request.AddFundsRequest;
import com.oracle.transactionmicroservice.dto.request.MakePaymentRequest;
import com.oracle.transactionmicroservice.dto.response.TransactionResponse;
import com.oracle.transactionmicroservice.entity.Account;
import com.oracle.transactionmicroservice.entity.Transaction;
import com.oracle.transactionmicroservice.entity.Wallet;
import com.oracle.transactionmicroservice.enums.AccountStatus;
import com.oracle.transactionmicroservice.enums.TransactionType;
import com.oracle.transactionmicroservice.exception.ForbiddenOperationException;
import com.oracle.transactionmicroservice.exception.InvalidMoneyException;
import com.oracle.transactionmicroservice.exception.ResourceNotFoundException;
import com.oracle.transactionmicroservice.policy.MoneyPolicy;
import com.oracle.transactionmicroservice.repository.AccountRepository;
import com.oracle.transactionmicroservice.repository.TransactionRepository;
import com.oracle.transactionmicroservice.repository.WalletRepository;
import com.oracle.transactionmicroservice.service.abstractions.WalletCommandService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class WalletCommandServiceImpl implements WalletCommandService {
    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final MoneyPolicy moneyPolicy;
    private final GuardedLocalTransaction localTransaction;

    public WalletCommandServiceImpl(AccountRepository accountRepository,
                                    WalletRepository walletRepository,
                                    TransactionRepository transactionRepository,
                                    MoneyPolicy moneyPolicy,
                                    GuardedLocalTransaction localTransaction) {
        this.accountRepository = accountRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.moneyPolicy = moneyPolicy;
        this.localTransaction = localTransaction;
    }

    @Override
    public TransactionResponse addFunds(Long currentUserId, AddFundsRequest request) {
        ServiceSupport.requireCurrentUser(currentUserId);
        if (request == null) {
            throw new InvalidMoneyException("Top-up request is required.");
        }
        ServiceSupport.requireResourceId(request.accountId(), "Account");
        moneyPolicy.validateAmount(request.amount());
        BigDecimal amount = request.amount().setScale(2);
        return localTransaction.execute(currentUserId, null, () -> {
            // Every operation needing both kinds of row must lock accounts first.
            Account source = accountRepository
                    .findByAccountIdAndUserIdForUpdate(request.accountId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found."));
            if (source.getStatus() != AccountStatus.ACTIVE) {
                throw new ForbiddenOperationException("Account must be active.");
            }
            Wallet destination = lockWallet(currentUserId);
            Transaction transaction = new Transaction(
                    TransactionType.A2W, null, source, destination, amount);
            if (source.getBalance().compareTo(amount) < 0) {
                return failed(transaction);
            }
            validateCredit(destination, amount);
            source.debit(amount);
            destination.credit(amount);
            return completed(transaction);
        });
    }

    @Override
    public TransactionResponse makePayment(Long currentUserId, MakePaymentRequest request) {
        ServiceSupport.requireCurrentUser(currentUserId);
        if (request == null) {
            throw new InvalidMoneyException("Payment request is required.");
        }
        ServiceSupport.requireResourceId(request.receiverUserId(), "Recipient");
        if (currentUserId.equals(request.receiverUserId())) {
            throw new ForbiddenOperationException("You cannot pay your own wallet.");
        }
        moneyPolicy.validateAmount(request.amount());
        BigDecimal amount = request.amount().setScale(2);
        return localTransaction.execute(currentUserId, request.receiverUserId(), () -> {
            // Separate single-row queries make the lock acquisition order explicit.
            Long firstUserId = Math.min(currentUserId, request.receiverUserId());
            Long secondUserId = Math.max(currentUserId, request.receiverUserId());
            Wallet first = lockWallet(firstUserId);
            Wallet second = lockWallet(secondUserId);
            Wallet source = currentUserId.equals(firstUserId) ? first : second;
            Wallet destination = currentUserId.equals(firstUserId) ? second : first;
            Transaction transaction = new Transaction(
                    TransactionType.W2W, source, null, destination, amount);
            if (source.getBalance().compareTo(amount) < 0) {
                return failed(transaction);
            }
            validateCredit(destination, amount);
            source.debit(amount);
            destination.credit(amount);
            return completed(transaction);
        });
    }
    @Transactional
    @Override
    public WalletResponse createWallet(Long userId) {
        ServiceSupport.requireCurrentUser(userId);

        return walletRepository.findByUserId(userId)
                .map(ServiceSupport::response)
                .orElseGet(() -> {
                    Wallet wallet = new Wallet(userId);
                    return ServiceSupport.response(
                            walletRepository.saveAndFlush(wallet)
                    );
                });    }

    private Wallet lockWallet(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }

    private void validateCredit(Wallet destination, BigDecimal amount) {
        moneyPolicy.validateAmount(destination.getBalance().add(amount));
    }

    private TransactionResponse failed(Transaction transaction) {
        transaction.markFailed();
        return ServiceSupport.response(transactionRepository.saveAndFlush(transaction));
    }

    private TransactionResponse completed(Transaction transaction) {
        transaction.markCompleted();
        // Flush also persists dirty managed balances; any failure rolls back all.
        return ServiceSupport.response(transactionRepository.saveAndFlush(transaction));
    }
}

