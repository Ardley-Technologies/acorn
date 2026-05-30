package com.ardley.acorn.jaxrs.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests that JaxRsRequestContext correctly adapts ContainerRequestContext
 * into the framework-agnostic RequestContext interface. This is the critical
 * bridge between JAX-RS and the Acorn extractor system.
 */
@ExtendWith(MockitoExtension.class)
class JaxRsRequestContextTest {

    @Mock ContainerRequestContext ctx;
    @Mock UriInfo uriInfo;

    private MultivaluedHashMap<String, String> pathParams;
    private MultivaluedHashMap<String, String> queryParams;

    @BeforeEach
    void setup() {
        pathParams = new MultivaluedHashMap<>();
        queryParams = new MultivaluedHashMap<>();
        lenient().when(ctx.getUriInfo()).thenReturn(uriInfo);
        lenient().when(uriInfo.getPathParameters()).thenReturn(pathParams);
        lenient().when(uriInfo.getQueryParameters()).thenReturn(queryParams);
    }

    @Nested
    @DisplayName("Path parameters")
    class PathParams {

        @Test
        @DisplayName("Returns present path parameter")
        void returnsPresentParam() {
            pathParams.add("id", "user-123");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.pathParam("id")).contains("user-123");
        }

        @Test
        @DisplayName("Returns empty for missing path parameter")
        void returnsEmptyForMissing() {
            var context = new JaxRsRequestContext(ctx);

            assertThat(context.pathParam("id")).isEmpty();
        }

        @Test
        @DisplayName("Returns first value when multiple values exist")
        void returnsFirstForMultiple() {
            pathParams.add("id", "first");
            pathParams.add("id", "second");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.pathParam("id")).contains("first");
        }
    }

    @Nested
    @DisplayName("Query parameters")
    class QueryParams {

        @Test
        @DisplayName("Returns present query parameter")
        void returnsPresentParam() {
            queryParams.add("filter", "active");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.queryParam("filter")).contains("active");
        }

        @Test
        @DisplayName("Returns empty for missing query parameter")
        void returnsEmptyForMissing() {
            var context = new JaxRsRequestContext(ctx);

            assertThat(context.queryParam("filter")).isEmpty();
        }

        @Test
        @DisplayName("queryParams returns all values for multi-valued parameter")
        void returnsAllValues() {
            queryParams.add("tag", "alpha");
            queryParams.add("tag", "beta");
            queryParams.add("tag", "gamma");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.queryParams("tag")).containsExactly("alpha", "beta", "gamma");
        }

        @Test
        @DisplayName("queryParams returns empty list for missing parameter")
        void returnsEmptyListForMissing() {
            var context = new JaxRsRequestContext(ctx);

            assertThat(context.queryParams("tag")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Headers")
    class Headers {

        @Test
        @DisplayName("Returns present header value")
        void returnsPresentHeader() {
            when(ctx.getHeaderString("Authorization")).thenReturn("Bearer token123");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.header("Authorization")).contains("Bearer token123");
        }

        @Test
        @DisplayName("Returns empty for missing header")
        void returnsEmptyForMissing() {
            when(ctx.getHeaderString("X-Custom")).thenReturn(null);

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.header("X-Custom")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Request metadata")
    class Metadata {

        @Test
        @DisplayName("Returns request path")
        void returnsPath() {
            when(uriInfo.getPath()).thenReturn("users/u-123/documents");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.path()).isEqualTo("users/u-123/documents");
        }

        @Test
        @DisplayName("Returns HTTP method")
        void returnsMethod() {
            when(ctx.getMethod()).thenReturn("DELETE");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.method()).isEqualTo("DELETE");
        }
    }

    @Nested
    @DisplayName("Request properties")
    class Properties {

        @Test
        @DisplayName("Returns present property")
        void returnsPresentProperty() {
            when(ctx.getProperty("acorn.principal")).thenReturn("some-value");

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.property("acorn.principal")).contains("some-value");
        }

        @Test
        @DisplayName("Returns empty for missing property")
        void returnsEmptyForMissing() {
            when(ctx.getProperty("missing")).thenReturn(null);

            var context = new JaxRsRequestContext(ctx);

            assertThat(context.property("missing")).isEmpty();
        }
    }
}
