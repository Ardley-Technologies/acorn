package com.ardley.acorn.cdi;

import com.ardley.acorn.attribute.AttributeSource;
import com.ardley.acorn.attribute.Attributes;
import com.ardley.acorn.context.RequestContext;
import com.ardley.acorn.resource.ResourceExtractor;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.context.spi.CreationalContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests the CDI extractor resolver's bean lookup behavior.
 */
@ExtendWith(MockitoExtension.class)
class CdiExtractorResolverTest {

    @Mock BeanManager beanManager;
    @SuppressWarnings("rawtypes")
    @Mock Bean bean;
    @SuppressWarnings("rawtypes")
    @Mock CreationalContext creationalContext;

    @Test
    @DisplayName("Resolves registered CDI bean by class")
    @SuppressWarnings("unchecked")
    void resolvesRegisteredBean() {
        StubExtractor expected = new StubExtractor();

        when(beanManager.getBeans(StubExtractor.class)).thenReturn(Set.of(bean));
        when(beanManager.resolve(any())).thenReturn(bean);
        when(beanManager.createCreationalContext(bean)).thenReturn(creationalContext);
        when(beanManager.getReference(eq(bean), eq(StubExtractor.class), eq(creationalContext)))
                .thenReturn(expected);

        CdiExtractorResolver resolver = new CdiExtractorResolver(beanManager);
        ResourceExtractor<?> result = resolver.resolve(StubExtractor.class);

        assertThat(result).isSameAs(expected);
        assertThat(result.resourceType()).isEqualTo("stub");
    }

    @Test
    @DisplayName("Throws when no bean found for extractor class")
    void throwsWhenNoBeanFound() {
        when(beanManager.getBeans(StubExtractor.class)).thenReturn(Set.of());

        CdiExtractorResolver resolver = new CdiExtractorResolver(beanManager);

        assertThatThrownBy(() -> resolver.resolve(StubExtractor.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No CDI bean found")
                .hasMessageContaining("StubExtractor");
    }

    public static class StubExtractor implements ResourceExtractor<Object> {
        @Override public String resourceType() { return "stub"; }
        @Override public Optional<String> extractId(RequestContext context) { return Optional.empty(); }
        @Override public Object load(String resourceId, AttributeSource principal) { return null; }
        @Override public Attributes attributes(Object resource) { return Attributes.empty(); }
    }
}
