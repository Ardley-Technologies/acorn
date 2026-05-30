package com.ardley.acorn.cdi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests the CDI module's servlet-based RequestContext adapter.
 * Path parameters are stored as request attributes with a known prefix.
 */
@ExtendWith(MockitoExtension.class)
class ServletRequestContextTest {

    @Mock HttpServletRequest request;

    @Test
    @DisplayName("Path param resolved from request attribute with acorn prefix")
    void pathParamFromAttribute() {
        when(request.getAttribute("acorn.pathparam.id")).thenReturn("u-42");

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.pathParam("id")).contains("u-42");
    }

    @Test
    @DisplayName("Missing path param returns empty")
    void missingPathParam() {
        when(request.getAttribute("acorn.pathparam.missing")).thenReturn(null);

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.pathParam("missing")).isEmpty();
    }

    @Test
    @DisplayName("Query param resolved from request.getParameter")
    void queryParamResolved() {
        when(request.getParameter("status")).thenReturn("active");

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.queryParam("status")).contains("active");
    }

    @Test
    @DisplayName("Multi-valued query params resolved")
    void multiValuedQueryParams() {
        when(request.getParameterValues("tag")).thenReturn(new String[]{"x", "y"});

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.queryParams("tag")).containsExactly("x", "y");
    }

    @Test
    @DisplayName("Missing query params returns empty list")
    void missingQueryParams() {
        when(request.getParameterValues("absent")).thenReturn(null);

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.queryParams("absent")).isEmpty();
    }

    @Test
    @DisplayName("Header resolved from request.getHeader")
    void headerResolved() {
        when(request.getHeader("X-Tenant")).thenReturn("tenant-1");

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.header("X-Tenant")).contains("tenant-1");
    }

    @Test
    @DisplayName("Path returns requestURI")
    void pathReturnsUri() {
        when(request.getRequestURI()).thenReturn("/api/documents/d-1");

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.path()).isEqualTo("/api/documents/d-1");
    }

    @Test
    @DisplayName("Method returns HTTP method")
    void methodReturnsHttpMethod() {
        when(request.getMethod()).thenReturn("DELETE");

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.method()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("Property resolved from request attribute")
    void propertyResolved() {
        when(request.getAttribute("acorn.principal")).thenReturn("principal-obj");

        var ctx = new ServletRequestContext(request);

        assertThat(ctx.property("acorn.principal")).contains("principal-obj");
    }

    @Test
    @DisplayName("setPathParam utility stores with correct prefix")
    void setPathParamUtility() {
        ServletRequestContext.setPathParam(request, "docId", "d-99");

        org.mockito.Mockito.verify(request).setAttribute("acorn.pathparam.docId", "d-99");
    }
}
