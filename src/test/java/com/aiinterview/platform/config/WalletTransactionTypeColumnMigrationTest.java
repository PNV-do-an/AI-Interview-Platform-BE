package com.aiinterview.platform.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletTransactionTypeColumnMigrationTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Test
    void convertsLegacyEnumColumnToVarchar() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("enum");

        new WalletTransactionTypeColumnMigration(jdbcTemplate)
                .run(new DefaultApplicationArguments());

        verify(jdbcTemplate).execute(
                "ALTER TABLE wallet_transactions MODIFY COLUMN type VARCHAR(40) NOT NULL");
    }

    @Test
    void leavesAlreadyMigratedColumnUntouched() {
        when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("varchar");

        new WalletTransactionTypeColumnMigration(jdbcTemplate)
                .run(new DefaultApplicationArguments());

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
