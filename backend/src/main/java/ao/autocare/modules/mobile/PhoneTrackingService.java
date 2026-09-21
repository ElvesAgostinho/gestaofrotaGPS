package ao.autocare.modules.mobile;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Asset;
import ao.autocare.domain.Driver;
import ao.autocare.domain.DriverAssignment;
import ao.autocare.domain.GpsDevice;
import ao.autocare.domain.User;
import ao.autocare.domain.enums.Enums.TelemetryProviderKind;
import ao.autocare.modules.telemetry.TelemetryService;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.IngestResult;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.PositionInput;
import ao.autocare.repo.DriverAssignmentRepository;
import ao.autocare.repo.DriverRepository;
import ao.autocare.repo.GpsDeviceRepository;
import ao.autocare.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O telemóvel do motorista como aparelho de localização.
 *
 * <p>Nem toda a viatura tem rastreador instalado, e um rastreador não diz quem
 * vai ao volante. O telemóvel resolve as duas coisas: enquanto a viagem está a
 * decorrer, a aplicação envia a posição e ela entra <b>pelo mesmo caminho</b>
 * das posições do aparelho da viatura — viagens, geocercas, excesso de
 * velocidade, desvio de rota, hora prevista de chegada e mapa ao vivo.
 *
 * <p>Cada motorista tem o seu aparelho lógico, criado na primeira posição que
 * envia. Fica com nome próprio — «Telemóvel · Joaquim Manuel» — para que no
 * mapa se veja de onde veio a posição: nunca se apresenta a posição de um
 * telemóvel como se fosse a de um rastreador instalado.
 *
 * <p>As posições chegam <b>em lote</b>. É deliberado: poupa bateria e dados, e
 * permite que a aplicação guarde o percurso enquanto não há rede e o envie
 * quando houver — que é o que acontece em metade das estradas do país.
 */
@Service
public class PhoneTrackingService {

    /** O que se devolve à aplicação depois de enviar um lote. */
    public record Resultado(int accepted, int rejected, List<String> reasons, String assetId) {}

    /** Uma posição como o telemóvel a lê. */
    public record PosicaoTelemovel(
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal speedKph,
            BigDecimal accuracyM,
            BigDecimal heading,
            Instant recordedAt) {}

    public record Lote(String assetId, List<PosicaoTelemovel> positions) {}

    private final DriverRepository drivers;
    private final DriverAssignmentRepository assignments;
    private final GpsDeviceRepository devices;
    private final UserRepository users;
    private final TelemetryService telemetry;

    public PhoneTrackingService(DriverRepository drivers,
            DriverAssignmentRepository assignments, GpsDeviceRepository devices,
            UserRepository users, TelemetryService telemetry) {
        this.drivers = drivers;
        this.assignments = assignments;
        this.devices = devices;
        this.users = users;
        this.telemetry = telemetry;
    }

    @Transactional
    public Resultado receber(String orgId, String userId, Lote lote) {
        if (lote == null || lote.positions() == null || lote.positions().isEmpty()) {
            return new Resultado(0, 0, List.of(), null);
        }
        Asset asset = viaturaDe(orgId, userId, lote.assetId());
        GpsDevice aparelho = aparelhoDe(orgId, userId, asset);

        int aceites = 0;
        int recusadas = 0;
        List<String> razoes = new ArrayList<>();
        for (PosicaoTelemovel p : lote.positions()) {
            IngestResult r = telemetry.record(aparelho, new PositionInput(
                    p.latitude(), p.longitude(),
                    p.recordedAt() != null ? p.recordedAt() : Instant.now(),
                    p.speedKph(), p.heading(), null, p.accuracyM(),
                    null, null, null, null, null, null, null, null, null));
            if (r.accepted()) {
                aceites++;
            } else {
                recusadas++;
                if (r.reason() != null && !razoes.contains(r.reason())) {
                    razoes.add(r.reason());
                }
            }
        }
        return new Resultado(aceites, recusadas, razoes, asset.getId());
    }

    /**
     * A viatura a que estas posições pertencem.
     *
     * <p>É sempre uma viatura atribuída a este motorista: não se aceita que a
     * aplicação diga que está noutra qualquer. Quando ele conduz só uma, nem
     * precisa de a indicar.
     */
    private Asset viaturaDe(String orgId, String userId, String assetId) {
        List<Asset> minhas = new ArrayList<>();
        for (Driver d : drivers.findByOrganizationIdOrderByNameAsc(orgId)) {
            if (d.getUser() != null && userId.equals(d.getUser().getId())) {
                for (DriverAssignment a : assignments.openForDriver(d.getId())) {
                    minhas.add(a.getAsset());
                }
            }
        }
        if (minhas.isEmpty()) {
            throw ApiException.badRequest(
                    "Ainda não tem nenhuma viatura atribuída. Fale com o seu gestor.");
        }
        if (assetId == null || assetId.isBlank()) {
            return minhas.get(0);
        }
        return minhas.stream().filter(a -> a.getId().equals(assetId)).findFirst()
                .orElseThrow(() -> ApiException.forbidden(
                        "Essa viatura não lhe está atribuída."));
    }

    /** O aparelho lógico deste motorista, criado à primeira posição. */
    private GpsDevice aparelhoDe(String orgId, String userId, Asset asset) {
        String externo = "phone:" + userId;
        GpsDevice existente = devices.findByExternalId(externo).stream()
                .filter(d -> d.getOrganization().getId().equals(orgId))
                .findFirst().orElse(null);
        if (existente != null) {
            // O motorista pode mudar de viatura; o aparelho acompanha-o.
            if (existente.getAsset() == null || !existente.getAsset().getId().equals(asset.getId())) {
                existente.setAsset(asset);
            }
            return existente;
        }
        User u = users.findById(userId).orElse(null);
        GpsDevice novo = new GpsDevice();
        novo.setOrganization(asset.getOrganization());
        novo.setAsset(asset);
        novo.setExternalId(externo);
        novo.setProvider(TelemetryProviderKind.GENERIC);
        novo.setName("Telemóvel · " + (u != null ? u.getName() : "motorista"));
        novo.setModel("Aplicação do motorista");
        return devices.save(novo);
    }

    /** Usado pelos testes e pelo ecrã de aparelhos: o ativo do telemóvel deste utilizador. */
    @Transactional(readOnly = true)
    public String assetIdDoTelemovel(String orgId, String userId) {
        return devices.findByExternalId("phone:" + userId).stream()
                .filter(d -> d.getOrganization().getId().equals(orgId))
                .map(d -> d.getAsset() != null ? d.getAsset().getId() : null)
                .findFirst().orElse(null);
    }
}
