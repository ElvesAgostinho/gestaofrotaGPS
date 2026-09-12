package ao.autocare.app.data.api.dto;

public final class ConfigDtos {

    private ConfigDtos() {}

    public static class AppConfig {
        public String name;
        public String tagline;
        public String currency;
        public String locale;
        public String supportPhone;
        public String supportEmail;
    }
}
