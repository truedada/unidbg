package com.anjia.unidbgserver.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * 过滤第三方库输出到控制台的噪音日志（例如 unidbg/so 的 METASEC 打印）。
 *
 * 通过 -Dfq.log.filterConsoleNoise=false 可关闭。
 */
public final class ConsoleNoiseFilter {

    private ConsoleNoiseFilter() {}

    public static void install() {
        String enabled = System.getProperty("fq.log.filterConsoleNoise", "true");
        if ("false".equalsIgnoreCase(enabled)) {
            return;
        }

        Charset charset = Charset.defaultCharset();
        System.setErr(new PrintStream(new LineFilteringOutputStream(System.err, charset), true));
        System.setOut(new PrintStream(new LineFilteringOutputStream(System.out, charset), true));
    }

    static final class LineFilteringOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final Charset charset;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);

        LineFilteringOutputStream(OutputStream delegate, Charset charset) {
            this.delegate = delegate;
            this.charset = charset;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            buffer.write(b);
            if (b == '\n') {
                flushBufferAsLine();
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) {
                write(b[off + i]);
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            if (buffer.size() > 0) {
                flushBufferAsLine();
            }
            delegate.flush();
        }

        @Override
        public synchronized void close() throws IOException {
            flush();
            delegate.close();
        }

        private void flushBufferAsLine() throws IOException {
            byte[] lineBytes = buffer.toByteArray();
            buffer.reset();

            String line = new String(lineBytes, charset);
            if (shouldDrop(line)) {
                return;
            }
            delegate.write(lineBytes);
        }

        private boolean shouldDrop(String line) {
            if (line == null || line.isEmpty()) {
                return false;
            }
            // 典型噪音输出（来源：libmetasec_ml.so）
            return line.contains("E/METASEC:")
                || line.contains("MSTaskManager::DoLazyInit()")
                || line.contains("SDK not init, crashing");
        }
    }
}

