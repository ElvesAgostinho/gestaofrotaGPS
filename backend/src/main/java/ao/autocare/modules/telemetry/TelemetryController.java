package ao.autocare.modules.telemetry;

import ao.autocare.common.ApiException;
import ao.autocare.common.PagedResponse;
import ao.autocare.domain.enums.Enums.MembershipRole;
import ao.autocare.modules.org.OrgContext;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.AlertView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.DeviceCreated;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.DeviceView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.IngestRequest;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.IngestResult;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.LiveAssetView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.ManualPositionRequest;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.PositionView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.SaveDeviceRequest;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.StreamTicketView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.TrackView;
import ao.autocare.modules.telemetry.dto.TelemetryDtos.TripView;
import ao.autocare.security.AuthPrincipal;
import ao.autocare.security.Permission;
import ao.autocare.security.RequirePermission;
import ao.autocare.security.RequireRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "GPS e telemetria")
@SecurityRequirement(name = "bearerAuth")
@RestController
public class TelemetryController {

    private final TraccarPositions traccar;

    private final TelemetryService telemetry;
    private final TelemetryStream stream;
    private final StreamTickets tickets;
    private final OrgContext orgContext;

    public TelemetryController(
            TelemetryService telemetry,
            TelemetryStream stream,
            StreamTickets tickets,
            OrgContext orgContext,
            TraccarPositions traccar) {
        this.traccar = traccar;
        this.telemetry = telemetry;
        this.stream = stream;
        this.tickets = tickets;
        this.orgContext = orgContext;
    }

    private String org(AuthPrincipal p) {
        return orgContext.requireOrganizationId(p);
    }

    // ---- Ingestão (pública: quem publica é o aparelho) ----------------
    @Operation(summary = "Publicar uma posição (chamado pelo aparelho de GPS)",
            security = {})
    @PostMapping("/api/v1/telemetry/positions")
    public IngestResult ingest(@Valid @RequestBody IngestRequest req) {
        return telemetry.ingest(req);
    }

