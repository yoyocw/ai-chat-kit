package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiMessageOriginPort;
import io.github.yoyocw.aichatkit.module.ai.contract.config.*;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.*;
import io.github.yoyocw.aichatkit.module.ai.contract.storage.*;
import io.github.yoyocw.aichatkit.module.ai.contract.audit.AiExecutionAuditPort;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.chat.vo.AiChatSendReqVO;
import org.junit.jupiter.api.Test;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionMode;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 模拟依赖失败，只证明发送编排不继续，不证明事务回滚。 */
class AiChatOriginFailureTest {
    @Test
    void sourceFailurePreventsReturningOrExecutingModelStream() throws Exception {
        String app = "0123456789abcdef0123456789abcdef";
        AiInvocationContext context = new AiInvocationContext("platform", "1", "7", "round");
        AiInvocationContextPort identity = mock(AiInvocationContextPort.class);
        AiHostExecutionScopePort scope = mock(AiHostExecutionScopePort.class);
        AiSingleChatPreparePort prepare = mock(AiSingleChatPreparePort.class);
        BailianClient client = mock(BailianClient.class);
        AiApplicationConfigPort configs = mock(AiApplicationConfigPort.class);
        AiChatBusinessContextService business = mock(AiChatBusinessContextService.class);
        AiExecutionAuditPort audit = mock(AiExecutionAuditPort.class);
        AiMessageOriginPort origin = mock(AiMessageOriginPort.class);
        AiApplicationConfig config = new AiApplicationConfig(app, null, Collections.emptyList());
        // 使用真实Spring事务生命周期与受控JDBC连接；不把本用例视为真实数据库回滚验证。
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        AiTransactionExecutor transactions = new AiTransactionExecutor(
                new DataSourceTransactionManager(source), AiTransactionMode.REUSE_HOST);
        AiChatExecutionService service = new AiChatExecutionService(null, identity, scope, null, prepare,
                client, configs, new BailianProperties(), business, null, null, audit, origin, transactions);
        when(identity.capture("7")).thenReturn(context);
        when(configs.load(context, AiChatMode.SINGLE)).thenReturn(config);
        when(client.isConfigured(app)).thenReturn(true);
        when(prepare.prepare(any())).thenReturn(new AiSingleChatPreparedTurn(8L, 9L, null, false, ""));
        RuntimeException failure = new IllegalStateException("simulated source failure");
        doThrow(failure).when(origin).record(context, AiChatMode.SINGLE, 9L, app);
        AiChatSendReqVO request = new AiChatSendReqVO();
        request.setContent("question");
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.sendMessage(request, 7L)));
        verify(prepare).prepare(any());
        verify(client).isConfigured(app);
        verifyNoMoreInteractions(client);
        verifyNoInteractions(scope, audit);
        verify(business).getMcpAuthorization(config, context);
        verifyNoMoreInteractions(business);
    }
}
