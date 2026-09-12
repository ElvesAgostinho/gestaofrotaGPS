package ao.autocare.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "vehicle_brands")
public class VehicleBrand extends BaseEntity {

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    private List<VehicleModel> models = new ArrayList<>();

    public void addModel(String modelName) {
        VehicleModel m = new VehicleModel();
        m.setBrand(this);
        m.setName(modelName);
        models.add(m);
    }
}
