package org.whispersystems.gcm.server;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.SocketPolicy;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.squareup.okhttp.mockwebserver.rule.MockWebServerRule;
import static junit.framework.TestCase.assertTrue;
import static org.whispersystems.gcm.server.util.FixtureHelpers.fixture;

public class SimultaneousSenderTest {

  @Rule
  public MockWebServerRule webserverRule = new MockWebServerRule();

  private static final ObjectMapper mapper = new ObjectMapper();

  static {
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Test
  public void testSimultaneousSuccess() throws TimeoutException, InterruptedException, ExecutionException, IOException {
    int requestCount = 1000;
    var body = fixture("fixtures/response-success.json");
    for (int i = 0; i < requestCount*2; i++) {
      webserverRule.enqueue(new MockResponse().setResponseCode(200).setBody(body));
    }

    Sender                          sender  = new Sender("foobarbaz", mapper, 2, httpURL());
    List<CompletableFuture<Result>> results = new LinkedList<>();

    for (int i = 0; i < requestCount; i++) {
      results.add(sender.send(Message.newBuilder().withDestination("1").build()));
    }

    for (CompletableFuture<Result> future : results) {
      Result result = future.get(60, TimeUnit.SECONDS);

      if (!result.isSuccess()) {
        throw new AssertionError(result.getError());
      }
    }
  }

  @Test
  public void testSimultaneousFailure() throws TimeoutException, InterruptedException {
    int requestCount = 1000;
    for (int i = 0; i < requestCount*2; i++) {
      webserverRule.enqueue(new MockResponse().setResponseCode(503));
    }

    Sender sender = new Sender("foobarbaz", mapper, 2, httpURL());
    List<CompletableFuture<Result>> futures = new LinkedList<>();

    for (int i = 0; i< requestCount; i++) {
      futures.add(sender.send(Message.newBuilder().withDestination("1").build()));
    }

    int i=0;
    for (CompletableFuture<Result> future : futures) {
      try {
        System.out.printf("Got %d\n", i);
        Result result = future.get(60, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        assertTrue(e.getCause().toString(), e.getCause() instanceof ServerFailedException);
      }
      i++;
    }
  }

  private String httpURL() {
    return webserverRule.getUrl("/gcm/send").toString();
  }

}
