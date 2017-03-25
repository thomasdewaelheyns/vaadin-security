package org.ilay;

import com.vaadin.data.HasDataProvider;
import com.vaadin.data.HasItems;
import com.vaadin.navigator.View;
import com.vaadin.ui.Component;

import org.ilay.api.Authorizer;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

/**
 * <b>Authorization</b> is the main entry point for the ILAY framework.
 * It provides methods for binding and unbinding {@link Component}s, {@link View}s
 * and {@link HasItems} to permissions as well as applying the bound permissions.
 *
 * The first method that is called on {@link Authorization} needs to be either {@link Authorization#start(Set)}
 * or {@link Authorization#start(Supplier)}
 * <code>
 *     Authorizer{@literal <}Foo{@literal >} fooEvaluator = new InMemoryAuthorizer(){
 *         public boolean isGranted(Foo foo){
 *             boolean granted = //evaluation logic goes here
 *             return granted;
 *         }
 *
 *         public Class{@literal <}Foo{@literal >} getPermissionClass(){
 *             return Foo.class;
 *         }
 *     }
 *
 *     Authorizer{@literal <}UserRole{@literal >} userRoleEvaluator = new InMemoryAuthorizer(){
 *         public boolean isGranted(UserRole userRole){
 *             boolean granted = //evaluation logic goes here
 *             return granted;
 *         }
 *
 *         public Class{@literal <}UserRole{@literal >} getPermissionClass(){
 *             return UserRole.class;
 *         }
 *     }
 *
 *     Set{@literal <}Authorizer{@literal >} evaluators = new HashSet{@literal <}{@literal >}();
 *
 *     evaluators.add(fooEvaluator);
 *     evaluators.add(userRoleEvaluator);
 *
 *     //...
 *
 *     Authorization.start(evaluators);
 * </code>
 *
 * Then, {@link Component}s, {@link View}s and {@link HasItems}' can be bound with
 * the {@link Authorization#bindComponents(Component...)}, {@link Authorization#bindViews(View...)} and
 * {@link Authorization#bindData(Class, HasDataProvider)} methods.
 *
 * <code>
 *     Button button = new Button();
 *     AdminView adminView = new AdminView();
 *     Grid{@literal <}Foo{@literal >} fooGrid = new Grid{@literal <}Foo{@literal >}(Foo.class);
 *
 *     Authorization.bindComponent(button).to(UserRole.USER);
 *     Authorization.bindView(myView).to(UserRole.ADMIN);
 *     Authorization.bindData(fooGrid);
 * </code>
 */
public final class Authorization {

    private static final String NOT_INITIALIZED_ERROR_MESSAGE = "Authorization.start() must be called before this method";
    static Supplier<NavigatorFacade> navigatorSupplier = new ProductionNavigatorFacadeSupplier();
    static Supplier<SessionInitNotifier> sessionInitNotifierSupplier = new ProductionSessionInitNotifierSupplier();
    private static boolean initialized = false;

    private Authorization() {
    }

    /**
     * starts the authorization-engine. This method or {@link Authorization#start(Supplier)} must be
     * called before any other method in {@link Authorization} is called. Use this method instead of
     * {@link Authorization#start(Supplier)} if the set of {@link Authorizer}s is immutable and the
     * same set can be used for all {@link com.vaadin.server.VaadinSession}s.
     *
     * @param authorizers the {@link Authorizer}s needed. For every object passed in {@link
     *                    ComponentBind#to(Object...)}, there must be a evaluator in the set where the {@link
     *                    Authorizer#getPermissionClass()} is assignable from the objects {@link
     *                    Class}.
     */
    public static void start(Set<Authorizer> authorizers) {
        Check.notEmpty(authorizers);
        start(() -> authorizers);
    }

    /**
     * starts the authorization-engine. This method or {@link Authorization#start(Set)} )} must be
     * called before any other method in {@link Authorization} is called. Use this method instead of
     * {@link Authorization#start(Set)} if the set of {@link Authorizer}s is not immutable and a different
     * set may be used for all {@link com.vaadin.server.VaadinSession}s.
     * @param evaluatorSupplier the {@link Authorizer}s needed. For every object passed in {@link ComponentBind#to(Object...)}, there
     * must be a evaluator in the set where the {@link Authorizer#getPermissionClass()} is assignable from the objects {@link Class}.
     */
    public static void start(Supplier<Set<Authorizer>> evaluatorSupplier) {
        requireNonNull(evaluatorSupplier);

        if (initialized) {
            throw new IllegalStateException("start() cannot be called more than once");
        }

        final SessionInitNotifier sessionInitNotifier = sessionInitNotifierSupplier.get();

        sessionInitNotifier.addSessionInitListener(
                //for every new VaadinSession, we initialize the AuthorizationContext
                e -> AuthorizationContext.init(Check.notEmpty(evaluatorSupplier.get()))
        );

        initialized = true;
    }

