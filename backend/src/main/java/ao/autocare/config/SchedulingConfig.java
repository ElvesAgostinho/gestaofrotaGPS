package ao.autocare.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Ativa as tarefas agendadas (recálculo de planos, geração de OM). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "autocare.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
