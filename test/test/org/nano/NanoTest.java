package test.org.nano;

import org.junit.Test;
import org.nano.Nano;
import org.nano.Nano.DependencyContainer;
import org.nano.Nano.Module;

import java.util.ArrayList;

import static junit.framework.Assert.*;

public class NanoTest {
  public static class A {}
  public interface BInterface {}

  public static class B implements BInterface {
    public final A a;

    public B(A a) {
      this.a = a;
    }
  }

  public static class C {
    public final A a;
    public final BInterface b;

    public C (A a, BInterface b) {
      this.a = a;
      this.b = b;
    }
  }

  public static class D {
    private final A a;

    public D(A a) {
      this.a = a;
    }
  }

  public static class E {
    public final F f;

    public E(F f) {
      this.f = f;
    }
  }

  public static class F {
    public F() {}
  }

  public static class Singleton {
    static int instances = 0;
    public Singleton() {
      instances++;
    }
  }

  public static class Named {
    public final A a1;
    public final A a2;

    public Named(@Nano.NanoName("a1") A a1, @Nano.NanoName("a2") A a2) {
      this.a1 = a1;
      this.a2 = a2;
    }
  }

  private static DependencyContainer create(Module... modules) {
    return new DependencyContainer.Builder()
      .with(modules)
      .build();
  }

  @Test public void can_resolve_single_instance_by_type() {
    final A instance = new A();
    DependencyContainer container = create(new Module() {{
      bind(A.class).to(instance);
    }});
    assertEquals("resolve instance by type", instance, container.resolve(A.class));
  }

  @Test public void can_resolve_instance_by_interface_type() {
    final BInterface instance = new B(new A());
    DependencyContainer container = create(new Module() {{
      bind(BInterface.class).to(instance);
    }});
    assertEquals("resolve instance by type", instance, container.resolve(BInterface.class));
  }

  @Test public void can_create_instance_from_types() {
    DependencyContainer container = create(new Module() {{
      bind(A.class).to(A.class);
      bind(B.class).to(B.class);
    }});
    assertNotNull("B class", container.resolve(B.class));
    assertNotNull("B class A variable", container.resolve(B.class).a);
  }

  @Test public void can_create_instance_from_mix_of_instances_and_super_types() {
    final A instance = new A();
    DependencyContainer container = create(new Module() {{
      bind(A.class).to(instance);
      bind(BInterface.class).to(B.class);
      bind(C.class).to(C.class);
    }});

    C c = container.resolve(C.class);

    assertNotNull("C class", c);
    assertNotNull("C class B variable", c.b);
    assertEquals("C class A variable", instance, c.a);

    B b = (B) c.b;

    assertEquals("B class A instance", instance, b.a);
  }

  @Test public void can_create_unknown_type_from_container_dependency() {
    DependencyContainer container = create(new Module() {{
      bind(A.class).to(A.class);
    }});
    D d = container.resolve(D.class);

    assertNotNull("D class", d);
    assertNotNull("D class A variable", d.a);
  }

  @Test public void can_create_create_unknown_intermediate_types_that_have_default_constructor() {
    DependencyContainer container = create(new Module() {{
      bind(E.class).to(E.class);
    }});
    E e = container.resolve(E.class);

    assertNotNull("E class", e);
    assertNotNull("E class F variable", e.f);
  }

  @Test public void creates_singletons_only_once() {
    DependencyContainer container = create(new Module() {{
      bind(Singleton.class).to(Singleton.class, true);
    }});

    Singleton singleton1 = container.resolve(Singleton.class);
    Singleton singleton2 = container.resolve(Singleton.class);

    assertEquals("instance count", 1, Singleton.instances);
    assertTrue("same instance ", singleton1 == singleton2);
  }

  @Test public void can_resolve_instances_by_name() {
    final A a1 = new A();
    final A a2 = new A();
    DependencyContainer container = create(new Module() {{
      bind(A.class, "a1").to(a1);
      bind(A.class, "a2").to(a2);
      bind(Named.class).to(Named.class);
    }});

    Named named = container.resolve(Named.class, "a1");

    assertTrue("a1 ", a1 == named.a1);
    assertTrue("a2 ", a2 == named.a2);
  }

  @Test public void can_resolved_named_singleton_types() {
    DependencyContainer container = create(new Module() {{
      bind(A.class).to(A.class);
      bind(B.class, "b1").to(B.class, true);
      bind(BInterface.class, "b2").to(B.class, true);
    }});

    B b1a = container.resolve(B.class, "b1");
    B b1b = container.resolve(B.class, "b1");

    B b2a = (B) container.resolve(BInterface.class, "b2");
    B b2b = (B) container.resolve(BInterface.class, "b2");

    assertTrue("different instances", b1a != b2a);
    assertTrue("b1 ", b1a == b1b);
    assertTrue("b2 ", b2a == b2b);
  }

  @Test public void can_resolve_instance_with_default_constructor_from_empty_container() {
    DependencyContainer container = new DependencyContainer.Builder(new ArrayList<Module>())
      .build();
    A instance = container.resolve(A.class);
    assertNotNull("a instance", instance);
  }

  @Test(expected = Nano.MissingDependencyException.class)
  public void throws_exception_for_unregistered_type_that_cannot_be_constructed_from_container() {
    DependencyContainer container = create(new Module() {{}});
    C instance = container.resolve(C.class);
    fail("Instance is not null " + instance);
  }
}
