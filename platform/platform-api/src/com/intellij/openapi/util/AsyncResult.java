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
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class AsyncResult<T> extends ActionCallback {
  protected T myResult;

  @NotNull
  public AsyncResult<T> setDone(T result) {
    myResult = result;
    setDone();
    return this;
  }

  @NotNull
  public AsyncResult<T> setRejected(T result) {
    myResult = result;
    setRejected();
    return this;
  }

  @NotNull
  public <DependentResult> AsyncResult<DependentResult> subResult(@NotNull Function<T, DependentResult> doneHandler) {
    return subResult(new AsyncResult<DependentResult>(), doneHandler);
  }

  @NotNull
  public <SubResult, SubAsyncResult extends AsyncResult<SubResult>> SubAsyncResult subResult(@NotNull SubAsyncResult subResult,
                                                                                             @NotNull Function<T, SubResult> doneHandler) {
    doWhenDone(new SubResultDoneCallback<T, SubResult, SubAsyncResult>(subResult, doneHandler)).notifyWhenRejected(subResult);
    return subResult;
  }

  @NotNull
  public AsyncResult<T> doWhenDone(@NotNull final Handler<T> handler) {
    doWhenDone(new Runnable() {
      public void run() {
        handler.run(myResult);
      }
    });
    return this;
  }

  @NotNull
  public AsyncResult<T> doWhenRejected(@NotNull final Handler<T> handler) {
    doWhenRejected(new Runnable() {
      public void run() {
        handler.run(myResult);
      }
    });
    return this;
  }

  @NotNull
  public final AsyncResult<T> notify(@NotNull final ActionCallback child) {
    super.notify(child);
    return this;
  }

  public T getResult() {
    return myResult;
  }

  public interface Handler<T> {
    void run(T t);
  }

  public static class Done<T> extends AsyncResult<T> {
    public Done(T value) {
      setDone(value);
    }
  }

  public static class Rejected<T> extends AsyncResult<T> {
    public Rejected() {
      setRejected();
    }

    public Rejected(T value) {
      setRejected(value);
    }
  }

  // we don't use inner class, avoid memory leak, we don't want to hold this result while dependent is computing
  private static class SubResultDoneCallback<Result, SubResult, AsyncSubResult extends AsyncResult<SubResult>> implements Handler<Result> {
    private static final Logger LOG = Logger.getInstance(SubResultDoneCallback.class);

    private final AsyncSubResult dependentResult;
    private final Function<Result, SubResult> doneHandler;

    public SubResultDoneCallback(AsyncSubResult dependentResult, Function<Result, SubResult> doneHandler) {
      this.dependentResult = dependentResult;
      this.doneHandler = doneHandler;
    }

    public void run(Result result) {
      SubResult v;
      try {
        v = doneHandler.fun(result);
      }
      catch (Throwable e) {
        dependentResult.setRejected();
        LOG.error(e);
        return;
      }
      dependentResult.setDone(v);
    }
  }
}