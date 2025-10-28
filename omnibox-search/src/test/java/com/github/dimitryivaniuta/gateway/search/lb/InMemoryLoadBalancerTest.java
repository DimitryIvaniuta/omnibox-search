package com.github.dimitryivaniuta.gateway.search.lb;


import com.github.dimitryivaniuta.gateway.search.config.LoadBalancerAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit + light Spring tests.
 */
class InMemoryLoadBalancerTest {

    @Test
    @DisplayName("register() should add unique addresses and preserve insertion order")
    void register_unique_ok() {
        InMemoryLoadBalancer lb = new InMemoryLoadBalancer();

        lb.register("http://a:8080");
        lb.register("http://b:8080");
        lb.register("http://c:8080");

        assertThat(lb.size()).isEqualTo(3);

        List<String> snapshot = lb.getInstances();
        assertThat(snapshot)
                .containsExactly(
                        "http://a:8080",
                        "http://b:8080",
                        "http://c:8080"
                );
    }

    @Test
    @DisplayName("register() should reject blank/null addresses")
    void register_blank_rejected() {
        InMemoryLoadBalancer lb = new InMemoryLoadBalancer();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> lb.register("   "))
                .withMessageContaining("address must not be null/blank");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> lb.register(null))
                .withMessageContaining("address must not be null/blank");

        assertThat(lb.size()).isZero();
    }

    @Test
    @DisplayName("register() should throw DuplicateAddressException when address already present")
    void register_duplicate_rejected() {
        InMemoryLoadBalancer lb = new InMemoryLoadBalancer();

        lb.register("http://a:8080");

        assertThatThrownBy(() -> lb.register("http://a:8080"))
                .isInstanceOf(DuplicateAddressException.class)
                .hasMessageContaining("http://a:8080");

        assertThat(lb.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("register() should enforce MAX_CAPACITY = 10")
    void register_capacity_limit() {
        InMemoryLoadBalancer lb = new InMemoryLoadBalancer();

        // Fill up to limit
        for (int i = 0; i < InMemoryLoadBalancer.MAX_CAPACITY; i++) {
            lb.register("http://svc-" + i + ":8080");
        }

        assertThat(lb.size()).isEqualTo(InMemoryLoadBalancer.MAX_CAPACITY);

        // 11th should fail
        assertThatThrownBy(() -> lb.register("http://overflow:8080"))
                .isInstanceOf(CapacityExceededException.class)
                .hasMessageContaining("Max allowed: " + InMemoryLoadBalancer.MAX_CAPACITY);

        assertThat(lb.size()).isEqualTo(InMemoryLoadBalancer.MAX_CAPACITY);
    }

    @Test
    @DisplayName("getInstances() should return an immutable snapshot")
    void getInstances_isImmutableSnapshot() {
        InMemoryLoadBalancer lb = new InMemoryLoadBalancer();
        lb.register("http://a:8080");

        List<String> snapshot = lb.getInstances();
        assertThat(snapshot).containsExactly("http://a:8080");

        assertThatThrownBy(() -> snapshot.add("http://evil:9999"))
                .isInstanceOf(UnsupportedOperationException.class);

        // ensure internal state not affected
        assertThat(lb.size()).isEqualTo(1);
        assertThat(lb.getInstances()).containsExactly("http://a:8080");
    }

    @Test
    @DisplayName("Spring Boot autoconfiguration should expose LoadBalancer bean by default")
    void springAutoConfig_exposesBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(LoadBalancerAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LoadBalancer.class);
                    LoadBalancer bean = ctx.getBean(LoadBalancer.class);

                    bean.register("http://boot:8080");
                    assertThat(bean.getInstances()).containsExactly("http://boot:8080");
                });
    }

    @Test
    @DisplayName("Spring Boot autoconfiguration should back off if user defines their own LoadBalancer bean")
    void springAutoConfig_backsOff() {

        class CustomLb implements LoadBalancer {
            @Override public void register(String address) { /* no-op */ }
            @Override public List<String> getInstances() { return List.of("custom"); }
            @Override public int size() { return 1; }
        }

        new ApplicationContextRunner()
                .withBean(LoadBalancer.class, CustomLb::new)
                .withUserConfiguration(LoadBalancerAutoConfiguration.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(LoadBalancer.class);
                    LoadBalancer bean = ctx.getBean(LoadBalancer.class);

                    // Should be our custom one, not InMemoryLoadBalancer
                    assertThat(bean.getInstances()).containsExactly("custom");
                });
    }
}
