package com.roots.mms.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.util.StringUtils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * Retained only for the data-migration profile.
 *
 * <p>MMS runtime persistence is PostgreSQL (Neon). The Mongo client lives on
 * only so the {@code MigrationRunner} can read the legacy Atlas source during
 * cutover. Once production has migrated and we are past the rollback window,
 * this class and the Mongo dependencies can be removed.
 */
@Configuration
@Profile("migration")
@EnableMongoAuditing
public class MongoConfig {

    @Value("${spring.mongodb.uri:mongodb://localhost:27017/roots-mms}")
    private String mongoUri;

    @Value("${MONGODB_SSL_KEYSTORE:#{null}}")
    private String keyStorePath;

    @Value("${MONGODB_SSL_KEYSTORE_PASSWORD:#{null}}")
    private String keyStorePassword;

    @Value("${MONGODB_SSL_KEYSTORE_TYPE:PKCS12}")
    private String keyStoreType;

    /**
     * Custom MongoClientSettings with X.509 SSL context. Only created when a keystore
     * is configured — in tests (no keystore), Spring Boot's auto-configured settings
     * are used instead, allowing Testcontainers @ServiceConnection to provide the URI.
     */
    @Bean
    @ConditionalOnProperty(name = "MONGODB_SSL_KEYSTORE")
    public MongoClientSettings mongoClientSettings() throws Exception {
        MongoClientSettings.Builder builder = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoUri));

        if (StringUtils.hasText(keyStorePath)) {
            SSLContext sslContext = buildSslContext();
            builder.applyToSslSettings(ssl -> {
                ssl.enabled(true);
                ssl.context(sslContext);
            });
        }

        return builder.build();
    }

    private SSLContext buildSslContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        char[] password = keyStorePassword != null ? keyStorePassword.toCharArray() : new char[0];

        try (FileInputStream fis = new FileInputStream(keyStorePath)) {
            keyStore.load(fis, password);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);
        return sslContext;
    }
}
