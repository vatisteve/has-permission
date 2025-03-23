package io.github.vatisteve;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * @author tinhnv - Mar 23 2025
 */
@Slf4j
@Aspect
public class HasPermissionAuthorizer<T extends Serializable> {

    private final PermissionService<T> permissionService;
    private final String defaultSubjectPropertyName;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public HasPermissionAuthorizer(PermissionService<T> permissionService, String defaultSubjectPropertyName) {
        Objects.requireNonNull(permissionService, "permissionService cannot be null");
        Objects.requireNonNull(defaultSubjectPropertyName, "subjectPropertyName cannot be null");
        this.permissionService = permissionService;
        this.defaultSubjectPropertyName = defaultSubjectPropertyName;
    }

    @Pointcut("@annotation(hasPermission)")
    public void hasPermissionOnMethod(HasPermission hasPermission) {}

    @Pointcut("@within(hasPermission)")
    public void hasPermissionOnClass(HasPermission hasPermission) {}

    @Before(value = "hasPermissionOnMethod(hasPermission)", argNames = "joinPoint,hasPermission")
    public void checkPermissionOnMethod(JoinPoint joinPoint, HasPermission hasPermission) {
        T subjectValue = getSubject(hasPermission.subject(), joinPoint);
        checkPermission(hasPermission, subjectValue, joinPoint.getSignature().getName());
    }

    @Before(value = "hasPermissionOnClass(hasPermission)", argNames = "joinPoint,hasPermission")
    public void checkPermissionOnClass(JoinPoint joinPoint, HasPermission hasPermission) {
        T subjectValue = joinPoint.getKind().equals(JoinPoint.METHOD_EXECUTION)
                ? getSubject(hasPermission.subject(), joinPoint)
                : null;
        checkPermission(hasPermission, subjectValue, joinPoint.getSignature().getName());
    }

    private void checkPermission(final HasPermission hasPermission, final T subjectValue, String signatureName) {
        if (subjectValue == null) {
            log.warn("Subject value is null for {}, cannot check permissions", signatureName);
            throw new PermissionDeniedException("Cannot determine subject for permission check on " + signatureName);
        }
        if (doCheckPermission(hasPermission, subjectValue)) return;
        throw new PermissionDeniedException(String.format("Access denied for subject [%s] on job [%s]", subjectValue, signatureName));
    }

    private boolean doCheckPermission(final HasPermission context, final T subjectValue) {
        // Normalize "of" value, preferring explicit "of" over "value"
        String of = context.of().isEmpty() ? context.value() : context.of();
        log.debug("Checking permissions for subject [{}]", subjectValue);
        Set<String> permissions = permissionService.getPermissions(subjectValue);
        log.trace("Permissions found: {}", permissions);
        if (of.isEmpty() && context.allOf().length == 0 && context.anyOf().length == 0) {
            log.debug("No permission constraints specified, allowing access");
            return true;
        }
        if (!of.isEmpty() && !permissions.contains(of)) {
            log.debug("Missing required permission [{}] for subject [{}]", of, subjectValue);
            return false;
        }
        if (context.allOf().length > 0) {
            Set<String> allOfSet = Set.of(context.allOf());
            if (!permissions.containsAll(allOfSet)) {
                log.debug("Subject [{}] is missing some required permissions from: {}", subjectValue, allOfSet);
                return false;
            }
        }
        if (context.anyOf().length > 0) {
            Set<String> anyOfSet = Set.of(context.anyOf());
            if (Collections.disjoint(permissions, anyOfSet)) {
                log.debug("Subject [{}] has none of the required permissions from: {}", subjectValue, anyOfSet);
                return false;
            }
        }
        log.debug("Permission check passed for subject [{}]", subjectValue);
        return true;
    }

    @SuppressWarnings("unchecked")
    private T getSubject(String expression, JoinPoint joinPoint) {
        if (expression == null || expression.isEmpty()) {
            expression = "#" + defaultSubjectPropertyName;
            log.trace("Using default subject expression: {}", expression);
        }
        try {
            StandardEvaluationContext context = createEvaluationContext(joinPoint);
            Expression exp = expressionParser.parseExpression(expression);
            Object value = exp.getValue(context);
            if (value == null) {
                log.warn("Subject expression [{}] evaluated to null", expression);
                return null;
            }
            return (T) value;
        } catch (ParseException e) {
            log.error("Failed to parse expression [{}]: {}", expression, e.getMessage());
            return null;
        } catch (EvaluationException e) {
            log.error("Failed to evaluate expression [{}]: {}", expression, e.getMessage());
            return null;
        } catch (ClassCastException e) {
            log.error("Expression [{}] result cannot be cast to expected type: {}", expression, e.getMessage());
            return null;
        }
    }

    private StandardEvaluationContext createEvaluationContext(JoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (paramNames != null && args != null && paramNames.length == args.length) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        } else {
            log.warn("Cannot bind method parameters for {}", signature);
        }
        context.setVariable("method", signature.getMethod());
        context.setVariable("methodName", signature.getName());
        context.setVariable("returnType", signature.getReturnType());
        context.setVariable("target", joinPoint.getTarget());
        context.setVariable("targetClass", joinPoint.getTarget().getClass());
        return context;
    }

}
