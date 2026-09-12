package ao.autocare.domain;

import ao.autocare.domain.converter.JsonConverters.StringListConverter;
import ao.autocare.domain.enums.Enums.PlanCode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "plans")
public class Plan extends TimestampedEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, unique = true)
    private PlanCode code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "max_vehicles", nullable = false)
    private int maxVehicles;

    @Column(name = "has_gps", nullable = false)
    private boolean hasGps = false;

    @Column(name = "price_monthly", nullable = false, precision = 14, scale = 2)
    private BigDecimal priceMonthly = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "AOA";

    @Convert(converter = StringListConverter.class)
    @Column(name = "features_json", columnDefinition = "text")
    private List<String> features = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
