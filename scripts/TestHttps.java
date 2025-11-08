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
      URI proxyUri = new URI(System.getenv("HTTPS_PROXY"));
      String[] parts = proxyUri.getUserInfo().split(":", 2);
      String proxyUser = parts[0];
      String proxyPass = parts[1];

      System.out.println("Proxy: " + proxyUri.getHost() + ":" + proxyUri.getPort());
      System.out.println("User: " + proxyUser);

      // Create HttpClient with proxy and authenticator
      HttpClient client = HttpClient.newBuilder()
        .proxy(ProxySelector.of(new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort())))
        .authenticator(new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            System.out.println("Authenticator called! RequestorType: " + getRequestorType());
            return new PasswordAuthentication(proxyUser, proxyPass.toCharArray());
          }
        })
        .connectTimeout(Duration.ofSeconds(10))
        .build();

      HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI("https://repo.maven.apache.org/maven2/"))
        .method("HEAD", HttpRequest.BodyPublishers.noBody())
        .build();

      HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());

      System.out.println("Response code: " + response.statusCode());
      System.out.println("Success!");
    } catch (Exception e) {
      System.out.println("Failed: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
