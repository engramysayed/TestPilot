package delivery.job;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Runs a subprocess with async output drain, a hard deadline, cancel, and process-tree kill.
 */
public final class ProcessSupervisor {
    public record Result(int exitCode, String output, boolean timedOut, boolean cancelled, boolean alive, long pid) {
        public boolean completedNormally() {
            return !timedOut && !cancelled && !alive && exitCode == 0;
        }
    }

    private ProcessSupervisor() {
    }

    public static Result run(ProcessBuilder builder, Duration deadline, BooleanSupplier cancel, int maxOutputChars)
            throws Exception {
        if (builder == null) {
            throw new IllegalArgumentException("process builder is required");
        }
        Duration limit = deadline == null || deadline.isZero() || deadline.isNegative()
                ? Duration.ofMinutes(10)
                : deadline;
        int cap = Math.max(256, maxOutputChars);
        builder.redirectErrorStream(true);
        Process proc = builder.start();
        long pid = proc.pid();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Thread drain = new Thread(() -> drain(proc.getInputStream(), buf, cap), "proc-drain-" + pid);
        drain.setDaemon(true);
        drain.start();
        long deadlineNs = System.nanoTime() + limit.toNanos();
        boolean cancelled = false;
        boolean timedOut = false;
        try {
            while (proc.isAlive()) {
                if (cancel != null && cancel.getAsBoolean()) {
                    cancelled = true;
                    killTree(proc);
                    break;
                }
                if (System.nanoTime() >= deadlineNs) {
                    timedOut = true;
                    killTree(proc);
                    break;
                }
                proc.waitFor(50, TimeUnit.MILLISECONDS);
            }
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                killTree(proc);
            }
        } finally {
            drain.join(500);
        }
        int exit = proc.isAlive() ? -1 : proc.exitValue();
        String output = capOutput(buf.toString(java.nio.charset.StandardCharsets.UTF_8), cap);
        return new Result(exit, output, timedOut, cancelled, proc.isAlive(), pid);
    }

    static void killTree(Process proc) {
        if (proc == null) {
            return;
        }
        try {
            ProcessHandle handle = proc.toHandle();
            handle.descendants().forEach(child -> child.destroyForcibly());
            handle.destroyForcibly();
        } catch (Exception e) {
            proc.destroyForcibly();
        }
    }

    private static void drain(InputStream in, ByteArrayOutputStream buf, int cap) {
        byte[] chunk = new byte[512];
        try {
            int n;
            while ((n = in.read(chunk)) >= 0) {
                synchronized (buf) {
                    int room = cap - buf.size();
                    if (room <= 0) {
                        continue;
                    }
                    buf.write(chunk, 0, Math.min(n, room));
                }
            }
        } catch (Exception ignored) {
            // process closed
        }
    }

    private static String capOutput(String raw, int cap) {
        if (raw == null) {
            return "";
        }
        return raw.length() <= cap ? raw : raw.substring(0, cap);
    }
}
