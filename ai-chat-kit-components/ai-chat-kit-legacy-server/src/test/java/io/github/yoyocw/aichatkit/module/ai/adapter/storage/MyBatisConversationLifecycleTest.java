package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import io.github.yoyocw.aichatkit.compat.framework.common.exception.ServiceException;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.config.AiShareProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatConversationDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.chat.AiChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatConversationDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMessageDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.agent.AiAgentMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.chat.AiChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMemberMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationManagementService;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiConversationShareService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.*;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiModelExecutionConstants.ERROR_CODE_USER_STOPPED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 真实Spring事务生命周期下的原表管理/分享流程；JDBC连接、Mapper和模型为受控边界。
 * 不执行SQL，不验证数据库租户过滤、TenantIgnore AOP、真实认证或外部模型取消。
 */
class MyBatisConversationLifecycleTest {
    /** 已由宿主捕获的测试身份，租户1、用户7。 */
    private final AiInvocationContext identity = new AiInvocationContext("platform", "1", "7", "test-call");
    /** 每次管理用例须重新捕获身份。 */
    private final AiInvocationContextPort identities = mock(AiInvocationContextPort.class);
    /** 原单聊会话数据边界。 */
    private final AiChatConversationMapper singles = mock(AiChatConversationMapper.class);
    /** 原单聊消息数据边界。 */
    private final AiChatMessageMapper singleMessages = mock(AiChatMessageMapper.class);
    /** 原群聊会话数据边界。 */
    private final AiGroupChatConversationMapper groups = mock(AiGroupChatConversationMapper.class);
    /** 原群聊消息数据边界。 */
    private final AiGroupChatMessageMapper groupMessages = mock(AiGroupChatMessageMapper.class);
    /** 原群聊成员数据边界。 */
    private final AiGroupChatMemberMapper members = mock(AiGroupChatMemberMapper.class);
    /** 审计失败必须使整个删除事务回滚。 */
    private final AiExecutionAuditPort audit = mock(AiExecutionAuditPort.class);
    /** 只观察取消调用，不发起模型请求。 */
    private final BailianClient client = mock(BailianClient.class);
    /** 模拟连接记录真实Spring事务的commit/rollback。 */
    private Connection connection;
    /** 与原表适配器共同使用的事务模板。 */
    private TransactionTemplate outer;
    /** 真实管理用例与真实原表适配器。 */
    private AiConversationManagementService management;
    /** 真实分享用例与真实原表适配器。 */
    private AiConversationShareService shares;

    @BeforeEach
    void setup() throws Exception {
        TenantContextHolder.setTenantId(1L); TenantContextHolder.setIgnore(false);
        DataSource source = mock(DataSource.class); connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
        AiTransactionExecutor transactions = new AiTransactionExecutor(manager, AiTransactionMode.REUSE_HOST);
        outer = new TransactionTemplate(manager);
        when(identities.capture("7")).thenReturn(identity);
        MyBatisGroupChatSupport support = new MyBatisGroupChatSupport(groups, members, groupMessages, mock(AiAgentMapper.class));
        MyBatisConversationStoreAdapter store = new MyBatisConversationStoreAdapter(transactions, singles,
                singleMessages, groups, groupMessages, members, support, audit);
        management = new AiConversationManagementService(identities, store, client, transactions);
        MyBatisConversationShareAdapter shareStore = new MyBatisConversationShareAdapter(transactions, singles,
                singleMessages, groups, groupMessages, members, support);
        shares = new AiConversationShareService(identities, shareStore, transactions, new AiShareProperties());
    }

    @AfterEach
    void cleanup() {
        TenantContextHolder.clear();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
    }

    @Test
    void singleDeleteCancelsOnlyAfterOuterCommit() throws Exception { committedDelete(AiChatMode.SINGLE); }

    @Test
    void groupDeleteCancelsOnlyAfterOuterCommit() throws Exception { committedDelete(AiChatMode.GROUP); }

    @Test
    void singleRegisteredCancellationIsDiscardedOnOuterRollback() throws Exception { rolledBackDelete(AiChatMode.SINGLE); }

    @Test
    void groupRegisteredCancellationIsDiscardedOnOuterRollback() throws Exception { rolledBackDelete(AiChatMode.GROUP); }

    @Test
    void singleAuditFailureRollsBackAndNeverCancels() throws Exception { failedAudit(AiChatMode.SINGLE); }

    @Test
    void groupAuditFailureRollsBackAndNeverCancels() throws Exception { failedAudit(AiChatMode.GROUP); }

    @Test
    void singleLostCasNeverAuditsOrCancelsCompletedCall() throws Exception { lostCas(AiChatMode.SINGLE); }

    @Test
    void groupLostCasNeverAuditsOrCancelsCompletedCall() throws Exception { lostCas(AiChatMode.GROUP); }

    @Test
    void singleShareRevocationUsesOwnerLockAndSameOuterTransaction() throws Exception { revoke(AiChatMode.SINGLE); }

    @Test
    void groupShareRevocationUsesOwnerLockAndSameOuterTransaction() throws Exception { revoke(AiChatMode.GROUP); }

