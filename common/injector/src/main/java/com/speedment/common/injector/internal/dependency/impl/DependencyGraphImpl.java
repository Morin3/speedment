package com.speedment.common.injector.internal.dependency.impl;

import com.speedment.common.injector.annotation.Execute;
import com.speedment.common.injector.annotation.ExecuteBefore;
import com.speedment.common.injector.annotation.Inject;
import com.speedment.common.injector.exception.CyclicReferenceException;
import com.speedment.common.injector.internal.dependency.Dependency;
import com.speedment.common.injector.internal.dependency.DependencyGraph;
import com.speedment.common.injector.internal.dependency.DependencyNode;
import com.speedment.common.injector.internal.dependency.Execution;
import static com.speedment.common.injector.internal.util.ReflectionUtil.traverseFields;
import static com.speedment.common.injector.internal.util.ReflectionUtil.traverseMethods;
import com.speedment.common.injector.State;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import static java.util.stream.Collectors.joining;

/**
 * The default implementation of the {@link DependencyGraph} interface.
 * 
 * @author Emil Forslund
 * @since  1.0.0
 */
public final class DependencyGraphImpl implements DependencyGraph {
    
    private final static State[] STATES = State.values();
    private final Map<Class<?>, DependencyNode> nodes;
    
    public static DependencyGraph create(Set<Class<?>> injectables) throws CyclicReferenceException {
        final DependencyGraphImpl graph = new DependencyGraphImpl();
        injectables.forEach(graph::getOrCreate);
        return graph.inject();
    }
    
    private DependencyGraphImpl() {
        nodes = new ConcurrentHashMap<>();
    }

    @Override
    public DependencyNode get(Class<?> clazz) throws CyclicReferenceException {
        return Optional.ofNullable(nodes.get(clazz))
            .orElseThrow(() -> new CyclicReferenceException(clazz));
    }

    @Override
    public DependencyNode getOrCreate(Class<?> clazz) {
        return nodes.computeIfAbsent(clazz, DependencyNodeImpl::new);
    }

    @Override
    public DependencyGraph inject() {
        nodes.forEach((clazz, node) -> {
            // Go through all the member variables with the '@Inject'-annotation in the 
            // class and add edges to them in the dependency graph.
            addDependenciesTo(clazz, node.getDependencies());

            // Go through all the methods with the '@Execute'-annotation in the class and
            // add them to the executors set.
            traverseMethods(clazz)
                .filter(m -> m.isAnnotationPresent(Execute.class))
                .forEach(m -> {
                    node.getExecutions().add(createExecution(clazz, m, State.STARTED));
                });

            // Go through all the methods with the '@ExecuteBefore'-annotation in the class 
            // and add them to the executors set.
            traverseMethods(clazz)
                .filter(m -> m.isAnnotationPresent(ExecuteBefore.class))
                .forEach(m -> {
                    final ExecuteBefore execute = m.getAnnotation(ExecuteBefore.class);
                    node.getExecutions().add(createExecution(clazz, m, execute.value()));
                });
        });
        
        return this;
    }

    @Override
    public Stream<DependencyNode> nodes() {
        return nodes.values().stream();
    }
    
    private static String methodName(Method m) {
        return m.getDeclaringClass().getName() + "#" + 
            m.getName() + "(" + 
            Stream.of(m.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(joining(", ")) + 
            ")";
    }
    
    private void addDependenciesTo(Class<?> clazz, Set<Dependency> dependencies) {
        traverseFields(clazz)
            .filter(f -> f.isAnnotationPresent(Inject.class))
            .forEach(f -> {

                final Inject inject = f.getAnnotation(Inject.class);
                final Class<?> type = f.getType();
                final State state   = inject.value();

                try {
                    dependencies.add(
                        new DependencyImpl(get(type), state)
                    );
                } catch (final CyclicReferenceException ex) {
                    throw new CyclicReferenceException(clazz, ex);
                }
            });
    }
    
    private Execution createExecution(Class<?> clazz, Method m, State executeBefore) {
     
        // Make sure all the methods parameters are annoted with the 
        // '@Inject'-annotation.
        if (Stream.of(m.getParameters())
            .anyMatch(p -> !p.isAnnotationPresent(Inject.class))) {
            throw new RuntimeException(
                "Method '" + methodName(m) + 
                "' has a parameter that is missing the @" + 
                Inject.class.getSimpleName() + " annotation."
            );
        }

        final Set<Dependency> dependencies = new HashSet<>();
        for (int i = 0; i < m.getParameterCount(); i++) {
            final Parameter p   = m.getParameters()[i];
            final Inject inject = p.getAnnotation(Inject.class);
            final Class<?> type = p.getType();
            final State state   = inject.value();

            try {
                dependencies.add(
                    new DependencyImpl(getOrCreate(type), state)
                );
            } catch (final CyclicReferenceException ex) {
                throw new CyclicReferenceException(clazz, ex);
            }
        }
        
        addDependenciesTo(clazz, dependencies);

        return new ExecutionImpl(executeBefore, m, dependencies);
    }
}
