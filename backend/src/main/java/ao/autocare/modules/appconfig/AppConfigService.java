package ao.autocare.modules.appconfig;

import ao.autocare.config.AutoCareProperties;
import ao.autocare.domain.AppConfigEntry;
import ao.autocare.repo.AppConfigRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuração pública da aplicação. O nome do produto é alterável (secção 2):
 * o valor guardado em {@code app_config} tem precedência sobre {@code application.yml}.
 */
@Service
public class AppConfigService {

    private final AppConfigRepository repository;
    private final AutoCareProperties props;

    public AppConfigService(AppConfigRepository repository, AutoCareProperties props) {
        this.repository = repository;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicConfig() {
        Map<String, String> stored = repository.findAll().stream()
                .collect(Collectors.toMap(AppConfigEntry::getKey, AppConfigEntry::getValue));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", stored.getOrDefault("app.name", props.app().name()));
        out.put("tagline", stored.getOrDefault("app.tagline", props.app().tagline()));
        out.put("currency", stored.getOrDefault("app.currency", "AOA"));
        out.put("locale", stored.getOrDefault("app.locale", "pt-AO"));
        out.put("supportPhone", stored.get("app.supportPhone"));
        out.put("supportEmail", stored.get("app.supportEmail"));
        return out;
    }

    @Transactional(readOnly = true)
    public List<AppConfigEntry> all() {
        return repository.findAll(org.springframework.data.domain.Sort.by("key"));
    }

    @Transactional
    public AppConfigEntry set(String key, String value) {
        AppConfigEntry entry = repository.findByKey(key).orElseGet(AppConfigEntry::new);
        entry.setKey(key);
        entry.setValue(value);
        return repository.save(entry);
    }
}
