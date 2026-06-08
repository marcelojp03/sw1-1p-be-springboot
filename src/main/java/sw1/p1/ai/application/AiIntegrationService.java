package sw1.p1.ai.application;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import sw1.p1.ai.domain.AiLog;
import sw1.p1.ai.domain.AiLogRepository;
import sw1.p1.ai.dto.AnalyzeBottlenecksRequest;
import sw1.p1.ai.dto.AnalyzeBottlenecksResponse;
import sw1.p1.ai.dto.FillFormRequest;
import sw1.p1.ai.dto.FillFormResponse;
import sw1.p1.ai.dto.GenerateDiagramRequest;
import sw1.p1.ai.dto.GenerateDiagramResponse;
import sw1.p1.ai.dto.IdentifyPolicyRequest;
import sw1.p1.ai.dto.IdentifyPolicyResponse;
import sw1.p1.ai.dto.NlReportRequest;
import sw1.p1.ai.dto.NlReportResponse;
import sw1.p1.ai.dto.RoutingPredictRequest;
import sw1.p1.ai.dto.RoutingPredictResponse;
import sw1.p1.ai.dto.TranscribeAudioResponse;
import sw1.p1.ai.dto.SuggestFormFieldsRequest;
import sw1.p1.ai.dto.SuggestFormFieldsResponse;
import sw1.p1.ai.dto.SuggestWorkflowRequest;
import sw1.p1.ai.dto.SuggestWorkflowResponse;
import sw1.p1.auth.domain.UserRepository;
import sw1.p1.policyrequest.application.PolicyRequestService;
import sw1.p1.policyrequest.dto.CreatePolicyRequestCommand;

