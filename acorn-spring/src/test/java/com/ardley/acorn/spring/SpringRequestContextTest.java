package com.ardley.acorn.spring;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests SpringRequestContext's adaptation of HttpServletRequest to the
 * framework-agnostic RequestContext interface.
 */
@ExtendWith(MockitoExtension.class)
class SpringRequestContextTest {

    @Mock HttpServletRequest request;

    @BeforeEach
    void setup() {
        lenient().when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("id", "user-42", "orgId", "org-1"));
    }

    @Test
    @DisplayName("Path param resolved from URI_TEMPLATE_VARIABLES_ATTRIBUTE")
    void pathParamResolved() {
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.pathParam("id")).contains("user-42");
        assertThat(ctx.pathParam("orgId")).contains("org-1");
    }

    @Test
    @DisplayName("Missing path param returns empty")
    void missingPathParam() {
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.pathParam("missing")).isEmpty();
    }

    @Test
    @DisplayName("Query param resolved from request.getParameter")
    void queryParamResolved() {
        when(request.getParameter("filter")).thenReturn("active");
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.queryParam("filter")).contains("active");
    }

    @Test
    @DisplayName("Multi-valued query params resolved from getParameterValues")
    void multiValuedQueryParams() {
        when(request.getParameterValues("tags")).thenReturn(new String[]{"a", "b", "c"});
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.queryParams("tags")).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("Missing query params returns empty list")
    void missingQueryParams() {
        when(request.getParameterValues("nope")).thenReturn(null);
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.queryParams("nope")).isEmpty();
    }

    @Test
    @DisplayName("Header resolved from request.getHeader")
    void headerResolved() {
        when(request.getHeader("Authorization")).thenReturn("Bearer xyz");
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.header("Authorization")).contains("Bearer xyz");
    }

    @Test
    @DisplayName("Path returns requestURI")
    void pathReturnsUri() {
        when(request.getRequestURI()).thenReturn("/api/users/42");
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.path()).isEqualTo("/api/users/42");
    }

    @Test
    @DisplayName("Method returns HTTP method")
    void methodReturnsHttpMethod() {
        when(request.getMethod()).thenReturn("POST");
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.method()).isEqualTo("POST");
    }

    @Test
    @DisplayName("Property resolved from request attribute")
    void propertyResolved() {
        when(request.getAttribute("custom.key")).thenReturn("custom-value");
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.property("custom.key")).contains("custom-value");
    }

    @Test
    @DisplayName("No path variables attribute returns empty for all path params")
    void noPathVariablesAttributeReturnsEmpty() {
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(null);
        var ctx = new SpringRequestContext(request);
        assertThat(ctx.pathParam("id")).isEmpty();
    }
}
