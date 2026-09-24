package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.origin.AiMessageOriginMapper;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import javax.sql.DataSource;
import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import org.springframework.transaction.UnexpectedRollbackException;


import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实 Spring 同源事务及受控 JDBC 连接；保留参与、回滚和禁止提前提交断言，不代表真实数据库验收。 */
class MyBatisMessageOriginTransactionTest {
    private static final String APP = "0123456789abcdef0123456789abcdef";
    private DataSourceTransactionManager manager;
    private Connection connection;
    private final AiOriginContextPort source = mock(AiOriginContextPort.class);
    private final AiMessageOriginMapper origins = mock(AiMessageOriginMapper.class);
    private final AiChatMessageMapper single = mock(AiChatMessageMapper.class);
    private final AiGroupChatMessageMapper group = mock(AiGroupChatMessageMapper.class);
    private final AiInvocationContext context = new AiInvocationContext("platform", "1", "7", "round");
    private AiMessageOriginPort proxy;

    @BeforeEach
    void setup() throws Exception {
        TenantContextHolder.setTenantId(1L);
        TenantContextHolder.setIgnore(false);
        DataSource dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        manager = new DataSourceTransactionManager(dataSource);
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        proxy = new MyBatisMessageOriginAdapter(transactions, source, origins, single, group);
        when(source.capture(APP)).thenReturn(new AiCallerOrigin(1L, 7L, 23L, "client", "platform", "test"));
        when(single.selectByIdAndUserId(9L, 7L)).thenReturn(
                AiChatMessageDO.builder().id(9L).userId(7L).role("assistant").status(0).build());
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        // 断言框架已释放事务上下文，不以手工clear掩盖泄漏。
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    @Test
    void mandatoryRejectsWithoutOuterTransactionBeforeTargetRuns() throws Exception {
        assertThrows(IllegalStateException.class,
                () -> proxy.record(context, AiChatMode.SINGLE, 9L, APP));
        verifyNoInteractions(source, origins, single, group);
        assertDoesNotThrow(() -> verify(connection, times(0)).setAutoCommit(false));
        assertDoesNotThrow(() -> verify(connection, times(0)).commit());
        assertDoesNotThrow(() -> verify(connection, times(0)).rollback());
    }

    @Test
    void participatesInOuterTransactionWithoutStartingAnother() throws Exception {
        when(origins.insert(any())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertDoesNotThrow(() -> verify(connection, times(1)).setAutoCommit(false));
            assertDoesNotThrow(() -> verify(connection, times(0)).commit());
            return 1;
        });
        new TransactionTemplate(manager).execute(status -> {
            proxy.record(context, AiChatMode.SINGLE, 9L, APP);
            assertDoesNotThrow(() -> verify(connection, times(0)).commit());
            return null;
        });
        verify(origins).insert(any());
        assertDoesNotThrow(() -> verify(connection, times(1)).setAutoCommit(false));
        assertDoesNotThrow(() -> verify(connection, times(1)).commit());
        assertDoesNotThrow(() -> verify(connection, times(0)).rollback());
    }

    @Test
    void insertFailureMarksOuterRollbackAndPreventsContinuation() throws Exception {
        RuntimeException failure = new IllegalStateException("simulated resource failure");
        when(origins.insert(any())).thenThrow(failure);
        AtomicBoolean continued = new AtomicBoolean();
        AtomicBoolean rollbackOnly = new AtomicBoolean();
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new TransactionTemplate(manager).execute(status -> {
                    try { proxy.record(context, AiChatMode.SINGLE, 9L, APP); }
                    finally { rollbackOnly.set(status.isRollbackOnly()); }
                    continued.set(true);
                    return null;
                }));
        assertSame(failure, thrown);
        assertFalse(continued.get());
        assertDoesNotThrow(() -> verify(connection, times(1)).setAutoCommit(false));
        assertTrue(rollbackOnly.get());
        assertDoesNotThrow(() -> verify(connection, times(1)).rollback());
        assertDoesNotThrow(() -> verify(connection, times(0)).commit());
        verify(origins, times(1)).insert(any());
    }

    @Test
    void caughtInnerFailureStillPreventsOuterCommit() throws Exception {
        RuntimeException failure = new IllegalStateException("simulated resource failure");
        when(origins.insert(any())).thenThrow(failure);
        AtomicBoolean caught = new AtomicBoolean();
        AtomicBoolean rollbackOnly = new AtomicBoolean();
        assertThrows(UnexpectedRollbackException.class,
                () -> new TransactionTemplate(manager).execute(status -> {
                    try {
                        proxy.record(context, AiChatMode.SINGLE, 9L, APP);
                    } catch (RuntimeException ex) {
                        assertSame(failure, ex);
                        caught.set(true);
                        rollbackOnly.set(status.isRollbackOnly());
                    }
                    return null;
                }));
        assertTrue(caught.get());
        assertDoesNotThrow(() -> verify(connection, times(1)).setAutoCommit(false));
        assertTrue(rollbackOnly.get());
        assertDoesNotThrow(() -> verify(connection, times(1)).rollback());
        assertDoesNotThrow(() -> verify(connection, times(0)).commit());
    }

    @Test
    void optionalOriginStillParticipatesWithoutPersistence() throws Exception {
        when(source.capture(APP)).thenReturn(null);
        new TransactionTemplate(manager).execute(status -> {
            proxy.record(context, AiChatMode.SINGLE, 9L, APP);
            return null;
        });
        verifyNoInteractions(origins, single, group);
        assertDoesNotThrow(() -> verify(connection, times(1)).setAutoCommit(false));
        assertDoesNotThrow(() -> verify(connection, times(1)).commit());
        assertDoesNotThrow(() -> verify(connection, times(0)).rollback());
    }
}
