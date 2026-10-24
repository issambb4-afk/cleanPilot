package com.cleanpilot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationContextStartupTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoadsAndAllBeansInstantiateSuccessfully() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.containsBean("dashboardViewController")).isTrue();
        assertThat(applicationContext.containsBean("smartCleanerService")).isTrue();
        assertThat(applicationContext.containsBean("appUninstallerService")).isTrue();
        assertThat(applicationContext.containsBean("developerCleanupService")).isTrue();
        assertThat(applicationContext.containsBean("fileDeletionService")).isTrue();
        assertThat(applicationContext.containsBean("quarantineService")).isTrue();
    }
}
