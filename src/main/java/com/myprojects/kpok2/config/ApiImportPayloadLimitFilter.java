package com.myprojects.kpok2.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class ApiImportPayloadLimitFilter extends OncePerRequestFilter {
    private static final String IMPORT_PATH = "/api/imports/questions";

    private final long maxBytes;

    ApiImportPayloadLimitFilter(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("API import limit must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !IMPORT_PATH.equals(path)
                && !path.startsWith(IMPORT_PATH + "/")
                && !path.startsWith(IMPORT_PATH + ";");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            writePayloadTooLarge(response);
            return;
        }

        HttpServletRequest limited = new LimitedRequest(request, maxBytes);
        try {
            filterChain.doFilter(limited, response);
        } catch (ServletException | IOException | RuntimeException e) {
            if (containsPayloadTooLarge(e) && !response.isCommitted()) {
                writePayloadTooLarge(response);
                return;
            }
            throw e;
        }
    }

    private void writePayloadTooLarge(HttpServletResponse response) throws IOException {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":413,\"title\":\"Payload Too Large\","
                        + "\"detail\":\"API import exceeds the configured "
                        + maxBytes + " byte limit\"}");
    }

    private boolean containsPayloadTooLarge(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof PayloadTooLargeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final long maxBytes;
        private ServletInputStream inputStream;

        private LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(
                        super.getInputStream(), maxBytes);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long consumed;

        private LimitedServletInputStream(
                ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                record(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = delegate.read(bytes, offset, length);
            if (count > 0) {
                record(count);
            }
            return count;
        }

        @Override
        public long skip(long count) throws IOException {
            long skipped = delegate.skip(count);
            if (skipped > 0) {
                record(skipped);
            }
            return skipped;
        }

        private void record(long count) throws PayloadTooLargeException {
            consumed += count;
            if (consumed > maxBytes) {
                throw new PayloadTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }

    private static final class PayloadTooLargeException extends IOException {
    }
}
