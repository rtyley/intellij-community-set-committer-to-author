/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author max
 */
public class UniqueResultsQuery<T, M> implements Query<T> {
  private final Query<T> myOriginal;
  private final TObjectHashingStrategy<M> myHashingStrategy;
  private final Function<T, M> myMapper;

  public UniqueResultsQuery(final Query<T> original) {
    this(original, ContainerUtil.<M>canonicalStrategy(), (Function<T, M>)FunctionUtil.<M>id());
  }

  public UniqueResultsQuery(final Query<T> original, TObjectHashingStrategy<M> hashingStrategy) {
    this(original, hashingStrategy, (Function<T, M>)FunctionUtil.<M>id());
  }

  public UniqueResultsQuery(final Query<T> original, TObjectHashingStrategy<M> hashingStrategy, Function<T, M> mapper) {
    myOriginal = original;
    myHashingStrategy = hashingStrategy;
    myMapper = mapper;
  }

  @Override
  public T findFirst() {
    return myOriginal.findFirst();
  }

  @Override
  public boolean forEach(@NotNull final Processor<T> consumer) {
    return process(consumer, Collections.synchronizedSet(new THashSet<M>(myHashingStrategy)));
  }

  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<T> consumer) {
    return processAsync(consumer, Collections.synchronizedSet(new THashSet<M>(myHashingStrategy)));
  }

  private boolean process(final Processor<T> consumer, final Set<M> processedElements) {
    return myOriginal.forEach(new MyProcessor(processedElements, consumer));
  }

  private AsyncFuture<Boolean> processAsync(final Processor<T> consumer, final Set<M> processedElements) {
    return myOriginal.forEachAsync(new MyProcessor(processedElements, consumer));
  }


  @Override
  @NotNull
  public Collection<T> findAll() {
    if (myMapper == Function.ID) {
      Set<M> set = new THashSet<M>(myHashingStrategy);
      process(CommonProcessors.<T>alwaysTrue(), Collections.synchronizedSet(set));
      //noinspection unchecked
      return (Collection<T>)set;
    }
    else {
      final CommonProcessors.CollectProcessor<T> processor = new CommonProcessors.CollectProcessor<T>(Collections.synchronizedList(new ArrayList<T>()));
      forEach(processor);
      return processor.getResults();
    }
  }

  @Override
  public T[] toArray(final T[] a) {
    return findAll().toArray(a);
  }

  @Override
  public Iterator<T> iterator() {
    return findAll().iterator();
  }

  private class MyProcessor implements Processor<T> {
    private final Set<M> myProcessedElements;
    private final Processor<T> myConsumer;

    public MyProcessor(Set<M> processedElements, Processor<T> consumer) {
      myProcessedElements = processedElements;
      myConsumer = consumer;
    }

    @Override
    public boolean process(final T t) {
      return !myProcessedElements.add(myMapper.fun(t)) || myConsumer.process(t);
    }
  }
}
