package com.oracle.transactionmicroservice.policy;

import com.oracle.transactionmicroservice.exception.InsufficientFundsException;
import com.oracle.transactionmicroservice.exception.InvalidMoneyException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MoneyPolicy {

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("999999999999999999.99");

    public void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidMoneyException("Amount is required.");
        }

        if (amount.signum() <= 0) {
            throw new InvalidMoneyException("Amount must be greater than zero.");
        }

        if (amount.scale() > 2) {
            throw new InvalidMoneyException(
                    "Amount must not contain more than two decimal places."
            );
        }

        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidMoneyException("Amount exceeds the allowed limit.");
        }
    }

    public void requireSufficientBalance(
            BigDecimal availableBalance,
            BigDecimal amount
    ) {
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException("Insufficient balance.");
        }
    }
}