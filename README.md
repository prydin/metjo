# metjo
MetJo - A Simple Bytecode Instrumentation Provider for Dropwizard Metrics

## Introduction
MetJo is a portmanteau for "Metrics for POJOs". It also appears to be a fairly common name in Suriname. 

MetJo was originally built to showcase method-level timing metrics with Dropwizard Metrics, but ended up taking on a life of its own.

MetJo picks up method timing in ways:
* Annotated mode - Methods timing is automatically collected if the method is annotated with @Timed (other annotations to be supported soon). This mode requires source code changes. 
* Pattern-based mode - Method timing is turned on by applying a pattern specified in a configuration file. This mode of operation does not require any code changes and you can even apply timing to system classes and framework classes. 

The two modes can be mixed. In other words, you can have an application that's instrumented using @Timed annotations and add ad-hoc method instrumentation using a pattern-based approach.

## Annotated Mode
Using the annotated mode is straightforward. Simply import the the "dropwizard-metric-core" and "dropwizard-metric-annotation" packages using the following Maven dependency snippet:

     <dependency>
          <groupId>io.dropwizard.metrics</groupId>
          <artifactId>metrics-core</artifactId>
          <version>3.2.3</version>
      </dependency>
      <dependency>
          <groupId>io.dropwizard.metrics</groupId>
          <artifactId>metrics-annotation</artifactId>
          <version>3.2.3</version>
      </dependency>

Once the library is imported, you can use the @Timed annotation on any method you want to be instrumented. Here is an example:

    @Timed(absolute = true)
    void doSomethingAwesome() { 
    }
    
That's all! From this point on, you will automatically receive performance metrics for this metric through the Dropwizard Metrics framework. 

## Pattern based mode
This mode is great for when you don't have the source code of an application or are unable to change the code. In pattern-based mode, you simply specify a pattern matching all the classes and methods you want to instrument. The patterns are specified as part of the configuration file. You can specify includes as well as excludes. For example, let's say you want to instrument all clases and methods in the com.ebberod.bank package, but you want to exclude the method com.ebberod.bank.Trader.idle. You'd specify this in the configuration file (more about that one below) as follows:

    includes: 
      - "com.ebberod.bank.*"
    excluces:
      - "com.ebberod.bank.Trader.idle"
  
## The configuration file
The configuration file defines the Dropwizard Metrics Reporter backend, as well as parameters for the instrumentation itself. The name of the configuration file is defined through the METJO_CONFIG environment variable. The configuration file has a section of includes and excludes (as described above) as well as a Reporter-specific property section. Here is an example of a configuration that's reporting data to Wavefront:

    includes:
      - "com.ebberod.*"
      - "java.net.URI.*"
      - "org.apache.tomcat.*"
    excludes:
      - "org.apache.tomcat.util.res.StringManager.getManager"
    reporter: wavefront
    properties:
      proxy: "localhost"
      port: 2878
      period: 20
      pointtags:
        dc: "virtualviking.net"
        service: "myService"
        jvmMetrics: true
        
## Running the agent
MetJo's bytecode instrumentation is implemented as a "Java Agent". Simply use the -javaagent flag for the java command and specify the MetJo jar file:
 
     java -javaagent:path/metjo-1.0-SNAPSHOT-jar-with-dependencies.jar -cp foo.jar foo.Main
     
 If you want to instrument system classes and framework classes, you need to append the MetJo jar to the boot classpath like this:
 
     java -bootclasspath/p:path/metjo-1.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:path/metjo-1.0-SNAPSHOT-jar-with-dependencies.jar -cp foo.jar foo.Main
 
 ## Known bugs and limitations
 * Only the @Timed annotation is supported. More annotations will be added soon.
 * Only the WavefrontReporter and ConsoleReporter are supported. More will be added soon.
 * Certain methods that make use of anonymous inner classes don't get instrumented.
 * Certain methods cause code verification exceptions. This seems to be due to a bug in javassist. To work around it, either exclude the offending method or run java with the -noverify flag.
