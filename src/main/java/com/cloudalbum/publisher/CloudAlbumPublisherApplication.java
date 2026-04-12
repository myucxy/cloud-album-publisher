package com.cloudalbum.publisher;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.Arrays;

@Slf4j
@SpringBootApplication
@MapperScan("com.cloudalbum.publisher.*.mapper")
public class CloudAlbumPublisherApplication {

    public static void main(String[] args) {
        SpringApplication.run(CloudAlbumPublisherApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();
        String appName = environment.getProperty("spring.application.name", "cloud-album-publisher");
        String port = environment.getProperty("server.port", "8080");
        String contextPath = normalizeContextPath(environment.getProperty("server.servlet.context-path", ""));
        String[] activeProfiles = environment.getActiveProfiles();
        String profileText = activeProfiles.length > 0 ? Arrays.toString(activeProfiles) : "[default]";
        String localUrl = "http://localhost:" + port + contextPath;
        String networkHost = resolveHostAddress();
        String networkUrl = networkHost == null ? "unavailable" : "http://" + networkHost + ":" + port + contextPath;

        log.info("""

                ================================================================================

                   #####  #        ###   #     # ######
                  #     # #       #   #  #     # #     #
                  #       #      #     # #     # #     #
                  #       #      #     # #     # #     #
                  #       #      #     # #     # #     #
                  #     # #       #   #  #     # #     #
                   #####  #######  ###    #####  ######

                           Publisher backend is READY to accept requests

                  Application : {}
                  Profiles    : {}
                  Local       : {}
                  Network     : {}

                ================================================================================
                """, appName, profileText, localUrl, networkUrl);
    }

    private String normalizeContextPath(String contextPath) {
        if (!StringUtils.hasText(contextPath) || "/".equals(contextPath)) {
            return "";
        }
        return contextPath.startsWith("/") ? contextPath : "/" + contextPath;
    }

    private String resolveHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            log.debug("Failed to resolve local host address", ex);
            return null;
        }
    }
}
