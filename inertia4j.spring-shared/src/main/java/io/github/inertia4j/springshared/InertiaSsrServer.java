package io.github.inertia4j.springshared;

import io.github.inertia4j.core.HttpSsrGateway;
import io.github.inertia4j.core.SsrServerProcess;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.context.SmartLifecycle;

/**
 * Operates the Inertia server-side rendering server along with the application context: starts it on context start
 * and stops it on context shutdown when {@code inertia.ssr.process.enabled} is set, and logs a warning on startup
 * when it is unreachable and {@code inertia.ssr.check-on-startup} is set. Does nothing on startup while the Vite dev
 * server renders pages.
 * <p>
 * Runs in an early phase, so that the server is started before and stopped after the web server.
 */
public class InertiaSsrServer implements SmartLifecycle {
    /**
     * Lifecycle phase, earlier than the one of the web server.
     */
    public static final int Phase = SmartLifecycle.DEFAULT_PHASE - 4096;

    private static final Log logger = LogFactory.getLog(InertiaSsrServer.class);

    private final HttpSsrGateway gateway;
    private final @Nullable SsrServerProcess process;
    private final boolean checkOnStartup;
    private volatile boolean running = false;

    /**
     * @param gateway        gateway reaching the server-side rendering server.
     * @param process        process running the server, or {@code null} when it is run outside the application.
     * @param checkOnStartup whether to log a warning on startup when the server is unreachable.
     */
    public InertiaSsrServer(HttpSsrGateway gateway, @Nullable SsrServerProcess process, boolean checkOnStartup) {
        this.gateway = gateway;
        this.process = process;
        this.checkOnStartup = checkOnStartup;
    }

    /**
     * @return gateway reaching the server-side rendering server.
     */
    public HttpSsrGateway getGateway() {
        return gateway;
    }

    /**
     * @return process running the server, or {@code null} when it is run outside the application.
     */
    public @Nullable SsrServerProcess getProcess() {
        return process;
    }

    @Override
    public void start() {
        running = true;

        String hotUrl = gateway.getHotUrl();

        if (hotUrl != null) {
            logger.debug("Inertia SSR is served by the Vite dev server at " + hotUrl);
            return;
        }

        if (process != null) {
            try {
                process.start();
            } catch (IllegalStateException exception) {
                logger.warn("Unable to start the Inertia SSR server: " + exception.getMessage(), exception.getCause());
            }
        }

        if (checkOnStartup && !gateway.isHealthy()) {
            logger.warn("Inertia SSR server is not reachable at " + gateway.getUrl() + ", pages are rendered client-side");
        }
    }

    @Override
    public void stop() {
        if (process != null) {
            process.stop();
        }

        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Phase;
    }
}
