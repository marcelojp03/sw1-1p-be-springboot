package sw1.p1.ai.application;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import sw1.p1.ai.domain.AiLog;
import sw1.p1.ai.domain.AiLogRepository;
import sw1.p1.ai.dto.AnalyzeBottlenecksRequest;
import sw1.p1.ai.dto.AnalyzeBottlenecksResponse;
import sw1.p1.ai.dto.SuggestFormFieldsRequest;
import sw1.p1.ai.dto.SuggestFormFieldsResponse;
import sw1.p1.ai.dto.SuggestWorkflowRequest;
import sw1.p1.ai.dto.SuggestWorkflowResponse;
import sw1.p1.auth.domain.UserRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AiIntegrationService {

    private final WebClient fastapiWebClient;
    private final AiLogRepository aiLogRepository;
    private final UserRepository userRepository;

    public SuggestWorkflowResponse suggestWorkflow(SuggestWorkflowRequest request, String organizationId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            SuggestWorkflowResponse response = fastapiWebClient.post()
                    .uri("/api/ai/suggest-workflow")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SuggestWorkflowResponse.class)
                    .block();
            saveLog(userId, organizationId, null, "WORKFLOW_DESIGN", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, null, "WORKFLOW_DESIGN", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al consultar IA para sugerir workflow: " + ex.getMessage(), ex);
        }
    }

    public SuggestFormFieldsResponse suggestFormFields(SuggestFormFieldsRequest request, String organizationId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            SuggestFormFieldsResponse response = fastapiWebClient.post()
                    .uri("/api/ai/suggest-form-fields")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SuggestFormFieldsResponse.class)
                    .block();
            saveLog(userId, organizationId, null, "FORM_SUGGESTION", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, null, "FORM_SUGGESTION", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al consultar IA para sugerir campos: " + ex.getMessage(), ex);
        }
    }

    public AnalyzeBottlenecksResponse analyzeBottlenecks(AnalyzeBottlenecksRequest request, String organizationId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            AnalyzeBottlenecksResponse response = fastapiWebClient.post()
                    .uri("/api/ai/analyze-bottlenecks")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AnalyzeBottlenecksResponse.class)
                    .block();
            saveLog(userId, organizationId, null, "BOTTLENECK_ANALYSIS", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, null, "BOTTLENECK_ANALYSIS", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al consultar IA para análisis de cuellos de botella: " + ex.getMessage(), ex);
        }
    }

    // ──────────────── helpers ────────────────

    private String currentUserId() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .map(sw1.p1.auth.domain.User::getId)
                .orElse(null);
    }

    private void saveLog(String userId, String organizationId, String policyId,
                         String context, Object input, Object output,
                         long durationMs, boolean success, String errorMessage) {
        AiLog log = AiLog.builder()
                .userId(userId)
                .organizationId(organizationId)
                .policyId(policyId)
                .context(context)
                .input(input)
                .output(output)
                .durationMs(durationMs)
                .success(success)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();
        aiLogRepository.save(log);
    }
}
