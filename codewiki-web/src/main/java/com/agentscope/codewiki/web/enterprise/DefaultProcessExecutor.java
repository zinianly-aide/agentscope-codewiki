package com.agentscope.codewiki.web.enterprise;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** ProcessBuilder based executor that never uses shell string execution. */
public class DefaultProcessExecutor implements ProcessExecutor {
    @Override
    public ProcessResult execute(List<String> command, Duration timeout, int outputLimitBytes)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getOutputStream().close();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> reader =
                executor.submit(
                        () -> readLimited(process.getInputStream(), output, outputLimitBytes));
        boolean timedOut = !process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (timedOut) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
        boolean truncated = false;
        try {
            truncated = reader.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            reader.cancel(true);
            truncated = true;
        } catch (java.util.concurrent.ExecutionException ex) {
            truncated = true;
        } finally {
            executor.shutdownNow();
        }
        int exitCode = timedOut ? -1 : process.exitValue();
        return new ProcessResult(
                exitCode, output.toString(StandardCharsets.UTF_8), timedOut, truncated);
    }

    private static boolean readLimited(InputStream input, ByteArrayOutputStream output, int limit)
            throws IOException {
        byte[] buffer = new byte[4096];
        boolean truncated = false;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            int remaining = limit - output.size();
            if (remaining <= 0) {
                truncated = true;
                continue;
            }
            output.write(buffer, 0, Math.min(read, remaining));
            if (read > remaining) {
                truncated = true;
            }
        }
        return truncated;
    }
}
