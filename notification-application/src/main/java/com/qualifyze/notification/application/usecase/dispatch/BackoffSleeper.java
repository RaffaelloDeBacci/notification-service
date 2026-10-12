package com.qualifyze.notification.application.usecase.dispatch;

import java.time.Duration;

/** Waits between delivery attempts; replaceable in tests so retries run instantly. */
@FunctionalInterface
public interface BackoffSleeper {

    BackoffSleeper THREAD_SLEEP = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
