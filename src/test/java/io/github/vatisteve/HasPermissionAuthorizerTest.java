package io.github.vatisteve;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HasPermissionAuthorizerTest {

    @Mock
    private PermissionService<String> permissionService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private HasPermissionAuthorizer<String> authorizer;

    @BeforeEach
    void setUp() {
        authorizer = new HasPermissionAuthorizer<>(permissionService, "userId");

        // Setup commonly mocks
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("testMethod");

        // Mock method target
        TestService testService = new TestService();
        when(joinPoint.getTarget()).thenReturn(testService);

        // Mock method parameters
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"userId", "data"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{"user123", "testData"});

        try {
            Method method = TestService.class.getMethod("doSomething", String.class, String.class);
            when(methodSignature.getMethod()).thenReturn(method);
        } catch (NoSuchMethodException e) {
            fail("Test setup failed: " + e.getMessage());
        }
    }

    @Test
    void testCheckPermissionWithSimplePermission() {
        // Setup
        HasPermission hasPermission = createHasPermission("READ");
        Set<String> permissions = Set.of("READ", "WRITE");
        when(permissionService.getPermissions("user123")).thenReturn(permissions);
        // Execute
        authorizer.checkPermissionOnMethod(joinPoint, hasPermission);
        // Verify
        verify(permissionService).getPermissions("user123");
    }

    @Test
    void testCheckPermissionWithoutRequiredPermission() {
        // Setup
        HasPermission hasPermission = createHasPermission("ADMIN");
        Set<String> permissions = Set.of("READ", "WRITE");
        when(permissionService.getPermissions("user123")).thenReturn(permissions);
        // Execute & Verify
        assertThrows(PermissionDeniedException.class, () -> authorizer.checkPermissionOnMethod(joinPoint, hasPermission));
    }

    @Test
    void testCheckAllOfPermissions() {
        // Setup
        HasPermission hasPermission = createHasPermissionWithAllOf("READ", "WRITE");
        Set<String> permissions = Set.of("READ", "WRITE", "DELETE");
        when(permissionService.getPermissions("user123")).thenReturn(permissions);
        // Execute
        authorizer.checkPermissionOnMethod(joinPoint, hasPermission);
        // Verify
        verify(permissionService).getPermissions("user123");
    }

    @Test
    void testCheckAllOfPermissionsFailure() {
        // Setup
        HasPermission hasPermission = createHasPermissionWithAllOf("READ", "ADMIN");
        Set<String> permissions = Set.of("READ", "WRITE");
        when(permissionService.getPermissions("user123")).thenReturn(permissions);
        // Execute & Verify
        assertThrows(PermissionDeniedException.class, () -> authorizer.checkPermissionOnMethod(joinPoint, hasPermission));
    }

    @Test
    void testCheckAnyOfPermissions() {
        // Setup
        HasPermission hasPermission = createHasPermissionWithAnyOf("READ", "ADMIN");
        Set<String> permissions = Set.of("READ", "WRITE");
        when(permissionService.getPermissions("user123")).thenReturn(permissions);
        // Execute
        authorizer.checkPermissionOnMethod(joinPoint, hasPermission);
        // Verify
        verify(permissionService).getPermissions("user123");
    }

    @Test
    void testCheckAnyOfPermissionsFailure() {
        // Setup
        HasPermission hasPermission = createHasPermissionWithAnyOf("ADMIN", "SUPER_USER");
        Set<String> permissions = Set.of("READ", "WRITE");
        when(permissionService.getPermissions("user123")).thenReturn(permissions);
        // Execute & Verify
        assertThrows(PermissionDeniedException.class,
                () -> authorizer.checkPermissionOnMethod(joinPoint, hasPermission));
    }

    @Test
    void testEmptyPermissionCheck() {
        // Setup - no permissions required
        HasPermission hasPermission = createEmptyHasPermission();
        Set<String> permissions = Set.of("READ", "WRITE");
        when(permissionService.getPermissions("user123")).thenReturn(permissions);
        // Execute
        authorizer.checkPermissionOnMethod(joinPoint, hasPermission);
        // Verify - should pass without checking permissions
        verify(permissionService).getPermissions("user123");
    }

    @Test
    void testCustomExpressionSubject() {
        // Setup custom subject expression
        HasPermission hasPermission = mock(HasPermission.class);
        when(hasPermission.subject()).thenReturn("'customSubject'");
        when(hasPermission.of()).thenReturn("READ");
        when(hasPermission.allOf()).thenReturn(new String[]{});
        when(hasPermission.anyOf()).thenReturn(new String[]{});
        Set<String> permissions = Set.of("READ", "WRITE");
        when(permissionService.getPermissions("customSubject")).thenReturn(permissions);
        // Execute
        authorizer.checkPermissionOnMethod(joinPoint, hasPermission);
        // Verify
        verify(permissionService).getPermissions("customSubject");
    }

    private HasPermission createHasPermission(String permission) {
        HasPermission hasPermission = mock(HasPermission.class);
        when(hasPermission.subject()).thenReturn("#userId");
        when(hasPermission.of()).thenReturn(permission);
        lenient().when(hasPermission.allOf()).thenReturn(new String[] {});
        lenient().when(hasPermission.anyOf()).thenReturn(new String[] {});
        return hasPermission;
    }

    private HasPermission createHasPermissionWithAllOf(String... permissions) {
        HasPermission hasPermission = mock(HasPermission.class);
        when(hasPermission.subject()).thenReturn("#userId");
        when(hasPermission.of()).thenReturn("");
        when(hasPermission.value()).thenReturn("");
        when(hasPermission.allOf()).thenReturn(permissions);
        lenient().when(hasPermission.anyOf()).thenReturn(new String[] {});
        return hasPermission;
    }

    private HasPermission createHasPermissionWithAnyOf(String... permissions) {
        HasPermission hasPermission = mock(HasPermission.class);
        when(hasPermission.subject()).thenReturn("#userId");
        when(hasPermission.of()).thenReturn("");
        when(hasPermission.value()).thenReturn("");
        when(hasPermission.anyOf()).thenReturn(permissions);
        when(hasPermission.allOf()).thenReturn(new String[] {});
        return hasPermission;
    }

    private HasPermission createEmptyHasPermission() {
        HasPermission hasPermission = mock(HasPermission.class);
        when(hasPermission.subject()).thenReturn("#userId");
        when(hasPermission.of()).thenReturn("");
        when(hasPermission.value()).thenReturn("");
        when(hasPermission.allOf()).thenReturn(new String[]{});
        when(hasPermission.anyOf()).thenReturn(new String[]{});
        return hasPermission;
    }

    // Helper test class for mocking
    static class TestService {
        public void doSomething(String userId, String data) {
            // Test method
        }
    }

}
