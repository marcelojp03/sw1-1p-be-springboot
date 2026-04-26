package sw1.p1.dashboard.dto;

import java.util.List;

public record ProceduresByStatusResponse(
        List<StatusCount> items
) {
    public record StatusCount(String status, long count) {}
}
