package ao.autocare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AutoCareApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoCareApplication.class, args);
    }
}
