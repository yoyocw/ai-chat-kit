package io.github.yoyocw.aichatkit.module.ai.framework.bailian;
import io.github.yoyocw.aichatkit.module.ai.config.*;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_QUOTA_UNAVAILABLE;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.BAILIAN_TIMEOUT;
import static org.assertj.core.api.Assertions.*;
class BailianModelClientTest {
 @Test void cancellationRoutesToExistingTransportAndPolicyPreservesTimeout() {
  try(Fake transport=new Fake()) {
   BailianProperties properties=new BailianProperties();properties.setReadTimeoutSeconds(73);
   AiModelClient client=new BailianModelClient(transport,new BailianGroupOutputParser());
   AiExecutionPolicy policy=new AiExecutionPolicy(properties.getReadTimeoutSeconds()+30L);
   assertThat(policy.getStaleGenerationSeconds()).isEqualTo(103);
   client.cancel(AiChatMode.SINGLE,1L);client.cancel(AiChatMode.GROUP,2L);
   assertThat(transport.single).isEqualTo(1L);assertThat(transport.group).isEqualTo(2L);
  }
 }
 @Test void credentialsCannotBeBeanSerializedAndSafeErrorsDropCauses() throws Exception {
  AiModelRequest request=request(AiChatMode.SINGLE,"secret-token");
  assertThat(request.toString()).doesNotContain("secret-token");
  assertThat(java.beans.Introspector.getBeanInfo(AiModelRequest.class).getPropertyDescriptors()).extracting(java.beans.PropertyDescriptor::getName).doesNotContain("toolAuthorization");
  AiModelException safe=AiModelException.from(new RuntimeException("secret-token"));
  assertThat(safe.getCause()).isNull();assertThat(safe.toString()).doesNotContain("secret-token");
 }
 @Test void invalidGroupOutputIsNonRetryableWithoutUpstreamText() throws Exception {
  try(Fake transport=new Fake()) {
   transport.result=new BailianStreamResult();transport.result.setContent("secret-untrusted-output");
   AiModelClient client=new BailianModelClient(transport,new BailianGroupOutputParser());
   assertThatThrownBy(()->client.stream(request(AiChatMode.GROUP,null),e->{},()->true)).isInstanceOf(AiModelException.class)
    .hasMessageNotContaining("secret-untrusted-output").satisfies(ex->assertThat(((AiModelException)ex).isRetryable()).isFalse());
  }
 }
 @Test void singleRequestMapsProtocolEventsAndMetricsWithoutPuttingCredentialInPrompt() throws Exception {
  try(Fake transport = new Fake()) {
   transport.result = new BailianStreamResult();
   transport.result.setContent("answer");transport.result.setRequestId("request");
   transport.result.setInputTokens(5);transport.result.setSessionId("remote");
   AiModelRequest request = new AiModelRequest(AiChatMode.SINGLE, 1L, "app", "question", "session", true,
     "{\"fact\":1}", "history", "trace", new AiApplicationConfig("app", null, Collections.singletonList("tool")),
     "Bearer mcp_jwt_secret", Collections.emptyList());
   List<AiModelEvent> events = new ArrayList<>();
   AiModelResult result = new BailianModelClient(transport, new BailianGroupOutputParser())
     .stream(request, events::add, () -> true);
   assertThat(events).extracting(AiModelEvent::getType).containsExactly(AiModelEventType.PROGRESS, AiModelEventType.DELTA);
   assertThat(result.getSessionId()).isEqualTo("remote");
   assertThat(result.metrics(123).getInputTokens()).isEqualTo(5);
   assertThat(result.metrics(123).getOutputTokens()).isNull();
   assertThat(result.metrics(123).getTotalDurationMs()).isEqualTo(123);
   assertThat(AiEngineJson.toJsonString(transport.params.get("user_prompt_params"))).doesNotContain("mcp_jwt_secret");
   assertThat(AiEngineJson.toJsonString(transport.params)).contains("mcp_jwt_secret", "fact", "history");
   assertThat(AiEngineJson.toJsonString(request)).doesNotContain("mcp_jwt_secret");
  }
 }
 @Test void providerFailuresKeepSafeClassificationAndSessionRecoverySignal() throws Exception {
  try(Fake transport = new Fake()) {
   AiModelClient client = new BailianModelClient(transport, new BailianGroupOutputParser());
   transport.failure = new BailianCallException(BAILIAN_QUOTA_UNAVAILABLE, false);
   assertThatThrownBy(() -> client.stream(request(AiChatMode.SINGLE, null), e -> {}, () -> true))
    .isInstanceOf(AiModelException.class).satisfies(ex -> {
     assertThat(((AiModelException) ex).getErrorCode()).isEqualTo(BAILIAN_QUOTA_UNAVAILABLE);
     assertThat(((AiModelException) ex).isRetryable()).isFalse();assertThat(ex.getCause()).isNull();
    });
   transport.failure = new BailianSessionExpiredException("secret upstream detail");
   assertThatThrownBy(() -> client.stream(request(AiChatMode.SINGLE, null), e -> {}, () -> true))
    .isInstanceOf(AiModelSessionExpiredException.class).hasMessageNotContaining("secret upstream detail");
  }
 }
 @Test
 void rawTimeoutsBecomeRetryableNeutralFailuresWithoutLeakingCredentials() throws Exception {
  String credential = "Bearer mcp_jwt_timeout_secret";
  IOException[] failures = {
    new SocketTimeoutException("read timeout with " + credential),
    new InterruptedIOException("interrupted request with " + credential),
    new IOException("transport wrapper with " + credential, new SocketTimeoutException(credential))
  };
  try (Fake transport = new Fake()) {
   AiModelClient client = new BailianModelClient(transport, new BailianGroupOutputParser());
   for (IOException upstreamFailure : failures) {
    transport.failure = upstreamFailure;
    assertThatThrownBy(() -> client.stream(request(AiChatMode.SINGLE, credential), event -> {}, () -> true))
      .isExactlyInstanceOf(AiModelException.class)
      .hasMessage(BAILIAN_TIMEOUT.getMsg())
      .satisfies(failure -> {
       AiModelException safe = (AiModelException) failure;
       assertThat(safe.getErrorCode()).isEqualTo(BAILIAN_TIMEOUT);
       assertThat(safe.isRetryable()).isTrue();
       assertThat(safe.getCause()).isNull();
       assertThat(safe.getSuppressed()).isEmpty();
       assertThat(safe.toString()).doesNotContain(credential, "transport wrapper", "read timeout with");
      });
   }
  }
 }
 private AiModelRequest request(AiChatMode mode,String token){return new AiModelRequest(mode,1L,"app","question",null,null,"{}","history","trace",null,token,Collections.emptyList());}
 private static class Fake extends BailianClient {
  Long single,group;BailianStreamResult result;Map<String,Object> params;IOException failure;
  Fake(){super(new BailianProperties());}
  @Override public boolean cancel(Long id){single=id;return true;}
  @Override public boolean cancelGroupWorkflow(Long id){group=id;return true;}
  @Override public BailianStreamResult streamGroupWorkflow(Long id,String app,String prompt,String session,Map<String,Object> params,BooleanSupplier probe){return result;}
  @Override public BailianStreamResult stream(Long id,String app,String prompt,String session,Map<String,Object> params,
    Consumer<BailianStreamEvent> events,BooleanSupplier probe) throws IOException {
   if(failure != null)throw failure;
   this.params = params;events.accept(BailianStreamEvent.progress("reasoning", "processing", null));
   events.accept(BailianStreamEvent.delta("answer"));return result;
  }
 }
}
