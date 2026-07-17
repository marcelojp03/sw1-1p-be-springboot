package sw1.p1.ai.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import sw1.p1.ai.domain.AiLog;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sw1.p1.ai.application.AiIntegrationService;
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

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN')")
public class AiController {

    private final AiIntegrationService aiIntegrationService;

    @PostMapping("/suggest-workflow")
    public SuggestWorkflowResponse suggestWorkflow(
            @RequestBody SuggestWorkflowRequest request,
            @RequestParam String organizationId,
            @RequestParam(required = false) String policyId
    ) {
        return aiIntegrationService.suggestWorkflow(request, organizationId, policyId);
    }

    @PostMapping("/suggest-form-fields")
    public SuggestFormFieldsResponse suggestFormFields(
            @RequestBody SuggestFormFieldsRequest request,
            @RequestParam String organizationId,
            @RequestParam(required = false) String policyId
    ) {
        return aiIntegrationService.suggestFormFields(request, organizationId, policyId);
    }

    @PostMapping("/analyze-bottlenecks")
    public AnalyzeBottlenecksResponse analyzeBottlenecks(
            @RequestBody AnalyzeBottlenecksRequest request,
            @RequestParam String organizationId,
            @RequestParam(required = false) String policyId
    ) {
        return aiIntegrationService.analyzeBottlenecks(request, organizationId, policyId);
    }

    @PostMapping("/generate-diagram")
    public GenerateDiagramResponse generateDiagram(
            @RequestBody GenerateDiagramRequest request,
            @RequestParam String organizationId,
            @RequestParam(required = false) String policyId
    ) {
        return aiIntegrationService.generateDiagram(request, organizationId, policyId);
    }

    // ──────── Ciclo 2 ────────

    @PostMapping("/identify-policy")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'CLIENT')")
    public IdentifyPolicyResponse identifyPolicy(
            @RequestBody IdentifyPolicyRequest request,
            @RequestParam String organizationId
    ) {
        return aiIntegrationService.identifyPolicy(request, organizationId);
    }

    @PostMapping("/fill-form")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'CLIENT')")
    public FillFormResponse fillForm(
            @RequestBody FillFormRequest request,
            @RequestParam String organizationId
    ) {
        return aiIntegrationService.fillForm(request, organizationId);
    }

    @PostMapping("/nl-report")
    public NlReportResponse nlReport(@RequestBody NlReportRequest request) {
        return aiIntegrationService.nlReport(request);
    }

    @PostMapping("/routing-predict")
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER')")
    public RoutingPredictResponse routingPredict(
            @RequestBody RoutingPredictRequest request,
            @RequestParam String organizationId
    ) {
        return aiIntegrationService.routingPredict(request, organizationId);
    }

    @PostMapping(value = "/transcribe-audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'OFFICER', 'CLIENT')")
    public TranscribeAudioResponse transcribeAudio(@RequestPart("file") MultipartFile file) {
        return aiIntegrationService.transcribeAudio(file);
    }

    @GetMapping("/routing-logs")
    public org.springframework.data.domain.Page<AiLog> getRoutingLogs(
            @RequestParam String organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return aiIntegrationService.getRoutingPredictions(organizationId, page, size);
    }
}
