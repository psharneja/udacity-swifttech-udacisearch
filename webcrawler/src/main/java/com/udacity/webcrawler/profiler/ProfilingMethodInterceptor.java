package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private final Object target;
  private final ProfilingState profilingState;


  ProfilingMethodInterceptor(Clock clock, Object target, ProfilingState profilingState) {
    this.clock = Objects.requireNonNull(clock);
    this.target = target;
    this.profilingState = profilingState;

  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // TODO: This method interceptor should inspect the called method to see if it is a profiled
    //       method. For profiled methods, the interceptor should record the start time, then
    //       invoke the method using the object that is being profiled. Finally, for profiled
    //       methods, the interceptor should record how long the method call took, using the
    //       ProfilingState methods.

    // Kept this at the top otherwise checking the condition in the if statement causes warning.
    boolean isProfiled = method.getAnnotation(Profiled.class) != null;

    Instant startTime = null;
    if(isProfiled){
      startTime = clock.instant();
    }

    Object res;
    try{
      res = method.invoke(target, args);
    } catch(InvocationTargetException ex){
      throw ex.getTargetException();
    } catch(Throwable t){
      throw new RuntimeException(t);
    } finally{
      if(isProfiled) {
        Duration duration = Duration.between(startTime, clock.instant());
        profilingState.record(target.getClass(), method, duration);
      }
    }
    return res;
  }
}
