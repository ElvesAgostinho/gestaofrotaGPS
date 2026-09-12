package ao.autocare.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Entidade com versão, para gravações concorrentes não se apagarem em silêncio.
 *
 * <p>Dois gestores abrem a mesma ficha. O segundo a gravar escrevia por cima
 * do primeiro sem ninguém dar por isso — o «guardado» dele era mentira. Com a
 * versão, o Hibernate compara ao gravar e recusa se o registo mudou entretanto;
 * o serviço traduz isso numa frase que diz o que fazer.
 *
 * <p>É a versão do <b>registo</b>, não das linhas filhas: mudar uma tarefa de
 * uma ordem não incrementa a ordem. Chega para o que interessa — os campos da
 * ficha — sem transformar cada toque numa colisão.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class VersionedEntity extends TimestampedEntity {

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
