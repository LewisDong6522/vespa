// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper.client;

import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builder for ZK client configuration
 *
 * @author bjorncs
 */
public class ZkClientConfigBuilder {

    public static final String CLIENT_SECURE_PROPERTY = "zookeeper.client.secure";
    public static final String SSL_CONTEXT_SUPPLIER_CLASS_PROPERTY = "zookeeper.ssl.context.supplier.class";
    public static final String SSL_ENABLED_PROTOCOLS_PROPERTY = "zookeeper.ssl.enabledProtocols";
    public static final String SSL_ENABLED_CIPHERSUITES_PROPERTY = "zookeeper.ssl.ciphersuites";
    public static final String SSL_CLIENTAUTH_PROPERTY = "zookeeper.ssl.clientAuth";

    private static final TlsContext tlsContext = getTlsContext().orElse(null);

    public ZkClientConfigBuilder() {}

    public String toConfigString() {
        StringBuilder builder = new StringBuilder();
        Map<String, String> properties = toConfigProperties();
        properties.forEach((key, value) -> builder.append(key).append('=').append(value).append('\n'));
        return builder.toString();
    }

    public Map<String, String> toConfigProperties() {
        Map<String, String> builder = new HashMap<>();
        builder.put(CLIENT_SECURE_PROPERTY, Boolean.toString(tlsContext != null));
        if (tlsContext != null) {
            builder.put(SSL_CONTEXT_SUPPLIER_CLASS_PROPERTY, VespaSslContextProvider.class.getName());
            String protocolsConfigValue = Arrays.stream(tlsContext.parameters().getProtocols()).sorted().collect(Collectors.joining(","));
            builder.put(SSL_ENABLED_PROTOCOLS_PROPERTY, protocolsConfigValue);
            String ciphersConfigValue = Arrays.stream(tlsContext.parameters().getCipherSuites()).sorted().collect(Collectors.joining(","));
            builder.put(SSL_ENABLED_CIPHERSUITES_PROPERTY, ciphersConfigValue);
            builder.put(SSL_CLIENTAUTH_PROPERTY, "NEED");
        }
        return Map.copyOf(builder);
    }

    private static Optional<TlsContext> getTlsContext() {
        // TODO(bjorncs) Remove handling of temporary feature flag
        boolean temporaryFeatureFlag = Optional.ofNullable(System.getenv("VESPA_USE_TLS_FOR_ZOOKEEPER_CLIENT")).map(Boolean::parseBoolean).orElse(false);
        if (!temporaryFeatureFlag) return Optional.empty();

        if (TransportSecurityUtils.getInsecureMixedMode() == MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER) return Optional.empty();
        return TransportSecurityUtils.getSystemTlsContext();
    }
}
