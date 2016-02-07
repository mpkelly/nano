Nano - A lightweight dependency injection container for Java and Android.
=====

##Features

- SimpleAPI
- Low ceremony
- Minimial amount of magic
- Modular
- Named dependencies
- Fast and lightweight
- Single file

##API Quick Start

To use Nano you first need to define some modules. A module definition looks like this:

```
    final A instance = new A();
    
    class MyModule extends Module {
      {
        bind(A.class).to(instance); //Instance binding
        bind(BInterface.class).to(B.class, true);  //Singleton 
        bind(C.class, "name").to(C.class); //Type binding - will be created when requested        
      }
    }
```

Then you need to create your container using the DependencyContainer Builder

```

    DependencyContainer container = new DependencyContainer.Builder()
      .with(new MyModule())
      .build();
      
```

Once you've built your container, you can resolve your dependencies like so

```
    A a = container.resolve(A.class);
    C c = container.resolve(C.class, "name");
``

And that's it!



