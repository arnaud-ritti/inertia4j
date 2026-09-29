package dev.arkoder.inertia4j.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs the Inertia server-side rendering server as a child process, like the {@code inertia:start-ssr} and
 * {@code inertia:stop-ssr} commands of the Laravel adapter.
 * <p>
 * The server is started with {@code <runtime> [arguments...] <bundle>}, inheriting the standard output and error of
 * the application, and stopped through its {@code /shutdown} endpoint, destroying the process when it does not exit.
 * Thread-safe.
 *
 * @see <a href="https://inertiajs.com/docs/v3/advanced/server-side-rendering#running-the-ssr-server">Running the SSR server</a>
 */
public class SsrServerProcess {
    /**
     * Program running the bundle when none is specified.
     */
    public static final String DefaultRuntime = "node";

    /**
     * Time given to the server to become healthy when none is specified.
     */
    public static final Duration DefaultStartupTimeout = Duration.ofSeconds(10);

    /**
     * Time given to the server to exit after a shutdown request when none is specified.
     */
    public static final Duration DefaultShutdownTimeout = Duration.ofSeconds(5);

    private static final Duration healthPollInterval = Duration.ofMillis(100);
    private static final System.Logger logger = System.getLogger(SsrServerProcess.class.getName());

    private final HttpSsrGateway gateway;
    private final Path bundle;
    private final String runtime;
    private final List<String> arguments;
    private final Path workingDirectory;
    private final Map<String, String> environment;
    private final Duration startupTimeout;
    private final Duration shutdownTimeout;
    private Process process;

    private SsrServerProcess(Builder builder) {
        this.gateway = builder.gateway;
        this.bundle = builder.bundle;
        this.runtime = builder.runtime;
        this.arguments = List.copyOf(builder.arguments);
        this.workingDirectory = builder.workingDirectory;
        this.environment = Map.copyOf(builder.environment);
        this.startupTimeout = builder.startupTimeout;
        this.shutdownTimeout = builder.shutdownTimeout;
    }

    /**
     * Creates a builder of SsrServerProcess.
     *
     * @param gateway gateway checking the health of the server and shutting it down.
     * @param bundle  server-side rendering bundle run by the runtime.
     * @return a new builder.
     */
    public static Builder builder(HttpSsrGateway gateway, Path bundle) {
        return new Builder(gateway, bundle);
    }

    /**
     * @return the command starting the server.
     */
    public List<String> command() {
        List<String> command = new ArrayList<>();
        command.add(runtime);
        command.addAll(arguments);
        command.add(bundle.toString());

        return command;
    }

    /**
     * Starts the server, then waits until it is healthy or the startup timeout elapses. Does nothing when this
     * process is running, or when a server already answers at the gateway URL.
     *
     * @throws IllegalStateException if the bundle is missing, the runtime cannot be started, or the server exits
     *                               before becoming healthy.
     */
    public synchronized void start() {
        if (isRunning()) {
            return;
        }

        if (gateway.isHealthy()) {
            logger.log(System.Logger.Level.INFO, "SSR server already running at " + gateway.getUrl() + ", not starting another one");
            return;
        }

        if (!Files.isRegularFile(bundle)) {
            throw new IllegalStateException("SSR bundle not found at " + bundle.toAbsolutePath());
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command()).inheritIO();

        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        processBuilder.environment().putAll(environment);

        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start the SSR server with " + String.join(" ", command()), exception);
        }

        awaitHealthy();
    }

    /**
     * Stops the server started by {@link #start()}: requests a shutdown, then destroys the process if it is still
     * running after the shutdown timeout. Does nothing when this process is not running.
     */
    public synchronized void stop() {
        Process current = process;
        process = null;

        if (current == null || !current.isAlive()) {
            return;
        }

        gateway.shutdown();

        if (waitFor(current, shutdownTimeout)) {
            return;
        }

        current.descendants().forEach(ProcessHandle::destroy);
        current.destroy();

        if (waitFor(current, shutdownTimeout)) {
            return;
        }

        current.descendants().forEach(ProcessHandle::destroyForcibly);
        current.destroyForcibly();
    }

    /**
     * @return whether the process started by {@link #start()} is running.
     */
    public synchronized boolean isRunning() {
        return process != null && process.isAlive();
    }

    private void awaitHealthy() {
        Instant deadline = Instant.now().plus(startupTimeout);

        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                process = null;

                throw new IllegalStateException("SSR server exited with code " + exitCode + " while starting");
            }

            if (gateway.isHealthy()) {
                return;
            }

            waitFor(process, healthPollInterval);
        }

        logger.log(
            System.Logger.Level.WARNING,
            "SSR server started with " + String.join(" ", command()) + " is not healthy at " + gateway.getUrl()
                + " after " + startupTimeout.toMillis() + "ms"
        );
    }

    private static boolean waitFor(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Builder of {@link SsrServerProcess}.
     */
    public static class Builder {
        private final HttpSsrGateway gateway;
        private final Path bundle;
        private String runtime = DefaultRuntime;
        private List<String> arguments = List.of();
        private Path workingDirectory = null;
        private Map<String, String> environment = Map.of();
        private Duration startupTimeout = DefaultStartupTimeout;
        private Duration shutdownTimeout = DefaultShutdownTimeout;

        private Builder(HttpSsrGateway gateway, Path bundle) {
            this.gateway = gateway;
            this.bundle = bundle;
        }

        /**
         * Sets the program running the bundle, such as {@code node}, {@code bun} or an absolute path. Defaults to
         * {@value SsrServerProcess#DefaultRuntime}.
         *
         * @param runtime runtime executable.
         * @return this builder.
         */
        public Builder runtime(String runtime) {
            this.runtime = runtime;
            return this;
        }

        /**
         * Sets the arguments passed to the runtime before the bundle, such as {@code --enable-source-maps}.
         *
         * @param arguments runtime arguments.
         * @return this builder.
         */
        public Builder arguments(List<String> arguments) {
            this.arguments = arguments;
            return this;
        }

        /**
         * Sets the working directory of the process. Defaults to the working directory of the application.
         *
         * @param workingDirectory working directory, or {@code null} for the application one.
         * @return this builder.
         */
        public Builder workingDirectory(Path workingDirectory) {
            this.workingDirectory = workingDirectory;
            return this;
        }

        /**
         * Sets environment variables added to the environment of the application for the process.
         *
         * @param environment environment variables.
         * @return this builder.
         */
        public Builder environment(Map<String, String> environment) {
            this.environment = environment;
            return this;
        }

        /**
         * Sets how long {@link SsrServerProcess#start()} waits for the server to become healthy. Defaults to
         * {@link SsrServerProcess#DefaultStartupTimeout}.
         *
         * @param startupTimeout startup timeout.
         * @return this builder.
         */
        public Builder startupTimeout(Duration startupTimeout) {
            this.startupTimeout = startupTimeout;
            return this;
        }

        /**
         * Sets how long {@link SsrServerProcess#stop()} waits for the server to exit before destroying it. Defaults to
         * {@link SsrServerProcess#DefaultShutdownTimeout}.
         *
         * @param shutdownTimeout shutdown timeout.
         * @return this builder.
         */
        public Builder shutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
            return this;
        }

        /**
         * Builds the process, without starting it.
         *
         * @return the process.
         */
        public SsrServerProcess build() {
            return new SsrServerProcess(this);
        }
    }
}
