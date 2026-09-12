package ao.autocare.domain;

import ao.autocare.domain.enums.Enums.GpsDeviceStatus;
import ao.autocare.domain.enums.Enums.TelemetryProviderKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Aparelho de GPS instalado num ativo. */
@Getter
@Setter
@Entity
@Table(name = "gps_devices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "external_id"}))
public class GpsDevice extends BaseEntity {

    /** Sem comunicar durante este tempo, o aparelho passa a OFFLINE. */
    public static final Duration OFFLINE_AFTER = Duration.ofMinutes(30);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    /** Identificador do aparelho no fornecedor (IMEI, id Traccar…). */
    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TelemetryProviderKind provider = TelemetryProviderKind.GENERIC;

    @Column(length = 160)
    private String name;

    @Column(length = 120)
    private String model;

    @Column(name = "sim_number", length = 40)
    private String simNumber;

    /** SHA-256 da chave de publicação; a chave em claro só é mostrada uma vez. */
    @Column(name = "ingest_key_hash", length = 64)
    private String ingestKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GpsDeviceStatus status = GpsDeviceStatus.NEVER_SEEN;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "battery_percent")
    private Integer batteryPercent;

    @Column(length = 500)
    private String notes;

    /**
     * Unidade do sensor de combustível: LITERS ou PERCENT.
     *
     * <p>O «fuel» do Traccar é litros ou percentagem conforme o aparelho.
     * Adivinhar pelo valor não serve — 100 é válido nas duas — por isso
     * diz-se aqui, uma vez, por aparelho.
     */
    @Column(name = "fuel_unit", nullable = false, length = 10)
    private String fuelUnit = "LITERS";

    /**
     * Protocolo do fabricante, tal como o fornecedor o identifica (teltonika,
     * gt06, meiligao...). Determina que comandos o aparelho aceita: "engineStop"
     * não existe em todos.
     */
    @Column(length = 60)
    private String protocol;

    /** Lista JSON dos comandos que este aparelho aceita, vinda do fornecedor. */
    @Column(name = "supported_commands", columnDefinition = "TEXT")
    private String supportedCommands;

    @Column(name = "commands_synced_at")
    private Instant commandsSyncedAt;

    /** Identificador do aparelho no fornecedor, para não o procurar a cada comando. */
    @Column(name = "provider_device_id", length = 40)
    private String providerDeviceId;

    /** Se o imobilizador está entre os comandos suportados. */
    public boolean supportsImmobiliser() {
        return supportedCommands != null && supportedCommands.contains("engineStop");
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Estado a partir da última comunicação e de o ativo estar ou não a mexer.
     * Usado na entrada de posições, onde se sabe o que o aparelho acabou de dizer.
     */
    public GpsDeviceStatus currentStatus(boolean moving) {
        if (lastSeenAt == null) {
            return GpsDeviceStatus.NEVER_SEEN;
        }
        if (isSilent()) {
            return GpsDeviceStatus.OFFLINE;
        }
        return moving ? GpsDeviceStatus.ONLINE : GpsDeviceStatus.IDLE;
    }

    /**
     * Estado para leitura, sem precisar de ir buscar a última posição: parte do
     * estado guardado na última comunicação e só o desce a OFFLINE quando o
     * aparelho se cala. Calculado à leitura para não depender de um processo
     * periódico que possa estar parado.
     */
    public GpsDeviceStatus currentStatus() {
        if (lastSeenAt == null) {
            return GpsDeviceStatus.NEVER_SEEN;
        }
        if (isSilent()) {
            return GpsDeviceStatus.OFFLINE;
        }
        return status == GpsDeviceStatus.ONLINE ? GpsDeviceStatus.ONLINE : GpsDeviceStatus.IDLE;
    }

    private boolean isSilent() {
        return Duration.between(lastSeenAt, Instant.now()).compareTo(OFFLINE_AFTER) > 0;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
