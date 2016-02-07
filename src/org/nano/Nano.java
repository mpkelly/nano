package org.nano;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.reflect.Modifier.isAbstract;
import static java.lang.reflect.Modifier.isPublic;
import static java.text.MessageFormat.format;
import static java.util.Arrays.asList;
import static java.util.Collections.sort;

public final class Nano {
  private static final String DEFAULT_NAME = "nano$default";

  @Retention(RUNTIME)
  @Target(PARAMETER)
  public @interface NanoName {
    String value ();
  }

  public static class Module {
    final Set<DependencyMapping> mappings = new HashSet<>();

    public <T> DependencyBinding<T> bind (Class<T> type) {
      return bind(type, DEFAULT_NAME);
    }

    public <T> DependencyBinding<T> bind (Class<T> type, String name) {
      return new DefaultDependencyBinding<>(type, name);
    }

    public class DefaultDependencyBinding<T> implements DependencyBinding<T> {
      private final Class<T> base;
      private final String name;

      private DefaultDependencyBinding (Class<T> base, String name) {
        this.base = base;
        this.name = name;
      }
      
      @Override public <X extends T> void to (Class<X> implementation) {
        to(implementation, false);
      }

      @Override public <X extends T> void to (Class<X> implementation, boolean singleton) {
        mappings.add(new DependencyMapping(base, implementation, name, singleton));
      }

      @Override public <X extends T> void to (X instance) {
        mappings.add(new DependencyMapping(base, instance, name));
      }
    }
  }

  public interface DependencyBinding<T> {
    <X extends T> void to (Class<X> implementation);

    <X extends T> void to (Class<X> implementation, boolean singleton);

    <X extends T> void to (X instance);
  }

  private static class DependencyId {
    final Class<?> base;
    final String name;

    public DependencyId (Class<?> base, String name) {
      this.base = base;
      this.name = name;
    }

