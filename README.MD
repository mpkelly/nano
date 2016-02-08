Nano - A lightweight dependency injection container for Java and Android.
=====

## Requirements

- Java 1.7 

##Features

- Simple API
- Low ceremony
- Minimal amount of magic
- Modular
- Named dependencies
- Fast and lightweight
- Single file

##API Quick Start

To use Nano you first need to define some modules. A module definition looks like this:

```java
final A instance = new A();

class MyModule extends Module {
  {
    bind(A.class).to(instance); //Instance binding
    bind(BInterface.class).to(B.class, true);  //Singleton 
    bind(C.class, "name").to(C.class); //Named binding        
  }
}
```

Then you need to create your container using the DependencyContainer Builder

```java

DependencyContainer container = new DependencyContainer.Builder()
  .with(new MyModule())
  .build();
      
```

Once you've built your container, you can resolve your dependencies like so

```java

A a = container.resolve(A.class);
C c = container.resolve(C.class, "name");

```

And that's it!

## Resolving unregistered types

Nano can create instances that are not registered within the container, whether these are root or intermediary types, providing at least one of these conditions are met:

- The types dependencies are registered with the container
- The types dependencies have a no-arg constructor
- The type itself has a no-arg constructor
 
## Named dependencies

You need to annotate your class's constructor parameters like so

```java

public Named (@NanoName("a1") A a1, @NanoName("a2") A a2) {
  this.a1 = a1;
  this.a2 = a2;
}
    
```

Take a look at the [tests] (/test/test/org/nano/NanoTest.java) for more info. 

