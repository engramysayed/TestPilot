package delivery.portal.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Reads the body once (bounded) so the signature can be checked and the controller still sees it. */
final class CachedBodyRequest extends HttpServletRequestWrapper {
    static final int MAX_BODY = 16 * 1024 * 1024;
    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request) throws IOException {
        super(request);
        byte[] raw = request.getInputStream().readNBytes(MAX_BODY + 1);
        if (raw.length > MAX_BODY) {
            throw new BodyTooLarge();
        }
        this.body = raw;
    }

    byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream in = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return in.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener l) { throw new UnsupportedOperationException(); }
            @Override public int read() { return in.read(); }
            @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }

    static final class BodyTooLarge extends IOException {
        BodyTooLarge() {
            super("request body too large");
        }
    }
}
