package neko.fantasy.auth.setup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SetupAccessFilter extends OncePerRequestFilter {

    private final SetupService setupService;

    public SetupAccessFilter(SetupService setupService) {
        this.setupService = setupService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!isSetupPath(path) || !setupService.isComplete()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (path.startsWith("/api/")) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"timestamp":"%s","status":404,"code":"NOT_FOUND","message":"请求的资源不存在"}
                    """.formatted(Instant.now()));
        } else {
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("Not Found");
        }
    }

    private static boolean isSetupPath(String path) {
        return path.equals("/setup")
                || path.startsWith("/setup/")
                || path.equals("/api/setup")
                || path.startsWith("/api/setup/");
    }
}
