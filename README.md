# metjo
MetJo - A Simple Bytecode Instrumentation Provider for Dropwizard Metrics

## Introduction
MetJo is a portmantuea for "Metrics for POJOs". It also appears to be a fairly common name in Suriname. 

MetJo was originally built to showcase method-level timing metrics with Dropwizard Metrics, but ended up taking on a life of its own.

MetJo picks up method timing in ways:
* Annotated mode - Methods timing is automatically collected if the method is annotated with @Timed (other annotations to be supported soon). This mode requires source code changes. 
* Pattern-based mode - Method timing is turned on by applying a pattern specified in a configuration file. This mode of operation does not require any code changes and you can even apply timing to system classes and framework classes. 

The two modes can be mixed. In other words, you can have an application that's instrumented using @Timed annotations and add ad-hoc method instrumentation using a pattern-based approach.

## Annotated Mode
Using the annotated mode is straightforward. Simply import the the "dropwizard-metric-core" and "dropwizard-metric-annotation"
