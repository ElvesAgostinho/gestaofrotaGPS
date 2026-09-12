package ao.autocare.modules.workorder;

import ao.autocare.domain.OrgCounter;
import ao.autocare.repo.OrgCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sequências por organização (números de OM, etc.). */
@Service
public class CounterService {

    private final OrgCounterRepository counters;

    public CounterService(OrgCounterRepository counters) {
        this.counters = counters;
    }

    /** Incrementa e devolve o próximo valor da sequência. */
    @Transactional(propagation = Propagation.REQUIRED)
    public long next(String organizationId, String key) {
        OrgCounter counter = counters.findByOrganizationIdAndCounterKey(organizationId, key)
                .orElseGet(() -> {
                    OrgCounter fresh = new OrgCounter();
                    fresh.setOrganizationId(organizationId);
                    fresh.setCounterKey(key);
                    fresh.setCounterValue(0);
                    return counters.save(fresh);
                });
        counter.setCounterValue(counter.getCounterValue() + 1);
        counters.save(counter);
        return counter.getCounterValue();
    }
}
