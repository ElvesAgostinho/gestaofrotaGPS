package ao.autocare.modules.workorder;

import ao.autocare.common.ApiException;
import ao.autocare.domain.Organization;
import ao.autocare.domain.User;
import ao.autocare.domain.WorkOrder;
import ao.autocare.domain.WorkOrderFaultCode;
import ao.autocare.domain.WorkOrderFluid;
import ao.autocare.domain.WorkOrderMeasurement;
import ao.autocare.domain.WorkOrderSignature;
import ao.autocare.domain.enums.Enums.FaultCodeStatus;
import ao.autocare.domain.enums.Enums.MeasurementVerdict;
import ao.autocare.domain.enums.Enums.RiskLevel;
import ao.autocare.domain.enums.Enums.TestKind;
import ao.autocare.domain.enums.Enums.TestResult;
import ao.autocare.domain.enums.Enums.SignatureRole;
import ao.autocare.modules.audit.AuditService;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FailureCodingRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FaultCodeRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.FluidRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.MeasurementRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.MeasurementTemplate;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.NextServiceRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.SafetyRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.SignatureRequest;
import ao.autocare.modules.workorder.dto.ShopFloorDtos.TestRequest;
import ao.autocare.repo.OrganizationRepository;
import ao.autocare.repo.UserRepository;
import ao.autocare.repo.WorkOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O lado técnico da ordem: o que o mecânico mede, despeja, lê e assina.
 *
 * <p>Está separado do {@link WorkOrderService} porque são duas conversas
 * diferentes. Aquele trata do percurso administrativo — aprovações, orçamentos,
 * custos, prazos. Este trata do que acontece com a viatura em cima da vala, e
 * quem o usa é o técnico, não o gestor.
 *
 * <p>Não duplica o que já existe: a análise de óleo vive no módulo preditivo
 * (que já liga à ordem), a inspeção de passa/não-passa vive nos checklists, e
 * as peças no stock. O que entra aqui é só o que não tinha casa.
 */
@Service
public class WorkOrderShopFloorService {

    private final WorkOrderRepository workOrders;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuditService audit;

    public WorkOrderShopFloorService(
            WorkOrderRepository workOrders,
            OrganizationRepository organizations,
            UserRepository users,
            AuditService audit) {
        this.workOrders = workOrders;
        this.organizations = organizations;
        this.users = users;
        this.audit = audit;
    }

    // ==== Medições =========================================================

    /**
     * Regista uma medição.
     *
     * <p>O veredicto é calculado a partir dos limites, nunca aceite do pedido:
     * deixar o cliente dizer que uma pastilha de 2 mm está «OK» é deixar entrar
     * na base exatamente a mentira que o sistema existe para apanhar.
     */
    @Transactional
    public List<WorkOrderMeasurement> addMeasurement(
            String orgId, String userId, String workOrderId, MeasurementRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "medições");

        WorkOrderMeasurement m = new WorkOrderMeasurement();
        m.setOrganization(organization(orgId));
        m.setWorkOrder(w);
        m.setGroupName(obrigatorio(req.groupName(), "Indique o grupo da medição."));
        m.setName(obrigatorio(req.name(), "Indique o que foi medido."));
        m.setPosition(vazioParaNulo(req.position()));
        m.setValueNum(req.valueNum());
        m.setValueText(vazioParaNulo(req.valueText()));
        m.setUnit(vazioParaNulo(req.unit()));
        m.setMinValue(req.minValue());
        m.setMaxValue(req.maxValue());
        m.setNote(vazioParaNulo(req.note()));
        m.setSortOrder(req.sortOrder() != null ? req.sortOrder() : w.getMeasurements().size());
        m.setRecordedAt(req.recordedAt() != null ? req.recordedAt() : Instant.now());

        if (m.getValueNum() == null && m.getValueText() == null) {
            throw ApiException.badRequest("Uma medição sem valor não mede nada.");
        }

        User quem = users.findById(userId).orElse(null);
        m.setRecordedBy(quem);
        m.setRecordedByLabel(quem != null ? quem.getName() : null);

        // Só quando não há limites é que o veredicto do pedido vale: aí é um
        // juízo do técnico, não uma conta.
        m.setVerdict(req.verdict() != null ? req.verdict() : MeasurementVerdict.OK);
        m.judge();

