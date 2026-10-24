package com.cleanpilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.awt.Desktop;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AppConfig {

    static {
        try {
            java.nio.file.Path appDir = java.nio.file.Paths.get(System.getProperty("user.home", "."), ".cleanpilot");
            if (!java.nio.file.Files.exists(appDir)) {
                java.nio.file.Files.createDirectories(appDir);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Virtual thread per task executor (Java 21) for lightweight, high-throughput background I/O.
     */
    @Bean(destroyMethod = "close")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Checks whether native Desktop trash support is available on this OS and desktop environment.
     */
    @Bean
    public Boolean isNativeTrashSupported() {
        try {
            return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH);
        } catch (Throwable t) {
            return false;
        }
    }
}
