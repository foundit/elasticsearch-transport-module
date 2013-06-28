package no.found.elasticsearch.transport.netty.ssl;

import java.util.concurrent.Executor;

/**
 * {@link Executor} which executes the command in the caller thread.
 */
public final class ImmediateExecutor implements Executor {

    /**
     * The default instance.
     */
    public static final ImmediateExecutor INSTANCE = new ImmediateExecutor();

    public void execute(Runnable command) {
        command.run();
    }

    private ImmediateExecutor() {
        // should use static instance
    }
}
