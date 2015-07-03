package com.github.kubernetes.java.client.v2;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.bouncycastle.openssl.PEMReader;
import org.jboss.resteasy.client.jaxrs.ProxyBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class RestFactory {

    private ClassLoader classLoader;
    private int connectionPoolSize;

    public RestFactory() {
    }

    public RestFactory(ClassLoader classLoader) {
        classLoader(classLoader);
    }

    public RestFactory classLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    public RestFactory connectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
        return this;
    }

    public KubernetesAPI createAPI(URI uri, String userName, String password, String serverCertificate) {

        // Configure HttpClient to authenticate preemptively
        // by prepopulating the authentication data cache.
        // http://docs.jboss.org/resteasy/docs/3.0.9.Final/userguide/html/RESTEasy_Client_Framework.html#transport_layer
        // http://hc.apache.org/httpcomponents-client-4.2.x/tutorial/html/authentication.html#d5e1032

        HttpHost targetHost = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());


        SSLConnectionSocketFactory sslsf = null;
        if (serverCertificate != null) {
            try {
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null);

                PEMReader reader = new PEMReader(new StringReader(serverCertificate));
                X509Certificate cert = (X509Certificate) reader.readObject();
                ks.setCertificateEntry(uri.getHost(), cert);

                sslsf = new SSLConnectionSocketFactory(
                        new SSLContextBuilder()
                                .loadTrustMaterial(ks)
                                .build());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (KeyManagementException e) {
                e.printStackTrace();
            } catch (KeyStoreException e) {
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                new UsernamePasswordCredentials(userName, password));

        CloseableHttpClient httpclient = HttpClients.custom()
                .setSSLSocketFactory(sslsf)
                .setDefaultCredentialsProvider(credsProvider)
                .build();

        // Create AuthCache instance
        AuthCache authCache = new BasicAuthCache();
        // Generate BASIC scheme object and add it to the local auth cache
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(targetHost, basicAuth);

        // Add AuthCache to the execution context
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);
        context.setAuthCache(authCache);

        // 4. Create client executor and proxy
        ApacheHttpClient4Engine engine = new ApacheHttpClient4Engine(httpclient, context);
        ResteasyClient client = new ResteasyClientBuilder().connectionPoolSize(connectionPoolSize).httpEngine(engine)
                .build();

        client.register(JacksonJaxbJsonProvider.class).register(JacksonConfig.class);
        ProxyBuilder<KubernetesAPI> proxyBuilder = client.target(uri).proxyBuilder(KubernetesAPI.class);
        if (classLoader != null) {
            proxyBuilder = proxyBuilder.classloader(classLoader);
        }
        return proxyBuilder.build();
    }

    public KubernetesAPI createAPI(String url, String userName, String password) throws URISyntaxException {
        return createAPI(url, userName, password, null);
    }

    public KubernetesAPI createAPI(URI uri, String userName, String password) {
        return createAPI(uri, userName, password, null);
    }


    public KubernetesAPI createAPI(String url, String userName, String password, String serverCertificate) throws URISyntaxException {
        URI uri = new URI(url);
        return createAPI(uri, userName, password, serverCertificate);
    }
}
