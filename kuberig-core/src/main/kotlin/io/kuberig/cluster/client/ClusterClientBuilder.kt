package io.kuberig.cluster.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.kuberig.config.KubeRigFlags
import io.kuberig.kubectl.AccessTokenAuthDetail
import io.kuberig.kubectl.AuthDetails
import io.kuberig.kubectl.ClientCertAuthDetails
import io.kuberig.kubectl.NoAuthDetails
import kong.unirest.UnirestInstance
import kong.unirest.apache.ApacheClient
import org.apache.http.config.RegistryBuilder
import org.apache.http.conn.socket.ConnectionSocketFactory
import org.apache.http.conn.socket.PlainConnectionSocketFactory
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.conn.ssl.TrustAllStrategy
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.conn.BasicHttpClientConnectionManager
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext

class ClusterClientBuilder(private val flags: KubeRigFlags,
                           private val objectMapper: ObjectMapper,
                           private val unirestInstance: UnirestInstance) {

    private val logger = LoggerFactory.getLogger(ClusterClientBuilder::class.java)

    fun initializeClient(certificateAuthorityData: String?, authDetails: AuthDetails) {

        unirestInstance.config().objectMapper = object : kong.unirest.ObjectMapper {
            override fun writeValue(value: Any?): String {
                return objectMapper.writeValueAsString(value)
            }

            override fun <T : Any?> readValue(value: String?, valueType: Class<T>?): T {
                return objectMapper.readValue(value, valueType)
            }
        }

        var keyStore: KeyStore? = null
        var keyStorePass: String? = null

        when (authDetails) {
            is ClientCertAuthDetails -> {
                keyStore = authDetails.keyStore
                keyStorePass = authDetails.keyStorePass
            }
            is AccessTokenAuthDetail -> {
                unirestInstance.config().clearDefaultHeaders()
                unirestInstance.config().setDefaultHeader("Authorization", "Bearer ${authDetails.accessToken}")
            }
            is NoAuthDetails -> {
                logger.warn("Connecting without authentication")
            }
        }

        val sslContextBuilder = SSLContexts.custom()

        if (keyStore != null && keyStorePass != null) {
            sslContextBuilder.loadKeyMaterial(keyStore, keyStorePass.toCharArray())
        }

        val sslcontext : SSLContext = if (certificateAuthorityData == null || certificateAuthorityData == "") {
            when {
                flags.trustAllSSL -> sslContextBuilder
                    .loadTrustMaterial(null, TrustAllStrategy())
                    .build()
                flags.trustSelfSignedSSL -> sslContextBuilder
                    .loadTrustMaterial(null, TrustSelfSignedStrategy())
                    .build()
                else -> SSLContext.getDefault()
            }
        } else {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val caCert = certificateFactory.generateCertificate(certificateAuthorityData.byteInputStream()) as X509Certificate

            val trustStore = KeyStore.getInstance("JKS")
            trustStore.load(null)
            trustStore.setCertificateEntry("cluster-ca-cert", caCert)

            sslContextBuilder
                .loadTrustMaterial(trustStore, null)
                // https://github.com/golang/go/issues/35722 - limit to 1.2 for now, needs a more fine grained behaviour.
                .setProtocol("TLSv1.2")
                .build()
        }

        val sslsf = SSLConnectionSocketFactory(sslcontext)

        val registry = RegistryBuilder.create<ConnectionSocketFactory>()
            .register("https", sslsf)
            .register("http", PlainConnectionSocketFactory())
            .build()
        val ccm = BasicHttpClientConnectionManager(registry)

        val clientBuilder = HttpClientBuilder.create()

        clientBuilder.setSSLSocketFactory(sslsf)
        clientBuilder.setConnectionManager(ccm)

        val httpClient = clientBuilder.build()
        unirestInstance.config().httpClient(ApacheClient.builder(httpClient))
    }

}