        w.getMeasurements().add(m);
        workOrders.flush();

        audit.record(orgId, userId, "work_order.measurement", "WorkOrder", w.getId(),
                w.getNumber() + " · " + m.getName()
                        + (m.getPosition() != null ? " (" + m.getPosition() + ")" : "")
                        + " = " + valorLegivel(m) + " · " + m.getVerdict().label());
        return List.copyOf(w.getMeasurements());
    }

    @Transactional
    public void removeMeasurement(String orgId, String userId, String workOrderId, String id) {
        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "medições");
        WorkOrderMeasurement m = w.getMeasurements().stream()
                .filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Medição não encontrada nesta ordem."));
        w.getMeasurements().remove(m);
        audit.record(orgId, userId, "work_order.measurement_delete", "WorkOrder", w.getId(),
                w.getNumber() + " · " + m.getName());
    }

    // ==== Fluidos ==========================================================

    @Transactional
    public List<WorkOrderFluid> addFluid(
            String orgId, String userId, String workOrderId, FluidRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "fluidos");

        WorkOrderFluid f = new WorkOrderFluid();
        f.setOrganization(organization(orgId));
        f.setWorkOrder(w);
        f.setKind(req.kind());
        f.setSpec(vazioParaNulo(req.spec()));
        f.setBrand(vazioParaNulo(req.brand()));
        if (req.action() != null) {
            f.setAction(req.action());
        }
        f.setQuantity(req.quantity());
        if (req.unit() != null && !req.unit().isBlank()) {
            f.setUnit(req.unit().trim());
        }
        f.setFilterChanged(Boolean.TRUE.equals(req.filterChanged()));
        f.setFilterPartNumber(vazioParaNulo(req.filterPartNumber()));
        f.setBatch(vazioParaNulo(req.batch()));
        f.setUnitCost(req.unitCost());
        f.setNote(vazioParaNulo(req.note()));

        // O total é calculado, nunca aceite: um total que não bate com a
        // quantidade vezes o preço é uma discussão com o fornecedor à espera.
        if (f.getQuantity() != null && f.getUnitCost() != null) {
            f.setTotalCost(f.getQuantity().multiply(f.getUnitCost())
                    .setScale(2, java.math.RoundingMode.HALF_UP));
        }

        w.getFluids().add(f);
        workOrders.flush();

        audit.record(orgId, userId, "work_order.fluid", "WorkOrder", w.getId(),
                w.getNumber() + " · " + f.getKind().label()
                        + (f.getSpec() != null ? " " + f.getSpec() : "")
                        + (f.getQuantity() != null ? " · " + f.getQuantity() + " " + f.getUnit() : ""));
        return List.copyOf(w.getFluids());
    }

    @Transactional
    public void removeFluid(String orgId, String userId, String workOrderId, String id) {
        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "fluidos");
        WorkOrderFluid f = w.getFluids().stream()
                .filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Fluido não encontrado nesta ordem."));
        w.getFluids().remove(f);
        audit.record(orgId, userId, "work_order.fluid_delete", "WorkOrder", w.getId(),
                w.getNumber() + " · " + f.getKind().label());
    }

    // ==== Códigos de avaria ================================================

    @Transactional
    public List<WorkOrderFaultCode> addFaultCode(
            String orgId, String userId, String workOrderId, FaultCodeRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "códigos de avaria");

        WorkOrderFaultCode c = new WorkOrderFaultCode();
        c.setOrganization(organization(orgId));
        c.setWorkOrder(w);
        if (req.source() != null) {
            c.setSource(req.source());
        }
        c.setCode(obrigatorio(req.code(), "Indique o código lido.").toUpperCase());
        c.setDescription(vazioParaNulo(req.description()));
        c.setOccurrences(req.occurrences());
        if (req.status() != null) {
            c.setStatus(req.status());
        }
        c.setFirstSeenAt(req.firstSeenAt());
        c.setNote(vazioParaNulo(req.note()));
        if (c.getStatus() == FaultCodeStatus.CLEARED) {
            c.setClearedAt(Instant.now());
        }

        w.getFaultCodes().add(c);
        workOrders.flush();

        audit.record(orgId, userId, "work_order.fault_code", "WorkOrder", w.getId(),
                w.getNumber() + " · " + c.getSource().name() + " " + c.getCode()
                        + " · " + c.getStatus().label());
        return List.copyOf(w.getFaultCodes());
    }

    /** Marca o código como apagado do módulo — e regista quando. */
    @Transactional
    public List<WorkOrderFaultCode> clearFaultCode(
            String orgId, String userId, String workOrderId, String id) {

        WorkOrder w = require(orgId, workOrderId);
        WorkOrderFaultCode c = w.getFaultCodes().stream()
                .filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Código não encontrado nesta ordem."));
        c.setStatus(FaultCodeStatus.CLEARED);
        c.setClearedAt(Instant.now());
        audit.record(orgId, userId, "work_order.fault_code_clear", "WorkOrder", w.getId(),
                w.getNumber() + " · " + c.getCode() + " apagado");
        return List.copyOf(w.getFaultCodes());
    }

    @Transactional
    public void removeFaultCode(String orgId, String userId, String workOrderId, String id) {
        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "códigos de avaria");
        WorkOrderFaultCode c = w.getFaultCodes().stream()
                .filter(x -> x.getId().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Código não encontrado nesta ordem."));
        w.getFaultCodes().remove(c);
        audit.record(orgId, userId, "work_order.fault_code_delete", "WorkOrder", w.getId(),
                w.getNumber() + " · " + c.getCode());
    }

    // ==== Assinaturas ======================================================

    /**
     * Assina a ordem num dos papéis.
     *
     * <p>Uma assinatura por papel: assinar duas vezes como técnico não faz
     * sentido, e a segunda substitui a primeira em vez de se acumular.
     */
    @Transactional
    public List<WorkOrderSignature> sign(
            String orgId, String userId, String workOrderId, SignatureRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        if (w.getStatus().isTerminal()) {
            throw ApiException.conflict(
                    "Uma ordem " + w.getStatus().label().toLowerCase() + " já não se assina.");
        }

        SignatureRole papel = req.role();
        String nome = obrigatorio(req.personName(), "Indique quem assina.");

        w.getSignatures().removeIf(s -> s.getRole() == papel);

        WorkOrderSignature s = new WorkOrderSignature();
        s.setOrganization(organization(orgId));
        s.setWorkOrder(w);
        s.setRole(papel);
        s.setPersonName(nome);
        s.setPersonDocument(vazioParaNulo(req.personDocument()));
        s.setAccepted(req.accepted() == null || req.accepted());
        s.setNote(vazioParaNulo(req.note()));
        s.setSignedAt(Instant.now());
        s.setUser(users.findById(userId).orElse(null));

        // Quem recebe com reservas tem de dizer quais, senão a reserva não
        // serve de nada quando a discussão aparecer.
        if (!s.isAccepted() && s.getNote() == null) {
            throw ApiException.badRequest(
                    "Se assina com reservas, escreva quais. É isso que vale mais tarde.");
        }

        w.getSignatures().add(s);
        workOrders.flush();

        audit.record(orgId, userId, "work_order.signature", "WorkOrder", w.getId(),
                w.getNumber() + " · " + papel.label() + ": " + nome
                        + (s.isAccepted() ? "" : " (com reservas)"));
        return List.copyOf(w.getSignatures());
    }

    // ==== Modelos de medição ===============================================

    /**
     * O que se mede em cada tipo de equipamento.
     *
     * <p>Escrever trinta medições à mão em cada ordem é o que faz um técnico
     * desistir do sistema e voltar ao papel. Os modelos abaixo trazem os
     * limites de serviço habituais já preenchidos — o técnico só escreve o
     * valor que leu.
     *
     * <p>Os limites são os comuns em frota pesada e servem de ponto de partida;
     * o manual do fabricante manda sempre, e por isso são editáveis.
     */
    public List<MeasurementTemplate> templates(String kind) {
        return switch (kind == null ? "" : kind.toUpperCase()) {
            case "GENERATOR" -> geradores();
            case "LIGHT" -> ligeiros();
            default -> pesados();
        };
    }

    private List<MeasurementTemplate> pesados() {
        return List.of(
                m("Travagem", "Espessura da pastilha", "Dianteiro esquerdo", "mm", d("4"), null),
                m("Travagem", "Espessura da pastilha", "Dianteiro direito", "mm", d("4"), null),
                m("Travagem", "Espessura da pastilha", "Traseiro esquerdo", "mm", d("4"), null),
                m("Travagem", "Espessura da pastilha", "Traseiro direito", "mm", d("4"), null),
                m("Travagem", "Espessura do disco", "Dianteiro", "mm", d("34"), null),
                m("Travagem", "Curso do pedal", null, "mm", null, d("70")),
                m("Pneus", "Profundidade do piso", "Dianteiro esquerdo", "mm", d("1.6"), null),
                m("Pneus", "Profundidade do piso", "Dianteiro direito", "mm", d("1.6"), null),
                m("Pneus", "Profundidade do piso", "Traseiro esquerdo", "mm", d("1.6"), null),
                m("Pneus", "Profundidade do piso", "Traseiro direito", "mm", d("1.6"), null),
                m("Pneus", "Pressão", "Dianteiro", "bar", d("7.5"), d("8.5")),
                m("Pneus", "Pressão", "Traseiro", "bar", d("7.5"), d("8.5")),
                m("Motor", "Pressão de óleo ao ralenti", null, "bar", d("1.0"), null),
                m("Motor", "Pressão de óleo em rotação", null, "bar", d("2.5"), d("6.0")),
                m("Motor", "Temperatura de funcionamento", null, "°C", d("80"), d("95")),
                m("Motor", "Folga das válvulas (admissão)", null, "mm", d("0.20"), d("0.40")),
                m("Motor", "Tensão da correia", null, "mm", d("8"), d("13")),
                m("Elétrico", "Tensão da bateria em repouso", null, "V", d("12.4"), d("12.9")),
                m("Elétrico", "Tensão em carga (alternador)", null, "V", d("13.8"), d("14.6")),
                m("Elétrico", "Teste de arranque", null, "V", d("9.6"), null),
                m("Arrefecimento", "Proteção do líquido", null, "°C", null, d("-25")),
                m("Suspensão", "Altura da mola", null, "mm", null, null),
                m("Transmissão", "Folga do veio", null, "mm", null, d("1.0")));
    }

    private List<MeasurementTemplate> ligeiros() {
        return List.of(
                m("Travagem", "Espessura da pastilha", "Dianteiro esquerdo", "mm", d("3"), null),
                m("Travagem", "Espessura da pastilha", "Dianteiro direito", "mm", d("3"), null),
                m("Travagem", "Espessura da pastilha", "Traseiro esquerdo", "mm", d("3"), null),
                m("Travagem", "Espessura da pastilha", "Traseiro direito", "mm", d("3"), null),
                m("Travagem", "Espessura do disco", "Dianteiro", "mm", d("22"), null),
                m("Pneus", "Profundidade do piso", "Dianteiro esquerdo", "mm", d("1.6"), null),
                m("Pneus", "Profundidade do piso", "Dianteiro direito", "mm", d("1.6"), null),
                m("Pneus", "Profundidade do piso", "Traseiro esquerdo", "mm", d("1.6"), null),
                m("Pneus", "Profundidade do piso", "Traseiro direito", "mm", d("1.6"), null),
                m("Pneus", "Pressão", "Dianteiro", "bar", d("2.0"), d("2.6")),
                m("Pneus", "Pressão", "Traseiro", "bar", d("2.0"), d("2.6")),
                m("Motor", "Temperatura de funcionamento", null, "°C", d("85"), d("105")),
                m("Elétrico", "Tensão da bateria em repouso", null, "V", d("12.4"), d("12.9")),
                m("Elétrico", "Tensão em carga (alternador)", null, "V", d("13.8"), d("14.6")),
                m("Arrefecimento", "Proteção do líquido", null, "°C", null, d("-25")));
    }

    private List<MeasurementTemplate> geradores() {
        return List.of(
                m("Prova de carga", "Potência atingida", null, "kW", null, null),
                m("Prova de carga", "Duração da prova", null, "min", d("60"), null),
                m("Elétrico", "Tensão", "Fase R", "V", d("220"), d("240")),
                m("Elétrico", "Tensão", "Fase S", "V", d("220"), d("240")),
                m("Elétrico", "Tensão", "Fase T", "V", d("220"), d("240")),
                m("Elétrico", "Frequência", null, "Hz", d("49.5"), d("50.5")),
                m("Elétrico", "Corrente", "Fase R", "A", null, null),
                m("Elétrico", "Corrente", "Fase S", "A", null, null),
                m("Elétrico", "Corrente", "Fase T", "A", null, null),
                m("Elétrico", "Resistência de isolamento", null, "MΩ", d("1"), null),
                m("Motor", "Pressão de óleo", null, "bar", d("2.0"), d("6.0")),
                m("Motor", "Temperatura da água", null, "°C", d("70"), d("95")),
                m("Motor", "Rotação sem carga", null, "rpm", d("1490"), d("1520")),
                m("Elétrico", "Tensão da bateria de arranque", null, "V", d("12.4"), d("12.9")),
                m("Combustível", "Nível do depósito", null, "%", d("25"), null),
                m("Arrefecimento", "Proteção do líquido", null, "°C", null, d("-15")));
    }

    private static MeasurementTemplate m(
            String grupo, String nome, String posicao, String unidade,
            BigDecimal min, BigDecimal max) {
        return new MeasurementTemplate(grupo, nome, posicao, unidade, min, max);
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    // ==== Auxiliares =======================================================

    private WorkOrder require(String orgId, String id) {
        return workOrders.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> ApiException.notFound("Ordem de manutenção não encontrada."));
    }

    private Organization organization(String orgId) {
        return organizations.findById(orgId)
                .orElseThrow(() -> ApiException.notFound("Empresa não encontrada."));
    }

    /**
     * Uma ordem fechada é registo histórico.
     *
     * <p>Mexer-lhe depois de assinada apagaria a prova de como as coisas
     * estavam — que é precisamente o que ela serve para guardar.
     */
    private void recusarSeFechada(WorkOrder w, String o_que) {
        if (w.getStatus().isTerminal()) {
            throw ApiException.conflict("Uma ordem " + w.getStatus().label().toLowerCase()
                    + " já não aceita " + o_que + ".");
        }
    }

    private static String valorLegivel(WorkOrderMeasurement m) {
        if (m.getValueNum() != null) {
            return m.getValueNum().stripTrailingZeros().toPlainString()
                    + (m.getUnit() != null ? " " + m.getUnit() : "");
        }
        return m.getValueText();
    }

    private static String obrigatorio(String v, String mensagem) {
        if (v == null || v.isBlank()) {
            throw ApiException.badRequest(mensagem);
        }
        return v.trim();
    }

    private static String vazioParaNulo(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    // ==== Segurança (bloqueio e etiquetagem) ===============================

    /**
     * Regista o bloqueio e etiquetagem.
     *
     * <p>A hora é posta pelo servidor, nunca pelo cliente: numa auditoria de
     * segurança, uma hora que o próprio operador escreveu não prova nada.
     */
    @Transactional
    public WorkOrder saveSafety(
            String orgId, String userId, String workOrderId, SafetyRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "alterações de segurança");

        if (req.riskLevel() != null && !req.riskLevel().isBlank()) {
            w.setRiskLevel(valorDe(RiskLevel.class, req.riskLevel(), "nível de risco"));
        }
        w.setWorkPermitNumber(vazioParaNulo(req.workPermitNumber()));
        w.setPpeRequired(vazioParaNulo(req.ppeRequired()));
        if (req.safetyNotes() != null) {
            w.setSafetyNotes(vazioParaNulo(req.safetyNotes()));
        }
        w.setLotoTagNumber(vazioParaNulo(req.lotoTagNumber()));

        boolean bloquear = Boolean.TRUE.equals(req.lotoApplied());
        boolean estava = Boolean.TRUE.equals(w.getLotoApplied());

        if (bloquear && !estava) {
            if (w.getLotoTagNumber() == null) {
                throw ApiException.badRequest(
                        "Um bloqueio sem número de etiqueta não se consegue rastrear.");
            }
            w.setLotoApplied(Boolean.TRUE);
            w.setLotoAppliedByLabel(quemE(userId, req.appliedByLabel()));
            w.setLotoAppliedAt(Instant.now());
            w.setLotoRemovedAt(null);
            w.setLotoRemovedByLabel(null);
        } else if (!bloquear && estava) {
            w.setLotoApplied(Boolean.FALSE);
            w.setLotoRemovedByLabel(quemE(userId, req.removedByLabel()));
            w.setLotoRemovedAt(Instant.now());
        }

        audit.record(orgId, userId, "work_order.safety", "WorkOrder", w.getId(),
                w.getNumber() + " · bloqueio "
                        + (Boolean.TRUE.equals(w.getLotoApplied()) ? "aplicado" : "removido")
                        + (w.getLotoTagNumber() != null ? " · etiqueta " + w.getLotoTagNumber() : ""));
        return w;
    }

    // ==== Ensaio final =====================================================

    /**
     * Regista o ensaio feito antes de entregar.
     *
     * <p>Um ensaio reprovado não deixa a ordem seguir para concluída: é o único
     * ponto do sistema que impede devolver ao condutor um problema que a
     * oficina já sabia que continuava lá.
     */
    @Transactional
    public WorkOrder saveTest(
            String orgId, String userId, String workOrderId, TestRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "resultados de ensaio");

        w.setTestPerformed(Boolean.TRUE);
        if (req.kind() != null && !req.kind().isBlank()) {
            w.setTestKind(valorDe(TestKind.class, req.kind(), "tipo de ensaio"));
        }
        w.setTestDistanceKm(req.distanceKm());
        w.setTestDurationMinutes(req.durationMinutes());
        if (req.result() != null && !req.result().isBlank()) {
            w.setTestResult(valorDe(TestResult.class, req.result(), "resultado do ensaio"));
        }
        w.setTestNotes(vazioParaNulo(req.notes()));

        if (w.getTestResult() == TestResult.FAILED && w.getTestNotes() == null) {
            throw ApiException.badRequest(
                    "Um ensaio reprovado tem de dizer o que falhou.");
        }

        audit.record(orgId, userId, "work_order.test", "WorkOrder", w.getId(),
                w.getNumber() + " · ensaio "
                        + (w.getTestResult() != null ? w.getTestResult().label() : "registado"));
        return w;
    }

    // ==== Próxima intervenção ==============================================

    @Transactional
    public WorkOrder saveNextService(
            String orgId, String userId, String workOrderId, NextServiceRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        w.setNextServiceMeter(req.meter());
        w.setNextServiceHourMeter(req.hourMeter());
        w.setNextServiceAt(req.at());
        w.setNextServiceNote(vazioParaNulo(req.note()));

        if (req.meter() == null && req.hourMeter() == null && req.at() == null) {
            throw ApiException.badRequest(
                    "Indique pelo menos um limite: quilómetros, horas ou data.");
        }
        audit.record(orgId, userId, "work_order.next_service", "WorkOrder", w.getId(),
                w.getNumber() + " · próxima intervenção marcada");
        return w;
    }

    // ==== Codificação da avaria ============================================

    @Transactional
    public WorkOrder saveFailureCoding(
            String orgId, String userId, String workOrderId, FailureCodingRequest req) {

        WorkOrder w = require(orgId, workOrderId);
        recusarSeFechada(w, "codificação de avaria");
        w.setComponentCode(maiusculas(req.componentCode()));
        w.setFailureMode(maiusculas(req.failureMode()));
        w.setFailureCause(maiusculas(req.failureCause()));
        audit.record(orgId, userId, "work_order.failure_coding", "WorkOrder", w.getId(),
                w.getNumber() + " · " + (w.getComponentCode() != null ? w.getComponentCode() : "-")
                        + "/" + (w.getFailureMode() != null ? w.getFailureMode() : "-"));
        return w;
    }

    private String quemE(String userId, String indicado) {
        if (indicado != null && !indicado.isBlank()) {
            return indicado.trim();
        }
        return users.findById(userId).map(User::getName).orElse(null);
    }

    private static <E extends Enum<E>> E valorDe(Class<E> tipo, String valor, String campo) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Valor inválido para " + campo + ": " + valor);
        }
    }

    private static String maiusculas(String v) {
        String limpo = vazioParaNulo(v);
        return limpo == null ? null : limpo.toUpperCase();
    }
}
