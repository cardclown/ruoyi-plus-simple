package org.dromara.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class UndertowUploadLimitBindingTest {

    @Test
    void applicationConfigBindsUndertowPostLimitToTwentyTwoHundredMegabytes() throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
            .forEach(sources::addLast);

        ServerProperties properties = new Binder(ConfigurationPropertySources.from(sources))
            .bind("server", Bindable.of(ServerProperties.class))
            .orElseThrow(() -> new AssertionError("server properties did not bind"));

        assertThat(properties.getUndertow().getMaxHttpPostSize())
            .isEqualTo(DataSize.ofMegabytes(2200));
        assertThat(properties.getUndertow().getMaxHttpPostSize().toBytes())
            .isGreaterThan(2L * 1024 * 1024 * 1024);
    }
}
