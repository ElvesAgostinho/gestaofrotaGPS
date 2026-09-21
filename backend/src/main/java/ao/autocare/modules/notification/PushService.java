package ao.autocare.modules.notification;

import ao.autocare.domain.Notification;
import ao.autocare.domain.PushKeys;
import ao.autocare.domain.PushSubscription;
import ao.autocare.domain.User;
import ao.autocare.repo.PushKeysRepository;
import ao.autocare.repo.PushSubscriptionRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import nl.martijndwars.webpush.Subscription;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * As notificações que chegam ao telemóvel com a aplicação fechada.
 *
 * <p>É o que separa um aviso útil de um aviso que ninguém vê: o motorista não
 * tem a aplicação aberta quando o gestor lhe atribui uma rota, e o gestor não
 * está ao computador quando um camião parte a meio da estrada. O aviso tem de
 * ir ter com eles.
 *
 * <p>As chaves de identificação do servidor (VAPID) são geradas à primeira
 * utilização e guardadas: ninguém tem de configurar nada para isto funcionar,
 * o que numa empresa pequena é a diferença entre usar e não usar.
 *
 * <p>Um envio que falhe nunca estraga o que o originou. Se o aparelho já não
 * existe — telemóvel formatado, aplicação desinstalada — a subscrição é
 * apagada: insistir para sempre com uma morada morta é um erro que só se
 * descobre com a base de dados cheia.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final PushSubscriptionRepository subscriptions;
    private final PushKeysRepository keys;

    public PushService(PushSubscriptionRepository subscriptions, PushKeysRepository keys) {
        this.subscriptions = subscriptions;
        this.keys = keys;
    }

    /** A chave pública desta instalação, que o browser precisa para subscrever. */
    @Transactional
    public String publicKey() {
        return chaves().getPublicKey();
    }

    @Transactional
    public PushKeys chaves() {
        return keys.findAll().stream().findFirst().orElseGet(this::gerar);
    }

    private PushKeys gerar() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
            g.initialize(new ECGenParameterSpec("prime256v1"), new SecureRandom());
            KeyPair par = g.generateKeyPair();
            PushKeys k = new PushKeys();
            k.setPublicKey(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Utils.encode((org.bouncycastle.jce.interfaces.ECPublicKey) par.getPublic())));
            k.setPrivateKey(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Utils.encode((org.bouncycastle.jce.interfaces.ECPrivateKey) par.getPrivate())));
            k.setSubject("mailto:suporte@imbondeiro.ao");
            return keys.save(k);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar as chaves de notificação.", e);
        }
    }

    /** Guarda (ou actualiza) o aparelho desta pessoa. */
    @Transactional
    public void subscrever(User user, String orgId, String endpoint, String p256dh,
            String auth, String userAgent, java.util.function.Function<String, ao.autocare.domain.Organization> org) {
        PushSubscription s = subscriptions.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        s.setUser(user);
        if (orgId != null) {
            s.setOrganization(org.apply(orgId));
        }
        s.setEndpoint(endpoint);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        s.setUserAgent(userAgent != null && userAgent.length() > 300
                ? userAgent.substring(0, 300) : userAgent);
        subscriptions.save(s);
    }

    @Transactional
    public void remover(String userId, String endpoint) {
        subscriptions.findByEndpoint(endpoint)
                .filter(s -> s.getUser().getId().equals(userId))
                .ifPresent(subscriptions::delete);
    }

    /**
     * Envia um aviso para todos os aparelhos de uma pessoa.
     *
     * <p>Corre numa transacção própria e engole os erros: uma notificação que
     * não sai não pode impedir a ordem de serviço de ser criada.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int enviar(Notification n) {
        List<PushSubscription> aparelhos = subscriptions.findByUserId(n.getUser().getId());
        if (aparelhos.isEmpty()) {
            return 0;
        }
        PushKeys k = chaves();
        String payload = json(n);
        int enviados = 0;
        for (PushSubscription s : aparelhos) {
            try {
                nl.martijndwars.webpush.PushService push = new nl.martijndwars.webpush.PushService(
                        k.getPublicKey(), k.getPrivateKey(), k.getSubject());
                var resposta = push.send(new nl.martijndwars.webpush.Notification(
                        new Subscription(s.getEndpoint(),
                                new Subscription.Keys(s.getP256dh(), s.getAuth())),
                        payload));
                int estado = resposta.getStatusLine().getStatusCode();
                if (estado == 404 || estado == 410) {
                    // O aparelho já não existe: apagar em vez de insistir.
                    subscriptions.delete(s);
                } else if (estado >= 200 && estado < 300) {
                    s.setLastUsedAt(Instant.now());
                    enviados++;
                } else {
                    log.debug("Push recusado ({}) para {}", estado, s.getEndpoint());
                }
            } catch (Exception e) {
                log.debug("Push falhou: {}", e.getMessage());
            }
        }
        return enviados;
    }

    /** O que o service worker recebe e mostra. */
    private static String json(Notification n) {
        return "{\"title\":" + texto(n.getTitle())
                + ",\"body\":" + texto(n.getBody())
                + ",\"link\":" + texto(n.getLink())
                + ",\"severity\":" + texto(n.getSeverity() != null ? n.getSeverity().name() : "INFO")
                + "}";
    }

    private static String texto(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ") + "\"";
    }
}
