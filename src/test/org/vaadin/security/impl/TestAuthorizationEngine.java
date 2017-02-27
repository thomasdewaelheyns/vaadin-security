package org.vaadin.security.impl;

import com.vaadin.navigator.Navigator;
import org.vaadin.security.api.EvaluatorPool;

public class TestAuthorizationEngine extends AuthorizationEngine {
    public TestAuthorizationEngine(EvaluatorPool evaluatorPool) {
        super(evaluatorPool);
    }

    @Override
    Navigator getNavigator() {
        return null;
    }
}