    @Test
    void mismatchedTenantContextRejectsManagementAndRevocationBeforeMapper() throws Exception {
        TenantContextHolder.setTenantId(2L);
        for (AiChatMode mode : AiChatMode.values()) {
            assertThrows(IllegalStateException.class, () -> management.deleteForContext(mode, 8L, identity));
            assertThrows(IllegalStateException.class, () -> shares.revokeForContext(mode, 8L, identity));
        }
        verifyNoInteractions(singles, singleMessages, groups, groupMessages, members, audit, client);
        verify(connection, never()).commit();
    }

    @Test
    void missingOwnerRecordRejectsRevocationBeforeMutation() throws Exception {
        // 模拟归属查询未命中，只证明代码在该结果下拒绝；不宣称真实SQL过滤已验证。
        for (AiChatMode mode : AiChatMode.values()) {
            assertThrows(ServiceException.class, () -> shares.revokeForContext(mode, 8L, identity));
        }
        verify(singles, never()).disableShare(anyLong(), anyLong());
        verify(groups, never()).disableShare(anyLong(), anyLong());
        verifyNoInteractions(singleMessages, groupMessages, members, audit, client);
        verify(connection, times(2)).rollback(); verify(connection, never()).commit();
    }

    @Test
    void singlePublicReadFinalRevocationCheckSuppressesLoadedContent() throws Exception { finalCheck(AiChatMode.SINGLE); }

    @Test
    void groupPublicReadFinalRevocationCheckSuppressesLoadedContent() throws Exception { finalCheck(AiChatMode.GROUP); }

    /** 外层事务提交之前不允许取消；验证逻辑删除顺序及commit先于固定模式取消。 */
    private void committedDelete(AiChatMode mode) throws Exception {
        generating(mode, true);
        outer.execute(status -> {
            management.deleteForContext(mode, 8L, identity);
            verifyNoInteractions(client);
            assertDoesNotThrow(() -> verify(connection, never()).commit());
            return null;
        });
        verify(audit).stop(identity, mode, 9L, ERROR_CODE_USER_STOPPED);
        if (mode == AiChatMode.SINGLE) {
            InOrder order = inOrder(singleMessages, singles, connection, client);
            order.verify(singleMessages).delete(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<AiChatMessageDO>>any());
            order.verify(singles).deleteById(8L); order.verify(connection).commit(); order.verify(client).cancel(9L);
        } else {
            InOrder order = inOrder(groupMessages, members, groups, connection, client);
            order.verify(groupMessages).deleteByConversationId(8L, 7L);
            order.verify(members).deleteByConversationId(8L, 7L);
            order.verify(groups).deleteById(8L); order.verify(connection).commit(); order.verify(client).cancelGroupWorkflow(9L);
        }
        verifyNoMoreInteractions(client); verify(connection, never()).rollback();
    }

    /** 删除已返回意味着afterCommit已注册；外层主动回滚必须丢弃取消回调。 */
    private void rolledBackDelete(AiChatMode mode) throws Exception {
        generating(mode, true);
        outer.execute(status -> {
            management.deleteForContext(mode, 8L, identity);
            verify(audit).stop(identity, mode, 9L, ERROR_CODE_USER_STOPPED);
            verifyNoInteractions(client);
            assertDoesNotThrow(() -> verify(connection, never()).commit());
            status.setRollbackOnly();
            return null;
        });
        if (mode == AiChatMode.SINGLE) { verify(singles).deleteById(8L); }
        else { verify(groups).deleteById(8L); }
        verify(connection).rollback(); verify(connection, never()).commit(); verifyNoInteractions(client);
    }

    /** 审计失败不能吞掉，也不能逻辑删除父记录或触发提交后的取消。 */
    private void failedAudit(AiChatMode mode) throws Exception {
        generating(mode, true);
        RuntimeException failure = new IllegalStateException("controlled audit failure");
        doThrow(failure).when(audit).stop(identity, mode, 9L, ERROR_CODE_USER_STOPPED);
        assertSame(failure, assertThrows(RuntimeException.class, () -> management.deleteForContext(mode, 8L, identity)));
        verify(connection).rollback(); verify(connection, never()).commit(); verifyNoInteractions(client);
        verify(singles, never()).deleteById(anyLong()); verify(groups, never()).deleteById(anyLong());
        verify(singleMessages, never()).delete(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.Wrapper<AiChatMessageDO>>any());
        verify(groupMessages, never()).deleteByConversationId(anyLong(), anyLong());
        verifyNoInteractions(members);
    }

    /** CAS丢失表示消息已改变状态，删除仍可提交但不得停止已终态模型或写停止审计。 */
    private void lostCas(AiChatMode mode) throws Exception {
        generating(mode, false);
        management.deleteForContext(mode, 8L, identity);
        verify(connection).commit(); verify(connection, never()).rollback(); verifyNoInteractions(audit, client);
        if (mode == AiChatMode.SINGLE) {
            verify(singleMessages, times(1)).updateGeneratingMessage(argThat(row -> row != null
                    && Long.valueOf(9L).equals(row.getId()) && Integer.valueOf(STATUS_STOPPED).equals(row.getStatus())));
            verify(singles).deleteById(8L);
        } else {
            verify(groupMessages, times(1)).updateGeneratingMessage(argThat(row -> row != null
                    && Long.valueOf(9L).equals(row.getId()) && Integer.valueOf(STATUS_STOPPED).equals(row.getStatus())));
            verify(groups).deleteById(8L);
        }
    }

