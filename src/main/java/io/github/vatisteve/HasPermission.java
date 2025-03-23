package io.github.vatisteve;

import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotate which method or class need to be checked permission
 *
 * @author tinhnv - Jan 15 2025
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HasPermission {

    /**
     * The identifier which is used to get permissions
     * <b>SpEL</b>
     * @see SpelExpressionParser
     */
    String subject() default "";

    /**
     * alias for {@link #of()}
     */
    String value() default "";

    /**
     * must have this permission
     */
    String of() default "";

    /**
     * must have all of these permissions
     */
    String[] allOf() default {};

    /**
     * must have one of these permissions
     */
    String[] anyOf() default {};

}