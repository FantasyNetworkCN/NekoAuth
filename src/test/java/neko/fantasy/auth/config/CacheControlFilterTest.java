package neko.fantasy.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CacheControlFilterTest {

    private final CacheControlFilter filter = new CacheControlFilter();

    @Test
    void disablesCachingForApplicationResponses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
    }

    @Test
    void leavesAvatarCacheHeadersToAvatarController() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/avatars/42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isNull();
    }
}
