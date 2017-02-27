package org.vaadin.security.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.vaadin.navigator.Navigator;
import com.vaadin.navigator.View;
import com.vaadin.server.SessionInitListener;
import com.vaadin.server.VaadinService;
import com.vaadin.ui.Component;
import com.vaadin.ui.UI;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import org.vaadin.security.api.Applier;
import org.vaadin.security.api.Binder;
import org.vaadin.security.api.Evaluator;
import org.vaadin.security.api.EvaluatorPool;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.*;
import static com.google.common.collect.ImmutableSet.copyOf;

@SuppressWarnings("unused")
public class AuthorizationEngine implements Binder, Applier {

    private final EvaluatorPool evaluatorPool;
    final Multimap<Component, Object> componentsToPermissions = HashMultimap.create();
    final Multimap<View, Object> viewsToPermissions = HashMultimap.create();
    private final Object2BooleanMap<Component> componentsToLastKnownVisibilityState = new Object2BooleanOpenHashMap<>();

    public AuthorizationEngine(EvaluatorPool evaluatorPool){
        this.evaluatorPool = checkNotNull(evaluatorPool);
    }

    static boolean setUp = false;

    public static void setUp(Supplier<EvaluatorPool> evaluatorPoolSupplier){
        checkState(!setUp, "setUp() cannot be called more than once");

        checkNotNull(evaluatorPoolSupplier);

        VaadinService.getCurrent().addSessionInitListener(
            (SessionInitListener) event -> {
                final EvaluatorPool evaluatorPool = checkNotNull(evaluatorPoolSupplier.get());
                AuthorizationEngine authorizationEngine = new AuthorizationEngine(evaluatorPool);
                event.getSession().setAttribute(Binder.class, authorizationEngine);
                event.getSession().setAttribute(Applier.class, authorizationEngine);
            }
        );

        setUp = true;
    }

    @Override
    public Set<Object> getPermissions(Component component) {
        checkNotNull(component);
        return copyOf(componentsToPermissions.get(component));
    }

    @Override
    public Set<Object> getViewPermissions(View view) {
        checkNotNull(view);
        return copyOf(viewsToPermissions.get(view));
    }

    @Override
    public Bind bind(Component... components) {
        checkNotNull(components);
        checkArgument(components.length > 0);
        return new BindImpl(this, components);
    }

    @Override
    public Bind bindView(View... views) {
        checkNotNull(views);
        checkArgument(views.length > 0);
        return new ViewBindImpl(this, views);
    }

    @Override
    public Unbind unbind(Component... components) {
        checkNotNull(components);
        checkArgument(components.length > 0);
        return new UnbindImpl(this, components);
    }

    @Override
    public Unbind unbindView(View... views) {
        checkNotNull(views);
        checkArgument(views.length > 0);
        return new ViewUnbindImpl(this, views);
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateAndCache(Collection<Object> permissions, Object2BooleanMap<Object> grantCache) {
        for (Object permission : permissions) {
            boolean granted;

            if (grantCache.containsKey(permission)) {
                granted = grantCache.getBoolean(permission);
            } else {
                final Evaluator evaluator = evaluatorPool.getEvaluator(permission.getClass());
                granted = evaluator.evaluate(permission);
                grantCache.put(permission, granted);
            }

            if (!granted) {
                return false;
            }
        }

        return true;
    }

    @Override
    public void applyAll() {
        Object2BooleanMap<Object> grantCache = new Object2BooleanOpenHashMap<>(componentsToPermissions.values().size());

        synchronized (this) {
            for (Map.Entry<Component, Collection<Object>> entry : componentsToPermissions.asMap().entrySet()) {
                final Collection<Object> permissions = entry.getValue();
                final Component component = entry.getKey();

                if (componentsToLastKnownVisibilityState.containsKey(component)) {
                    checkState(
                            componentsToLastKnownVisibilityState.getBoolean(component) == component.isVisible(),
                            "Component.setVisible() must not be called for components in the vaadin-authorization context, " +
                                    "consider making these components invisible via CSS instead if you want to hide them. In Component %s",
                            component
                    );
                }

                final boolean newVisibility = evaluateAndCache(permissions, grantCache);
                component.setVisible(newVisibility);

                componentsToLastKnownVisibilityState.put(component, newVisibility);
            }

            reEvaluateCurrentViewAccess();
        }
    }

    @Override
    public void apply(Component... components) {
        checkNotNull(components);

        Object2BooleanMap<Object> grantCache = components.length > 1
                ? new Object2BooleanOpenHashMap<>(componentsToPermissions.values().size())
                : NoImplGrantCache.INSTANCE;

        synchronized (this) {
            for (Component component : components) {
                if (componentsToLastKnownVisibilityState.containsKey(component)) {
                    checkState(
                            componentsToLastKnownVisibilityState.getBoolean(component) == component.isVisible(),
                            "Component.setVisible() must not be called for components in the vaadin-authorization context, " +
                                    "consider making these components invisible via CSS instead if you want to hide them. In Component %s",
                            component
                    );
                }

                final Collection<Object> permissions = componentsToPermissions.get(component);

                boolean newVisibility = permissions == null || evaluateAndCache(permissions, grantCache);

                componentsToLastKnownVisibilityState.put(component, newVisibility);

                component.setVisible(newVisibility);
            }

            reEvaluateCurrentViewAccess();
        }
    }

    Navigator getNavigator(){
        final UI ui = checkNotNull(UI.getCurrent());
        return ui.getNavigator();
    }

    private void reEvaluateCurrentViewAccess() {
        final Navigator navigator = getNavigator();

        if (navigator == null) {
            //no navigator -> no views to check
            return;
        }

        navigator.navigateTo(navigator.getState());
    }
}