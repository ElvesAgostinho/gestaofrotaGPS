package ao.autocare.modules.command;

import ao.autocare.domain.DeviceCommand;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Usado enquanto não houver fornecedor configurado.
 *
 * <p>Recusa a entrega, em vez de a fingir. Num sistema de bloqueio de motor,
 * dizer "enviado" sem ter enviado é a pior mentira possível: alguém acredita que
 * a viatura está imobilizada quando não está.
 */
@Component
public class DemoCommandProvider implements CommandProvider {

    private static final Logger log = LoggerFactory.getLogger(DemoCommandProvider.class);

    private static final String NOT_CONFIGURED =
            "Não há servidor Traccar configurado. Defina TRACCAR_URL e as credenciais "
                    + "para poder bloquear viaturas — ver docs/TRACCAR.md.";

    @Override
    public Dispatch dispatch(DeviceCommand command) {
        log.warn("[MODO DEMONSTRAÇÃO] Sem fornecedor de comandos configurado. "
                        + "NÃO foi enviado {} para o aparelho {}.",
                command.getKind(), command.getDevice().getExternalId());
        return Dispatch.rejected(NOT_CONFIGURED);
    }

    @Override
    public Optional<DeviceInfo> describeDevice(String externalId) {
        return Optional.empty();
    }

    @Override
    public Evidence confirmationFor(DeviceCommand command) {
        return Evidence.none();
    }

    @Override
    public ProviderHealth health() {
        return new ProviderHealth(false, false, name(), null, NOT_CONFIGURED);
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String name() {
        return "Modo demonstração (sem Traccar)";
    }
}
