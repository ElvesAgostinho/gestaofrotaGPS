package ao.autocare.domain.enums;

/**
 * Enumerações do domínio. Persistidas como texto ({@code @Enumerated(EnumType.STRING)})
 * para manter as migrações portáveis entre H2 e PostgreSQL.
 */
public final class Enums {

    private Enums() {}

    public enum UserTheme { SYSTEM, LIGHT, DARK }

    public enum OrganizationType { PERSONAL, COMPANY, WORKSHOP, FLEET }

    /**
     * Papel de um utilizador dentro da empresa, do mais forte para o mais fraco.
     * O {@code rank} permite comparar permissões: quem tem um papel de rank igual
     * ou superior ao exigido pode executar a ação.
     *
     * <ul>
     *   <li>{@code OWNER} — dono: tudo, incluindo gerir a equipa e a empresa.</li>
     *   <li>{@code MANAGER} — gestor de manutenção: ativos, planos, peças, aprovar ordens.</li>
     *   <li>{@code TECHNICIAN} — técnico: executa ordens, leituras e checklists.</li>
     *   <li>{@code VIEWER} — consulta: apenas leitura.</li>
     *   <li>{@code DRIVER} — legado (domínio antigo de viaturas); equivale a técnico.</li>
     * </ul>
     */
    public enum MembershipRole {
        OWNER(40, "Dono"),
        MANAGER(30, "Gestor"),
        TECHNICIAN(20, "Técnico"),
        DRIVER(20, "Condutor"),
        VIEWER(10, "Consulta");

        private final int rank;
        private final String label;

        MembershipRole(int rank, String label) {
            this.rank = rank;
            this.label = label;
        }

        public int rank() {
            return rank;
        }

        /** Nome do papel em português, para mostrar na interface. */
        public String label() {
            return label;
        }

        /** {@code true} se este papel cobre tudo o que {@code required} permite. */
        public boolean covers(MembershipRole required) {
            return rank >= required.rank;
        }
    }

    public enum VerificationChannel { EMAIL, SMS }

    public enum FuelType { PETROL, DIESEL, HYBRID, ELECTRIC, LPG, OTHER }

    public enum Transmission { MANUAL, AUTOMATIC }

    public enum MileageSource { MANUAL, GPS, OBD, API }

    public enum DocumentType {
        INSURANCE, REGISTRATION, INSPECTION, LICENSE, LICENSING, TAX, OTHER
    }

    public enum AlertSeverity { INFO, WARNING, CRITICAL }

    /**
     * Categorias de alerta. As primeiras vêm do esboço da Fase 1 e mantêm-se
     * por causa das preferências já gravadas; {@code STOCK} e {@code WORK_ORDER}
     * entraram com o CMMS.
     */
    public enum AlertCategory {
        MAINTENANCE, DOCUMENT, GPS, EXPENSE, TIRE, INSURANCE, INSPECTION, BATTERY, SYSTEM,
        STOCK, WORK_ORDER
    }

    /** Como correu o envio por email de uma notificação. */
    public enum EmailState { NOT_REQUESTED, DEMO_MODE, SENT, FAILED }

    public enum MaintenanceKind { PREVENTIVE, CORRECTIVE }

    public enum MaintenanceCategory {
        ENGINE, TRANSMISSION, BRAKES, SUSPENSION, STEERING, TIRES, ELECTRICAL, AC, EXTERIOR, OTHER
    }

    public enum ExpenseCategory {
        FUEL, MAINTENANCE, TIRES, INSURANCE, TAX, WASH, PARKING, TOLLS, FINE, PARTS, WORKSHOP, OTHER
    }

    public enum TirePosition { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, SPARE, ALL }

    public enum CoverageType { THIRD_PARTY, COMPREHENSIVE, OTHER }

    public enum InspectionResult { PASS, PASS_WITH_DEFECTS, FAIL }

    public enum FineStatus { PENDING, PAID, CONTESTED }