    /**
     * returns a {@link ComponentBind} to connect
     * a {@link Component} to one or more permissions
     *
     * <code>
     *   Button button = new Button();
     *   Authorization.bindComponent(button).to(Permission.ADMIN);
     * </code>
     * @param component the component to be bound to one or more permission, cannot be null
     * @return a {@link ComponentBind} for a chained fluent API
     */
    public static ComponentBind bindComponent(Component component) {
        requireNonNull(component);
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        return bindComponents(component);
    }

    /**
     * returns a {@link ComponentBind} to connect {@link Component}s to one or more permissions
     *
     * <code> Button button = new Button(); Label label = new Label();
     * Authorization.bindComponents(button, label).to(Permission.ADMIN); </code>
     *
     * @param components the {@link Component}s to be bound to one or more permission, cannot be
     *                   null or empty
     * @return a {@link ComponentBind} for a chained fluent API
     */
    public static ComponentBind bindComponents(Component... components) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        return new ComponentBind(components);
    }

    /**
     * returns a {@link ViewBind} to connect a {@link View} to one or more permissions
     *
     * <code> View view = createView(); Authorization.bindView(view).to(Permission.ADMIN); </code>
     *
     * @param view the {@link View} to be bound to one or more permission, cannot be null or empty
     * @return a {@link ViewBind} for a chained fluent API
     */
    public static ViewBind bindView(View view) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        return bindViews(view);
    }

    /**
     * returns a {@link ViewBind} to connect
     * {@link View}s to one or more permissions
     *
     * <code>
     *   View view = createView();
     *   View view2 = createView();
     *   Authorization.bindViews(view, view2).to(Permission.ADMIN);
     * </code>
     * @param views the {@link View}s to be bound to one or more permission, cannot be null or empty
     * @return a {@link ViewBind} for a chained fluent API
     */
    public static ViewBind bindViews(View... views) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        AuthorizationContext.getCurrent().ensureViewChangeListenerRegistered();
        return new ViewBind(views);
    }

    /**
     * binds the data, or items, in the {@link HasDataProvider} to authorization. Each item t of type
     * T in an HasDataProvider{@literal <}T{@literal >} is it's own permission and will only be displayed
     * when an {@link Authorizer}{@literal <}T, ?{@literal >}'s {@link Authorizer#isGranted(Object)}-method
     * returned true for t. If no {@link Authorizer} for the type T is available, an exception will be thrown.
     * @param itemClass
     * @param hasItems
     * @param <T>
     */
    public static <T> void bindData(Class<T> itemClass, HasDataProvider<T> hasItems) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();
        authorizationContext.bindData(itemClass, hasItems);
    }

    /**
     * Reverses a {@link Authorization#bindComponents(Component...)} or {@link
     * Authorization#bindComponent(Component)} operation. No exception is thrown if the {@link
     * Component} was not bound. <code> Button button = new Button(); Label label = new Label();
     * Authorization.bindComponents(button, label).to(Permission.ADMIN);
     *
     * //...
     *
     * Authorization.unbindComponens(button).from(Permission.ADMIN);
     *
     * //button is not under authorization anymore
     *
     * </code>
     *
     * @param component the component to be unbound
     * @return a {@link ComponentUnbind} for a chained fluent API
     */
    public static ComponentUnbind unbindComponent(Component component) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        requireNonNull(component);
        return unbindComponents(component);
    }

    /**
     * Reverses a {@link Authorization#bindComponents(Component...)}
     * or {@link Authorization#bindComponent(Component)} operation.
     * No exception is thrown if the {@link Component} was not bound.
     * *
     * <code>
     *     Button button = new Button();
     *     Label label = new Label();
     *     Authorization.bindComponents(button, label).to(Permission.ADMIN);
     *
     *     //...
     *
     *     Authorization.unbindComponents(button).from(Permission.ADMIN);
     *
     *     //button is not under authorization anymore
     *
     * </code>
     *
     * @param components the components to be unbound
     * @return a {@link ComponentUnbind} for a chained fluent API
     */
    public static ComponentUnbind unbindComponents(Component... components) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        Check.arg(stream(components).allMatch(c -> c != null), "components cannot contain null");
        return new ComponentUnbind(components);
    }

    /**
     * Reverses a {@link Authorization#bindView(View)}
     * or {@link Authorization#bindViews(View...)} operation.
     * No exception is thrown if the {@link View} was not bound.
     * *
     * <code>
     *     View view = createView();
     *     Authorization.bindView(view).to(Permission.ADMIN);
     *
     *     //...
     *
     *     Authorization.unbindView(view).from(Permission.ADMIN);
     *
     *     //view is not under authorization anymore
     *
     * </code>
     *
     * @param view the view to be unbound
     * @return a {@link ViewUnbind} for a chained fluent API
     */
    public static ViewUnbind unbindView(View view) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        requireNonNull(view);
        return unbindViews(view);
    }


    /**
     * Reverses a {@link Authorization#bindView(View)}
     * or {@link Authorization#bindViews(View...)} operation.
     * No exception is thrown if the {@link View} was not bound.
     * *
     * <code>
     *     View view1 = createView();
     *     View view2 = createView();
     *     Authorization.bindViews(view1, view2).to(Permission.ADMIN);
     *
     *     //...
     *
     *     Authorization.unbindViews(view1, view2).from(Permission.ADMIN);
     *
     *     //views are not under authorization anymore
     *
     * </code>
     *
     * @param views the view to be unbound
     * @return a {@link ViewUnbind} for a chained fluent API
     */
    public static ViewUnbind unbindViews(View... views) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        return new ViewUnbind(views);
    }

    /**
     * Reverses a {@link Authorization#bindData(Class, HasDataProvider)} operation.
     * No exception is thrown if the {@link HasDataProvider} was not bound.
     *
     * <code>
     *      Grid{@literal <}Foo{@literal >} fooGrid = new Grid{@literal <}Foo{@literal >}();
     *
     *      Authorization.bindData(fooGrid);
     *
     *      //foo-items will now only be displayed in the grid,
     *      //if an Authorizer{@literal <}Foo, ?{@literal >}
     *      //grants them authorization in the {@link Authorizer#isGranted(Object)}
     *      //method
     *
     *      Authorization.unbindData(fooGrid);
     *
     *      //grid is not under authorization anymore
     * </code>
     * @param hasDataProvider the HasDataProvider to be unbound
     */
    public static <T> boolean unbindData(HasDataProvider<T> hasDataProvider) {
        requireNonNull(hasDataProvider);
        return AuthorizationContext.getCurrent().unbindData(hasDataProvider);
    }

    /**
     * All permissions will be re-evaluated. Call this method when
     * for example the current users roles change or generally whenever
     * there is reason to believe that an {@link Authorizer} would now
     * grant permissions differently than in the past.
     *
     * <code>
     *  User user = createUser();
     *
     *  user.setRole(Role.USER);
     *
     *  Button button = new Button("admin mode");
     *
     *  Authorization.bindComponent(button).to(Role.ADMIN);
     *
     *  assert !button.getVisible();
     *
     *  user.setRole(Role.ADMIN);
     *
     *  assert !button.getVisible();
     *
     *  Authorization.rebind();
     *
     *  assert button.getVisible();
     * </code>
     */
    public static void rebind() {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();
        final Map<Component, Set<Object>> componentsToPermissions = authorizationContext.getComponentsToPermissions();
        apply(componentsToPermissions, authorizationContext);
    }

    static void apply(Component... components) {
        Check.state(initialized, NOT_INITIALIZED_ERROR_MESSAGE);
        requireNonNull(components);
        final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();
        apply(components, authorizationContext);
    }

    static void apply(Component[] components, AuthorizationContext authorizationContext){
        final Map<Component, Set<Object>> componentsToPermissions = authorizationContext.getComponentsToPermissions();
        final Map<Component, Set<Object>> reducedComponentsToPermissions = stream(components).collect(toMap(c -> c, componentsToPermissions::get));
        apply(reducedComponentsToPermissions, authorizationContext);
    }

    static void apply(Map<Component, Set<Object>> componentsToPermissions, AuthorizationContext authorizationContext) {
        authorizationContext.applyComponents(componentsToPermissions);
        authorizationContext.applyData();
        reEvaluateCurrentViewAccess();
    }

    private static void reEvaluateCurrentViewAccess() {
        final NavigatorFacade navigator = navigatorSupplier.get();

        if (navigator == null) {
            //no navigator -> no views to check
            return;
        }

        final String state = navigator.getState();
        navigator.navigateTo("");
        navigator.navigateTo(state);
    }

    public static class ComponentBind {

        private final Component[] components;

        ComponentBind(Component[] components) {
            requireNonNull(components);
            if (components.length == 0) {
                throw new IllegalArgumentException("components must not be empty");
            }

            this.components = components;
        }

        public void to(Object... permissions) {
            requireNonNull(permissions);
            if (permissions.length == 0) {
                throw new IllegalArgumentException("one ore more permissions needed");
            }

            final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();
            final Map<Component, Set<Object>> componentsToPermissions = authorizationContext.getComponentsToPermissions();

            for (Component component : components) {

                Collection<Object> currentPermissions = componentsToPermissions.get(component);

                final Set<Object> newPermissions = new HashSet<>(asList(permissions));

                if (currentPermissions == null) {
                    componentsToPermissions.put(component, newPermissions);
                } else {
                    currentPermissions.addAll(newPermissions);
                }
            }

            apply(components, authorizationContext);
        }
    }

    public static class ComponentUnbind {

        private final Component[] components;

        ComponentUnbind(Component[] components) {
            requireNonNull(components);

            if (components.length == 0) {
                throw new IllegalArgumentException("components must not be empty");
            }

            this.components = components;
        }

        public void from(Object... permissions) {
            requireNonNull(permissions);
            if (permissions.length == 0) {
                throw new IllegalArgumentException("permissions cannot be empty");
            }

            final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();
            final Map<Component, Set<Object>> componentsToPermissions = authorizationContext.getComponentsToPermissions();

            for (Component component : components) {
                final Collection<Object> componentPermissions = componentsToPermissions.get(component);

                for (Object permission : permissions) {
                    componentPermissions.remove(permission);
                }
            }

            apply(components, authorizationContext);
        }

        public void fromAll() {
            final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();
            final Map<Component, Set<Object>> componentsToPermissions = authorizationContext.getComponentsToPermissions();

            for (Component component : components) {
                componentsToPermissions.remove(component);
                component.setVisible(true);
            }
        }
    }

    public static class ViewUnbind {

        private final View[] views;

        ViewUnbind(View[] views) {
            requireNonNull(views);

            if (views.length == 0) {
                throw new IllegalArgumentException("components must not be empty");
            }

            this.views = views;
        }

        public void from(Object... permissions) {
            requireNonNull(permissions);
            Check.arg(permissions.length != 0, "permissions cannot be empty");

            Collection<Object> permissionsCollection = asList(permissions);

            final Map<View, Set<Object>> viewsToPermissions = AuthorizationContext
                    .getCurrent()
                    .getViewsToPermissions();

            stream(views)
                    .map(viewsToPermissions::get)
                    .filter(p -> p != null)
                    .forEach(p -> p.removeAll(permissionsCollection));
        }

        public void fromAll() {
            final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();
            final Map<View, Set<Object>> viewsToPermissions = authorizationContext.getViewsToPermissions();

            for (View view : views) {
                viewsToPermissions.remove(view);
            }
        }
    }

    static class ViewBind {
        private final View[] views;

        ViewBind(View[] views) {
            requireNonNull(views);
            if (views.length == 0) {
                throw new IllegalArgumentException("views must not be empty");
            }

            this.views = views;
        }

        public void to(Object... permissions) {
            requireNonNull(permissions);

            if (permissions.length == 0) {
                throw new IllegalArgumentException("one ore more permissions needed");
            }

            final AuthorizationContext authorizationContext = AuthorizationContext.getCurrent();

            final Map<View, Set<Object>> viewsToPermissions = authorizationContext.getViewsToPermissions();

            for (View view : views) {
                final Set<Object> currentPermissions = viewsToPermissions.get(view);

                final Set<Object> newPermissions = new HashSet<>(asList(permissions));

                if (currentPermissions == null) {
                    viewsToPermissions.put(view, newPermissions);
                } else {
                    currentPermissions.addAll(newPermissions);
                }
            }
        }
    }
}
