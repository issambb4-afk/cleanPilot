package com.cleanpilot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Robust subprocess runner with non-blocking stream draining, native codepage decoding,
 * and guaranteed timeout enforcement to prevent pipe buffer deadlocks.
 */
public final class ProcessExecutionUtils {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutionUtils.class);

    private ProcessExecutionUtils() {}

    public record ProcessResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean timedOut
    ) {
        public boolean isSuccess() {
            return !timedOut && exitCode == 0;
        }

        public String getOutput() {
            return stdout != null && !stdout.isBlank() ? stdout : (stderr != null ? stderr : "");
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    /**
     * Resolves the native console charset on Windows (OEM codepage), falling back to UTF-8.
     */
    public static Charset getNativeCharset() {
        String nativeEncoding = System.getProperty("native.encoding");
        if (nativeEncoding != null && !nativeEncoding.isBlank()) {
            try {
                return Charset.forName(nativeEncoding);
            } catch (Exception ignored) {}
        }
        return Charset.defaultCharset();
    }

    /**
     * Executes an external process, asynchronously draining stdout and stderr to prevent
     * OS pipe buffer deadlocks, and terminates the process forcibly if timeout is exceeded.
     *
     * @param pb ProcessBuilder configuration
     * @param timeoutSeconds Maximum duration to await process completion
     * @return ProcessResult with output and exit status
     */
    public static ProcessResult execute(ProcessBuilder pb, long timeoutSeconds) {
        return execute(pb, timeoutSeconds, getNativeCharset());
    }

    /**
     * Executes with explicit Charset for stream decoding.
     */
    public static ProcessResult execute(ProcessBuilder pb, long timeoutSeconds, Charset charset) {
        StringBuilder stdoutBuilder = new StringBuilder();
        StringBuilder stderrBuilder = new StringBuilder();
        boolean timedOut = false;
        int exitCode = -1;

        try {
            Process process = pb.start();

            // Asynchronously drain stdout and stderr using virtual threads
            Thread stdoutThread = Thread.ofVirtual().name("proc-stdout-drainer").start(() -> {
                drainStream(process.getInputStream(), stdoutBuilder, charset);
            });

            Thread stderrThread = null;
            if (!pb.redirectErrorStream()) {
                stderrThread = Thread.ofVirtual().name("proc-stderr-drainer").start(() -> {
                    drainStream(process.getErrorStream(), stderrBuilder, charset);
                });
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                timedOut = true;
                log.warn("Process [{}] timed out after {} seconds. Terminating forcibly.",
                        String.join(" ", pb.command()), timeoutSeconds);
                try {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                } catch (Exception ignored) {}
                process.destroyForcibly();
            } else {
                exitCode = process.exitValue();
            }

            // Await stream completion
            stdoutThread.join(2000);
            if (stderrThread != null) {
                stderrThread.join(2000);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Process execution interrupted for: {}", String.join(" ", pb.command()));
            timedOut = true;
        } catch (Exception e) {
            log.error("Failed executing process [{}]: {}", String.join(" ", pb.command()), e.getMessage(), e);
        }

        return new ProcessResult(exitCode, stdoutBuilder.toString(), stderrBuilder.toString(), timedOut);
    }

    private static void drainStream(InputStream in, StringBuilder out, Charset charset) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append(System.lineSeparator());
            }
        } catch (Exception ignored) {
            // Stream closed or process destroyed
        }
    }
}
