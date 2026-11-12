package com.example.leaveplatform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Guards the setting the two public counts depend on. Security-code pictures per
 * address and printed-form checks per address are both keyed on
 * {@code request.getRemoteAddr()}, so they are only as strong as where that
 * address comes from. Under {@code framework} it comes from X-Forwarded-For on
 * every request, which a script rotates to keep both counts empty; under
 * {@code native} Tomcat believes that header only from a proxy on an internal
 * address and otherwise reports the socket peer.
 */
class ForwardedHeaderPolicyTest {

    @Test
    void theClientAddressIsTheSocketPeerUnlessAnInternalProxySaysOtherwise() throws Exception {
        assertThat(serverSetting("forward-headers-strategy")).isEqualTo("native");
    }

    @SuppressWarnings("unchecked")
    private static Object serverSetting(String name) throws Exception {
        try (InputStream yaml = ForwardedHeaderPolicyTest.class.getResourceAsStream("/application.yml")) {
            assertThat(yaml).isNotNull();
            Map<String, Object> root = new Yaml().load(yaml);
            return ((Map<String, Object>) root.get("server")).get(name);
        }
    }
}