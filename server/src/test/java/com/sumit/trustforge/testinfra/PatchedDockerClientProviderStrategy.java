package com.sumit.trustforge.testinfra;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.time.Duration;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.TransportConfig;

/** Test-only supported provider; removes the zerodep transport's unpatchable shaded HTTP copy. */
public class PatchedDockerClientProviderStrategy extends DockerClientProviderStrategy {
    private final DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withApiVersion(System.getProperty("api.version", "1.44")).build();
    private DockerClient client;

    @Override public String getDescription() {
        return "Patched external Apache HTTP transport for the configured Docker daemon";
    }

    @Override public TransportConfig getTransportConfig() {
        return TransportConfig.builder().dockerHost(config.getDockerHost()).sslConfig(config.getSSLConfig()).build();
    }

    @Override protected int getPriority() { return Integer.MAX_VALUE; }

    @Override protected boolean isPersistable() { return false; }

    @Override public synchronized DockerClient getDockerClient() {
        if (client == null) {
            var http = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig()).maxConnections(20)
                .connectionTimeout(Duration.ofSeconds(10)).responseTimeout(Duration.ofMinutes(5)).build();
            client = DockerClientImpl.getInstance(config, http);
        }
        return client;
    }
}
