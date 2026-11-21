import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;

/**
 * Minimal stand-in for `mvn test` while the Maven wrapper is unusable: runs the named test
 * classes through the JUnit Platform launcher and reports the standard summary.
 */
public final class TestRunner {

    public static void main(String[] args) {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
        for (String className : args) {
            builder.selectors(DiscoverySelectors.selectClass(className));
        }
        LauncherDiscoveryRequest request = builder.build();

        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        PrintWriter out = new PrintWriter(System.out, true);
        summary.printTo(out);
        summary.printFailuresTo(out);

        if (summary.getTotalFailureCount() > 0 || summary.getTestsSucceededCount() == 0) {
            System.exit(1);
        }
    }
}
