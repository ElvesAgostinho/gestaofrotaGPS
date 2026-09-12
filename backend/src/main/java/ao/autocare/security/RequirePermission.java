package ao.autocare.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * O endpoint exige uma permissão de módulo.
 *
 * <p>Substitui o {@link RequireRole} onde a ação tem um nome que o cliente
 * reconhece — «imobilizar viaturas», «ver custos» — e que pode querer dar a
 * alguém fora do papel habitual. Um contabilista que vê custos sem ser gestor
 * é o caso típico.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequirePermission {
    Permission value();
}
