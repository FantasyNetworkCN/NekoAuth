package neko.fantasy.auth.service;

import neko.fantasy.auth.domain.AuditLog;
import neko.fantasy.auth.dto.ApiResponses;
import neko.fantasy.auth.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(Long actorId, String actorUsername, String action, Long targetId,
                       String targetUsername, String details, String ipAddress) {
        repository.save(new AuditLog(actorId, actorUsername, action, targetId, targetUsername,
                details == null ? "" : details, ipAddress));
    }

    @Transactional(readOnly = true)
    public ApiResponses.Page<ApiResponses.AuditLog> list(String query, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Page<AuditLog> logs = normalized.isEmpty()
                ? repository.findAll(pageable)
                : repository.search(normalized, pageable);
        return new ApiResponses.Page<>(logs.getContent().stream().map(ApiResponses.AuditLog::from).toList(),
                logs.getNumber(), logs.getSize(), logs.getTotalElements(), logs.getTotalPages());
    }
}
