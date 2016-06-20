import org.apache.http.*;
import org.apache.http.auth.*;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * Simple class to launch a jenkins build on run@Cloud platform, should also work on every jenkins instance (not tested)
 *
 */
public class TestPreemptive {

    public static void main(String[] args) {

        // Credentials
        String username = "YOUR_USERNAME";
        String password = "YOUR_PASSWORD";

        // Jenkins url
        String jenkinsUrl = "JENKINS_URL";

        // Build name
        String jobName = "JOB";

        // Build token
        String buildToken = "BUILD_TOKEN";

        // Create your httpclient
        DefaultHttpClient client = new DefaultHttpClient();

        // Then provide the right credentials
        client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                new UsernamePasswordCredentials(username, password));

        // Generate BASIC scheme object and stick it to the execution context
        BasicScheme basicAuth = new BasicScheme();
        BasicHttpContext context = new BasicHttpContext();
        context.setAttribute("preemptive-auth", basicAuth);

        // Add as the first (because of the zero) request interceptor
        // It will first intercept the request and preemptively initialize the authentication scheme if there is not
        client.addRequestInterceptor(new PreemptiveAuth(), 0);

        // You get request that will start the build
        String getUrl = jenkinsUrl + "/job/" + jobName + "/build?token=" + buildToken;
        HttpGet get = new HttpGet(getUrl);

        try {
            // Execute your request with the given context
            HttpResponse response = client.execute(get, context);
            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity);
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Preemptive authentication interceptor
     *
     */
    static class PreemptiveAuth implements HttpRequestInterceptor {

        /*
         * (non-Javadoc)
         *
         * @see org.apache.http.HttpRequestInterceptor#process(org.apache.http.HttpRequest,
         * org.apache.http.protocol.HttpContext)
         */
        public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
            // Get the AuthState
            AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);

            // If no auth scheme available yet, try to initialize it preemptively
            if (authState.getAuthScheme() == null) {
                AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
                CredentialsProvider credsProvider = (CredentialsProvider) context
                        .getAttribute(ClientContext.CREDS_PROVIDER);
                HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
                if (authScheme != null) {
                    Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost
                            .getPort()));
                    if (creds == null) {
                        throw new HttpException("No credentials for preemptive authentication");
                    }
                    authState.setAuthScheme(authScheme);
                    authState.setCredentials(creds);
                }
            }

        }

    }
}