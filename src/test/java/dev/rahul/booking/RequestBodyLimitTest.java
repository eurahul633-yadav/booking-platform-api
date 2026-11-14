package dev.rahul.booking;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RequestBodyLimitTest {
  @Test
  void unknownLengthBodiesAreAlsoBounded() throws Exception {
    var request =
        new MockHttpServletRequest("POST", "/api/v1/auth/workspaces") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public int getContentLength() {
            return -1;
          }
        };
    request.setContentType("application/json");
    request.setContent(new byte[65537]);
    var response = new MockHttpServletResponse();
    var chain = new MockFilterChain();
    new RequestBodyLimitFilter().doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(chain.getRequest()).isNull();
  }
}
