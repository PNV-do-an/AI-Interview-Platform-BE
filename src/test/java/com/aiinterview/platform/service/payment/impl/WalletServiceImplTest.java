package com.aiinterview.platform.service.payment.impl;

import com.aiinterview.platform.common.exception.BadRequestException;
import com.aiinterview.platform.model.entity.User;
import com.aiinterview.platform.model.entity.Wallet;
import com.aiinterview.platform.model.entity.WalletTransaction;
import com.aiinterview.platform.model.repository.WalletRepository;
import com.aiinterview.platform.model.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Staging có ví cũ với available_balance / escrow_balance = NULL (ddl-auto=update
 * không backfill). Unboxing trực tiếp từng ném NPE và trả 500 ở /payos/return ngay
 * sau khi payOS báo PAID, khiến tiền không được cộng.
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletTransactionRepository txRepository;
    @InjectMocks
    private WalletServiceImpl walletService;

    @Test
    void creditAvailableHandlesNullBalances() {
        User user = User.builder().id(2L).build();
        Wallet wallet = Wallet.builder().user(user).availableBalance(null).escrowBalance(null).build();
        when(walletRepository.findByUserForUpdate(user)).thenReturn(Optional.of(wallet));
        when(txRepository.save(any(WalletTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        walletService.creditAvailable(user, 1000L, "PAYOS_TOPUP", "PAYOS", 1L,
                "PAYOS-123", "Nap tien qua payOS");

        assertThat(wallet.getAvailableBalance()).isEqualTo(1000L);
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(txRepository).save(captor.capture());
        assertThat(captor.getValue().getBalanceAfter()).isEqualTo(1000L);
    }

    @Test
    void debitAvailableWithNullBalanceReportsInsufficientFundsNotNpe() {
        User user = User.builder().id(2L).build();
        Wallet wallet = Wallet.builder().user(user).availableBalance(null).build();
        when(walletRepository.findByUserForUpdate(user)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.debitAvailable(user, 1000L, "COMMISSION",
                "PAYOS", 1L, "key", "desc"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void releaseEscrowHoldHandlesNullEscrowBalance() {
        User user = User.builder().id(2L).build();
        Wallet wallet = Wallet.builder().user(user).escrowBalance(null).build();
        when(walletRepository.findByUserForUpdate(user)).thenReturn(Optional.of(wallet));

        walletService.releaseEscrowHold(user, 500L);

        assertThat(wallet.getEscrowBalance()).isZero();
    }
}
