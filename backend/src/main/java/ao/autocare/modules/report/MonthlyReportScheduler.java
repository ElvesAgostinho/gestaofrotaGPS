package ao.autocare.modules.report;

import ao.autocare.domain.Organization;
import ao.autocare.repo.OrganizationRepository;
import java.time.YearMonth;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dia 1 às 07:00 de Luanda o relatório do mês anterior segue sozinho; e um
 * pouco depois de cada arranque tenta-se de novo, para o caso de o dia 1 ter
 * apanhado o servidor em baixo (o mês já enviado não se repete).
 */
@Component
@ConditionalOnProperty(name = "autocare.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class MonthlyReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyReportScheduler.class);
    private static final ZoneId FUSO = ZoneId.of("Africa/Luanda");

    private final OrganizationRepository organizations;
    private final MonthlyReportMailer mailer;

    public MonthlyReportScheduler(OrganizationRepository organizations, MonthlyReportMailer mailer) {
        this.organizations = organizations;
        this.mailer = mailer;
    }

    @Scheduled(cron = "${autocare.scheduler.monthly-report-cron:0 0 7 1 * *}", zone = "Africa/Luanda")
    public void enviarDoMesAnterior() {
        YearMonth mes = YearMonth.now(FUSO).minusMonths(1);
        for (Organization o : organizations.findAll()) {
            try {
                mailer.enviar(o.getId(), mes, false);
            } catch (Exception e) {
                log.warn("Relatório mensal da empresa {} falhou: {}", o.getId(), e.toString());
            }
        }
    }

    @Scheduled(initialDelayString = "${autocare.scheduler.monthly-report-catchup-ms:600000}", fixedDelay = Long.MAX_VALUE)
    public void recuperarSePreciso() {
        enviarDoMesAnterior();
    }
}
