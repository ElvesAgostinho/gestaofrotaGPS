package ao.autocare.security;

import ao.autocare.domain.enums.Enums.MembershipRole;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Papel mínimo exigido para chamar este endpoint. Aplicado a um método de
 * controlador ou à classe inteira (o método tem prioridade sobre a classe).
 *
 * <p>A verificação é feita pelo {@link RoleInterceptor}. Sem anotação, basta
 * estar autenticado — é o caso das leituras, que qualquer membro pode fazer.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    MembershipRole value();
}