import java.io.IOException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AiIntegrationService {

    private static final double POLICY_CONFIDENCE_THRESHOLD = 40.0;

    private final WebClient fastapiWebClient;
    private final AiLogRepository aiLogRepository;
    private final UserRepository userRepository;
    private final PolicyRequestService policyRequestService;
    private final org.springframework.web.client.RestTemplate restTemplate;
    private final org.springframework.core.env.Environment env;

    public SuggestWorkflowResponse suggestWorkflow(SuggestWorkflowRequest request, String organizationId, String policyId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            SuggestWorkflowResponse response = fastapiWebClient.post()
                    .uri("/api/ai/suggest-workflow")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SuggestWorkflowResponse.class)
                    .block();
            saveLog(userId, organizationId, policyId, "WORKFLOW_DESIGN", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, policyId, "WORKFLOW_DESIGN", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al consultar IA para sugerir workflow: " + ex.getMessage(), ex);
        }
    }

    public SuggestFormFieldsResponse suggestFormFields(SuggestFormFieldsRequest request, String organizationId, String policyId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            SuggestFormFieldsResponse response = fastapiWebClient.post()
                    .uri("/api/ai/suggest-form-fields")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(SuggestFormFieldsResponse.class)
                    .block();
            saveLog(userId, organizationId, policyId, "FORM_SUGGESTION", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, policyId, "FORM_SUGGESTION", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al consultar IA para sugerir campos: " + ex.getMessage(), ex);
        }
    }

    public AnalyzeBottlenecksResponse analyzeBottlenecks(AnalyzeBottlenecksRequest request, String organizationId, String policyId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            AnalyzeBottlenecksResponse response = fastapiWebClient.post()
                    .uri("/api/ai/analyze-bottlenecks")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AnalyzeBottlenecksResponse.class)
                    .block();
            saveLog(userId, organizationId, policyId, "BOTTLENECK_ANALYSIS", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, policyId, "BOTTLENECK_ANALYSIS", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al consultar IA para análisis de cuellos de botella: " + ex.getMessage(), ex);
        }
    }

    public GenerateDiagramResponse generateDiagram(GenerateDiagramRequest request, String organizationId, String policyId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            GenerateDiagramResponse response = fastapiWebClient.post()
                    .uri("/api/ai/generate-diagram")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GenerateDiagramResponse.class)
                    .block();
            saveLog(userId, organizationId, policyId, "DIAGRAM_GENERATION", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, policyId, "DIAGRAM_GENERATION", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al consultar IA para generar diagrama: " + ex.getMessage(), ex);
        }
    }

    // ──────────────── Ciclo 2 ────────────────

    /**
     * Identifica la política adecuada para el texto dado.
     * Si confidence < 40%, crea un PolicyRequest(PENDING_REVIEW) automáticamente.
     */
    public IdentifyPolicyResponse identifyPolicy(IdentifyPolicyRequest request, String organizationId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            IdentifyPolicyResponse response = fastapiWebClient.post()
                    .uri("/api/ai/identify-policy")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(IdentifyPolicyResponse.class)
                    .block();

            if (response != null
                    && (response.confidence() == null || response.confidence() < POLICY_CONFIDENCE_THRESHOLD)) {
                policyRequestService.create(new CreatePolicyRequestCommand(
                        organizationId,
                        request.text(),
                        response.policyKey(),
                        response.confidence(),
                        userId
                ));
            }

            saveLog(userId, organizationId, null, "IDENTIFY_POLICY", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, null, "IDENTIFY_POLICY", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al identificar política: " + ex.getMessage(), ex);
        }
    }

    public FillFormResponse fillForm(FillFormRequest request, String organizationId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            FillFormResponse response = fastapiWebClient.post()
                    .uri("/api/ai/fill-form")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(FillFormResponse.class)
                    .block();
            saveLog(userId, organizationId, null, "FILL_FORM", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, null, "FILL_FORM", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al completar formulario con IA: " + ex.getMessage(), ex);
        }
    }

    public NlReportResponse nlReport(NlReportRequest request) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            NlReportResponse response = fastapiWebClient.post()
                    .uri("/api/ai/nl-report")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NlReportResponse.class)
                    .block();
            saveLog(userId, request.organizationId(), null, "NL_REPORT", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, request.organizationId(), null, "NL_REPORT", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al generar reporte NL: " + ex.getMessage(), ex);
        }
    }

    public RoutingPredictResponse routingPredict(RoutingPredictRequest request, String organizationId) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            RoutingPredictResponse response = fastapiWebClient.post()
                    .uri("/api/ai/routing-predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(RoutingPredictResponse.class)
                    .block();
            saveLog(userId, organizationId, request.procedureId(), "ROUTING_PREDICT", request, response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (WebClientResponseException ex) {
            saveLog(userId, organizationId, request.procedureId(), "ROUTING_PREDICT", request, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al predecir enrutamiento: " + ex.getMessage(), ex);
        }
    }

    // ──────────────── helpers ────────────────

    public TranscribeAudioResponse transcribeAudio(MultipartFile file) {
        long start = System.currentTimeMillis();
        String userId = currentUserId();
        try {
            String fastapiBaseUrl = env.getProperty("fastapi.base-url", "http://localhost:8001");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override public String getFilename() { return file.getOriginalFilename(); }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(resource, headers));
            HttpHeaders reqHeaders = new HttpHeaders();
            reqHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
            ResponseEntity<TranscribeAudioResponse> resp = restTemplate.postForEntity(
                    fastapiBaseUrl + "/api/ai/transcribe-audio",
                    new HttpEntity<>(body, reqHeaders),
                    TranscribeAudioResponse.class
            );
            TranscribeAudioResponse response = resp.getBody();
            saveLog(userId, null, null, "TRANSCRIBE_AUDIO", file.getOriginalFilename(), response,
                    System.currentTimeMillis() - start, true, null);
            return response;
        } catch (IOException ex) {
            saveLog(userId, null, null, "TRANSCRIBE_AUDIO", null, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al leer archivo de audio: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            saveLog(userId, null, null, "TRANSCRIBE_AUDIO", null, null,
                    System.currentTimeMillis() - start, false, ex.getMessage());
            throw new RuntimeException("Error al transcribir audio: " + ex.getMessage(), ex);
        }
    }

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

    public org.springframework.data.domain.Page<AiLog> getRoutingPredictions(
            String organizationId, int page, int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                page, size,
                org.springframework.data.domain.Sort.by("createdAt").descending());
        return aiLogRepository.findByOrganizationIdAndContext(organizationId, "ROUTING_PREDICT", pageable);
    }
}
