package ao.autocare.domain;

import ao.autocare.domain.converter.JsonConverters.IntListConverter;
import ao.autocare.domain.enums.Enums.AlertCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category"}))
public class NotificationPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertCategory category;

    @Column(nullable = false)
    private boolean push = true;

    @Column(nullable = false)
    private boolean email = true;

    @Column(nullable = false)
    private boolean sms = false;

    @Convert(converter = IntListConverter.class)
    @Column(name = "lead_days_json", columnDefinition = "text")
    private List<Integer> leadDays = new ArrayList<>();
}