    // GpsAdapter / GpsDeviceStatus / GeofenceType do esboço da Fase 1 foram
    // substituídos pelos da telemetria (Fatia 4), mais abaixo. Nunca chegaram
    // a ser usados por código nenhum.

    public enum NotificationChannel { PUSH, EMAIL, SMS, WHATSAPP }

    public enum PlanCode { FREE, PERSONAL, GPS, FLEET }

    public enum SubscriptionStatus { TRIALING, ACTIVE, PAST_DUE, CANCELED }

    public enum PaymentProvider { MULTICAIXA_EXPRESS, REFERENCE, CARD, TRANSFER, MANUAL }

    public enum PaymentStatus { PENDING, PAID, FAILED, REFUNDED }

    // ---------------------------------------------------------------------
    // CMMS (Fase 2+) — gestão de manutenção de frotas e ativos
    // ---------------------------------------------------------------------

    /** Local na hierarquia empresa → obra → ... */
    /**
     * {@code BRANCH} e uma filial: a unidade por onde os custos da frota sao
     * somados. Nao tem tabela propria porque uma filial <b>e</b> um local --
     * com hierarquia, coordenadas e ativos ja ligados. Uma segunda hierarquia
     * paralela obrigaria a decidir, em cada conta de custos, qual delas manda.
     */
    public enum LocationKind { BRANCH, SITE, YARD, WAREHOUSE, DEPARTMENT, OTHER }

    /** Situacao de um motorista. Suspenso nao conduz, mas o historico fica. */
    public enum DriverStatus { ACTIVE, SUSPENDED, INACTIVE }

    /** Natureza do ativo. */
    /**
     * Familia do ativo.
     *
     * <p>Serve para agrupar a frota: um camiao, uma retroescavadora e um
     * gerador nao se gerem da mesma maneira nem se comparam entre si. Ve-los
     * misturados na mesma lista e o que faz um gestor perder a nocao do que
     * tem.
     */
    public enum AssetCategory {
        VEHICLE("Viaturas", "Camiões, ligeiros e reboques"),
        MACHINE("Máquinas", "Equipamento de movimentação de terras e obra"),
        GENERATOR("Geradores", "Grupos electrogéneos e energia"),
        IMPLEMENT("Alfaias e implementos", "Equipamento acoplável"),
        OTHER("Outros", "O que não cabe nas famílias acima");

        private final String label;
        private final String description;

        AssetCategory(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        /** Ordem em que as familias aparecem nos ecras. */
        public int sortOrder() {
            return ordinal();
        }
    }

    /** Tipo de medidor de utilização. */
    public enum MeterKind { HOURMETER, ODOMETER }

    /** Origem de uma leitura de medidor. */
    public enum MeterReadingSource { MANUAL, TELEMETRY, WORK_ORDER, IMPORT }

    /** Estado operacional de um ativo. */
    public enum AssetStatus { OPERATIONAL, MAINTENANCE, DOWN, STANDBY, RETIRED }

    /** Criticidade geral do ativo (do documento de referência). */
    public enum CriticalityLevel { LOW, MEDIUM, HIGH, CRITICAL }

    /**
     * Sistemas do ativo do documento de referência. É apenas o conjunto
     * sugerido — a coluna {@code asset_systems.code} aceita códigos próprios.
     */
    public enum AssetSystemCode {
        ENGINE, HYDRAULIC, FUEL, TRANSMISSION, AXLES, ELECTRICAL, BRAKES, STRUCTURE, OTHER
    }

    // --- Checklists de inspeção (Fase 3) ---

    /** Tipo de verificação de um item de checklist (coluna do documento). */
    public enum VerificationType { VERIFY, INSPECT, TEST }

    /** Resultado global de uma inspeção. */
    public enum ChecklistOutcome { OK, ISSUES }

    /** Resultado de um item de inspeção. */
    public enum ChecklistItemResult { OK, NOT_OK, NA }

