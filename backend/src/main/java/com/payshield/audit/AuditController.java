package com.payshield.audit;

import com.payshield.common.PagedResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','FINANCIAL_AUDITOR','OPERATIONS_ANALYST','RISK_ANALYST')")
    public PagedResponse<AuditEventDto> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) AuditOutcome outcome,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        int clampedSize = Math.min(Math.max(size, 1), 100);
        return PagedResponse.from(auditService.search(action, resourceType, outcome, PageRequest.of(page, clampedSize)));
    }
}
