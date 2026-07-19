package sw1.p1.dashboard.api;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sw1.p1.config.WebSocketSessionTracker;
import sw1.p1.dashboard.application.DashboardService;
import sw1.p1.dashboard.dto.AverageTimeByNodeResponse;
import sw1.p1.dashboard.dto.DashboardSummaryResponse;
import sw1.p1.dashboard.dto.ProceduresByStatusResponse;
import sw1.p1.dashboard.dto.TaskOverdueResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN')")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final WebSocketSessionTracker sessionTracker;

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@RequestParam String organizationId) {
        return dashboardService.getSummary(organizationId);
    }

    @GetMapping("/procedures-by-status")
    public ProceduresByStatusResponse proceduresByStatus(
            @RequestParam String organizationId,
            @RequestParam(required = false) String policyId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return dashboardService.getProceduresByStatus(organizationId, policyId, startDate, endDate);
    }

    @GetMapping("/tasks-overdue")
    public Page<TaskOverdueResponse> tasksOverdue(
            @RequestParam String organizationId,
            @RequestParam(required = false) String departmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return dashboardService.getTasksOverdue(organizationId, departmentId, page, size);
    }

    @GetMapping("/average-time-by-node")
    public List<AverageTimeByNodeResponse> averageTimeByNode(
            @RequestParam String organizationId,
            @RequestParam(required = false) String policyId
    ) {
        return dashboardService.getAverageTimeByNode(organizationId, policyId);
    }

    @GetMapping("/connected-users")
    public Map<String, Integer> connectedUsers() {
        return Map.of("count", sessionTracker.getConnectedCount());
    }
}
