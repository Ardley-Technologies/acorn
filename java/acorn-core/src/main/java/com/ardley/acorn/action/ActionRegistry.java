package com.ardley.acorn.action;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all actions available in the application.
 *
 * <p>Actions are registered at startup and resolved by name during request processing.
 * The registry serves two purposes:
 * <ol>
 *   <li>Runtime resolution of action names from annotations to {@link Action} instances</li>
 *   <li>Introspection for admin endpoints that list available permissions</li>
 * </ol>
 *
 * <p>Duplicate registrations (same name) are rejected immediately to surface
 * configuration errors at startup rather than runtime.
 */
public final class ActionRegistry {

    private final Map<String, Action> actions = new ConcurrentHashMap<>();

    /**
     * Registers an action. Throws immediately if a duplicate name is detected.
     *
     * @param action the action to register
     * @throws IllegalArgumentException if an action with the same name already exists
     */
    public void register(Action action) {
        Action existing = actions.putIfAbsent(action.name(), action);
        if (existing != null) {
            throw new IllegalArgumentException(
                    "Duplicate action name: \"" + action.name() + "\"");
        }
    }

    /**
     * Registers all constants from an enum that implements {@link Action}.
     *
     * @param enumClass the enum class containing action constants
     * @param <E> an enum type implementing Action
     */
    public <E extends Enum<E> & Action> void registerAll(Class<E> enumClass) {
        for (E constant : enumClass.getEnumConstants()) {
            register(constant);
        }
    }

    /**
     * Resolves an action by name.
     *
     * @param name the action name
     * @return the resolved action
     * @throws IllegalArgumentException if no action is registered with this name
     */
    public Action resolve(String name) {
        Action action = actions.get(name);
        if (action == null) {
            throw new IllegalArgumentException("Unknown action: \"" + name + "\"");
        }
        return action;
    }

    /**
     * Returns all registered actions for introspection.
     */
    public Collection<Action> all() {
        return Collections.unmodifiableCollection(actions.values());
    }

    public int size() {
        return actions.size();
    }
}
