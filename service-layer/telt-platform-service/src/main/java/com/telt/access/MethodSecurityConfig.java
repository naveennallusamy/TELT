package com.telt.access;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;

import java.io.Serializable;

@Configuration
public class MethodSecurityConfig {

    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            ObjectProvider<TeltPermissionEvaluator> evaluator) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(new PermissionEvaluator() {
            @Override
            public boolean hasPermission(Authentication authentication, Object target, Object permission) {
                return evaluator.getObject().hasPermission(authentication, target, permission);
            }

            @Override
            public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
                                         Object permission) {
                return evaluator.getObject().hasPermission(authentication, targetId, targetType, permission);
            }
        });
        return handler;
    }
}
