package sw1.p1.ai.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sw1.p1.ai.application.AiIntegrationService;
import sw1.p1.ai.dto.AnalyzeBottlenecksRequest;
import sw1.p1.ai.dto.AnalyzeBottlenecksResponse;
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
            @RequestParam String organizationId
    ) {
        return aiIntegrationService.suggestWorkflow(request, organizationId);
    }

    @PostMapping("/suggest-form-fields")
    public SuggestFormFieldsResponse suggestFormFields(
            @RequestBody SuggestFormFieldsRequest request,
            @RequestParam String organizationId
    ) {
        return aiIntegrationService.suggestFormFields(request, organizationId);
    }

    @PostMapping("/analyze-bottlenecks")
    public AnalyzeBottlenecksResponse analyzeBottlenecks(
            @RequestBody AnalyzeBottlenecksRequest request,
            @RequestParam String organizationId
    ) {
        return aiIntegrationService.analyzeBottlenecks(request, organizationId);
    }
}
