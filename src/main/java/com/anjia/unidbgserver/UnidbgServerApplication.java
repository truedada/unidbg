package com.anjia.unidbgserver;

import lombok.extern.slf4j.Slf4j;
import com.anjia.unidbgserver.utils.ConsoleNoiseFilter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@ConfigurationPropertiesScan
@EnableConfigurationProperties
@SpringBootApplication(scanBasePackages = {"com.anjia"})
public class UnidbgServerApplication {

    private static final String SERVER_PORT = "server.port";
    private static final String SERVER_SERVLET_CONTEXT_PATH = "server.servlet.context-path";
    private static final String SPRING_APPLICATION_NAME = "spring.application.name";
    private static final String DEFAULT_APPLICATION_NAME = "unidbg-boot-server";
    private static final String PROFILE_PREFIX = "application";

    public static void main(String[] args) {
        ConsoleNoiseFilter.install();
        SpringApplication app = new SpringApplication(UnidbgServerApplication.class);
        Environment env = app.run(args).getEnvironment();
        logApplicationStartup(env);
    }

    private static void logApplicationStartup(Environment env) {
        String serverPort = env.getProperty(SERVER_PORT);
        String contextPath = env.getProperty(SERVER_SERVLET_CONTEXT_PATH);
        if (StringUtils.isBlank(contextPath)) {
            contextPath = "/";
        }
        String hostAddress = InetAddress.getLoopbackAddress().getHostAddress();
        List<String> profiles = new ArrayList<>(env.getActiveProfiles().length + 1);
        profiles.add(PROFILE_PREFIX);
        for (String profile : env.getActiveProfiles()) {
            profiles.add(PROFILE_PREFIX + "-" + profile);
        }
        log.info("应用已启动: name={}, url=http://{}:{}{} , profiles={}",
            StringUtils.defaultIfBlank(env.getProperty(SPRING_APPLICATION_NAME), DEFAULT_APPLICATION_NAME),
            hostAddress,
            serverPort,
            contextPath,
            profiles);
    }
}
