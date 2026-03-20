package com.fryrank.dagger;

import lombok.extern.log4j.Log4j2;

@Log4j2
public final class Dependencies {
    private static final AppComponent APP_COMPONENT;

    static {
        final String lambdaVersion = System.getenv("AWS_LAMBDA_FUNCTION_VERSION");
        final String initType = System.getenv("AWS_LAMBDA_INITIALIZATION_TYPE");
        log.info("Lambda init: AWS_LAMBDA_FUNCTION_VERSION={}, AWS_LAMBDA_INITIALIZATION_TYPE={}", lambdaVersion, initType);

        final long startNanos = System.nanoTime();
        APP_COMPONENT = DaggerAppComponent.create();

        final long createdMillis = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Dagger graph created in {} ms", createdMillis);

        final long warmupStartNanos = System.nanoTime();
        Warmup.run(APP_COMPONENT);
        final long warmupMillis = (System.nanoTime() - warmupStartNanos) / 1_000_000;
        log.info("Warmup completed in {} ms", warmupMillis);
    }

    private Dependencies() {}

    public static AppComponent appComponent() {
        return APP_COMPONENT;
    }
}
