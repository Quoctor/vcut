package ru.planetmc.vcut;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@Modulith(
        systemName = "vcut-microservices",
        sharedModules = {"common"}
)
@EnableAsync
@EnableKafka
public class VcutApplication {
    public static void main(String[] args) {
        SpringApplication.run(VcutApplication.class, args);
    }
}