    @Override public boolean equals (Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof DependencyId)) {
        return false;
      }
      DependencyId id = (DependencyId) o;
      return base.equals(id.base) && name.equals(id.name);
    }

    @Override public int hashCode () {
      int result = base.hashCode();
      result = 31 * result + name.hashCode();
      return result;
    }

    @Override public String toString () {
      return DEFAULT_NAME.equals(name) ? base.getName() : "Named dependency " + name + " of type " + base.getName();
    }
  }

  private static class DependencyMapping extends DependencyId {
    final Class<?> implementation;
    Object instance;
    final boolean singleton;

    public DependencyMapping (Class<?> base, Class<?> implementation, String name, boolean singleton) {
      this(base, implementation, null, name, singleton);
    }

    public DependencyMapping (Class<?> base, Object instance, String name) {
      this(base, instance.getClass(), instance, name, true);
    }

    private DependencyMapping (Class<?> base, Class<?> implementation, Object instance, String name, boolean singleton) {
      super(base, name);
      this.implementation = implementation;
      this.instance = instance;
      this.singleton = singleton;
    }

    Object get () {
      return instance;
    }

    void set (Object instance) {
      this.instance = instance;
    }
  }

  public static class DependencyContainer {
    private final Map<DependencyId, DependencyMapping> mappings;

    private DependencyContainer (List<Module> modules) {
      this.mappings = processModules(modules);
    }

    private Map<DependencyId, DependencyMapping> processModules (List<Module> modules) {
      Map<DependencyId, DependencyMapping> mappings = new HashMap<>();
      for (Module module : modules) {
        for (DependencyMapping mapping : module.mappings) {
          mappings.put(mapping, mapping);
        }
      }
      return mappings;
    }

    public <T> T resolve (Class<T> clazz) {
      return resolve(clazz, DEFAULT_NAME);
    }

    public <T> T resolve (Class<T> clazz, String name) {
      DependencyId id = new DependencyId(clazz, name);
      T instance = _resolve(id);
      if (instance != null) {
        return instance;
      }
      throw new MissingDependencyException("No type or instance in container is bound to {0} and I could not create one using the registered dependencies", id);
    }

    Object tryToResolve (Class<?> type, String name) {
      DependencyId id = new DependencyId(type, name);
      return _resolve(id);
    }

    //Types are checked on the public API but the type info is lost internally
    @SuppressWarnings("unchecked")
    private <T> T _resolve (DependencyId id) {
      T instance = null;
      if (mappings.containsKey(id)) {
        DependencyMapping mapping = mappings.get(id);
        if (mapping.get() != null) {
          return (T) mapping.get();
        }
        Class<T> implementation = (Class<T>) mapping.implementation;
        instance = tryToCreateInstance(implementation);
        if (mapping.singleton) {
          mapping.set(instance);
        }
      } else if (isEligible(id.base)) {
        instance = (T) tryToCreateInstance(id.base);
      }
      return instance;
    }

    private boolean isEligible (Class<?> clazz) {
      if (isAbstract(clazz.getModifiers())) {
        return false;
      }
      if (!isPublic(clazz.getModifiers())) {
        return false;
      }
      return clazz.getConstructors().length >= 1;
    }

    <T> T tryToCreateInstance (Class<T> implementation) {
      List<Constructor<T>> constructors = constructors(implementation);
      for (Constructor<T> constructor : constructors) {
        T instance = tryToCreateInstanceWith(constructor);
        if (instance != null) {
          return instance;
        }
      }
      return null;
    }

    private <T> T tryToCreateInstanceWith (Constructor<T> constructor) {
      Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
      Object[] params = new Object[constructor.getParameterTypes().length];
      int index = 0;
      for (Class<?> type : constructor.getParameterTypes()) {
        String instanceName = instanceNameFrom(parameterAnnotations[index]);
        Object maybe = tryToResolve(type, instanceName);
        if (maybe == null) {
          return null;
        }
        params[index++] = maybe;
      }
      return createInstance(constructor, params);
    }

    public String instanceNameFrom (Annotation[] annotations) {
      for (Annotation annotation : annotations) {
        if (NanoName.class.isAssignableFrom(annotation.getClass())) {
          return NanoName.class.cast(annotation).value();
        }
      }
      return DEFAULT_NAME;
    }

    private <T> T createInstance (Constructor<T> constructor, Object... params) {
      try {
        return constructor.newInstance(params);
      } catch (InstantiationException e) {
        throw new MissingDependencyException(e, "Could not instantiate class using constructor {0}", constructor);
      } catch (IllegalAccessException e) {
        throw new MissingDependencyException(e, "Could not access constructor {0}", constructor);
      } catch (InvocationTargetException e) {
        throw new MissingDependencyException(e, "Exception when invoking constructor {0}", constructor);
      }
    }

    public static <T> List<Constructor<T>> constructors (Class<T> clazz) {
      List<Constructor<T>> result = new ArrayList<>();
      for (Constructor<?> constructor : clazz.getConstructors()) {
        try {
          result.add(clazz.getConstructor(constructor.getParameterTypes()));
        } catch (NoSuchMethodException ignored) {
          //Not possible as we get the parameters from the class
        }
      }
      sort(result, new Comparator<Constructor<T>>() {
        //Sort the constructors so that the most specific is first
        @Override
        public int compare (Constructor<T> first, Constructor<T> second) {
          return Integer.compare(second.getParameterTypes().length, first.getParameterTypes().length);
        }
      });
      return result;
    }

    public static class Builder {
      protected final List<Module> modules = new ArrayList<>();

      public Builder () {
      }

      public Builder (List<Module> modules) {
        this.modules.addAll(modules);
      }

      public Builder with (Module... modules) {
        this.modules.addAll(asList(modules));
        return this;
      }

      public DependencyContainer build () {
        return new DependencyContainer(this.modules);
      }
    }
  }

  public static class MissingDependencyException extends RuntimeException {

    public MissingDependencyException (Exception cause, String message, Object... params) {
      super(format(message, params), cause);
    }

    public MissingDependencyException (String message, Object... params) {
      super(format(message, params));
    }
  }
}