    // --- Planos de manutenção preventiva (Fase 3) ---

    /** Como uma tarefa do plano é despoletada. */
    public enum PlanTriggerType {
        /** A cada N unidades do medidor (ex.: cada 250 h). */
        METER_INTERVAL,
        /** A cada N dias de calendário. */
        CALENDAR_DAYS
    }

    /** Estado de vencimento de uma tarefa de plano para um ativo. */
    public enum PlanTaskStatus { OK, DUE_SOON, OVERDUE }

    // --- Ficheiros e fotografias (Fatia 1) ---

    /** Natureza de uma fotografia de ativo. */
    /**
     * O que a fotografia mostra.
     *
     * <p>Uma pasta de fotografias soltas nao diz nada a quem nao esteve la. Por
     * parte, dizem: o gestor ve o estado do motor, dos pneus e da cabine sem
     * sair do escritorio -- e, quando a maquina volta da oficina, ha com o que
     * comparar.
     */
    public enum PhotoKind {
        GENERAL("Vista geral"),
        ENGINE("Motor"),
        CABIN("Cabine"),
        TYRES("Pneus e rodado"),
        HYDRAULIC("Sistema hidráulico"),
        ELECTRICAL("Sistema elétrico"),
        CHASSIS("Chassi e estrutura"),
        ATTACHMENT("Implemento ou concha"),
        PLATE("Matrícula"),
        METER("Contador"),
        DAMAGE("Dano"),
        DOCUMENT("Documento");

        private final String label;

        PhotoKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    // --- Coordenadas / peças / stock (Fatia 3) ---

    /** Origem da posição geográfica de um ativo. */
    public enum PositionSource { MANUAL, TELEMETRY, IMPORT }

    // ---- Telemetria / GPS (Fatia 4) ----------------------------------

    /**
     * Origem das posições. {@code DEMO} é o simulador interno usado quando não
     * há nenhum fornecedor configurado — nunca se apresenta como dados reais.
     */
    public enum TelemetryProviderKind { GENERIC, TRACCAR, DEMO }

    /**
     * Estado do aparelho, derivado de {@code lastSeenAt}:
     * ONLINE (visto há pouco), IDLE (parado mas a comunicar), OFFLINE (calado),
     * NEVER_SEEN (registado e ainda sem qualquer posição).
     */
    public enum GpsDeviceStatus { ONLINE, IDLE, OFFLINE, NEVER_SEEN }

    public enum GeofenceKind { CIRCLE, POLYGON }

    public enum GeofenceEventType { ENTER, EXIT }

    /**
     * Alertas gerados pela telemetria.
     *
     * <ul>
     *   <li>{@code SPEEDING} — excesso de velocidade face ao limite aplicável.</li>
     *   <li>{@code COMMS_LOST} — o aparelho deixou de comunicar. É o primeiro
     *       sinal de avaria ou de sabotagem, por isso é um alerta e não apenas
     *       um estado no ecrã.</li>
     * </ul>
     */
    public enum TelemetryAlertKind { SPEEDING, COMMS_LOST }

    // ---- Conducao (Fatia 14) -----------------------------------------

    /**
     * Infracoes de conducao.
     *
     * <p>{@code OVERSPEED} nao e detetado aqui: vem do alerta de telemetria que
     * ja aplica os limites por zona, ativo e empresa. Duplicar a deteccao daria
     * dois numeros diferentes para o mesmo excesso.
     */
    public enum DrivingEventKind {
        OVERSPEED("Excesso de velocidade"),
        HARSH_BRAKE("Travagem brusca"),
        HARSH_ACCELERATION("Aceleracao brusca"),
        HARSH_CORNERING("Curva brusca"),
        IDLING("Motor ao ralenti"),
        NIGHT_DRIVING("Conducao noturna");

        private final String label;

        DrivingEventKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** De onde veio o nome de um sitio numa viagem. */
    public enum PlaceKind { BRANCH, LOCATION, GEOFENCE, UNKNOWN }

    /** Faixa de pontuacao de conducao. */
    public enum ScoreBand {
        EXCELLENT("Excelente"),
        GOOD("Bom"),
        NEEDS_IMPROVEMENT("A melhorar"),
        CRITICAL("Critico");

        private final String label;

        ScoreBand(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    // ---- Manutenção preditiva (Fatia 7) ------------------------------

    /**
     * Técnicas de monitorização de condição. As três primeiras são as do
     * documento de referência; as restantes são comuns em frotas e geradores.
     */
    public enum PredictiveTechnique {
        VIBRATION("Análise de vibração", 1),
        THERMOGRAPHY("Termografia", 3),
        OIL_ANALYSIS("Análise de óleo", 6),
        ULTRASOUND("Ultrassom", 6),
        ALIGNMENT("Alinhamento e balanceamento", 12),
        INSULATION("Resistência de isolamento", 12),
        OTHER("Outra técnica", 6);

        private final String label;
        private final int defaultFrequencyMonths;

        PredictiveTechnique(String label, int defaultFrequencyMonths) {
            this.label = label;
            this.defaultFrequencyMonths = defaultFrequencyMonths;
        }

        public String label() {
            return label;
        }

        /** Periodicidade habitual, usada quando não é indicada outra. */
        public int defaultFrequencyMonths() {
            return defaultFrequencyMonths;
        }
    }

    /**
     * Resultado de uma medição. É o que separa medir de decidir: um valor só
     * vale alguma coisa quando alguém o classifica.
     */
    public enum PredictiveResult { NORMAL, ATTENTION, CRITICAL }

    // ---- Documentos do ativo (Fatia 8) --------------------------------

    /** Tipo de documento, com o nome que se usa na interface. */
    // ---- Comandos ao aparelho (Fatia 13) ------------------------------

    /**
     * Comandos que se podem enviar a um aparelho.
     *
     * <p>{@code ENGINE_STOP} impede o <b>próximo arranque</b>; não mata o motor
     * em andamento. Essa é a única forma segura de o fazer e é o que os
     * imobilizadores sérios implementam — cortar um motor em movimento tira a
     * direção assistida e os travões assistidos a quem vai ao volante.
     */
    public enum DeviceCommandKind { ENGINE_STOP, ENGINE_RESUME }

    /**
     * Ciclo de vida de um comando. Sem confirmação de volta do aparelho não se
     * sabe se a viatura está bloqueada — por isso {@code SENT} e
     * {@code CONFIRMED} são estados distintos e o ecrã tem de os distinguir.
     *
     * <ul>
     *   <li>{@code PENDING_APPROVAL} — pedido, à espera do segundo passo.</li>
     *   <li>{@code QUEUED} — aprovado, à espera de a viatura estar parada.</li>
     *   <li>{@code SENT} — entregue ao fornecedor; ainda sem resposta do aparelho.</li>
     *   <li>{@code CONFIRMED} — o aparelho confirmou que executou.</li>
     *   <li>{@code FAILED} — o fornecedor ou o aparelho recusaram.</li>
     *   <li>{@code EXPIRED} — caducou sem chegar a ser enviado.</li>
     *   <li>{@code CANCELLED} — anulado por uma pessoa.</li>
     * </ul>
     */
    /**
     * Estado de um comando enviado ao aparelho.
     *
     * <p>{@code UNCONFIRMED} e {@code SUPERSEDED} existem porque a alternativa
     * era pior: um comando preso em {@code SENT} para sempre — o que acontece
     * com os protocolos que nunca reportam nada — impedia qualquer comando
     * novo, <b>incluindo o desbloqueio</b>. Uma viatura que não se consegue
     * desbloquear é exatamente o perigo que este módulo existe para evitar.
     */
    public enum DeviceCommandStatus {
        PENDING_APPROVAL,
        QUEUED,
        SENT,
        CONFIRMED,
        FAILED,
        EXPIRED,
        CANCELLED,
        /** Enviado, mas o aparelho nunca deu prova de o ter executado. */
        UNCONFIRMED,
        /** Substituído por um comando posterior — tipicamente um desbloqueio. */
        SUPERSEDED
    }

    /**
     * De onde veio a confirmação de que o aparelho executou o comando.
     *
     * <p>A distinção não é burocrática: {@code MANUAL} significa que uma pessoa
     * afirmou que a viatura está bloqueada <b>sem prova nenhuma</b> vinda do
     * aparelho. Uma imobilização confirmada assim não vale como garantia, e o
     * ecrã tem de o dizer.
     */
    public enum CommandConfirmationSource {
        /** O aparelho reportou o seu estado (atributo blocked na posição). */
        DEVICE_ATTRIBUTE,
        /** O Traccar registou um resultado de comando vindo do aparelho. */
        TRACCAR_EVENT,
        /** Alguém afirmou, sem confirmação do aparelho. */
        MANUAL
    }

    /**
     * Motivo do bloqueio, por categoria. O texto livre continua obrigatório —
     * isto serve para filtrar, contar, e para o motivo não se resumir a "teste".
     */
    public enum LockReasonCategory {
        THEFT("Furto ou roubo"),
        NON_PAYMENT("Incumprimento de pagamento"),
        MAINTENANCE("Manutenção ou segurança do equipamento"),
        UNAUTHORISED_USE("Utilização não autorizada"),
        ADMINISTRATIVE("Ordem administrativa"),
        RECOVERY("Recuperação do bem"),
        OTHER("Outro");

        private final String label;

        LockReasonCategory(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    // ---- Combustível (Fatia 12) ---------------------------------------

    /**
     * Origem do registo de combustível.
     *
     * <p>{@code MANUAL} é o abastecimento lançado por uma pessoa: dá
     * contabilidade de consumo, não deteção de furto em tempo real.
     * {@code SENSOR} fica reservado para leitura automática de nível, que exige
     * calibração ponto a ponto do depósito para os litros fazerem sentido.
     */
    public enum FuelSource { MANUAL, SENSOR }

    /**
     * O que pode estar mal num abastecimento.
     *
     * <p>Cada regra responde a uma forma concreta de o combustivel desaparecer
     * ou de as contas nao baterem certo. Nenhuma delas prova furto sozinha -- e
     * e por isso que o texto de cada uma pede verificacao em vez de acusar.
     */
    public enum FuelAnomalyKind {
        VOLUME_EXCEEDS_TANK("Litros acima da capacidade do deposito"),
        CONSUMPTION_SPIKE("Consumo muito acima da base deste ativo"),
        DISTANCE_MISMATCH("Quilometros declarados nao batem com o GPS"),
        REFUEL_AWAY_FROM_VEHICLE("Abastecimento longe de onde a viatura estava"),
        DUPLICATE_REFUEL("Dois abastecimentos quase ao mesmo tempo"),
        ODOMETER_ROLLBACK("Medidor a recuar"),
        REFUEL_WITHOUT_MOVEMENT("Abasteceu sem ter andado"),
        MISSING_ODOMETER("Sem leitura de medidor: consumo por calcular"),
        SENSOR_DRAIN("Nível do depósito caiu com a viatura parada"),
        SENSOR_MISMATCH("O sensor não confirma os litros declarados");

        private final String label;

        FuelAnomalyKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Ciclo de vida de uma anomalia de combustivel. */
    public enum AnomalyStatus {
        OPEN("Por analisar"),
        CONFIRMED("Confirmada"),
        DISMISSED("Sem fundamento"),
        RESOLVED("Resolvida");

        private final String label;

        AnomalyStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum DocumentKind {
        MANUAL("Manual"),
        INSURANCE("Seguro"),
        INSPECTION("Inspeção"),
        REGISTRATION("Livrete / registo"),
        LICENSE("Licença"),
        CERTIFICATE("Certificado"),
        CONTRACT("Contrato"),
        INVOICE("Fatura"),
        WARRANTY("Garantia"),
        OTHER("Outro");

        private final String label;

        DocumentKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Categoria de uma peça. */
    public enum PartCategory {
        GENERAL, FILTER, LUBRICANT, WEAR, ELECTRICAL, HYDRAULIC, ENGINE, TIRE, BATTERY, CONSUMABLE
    }

    /** Tipo de movimento de stock. */
    public enum StockMovementType {
        IN, OUT_WORK_ORDER, OUT_OTHER, ADJUSTMENT, TRANSFER_IN, TRANSFER_OUT
    }

    // --- Ordens de Manutenção (Fatia 3b) ---

    /**
     * Natureza da intervencao.
     *
     * <p>Os tres primeiros existiam desde o inicio; os restantes entraram com o
     * modulo de manutencao completo. Separar EMERGENCY de CORRECTIVE nao e
     * burocracia: uma avaria que para a obra e uma que se resolve na proxima
     * semana tem prazos, aprovacoes e custos de paragem diferentes.
     */
    public enum WorkOrderType {
        PREVENTIVE("Preventiva"),
        CORRECTIVE("Corretiva"),
        INSPECTION("Inspeção"),
        PREDICTIVE("Preditiva"),
        EMERGENCY("Emergencial"),
        OVERHAUL("Revisão");

        private final String label;

        WorkOrderType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Quem executa: a equipa da casa ou uma oficina de fora. */
    public enum WorkOrderExecution { INTERNAL, EXTERNAL }

    /** Natureza de um fornecedor. */
    public enum SupplierKind {
        WORKSHOP("Oficina"),
        PARTS("Pecas"),
        BOTH("Oficina e pecas"),
        OTHER("Outro");

        private final String label;

        SupplierKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Natureza de um anexo de ordem de manutencao. */
    public enum WorkOrderAttachmentKind {
        BEFORE("Antes"),
        AFTER("Depois"),
        INVOICE("Fatura"),
        REPORT("Relatorio"),
        PART("Peca"),
        OTHER("Outro");

        private final String label;

        WorkOrderAttachmentKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Estados de uma ordem de manutencao.
     *
     * <p>Os seis primeiros ja existiam e continuam a funcionar exatamente como
     * antes -- abrir, iniciar, concluir, verificar. Os restantes acrescentam o
     * percurso completo de uma empresa que orcamenta e aprova antes de mandar
     * reparar. Uma ordem simples nunca passa por eles.
     *
     * <p>{@code AWAITING_PARTS} existe por uma razao pratica: sem ele, uma
     * viatura parada tres semanas a espera de uma peca aparece como "em
     * manutencao", e nenhuma media de tempo de reparacao explica porque e que
     * demorou tanto.
     */
    public enum WorkOrderStatus {
        OPEN("Aberta"),
        PLANNED("Planeada"),
        DIAGNOSIS("Em diagnóstico"),
        QUOTING("Em orçamento"),
        AWAITING_APPROVAL("A aguardar aprovação"),
        APPROVED("Aprovada"),
        IN_PROGRESS("Em manutenção"),
        AWAITING_PARTS("A aguardar peças"),
        TESTING("Em teste"),
        DONE("Concluída"),
        VERIFIED("Verificada"),
        CLOSED("Fechada"),
        REJECTED("Rejeitada"),
        CANCELLED("Anulada");

        private final String label;

        WorkOrderStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Ja nao se mexe: fechada ou anulada. */
        public boolean isTerminal() {
            return this == CLOSED || this == CANCELLED;
        }
    }

    public enum WorkOrderPriority {
        LOW("Baixa"),
        NORMAL("Normal"),
        HIGH("Alta"),
        URGENT("Urgente");

        private final String label;

        WorkOrderPriority(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }


    // --- Chao de oficina (Fatia 19) ---

    /** Veredicto de uma medicao face aos limites de servico. */
    public enum MeasurementVerdict {
        OK("Dentro do limite"),
        ATTENTION("Perto do limite"),
        REPLACE("Fora do limite — substituir"),
        FAIL("Reprovado");

        private final String label;

        MeasurementVerdict(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Obriga a agir: ou se substitui, ou nao sai da oficina. */
        public boolean needsAction() {
            return this == REPLACE || this == FAIL;
        }
    }

    /** Tipo de fluido ou lubrificante. */
    public enum FluidKind {
        ENGINE_OIL("Óleo do motor"),
        TRANSMISSION("Óleo da transmissão"),
        HYDRAULIC("Óleo hidráulico"),
        DIFFERENTIAL("Óleo do diferencial"),
        COOLANT("Líquido de refrigeração"),
        BRAKE_FLUID("Líquido dos travões"),
        GREASE("Massa lubrificante"),
        FUEL("Combustível"),
        ADBLUE("AdBlue"),
        OTHER("Outro fluido");

        private final String label;

        FluidKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** O que se fez ao fluido. */
    public enum FluidAction {
        REPLACED("Substituído"),
        ADDED("Acrescentado"),
        TOPPED_UP("Atestado"),
        DRAINED("Drenado"),
        SAMPLED("Recolhida amostra");

        private final String label;

        FluidAction(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** De onde saiu o codigo de avaria. */
    public enum FaultCodeSource {
        J1939("J1939 (pesados)"),
        OBD2("OBD-II (ligeiros)"),
        PANEL("Painel do equipamento"),
        MANUFACTURER("Diagnóstico do fabricante"),
        OTHER("Outra origem");

        private final String label;

        FaultCodeSource(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Estado de um codigo de avaria. */
    public enum FaultCodeStatus {
        ACTIVE("Ativo"),
        STORED("Memorizado"),
        CLEARED("Apagado");

        private final String label;

        FaultCodeStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Quem assina a ordem. */
    public enum SignatureRole {
        TECHNICIAN("Técnico executante"),
        SUPERVISOR("Responsável da oficina"),
        QUALITY("Controlo de qualidade"),
        OPERATOR("Operador ou motorista"),
        CLIENT("Cliente");

        private final String label;

        SignatureRole(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Nivel de risco da intervencao. */
    public enum RiskLevel {
        LOW("Baixo"),
        MEDIUM("Médio"),
        HIGH("Alto"),
        CRITICAL("Crítico");

        private final String label;

        RiskLevel(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Acima disto exige bloqueio e etiquetagem antes de comecar. */
        public boolean requiresLoto() {
            return this == HIGH || this == CRITICAL;
        }
    }

    /** Ensaio feito antes de entregar. */
    public enum TestKind {
        ROAD("Ensaio de estrada"),
        LOAD("Prova de carga"),
        BENCH("Ensaio em banco"),
        FUNCTIONAL("Ensaio funcional");

        private final String label;

        TestKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Resultado do ensaio final. */
    public enum TestResult {
        PASSED("Aprovado"),
        PARTIAL("Aprovado com reservas"),
        FAILED("Reprovado");

        private final String label;

        TestResult(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }


    // --- Guia de transporte (Fatia 21) ---

    /** Percurso de uma guia de transporte. */
    public enum TransportNoteStatus {
        DRAFT("Rascunho"),
        ISSUED("Emitida"),
        IN_TRANSIT("Em trânsito"),
        DELIVERED("Entregue"),
        CANCELLED("Anulada");

        private final String label;

        TransportNoteStatus(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Períodos de alerta de caducidade por omissão (dias antes). */
    public static final int[] DEFAULT_EXPIRY_LEAD_DAYS = {60, 30, 15, 7, 1, 0};
}
