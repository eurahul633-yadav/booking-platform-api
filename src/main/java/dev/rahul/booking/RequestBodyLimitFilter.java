package dev.rahul.booking;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {
  static final int MAX_BYTES = 65536;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!request.getRequestURI().startsWith("/api/") || SetMethods.safe(request.getMethod())) {
      chain.doFilter(request, response);
      return;
    }
    if (request.getContentLengthLong() > MAX_BYTES) {
      SecurityConfig.writeError(response, 413, "BODY_TOO_LARGE", "Request body exceeds 64 KiB");
      return;
    }
    byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) {
      SecurityConfig.writeError(response, 413, "BODY_TOO_LARGE", "Request body exceeds 64 KiB");
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public ServletInputStream getInputStream() {
            var input = new ByteArrayInputStream(bytes);
            return new ServletInputStream() {
              public int read() {
                return input.read();
              }

              public int read(byte[] b, int off, int len) {
                return input.read(b, off, len);
              }

              public boolean isFinished() {
                return input.available() == 0;
              }

              public boolean isReady() {
                return true;
              }

              public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Synchronous JSON request bodies only");
              }
            };
          }

          @Override
          public BufferedReader getReader() {
            return new BufferedReader(
                new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
          }
        },
        response);
  }

  private static class SetMethods {
    static boolean safe(String method) {
      return java.util.Set.of("GET", "HEAD", "OPTIONS").contains(method);
    }
  }
}