    /** 分享撤销在锁归属父记录后更新；原表适配不得另起事务提前提交。 */
    private void revoke(AiChatMode mode) throws Exception {
        owner(mode);
        outer.execute(status -> {
            shares.revokeForContext(mode, 8L, identity);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertDoesNotThrow(() -> verify(connection, never()).commit());
            return null;
        });
        if (mode == AiChatMode.SINGLE) {
            InOrder order = inOrder(singles, connection);
            order.verify(singles).selectByIdAndUserIdForUpdate(8L, 7L);
            order.verify(singles).disableShare(8L, 7L); order.verify(connection).commit();
        } else {
            InOrder order = inOrder(groups, connection);
            order.verify(groups).selectByIdAndUserIdForUpdate(8L, 7L);
            order.verify(groups).disableShare(8L, 7L); order.verify(connection).commit();
        }
        verifyNoInteractions(client, audit); verify(connection, never()).rollback();
    }

    /** 最终条件更新返回0模拟读取期间分享撤销，已读取内容必须抛弃并回滚计数事务。 */
    private void finalCheck(AiChatMode mode) throws Exception {
        String code = "0123456789abcdef0123456789abcdef";
        if (mode == AiChatMode.SINGLE) {
            when(singles.selectActiveShare(eq(code), any())).thenReturn(AiChatConversationDO.builder().id(8L).userId(7L).build());
            AiChatMessageDO message = AiChatMessageDO.builder().userId(7L).role(ROLE_ASSISTANT).content("must not escape").build();
            message.setCreateTime(java.time.LocalDateTime.of(2026, 9, 17, 10, 0));
            when(singleMessages.selectCompletedListByConversationId(8L)).thenReturn(Collections.singletonList(message));
        } else {
            when(groups.selectActiveShare(eq(code), any())).thenReturn(AiGroupChatConversationDO.builder().id(8L).userId(7L).build());
            AiGroupChatMessageDO message = AiGroupChatMessageDO.builder().userId(7L).role(ROLE_ASSISTANT).content("must not escape").build();
            message.setCreateTime(java.time.LocalDateTime.of(2026, 9, 17, 10, 0));
            when(groupMessages.selectCompletedListByConversationId(8L)).thenReturn(Collections.singletonList(message));
            when(members.selectListByConversationId(8L)).thenReturn(Collections.emptyList());
        }
        assertThrows(AiExecutionException.class, () -> shares.readPublic(mode, code));
        if (mode == AiChatMode.SINGLE) {
            verify(singleMessages).selectCompletedListByConversationId(8L);
            verify(singles).incrementShareAccess(eq(8L), eq(code), any());
        } else {
            verify(groupMessages).selectCompletedListByConversationId(8L);
            verify(groups).incrementShareAccess(eq(8L), eq(code), any());
        }
        verify(connection).rollback(); verify(connection, never()).commit(); verifyNoInteractions(identities, client, audit);
    }

    /** 原归属锁查询的受控成功结果。 */
    private void owner(AiChatMode mode) {
        if (mode == AiChatMode.SINGLE) {
            when(singles.selectByIdAndUserIdForUpdate(8L, 7L)).thenReturn(AiChatConversationDO.builder().id(8L).userId(7L).build());
        } else {
            when(groups.selectByIdAndUserIdForUpdate(8L, 7L)).thenReturn(AiGroupChatConversationDO.builder().id(8L).userId(7L).build());
        }
    }

    /** 一个生成中助手；只允许把该占位CAS到STOPPED，避免宽泛stub掩盖错误状态。 */
    private void generating(AiChatMode mode, boolean cas) {
        owner(mode);
        if (mode == AiChatMode.SINGLE) {
            when(singleMessages.selectListByConversationId(8L, 7L)).thenReturn(Collections.singletonList(
                    AiChatMessageDO.builder().id(9L).role(ROLE_ASSISTANT).status(STATUS_GENERATING).build()));
            when(singleMessages.updateGeneratingMessage(argThat(row -> row != null && Long.valueOf(9L).equals(row.getId())
                    && Integer.valueOf(STATUS_STOPPED).equals(row.getStatus())))).thenReturn(cas);
        } else {
            when(groupMessages.selectListByConversationId(8L, 7L)).thenReturn(Collections.singletonList(
                    AiGroupChatMessageDO.builder().id(9L).role(ROLE_ASSISTANT).status(STATUS_GENERATING).build()));
            when(groupMessages.updateGeneratingMessage(argThat(row -> row != null && Long.valueOf(9L).equals(row.getId())
                    && Integer.valueOf(STATUS_STOPPED).equals(row.getStatus())))).thenReturn(cas);
        }
    }
}
