import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TestHttps {
  public static void main(String[] args) {
    try {
      // Parse proxy from environment
      String proxyEnv = System.getenv("HTTPS_PROXY");
      HttpClient client;

      if (proxyEnv != null && !proxyEnv.isEmpty()) {
        URI proxyUri = new URI(proxyEnv);
        String userInfo = proxyUri.getUserInfo();

        System.out.println("Configuring proxy: " + proxyUri.getHost() + ":" + proxyUri.getPort());

        if (userInfo != null && userInfo.contains(":")) {
          String[] parts = userInfo.split(":", 2);
          String proxyUser = parts[0];
          String proxyPass = parts[1];

          System.out.println("Proxy user: " + proxyUser);

          // Create HttpClient with proxy and authenticator
          client = HttpClient.newBuilder()
            .proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())))
            .authenticator(new Authenticator() {
              @Override
              protected PasswordAuthentication getPasswordAuthentication() {
                System.out.println("Authenticator called! RequestorType: " + getRequestorType());
                System.out.println("Requesting host: " + getRequestingHost());
                System.out.println("Requesting port: " + getRequestingPort());
                return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
              }
            })
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        } else {
          // Proxy without authentication
          client = HttpClient.newBuilder()
            .proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())))
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        }
      } else {
        // No proxy
        client = HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .build();
      }

      HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI("https://repo.maven.apache.org/maven2/"))
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .build();

      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

      System.out.println("Response code: " + response.statusCode());
      System.out.println("Success! Connected to repo.maven.apache.org");
    } catch (Exception e) {
      System.out.println("HTTPS Connection Failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
