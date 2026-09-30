package io.onloopio.api;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/** Per-server TLS 1.2 with system or explicitly provisioned CA trust; HTTPS verifies hostnames. */
final class Tls12SocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    Tls12SocketFactory(String trustedCaPem) throws IOException {
        try {
            // The Android APK bundles Conscrypt; host tests use the JDK provider.
            // Keep this provider local to the connection, without changing system trust.
            Provider provider = null;
            try {
                provider = (Provider) Class.forName("org.conscrypt.Conscrypt")
                        .getMethod("newProvider").invoke(null);
            } catch (ClassNotFoundException hostTest) { /* JDK TLS */ }
            SSLContext context = provider == null ? SSLContext.getInstance("TLSv1.2")
                    : SSLContext.getInstance("TLSv1.2", provider);
            // Conscrypt 2.5.2's optional trust manager needs newer javax.net.ssl APIs.
            // API 17's platform trust manager validates the chain against our anchors.
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore anchors = null;
            if (trustedCaPem.length() > 0) {
                CertificateFactory certificates = provider == null ? CertificateFactory.getInstance("X.509")
                        : CertificateFactory.getInstance("X.509", provider);
                X509Certificate ca = (X509Certificate) certificates.generateCertificate(
                        new ByteArrayInputStream(trustedCaPem.getBytes("UTF-8")));
                ca.checkValidity();
                if (ca.getBasicConstraints() < 0) throw new IOException("Configured certificate is not a CA.");
                anchors = KeyStore.getInstance(KeyStore.getDefaultType()); anchors.load(null,null);
                anchors.setCertificateEntry("navidrome-ca",ca);
            }
            factory.init(anchors);
            context.init(null, factory.getTrustManagers(), null);
            delegate = context.getSocketFactory();
        } catch (Exception e) {
            javax.net.ssl.SSLException failure = new javax.net.ssl.SSLException(
                    "Cannot initialize verified TLS 1.2. Check device time and configured CA.");
            failure.initCause(e); throw failure;
        }
    }
    private Socket enable(Socket socket) {
        ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1.2"});
        return socket;
    }
    public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
    public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
    public Socket createSocket() throws IOException { return enable(delegate.createSocket()); }
    public Socket createSocket(Socket s, String h, int p, boolean close) throws IOException {
        return enable(delegate.createSocket(s, h, p, close));
    }
    public Socket createSocket(String h, int p) throws IOException { return enable(delegate.createSocket(h, p)); }
    public Socket createSocket(String h, int p, InetAddress l, int lp) throws IOException {
        return enable(delegate.createSocket(h, p, l, lp));
    }
    public Socket createSocket(InetAddress h, int p) throws IOException { return enable(delegate.createSocket(h, p)); }
    public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) throws IOException {
        return enable(delegate.createSocket(h, p, l, lp));
    }
}
