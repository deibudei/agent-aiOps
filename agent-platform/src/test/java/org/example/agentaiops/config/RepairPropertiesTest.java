package org.example.agentaiops.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepairPropertiesTest {

    @Test
    void defaultsExternalSideEffectsOff() {
        RepairProperties properties = new RepairProperties();

        assertThat(properties.getLlm().isEnabled()).isFalse();
        assertThat(properties.getGithub().isEnabled()).isFalse();
        assertThat(properties.getFeishu().isEnabled()).isFalse();
        assertThat(properties.getGit().getBaseBranch()).isEqualTo("demo/fault/quantity-division-by-zero");
    }
}