    @Operation(summary = "Receber uma posicao encaminhada pelo Traccar (forward.url)",
            description = "O Traccar manda o segredo no cabecalho X-Forward-Secret. "
                    + "Sem segredo valido: 401.", security = {})
    @PostMapping("/api/v1/telemetry/traccar/forward")
    public IngestResult traccarForward(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Forward-Secret",
                    required = false) String segredo,
            @org.springframework.web.bind.annotation.RequestParam(value = "secret",
                    required = false) String segredoNoUrl,
            @RequestBody com.fasterxml.jackson.databind.JsonNode corpo) {
        // O Traccar antigo nao poe cabecalhos; aceita-se tambem ?secret= no URL.
        return traccar.receber(segredo != null ? segredo : segredoNoUrl, corpo)
                .orElseThrow(() -> ApiException.unauthorized("Segredo de encaminhamento inválido."));
    }

    // ---- Mapa ao vivo ------------------------------------------------
    @Operation(summary = "Posição atual de todos os ativos localizáveis")
    @GetMapping("/api/v1/telemetry/live")
    public List<LiveAssetView> live(@AuthenticationPrincipal AuthPrincipal p) {
        return telemetry.live(org(p));
    }

    @Operation(summary = "Pedir um bilhete para abrir o mapa em tempo real",
            description = "O bilhete vale " + StreamTickets.TTL_SECONDS
                    + " segundos e serve uma única vez.")
    @PostMapping("/api/v1/telemetry/stream-ticket")
    public StreamTicketView streamTicket(@AuthenticationPrincipal AuthPrincipal p) {
        String ticket = tickets.issue(org(p), p.id());
        return new StreamTicketView(ticket,
                "/api/v1/telemetry/stream?ticket=" + ticket, StreamTickets.TTL_SECONDS);
    }

    @Operation(summary = "Fluxo de posições em tempo real (SSE)",
            description = "Ligar com EventSource usando um bilhete acabado de pedir.",
            security = {})
    @GetMapping(value = "/api/v1/telemetry/stream", produces = "text/event-stream")
    public SseEmitter streamPositions(@RequestParam String ticket) {
        StreamTickets.Ticket valid = tickets.consume(ticket);
        return stream.open(valid.orgId());
    }

    @Operation(summary = "Trajeto de um ativo num intervalo (por omissão, últimas 24 h)")
    @GetMapping("/api/v1/assets/{assetId}/track")
    public TrackView track(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @Parameter(description = "Início, ISO-8601") @RequestParam(required = false) Instant from,
            @Parameter(description = "Fim, ISO-8601") @RequestParam(required = false) Instant to) {
        return telemetry.track(org(p), assetId, from, to);
    }

    @Operation(summary = "Histórico de posições de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/positions")
    public PagedResponse<PositionView> positions(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return telemetry.history(org(p), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500)));
    }

    @Operation(summary = "Viagens de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/trips")
    public PagedResponse<TripView> assetTrips(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return telemetry.tripsForAsset(org(p), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Viagens de toda a frota")
    @GetMapping("/api/v1/trips")
    public PagedResponse<TripView> trips(
            @AuthenticationPrincipal AuthPrincipal p,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return telemetry.tripsForOrg(org(p),
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Marcar a posição de um ativo à mão (sem aparelho instalado)")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping("/api/v1/assets/{assetId}/position")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionView setPosition(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @Valid @RequestBody ManualPositionRequest req) {
        return telemetry.recordManual(org(p), p.id(), assetId, req);
    }

    // ---- Alertas -----------------------------------------------------
    @Operation(summary = "Alertas de telemetria (excesso de velocidade, perda de comunicação)")
    @GetMapping("/api/v1/telemetry/alerts")
    public PagedResponse<AlertView> alerts(
            @AuthenticationPrincipal AuthPrincipal p,
            @Parameter(description = "SPEEDING ou COMMS_LOST")
            @RequestParam(required = false) String kind,
            @Parameter(description = "Só os que ainda estão a decorrer")
            @RequestParam(defaultValue = "false") boolean open,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return telemetry.listAlerts(org(p), kind, open,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Alertas de um ativo")
    @GetMapping("/api/v1/assets/{assetId}/telemetry-alerts")
    public PagedResponse<AlertView> assetAlerts(
            @AuthenticationPrincipal AuthPrincipal p,
            @PathVariable String assetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return telemetry.listAlertsForAsset(org(p), assetId,
                PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200)));
    }

    @Operation(summary = "Marcar um alerta como visto")
    @RequireRole(MembershipRole.TECHNICIAN)
    @PostMapping("/api/v1/telemetry/alerts/{id}/acknowledge")
    public AlertView acknowledgeAlert(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return telemetry.acknowledgeAlert(org(p), p.id(), id);
    }

    // ---- Aparelhos ---------------------------------------------------
    @Operation(summary = "Listar aparelhos de GPS")
    @GetMapping("/api/v1/gps-devices")
    public List<DeviceView> devices(@AuthenticationPrincipal AuthPrincipal p) {
        return telemetry.listDevices(org(p));
    }

    @Operation(summary = "Obter um aparelho")
    @GetMapping("/api/v1/gps-devices/{id}")
    public DeviceView device(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return telemetry.getDevice(org(p), id);
    }

    @Operation(summary = "Registar um aparelho — devolve a chave de publicação uma única vez")
    @RequirePermission(Permission.GPS_MANAGE)
    @PostMapping("/api/v1/gps-devices")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceCreated createDevice(
            @AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody SaveDeviceRequest req) {
        return telemetry.createDevice(org(p), p.id(), req);
    }

    @Operation(summary = "Atualizar um aparelho (incluindo o ativo onde está instalado)")
    @RequirePermission(Permission.GPS_MANAGE)
    @PatchMapping("/api/v1/gps-devices/{id}")
    public DeviceView updateDevice(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id,
            @RequestBody SaveDeviceRequest req) {
        return telemetry.updateDevice(org(p), p.id(), id, req);
    }

    @Operation(summary = "Gerar uma chave de publicação nova (a anterior deixa de servir)")
    @RequirePermission(Permission.GPS_MANAGE)
    @PostMapping("/api/v1/gps-devices/{id}/rotate-key")
    public DeviceCreated rotateKey(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        return telemetry.rotateKey(org(p), p.id(), id);
    }

    @Operation(summary = "Remover um aparelho")
    @RequirePermission(Permission.GPS_MANAGE)
    @DeleteMapping("/api/v1/gps-devices/{id}")
    public Map<String, String> deleteDevice(
            @AuthenticationPrincipal AuthPrincipal p, @PathVariable String id) {
        telemetry.deleteDevice(org(p), p.id(), id);
        return Map.of("message", "Aparelho removido.");
    }
}
