package io.github.vatisteve;

import lombok.NonNull;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.Set;

/**
 * @author tinhnv - Jan 15 2025
 */
public interface PermissionService<T extends Serializable> {
    @NonNull Set<String> getPermissions(@Nullable T subject);
}
