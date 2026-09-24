package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.origin.AiMessageOriginDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.origin.AiMessageOriginMapper;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import javax.sql.DataSource;
import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 同源事务加受控 JDBC 连接，校验原有归属与错误传播，不证明真实数据库回滚。 */
class MyBatisMessageOriginAdapterTest {
    private static final String APP = "0123456789abcdef0123456789abcdef";
    private final AiOriginContextPort source = mock(AiOriginContextPort.class);
    private final AiMessageOriginMapper origins = mock(AiMessageOriginMapper.class);
    private final AiChatMessageMapper single = mock(AiChatMessageMapper.class);
    private final AiGroupChatMessageMapper group = mock(AiGroupChatMessageMapper.class);
    private MyBatisMessageOriginAdapter adapter;
    private DataSourceTransactionManager manager;
    private TransactionStatus transaction;
    private final AiInvocationContext context = new AiInvocationContext("platform", "1", "7", "round");

    @BeforeEach
    void setup() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        manager = new DataSourceTransactionManager(dataSource);
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        adapter = new MyBatisMessageOriginAdapter(transactions, source, origins, single, group);
        transaction = manager.getTransaction(new DefaultTransactionDefinition());
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(false);
        when(source.capture(APP)).thenReturn(new AiCallerOrigin(1L, 7L, 23L, "client", "platform", "test"));
    }

    @AfterEach
    void cleanup() {
        try { manager.rollback(transaction); }
        finally { TenantContextHolder.clear(); }
    }

    @Test
    void absentOptionalOriginDoesNotAccessPersistence() {
        when(source.capture(APP)).thenReturn(null);
        adapter.record(context, AiChatMode.SINGLE, 9L, APP);
        verifyNoInteractions(origins, single, group);
    }

    @Test
    void untrustedContextIsRejectedBeforeMessageLookup() {
        AiInvocationContext[] invalid = {null, new AiInvocationContext("other", "1", "7", "round"),
                new AiInvocationContext("platform", "2", "7", "round"),
                new AiInvocationContext("platform", "1", "8", "round"),
                new AiInvocationContext("platform", "1", "7", " ")};
        for (AiInvocationContext value : invalid) {
            assertThrows(IllegalStateException.class, () -> adapter.record(value, AiChatMode.SINGLE, 9L, APP));
        }
        verifyNoInteractions(origins, single, group);
    }

    @Test
    void tenantMismatchOrIgnoredScopeIsRejected() {
        TenantContextHolder.setTenantId(2L);
        assertThrows(IllegalStateException.class, () -> adapter.record(context, AiChatMode.SINGLE, 9L, APP));
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(true);
        assertThrows(IllegalStateException.class, () -> adapter.record(context, AiChatMode.SINGLE, 9L, APP));
        verifyNoInteractions(origins, single, group);
    }

    @ParameterizedTest
    @EnumSource(AiChatMode.class)
    void missingOwnedPlaceholderDoesNotInsert(AiChatMode mode) {
        assertThrows(IllegalStateException.class, () -> adapter.record(context, mode, 9L, APP));
        verify(origins, never()).insert(any());
        if (mode == AiChatMode.SINGLE) { verify(single).selectByIdAndUserId(9L, 7L); verifyNoInteractions(group); }
        else { verify(group).selectByIdAndUserId(9L, 7L); verifyNoInteractions(single); }
    }

    @ParameterizedTest
    @EnumSource(AiChatMode.class)
    void wrongRoleOrTerminalPlaceholderDoesNotInsert(AiChatMode mode) {
        placeholder(mode, "user", 0);
        assertThrows(IllegalStateException.class, () -> adapter.record(context, mode, 9L, APP));
        placeholder(mode, "assistant", 1);
        assertThrows(IllegalStateException.class, () -> adapter.record(context, mode, 9L, APP));
        verifyNoInteractions(origins);
    }

    @ParameterizedTest
    @EnumSource(AiChatMode.class)
    void writesExactTrustedOriginAndSelectedMode(AiChatMode mode) {
        placeholder(mode, "assistant", 0);
        when(origins.insert(any())).thenReturn(1);
        adapter.record(context, mode, 9L, APP);
        ArgumentCaptor<AiMessageOriginDO> captured = ArgumentCaptor.forClass(AiMessageOriginDO.class);
        verify(origins).insert(captured.capture());
        AiMessageOriginDO row = captured.getValue();
        assertAll(() -> assertEquals(1L, row.getTenantId()), () -> assertEquals(7L, row.getUserId()),
                () -> assertEquals(23L, row.getClientRecordId()), () -> assertEquals("client", row.getClientId()),
                () -> assertEquals("platform", row.getBusinessSystem()), () -> assertEquals("test", row.getEnvironment()),
                () -> assertEquals(9L, row.getMessageId()), () -> assertEquals(APP, row.getAppId()),
                () -> assertEquals(mode == AiChatMode.SINGLE ? "single" : "group", row.getMode()));
    }

    @Test
    void databaseFailurePropagatesWithoutRetry() {
        placeholder(AiChatMode.SINGLE, "assistant", 0);
        RuntimeException failure = new IllegalStateException("simulated insert failure");
        when(origins.insert(any())).thenThrow(failure);
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> adapter.record(context, AiChatMode.SINGLE, 9L, APP)));
        verify(origins, times(1)).insert(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void unexpectedInsertCountPropagates(int count) {
        placeholder(AiChatMode.SINGLE, "assistant", 0);
        when(origins.insert(any())).thenReturn(count);
        assertThrows(IllegalStateException.class, () -> adapter.record(context, AiChatMode.SINGLE, 9L, APP));
    }

    private void placeholder(AiChatMode mode, String role, int status) {
        if (mode == AiChatMode.SINGLE) {
            when(single.selectByIdAndUserId(9L, 7L)).thenReturn(
                    AiChatMessageDO.builder().id(9L).userId(7L).role(role).status(status).build());
        } else {
            when(group.selectByIdAndUserId(9L, 7L)).thenReturn(
                    AiGroupChatMessageDO.builder().id(9L).userId(7L).role(role).status(status).build());
        }
    }
}
