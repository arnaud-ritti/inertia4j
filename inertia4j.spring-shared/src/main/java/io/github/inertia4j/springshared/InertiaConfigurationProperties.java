package io.github.inertia4j.springshared;

import io.github.inertia4j.core.HttpSsrGateway;
import io.github.inertia4j.core.InertiaRenderer;
import io.github.inertia4j.core.PropertyNaming;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Inertia4j integration with Spring Boot.
 * Properties are prefixed with `inertia`.
 * <p>
 * Example `application.properties`:
 * <pre>
 * inertia.template-path=templates/my-app.html
 * inertia.encrypt-history=true
 * inertia.ssr.enabled=true
 * inertia.ssr.url=http://127.0.0.1:13714
 * inertia.property-naming=snake
 * </pre>
 */
@ConfigurationProperties(prefix = "inertia")
public class InertiaConfigurationProperties {
    /**
     * The classpath path to the main HTML template file used by the default {@link io.github.inertia4j.core.SimpleTemplateRenderer}.
     */
    private String templatePath = "templates/app.html";

    /**
     * Default value for the encryptHistory flag, determining whether browser history state should be encrypted.
     */
    private boolean encryptHistory = false;

    /**
     * Id of the element the client-side application is mounted on.
     */
    private String rootId = InertiaRenderer.DefaultRootId;

    /**
     * Whether page objects list the top-level keys of shared props in {@code sharedProps}.
     */
    private boolean exposeSharedPropKeys = true;

    /**
     * Naming strategy used when converting typed props objects, also applied to the objects inside props by the default
     * {@link io.github.inertia4j.spi.PageObjectSerializer} ({@code camel} or {@code snake}).
     */
    private PropertyNaming propertyNaming = PropertyNaming.Camel;

    /**
     * Server-side rendering settings.
     */
    private final Ssr ssr = new Ssr();

    public String getTemplatePath() {
        return templatePath;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public boolean isEncryptHistory() {
        return encryptHistory;
    }

    public void setEncryptHistory(boolean encryptHistory) {
        this.encryptHistory = encryptHistory;
    }

    public String getRootId() {
        return rootId;
    }

    public void setRootId(String rootId) {
        this.rootId = rootId;
    }

    public boolean isExposeSharedPropKeys() {
        return exposeSharedPropKeys;
    }

    public void setExposeSharedPropKeys(boolean exposeSharedPropKeys) {
        this.exposeSharedPropKeys = exposeSharedPropKeys;
    }

    /**
     * @return naming strategy used when converting typed props objects.
     */
    public PropertyNaming getPropertyNaming() {
        return propertyNaming;
    }

    /**
     * @param propertyNaming naming strategy used when converting typed props objects.
     */
    public void setPropertyNaming(PropertyNaming propertyNaming) {
        this.propertyNaming = propertyNaming;
    }

    public Ssr getSsr() {
        return ssr;
    }

    /**
     * Server-side rendering settings, prefixed with `inertia.ssr`.
     */
    public static class Ssr {
        /**
         * Whether full page loads are server-side rendered.
         */
        private boolean enabled = false;

        /**
         * URL of the server-side rendering server.
         */
        private String url = HttpSsrGateway.DefaultUrl;

        /**
         * URL of the Vite development server, used instead of the server-side rendering server when set.
         */
        private String hotUrl;

        /**
         * Timeout of render requests. No timeout is applied when unset.
         */
        private Duration timeout;

        /**
         * Whether failed renders throw instead of falling back to client-side rendering.
         */
        private boolean throwOnError = false;

        /**
         * Request paths never server-side rendered; {@code *} matches any sequence of characters.
         */
        private List<String> except = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getHotUrl() {
            return hotUrl;
        }

        public void setHotUrl(String hotUrl) {
            this.hotUrl = hotUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public boolean isThrowOnError() {
            return throwOnError;
        }

        public void setThrowOnError(boolean throwOnError) {
            this.throwOnError = throwOnError;
        }

        public List<String> getExcept() {
            return except;
        }

        public void setExcept(List<String> except) {
            this.except = except;
        }
    }
}
