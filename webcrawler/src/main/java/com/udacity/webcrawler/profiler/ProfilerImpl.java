package com.udacity.webcrawler.profiler;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Objects;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

/**
 * Concrete implementation of the {@link Profiler}.
 */
final class ProfilerImpl implements Profiler {

  private final Clock clock;
  private final ProfilingState state = new ProfilingState();
  private final ZonedDateTime startTime;

  @Inject
  ProfilerImpl(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
    this.startTime = ZonedDateTime.now(clock);
  }

  @Override
  public <T> T wrap(Class<T> klass, T delegate) {
    Objects.requireNonNull(klass);

    // TODO: Use a dynamic proxy (java.lang.reflect.Proxy) to "wrap" the delegate in a
    //       ProfilingMethodInterceptor and return a dynamic proxy from this method.
    //       See https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html.

    boolean annotatedMethods = false;
    for(Method method : klass.getMethods()){
      if(method.getAnnotation(Profiled.class) != null) {
        annotatedMethods = true;
        break;
      }
    }
    if(!annotatedMethods){
      throw new IllegalArgumentException(klass.getName() +" doesnt have @Profiled methods");
    }

    ProfilingMethodInterceptor profilingMethodInterceptor = new ProfilingMethodInterceptor(clock, delegate, state);

    // Because IntelliJ was giving error: Unchecked cast: 'java.lang.Object' to 'T' , Try to Generify ProfilerImpl.java
    @SuppressWarnings("unchecked")
    T proxy = (T) Proxy.newProxyInstance(klass.getClassLoader(), new Class[]{klass}, profilingMethodInterceptor);

    return proxy;
  }

  @Override
  public void writeData(Path path) {
    // a simple buffered writer.
    try(BufferedWriter writer = Files.newBufferedWriter(path)){
      writeData(writer);
      writer.flush();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void writeData(Writer writer) throws IOException {
    writer.write("Run at " + RFC_1123_DATE_TIME.format(startTime));
    writer.write(System.lineSeparator());
    state.write(writer);
    writer.write(System.lineSeparator());
  }
}
