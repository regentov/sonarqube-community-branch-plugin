package com.github.mc1arke.sonarqube.plugin.scanner;

import org.junit.Test;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.utils.System2;
import org.sonar.core.config.ScannerProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ScannerPullRequestPropertySensorTest {

    private final System2 system2 = mock(System2.class);

    @Test
    public void testPropertySensorWithGitlabCIEnvValues() throws IOException {

        Path temp = Files.createTempDirectory("sensor");

        DefaultInputFile inputFile = new TestInputFileBuilder("foo", "src/Foo.xoo").initMetadata("a\nb\nc\nd\ne\nf\ng\nh\ni\n").build();
        SensorContextTester context = SensorContextTester.create(temp);
        context.fileSystem().add(inputFile);

        when(system2.envVariable("GITLAB_CI")).thenReturn("true");
        when(system2.envVariable("CI_PIPELINE_ID")).thenReturn("value");
        when(system2.envVariable("CI_MERGE_REQUEST_PROJECT_URL")).thenReturn("value");

        ScannerPullRequestPropertySensor sensor = new ScannerPullRequestPropertySensor(system2);
        sensor.execute(context);

        Map<String, String> properties = context.getContextProperties();

        assertEquals(2, properties.size());
    }

    @Test
    public void testPropertySensorWithAzureDevOpsEnvValuesForkedProject() {
        SensorContext sensorContext = mock(SensorContext.class);

        when(system2.envVariable("TF_BUILD")).thenReturn("True");
        when(system2.envVariable("SYSTEM_PULLREQUEST_PULLREQUESTID")).thenReturn("key");
        when(system2.envVariable("SYSTEM_PULLREQUEST_SOURCEBRANCH")).thenReturn("refs/heads/users/raisa/feature/new-feature");
        when(system2.envVariable("SYSTEM_PULLREQUEST_TARGETBRANCH")).thenReturn("refs/heads/master");
        when(system2.envVariable("SYSTEM_PULLREQUEST_ISFORK")).thenReturn("True");

        ScannerPullRequestPropertySensor sensor = new ScannerPullRequestPropertySensor(system2);
        sensor.execute(sensorContext);

        verify(sensorContext).addContextProperty(ScannerProperties.PULL_REQUEST_KEY, "key");
        verify(sensorContext).addContextProperty(ScannerProperties.PULL_REQUEST_BRANCH, "feature/new-feature");
        verify(sensorContext).addContextProperty(ScannerProperties.PULL_REQUEST_BASE, "master");
    }

    @Test
    public void testPropertySensorWithAzureDevOpsEnvValues() {
        SensorContext sensorContext = mock(SensorContext.class);

        when(system2.envVariable("TF_BUILD")).thenReturn("True");
        when(system2.envVariable("SYSTEM_PULLREQUEST_PULLREQUESTID")).thenReturn("key");
        when(system2.envVariable("SYSTEM_PULLREQUEST_SOURCEBRANCH")).thenReturn("feature/new-feature");
        when(system2.envVariable("SYSTEM_PULLREQUEST_TARGETBRANCH")).thenReturn("refs/heads/master");

        ScannerPullRequestPropertySensor sensor = new ScannerPullRequestPropertySensor(system2);
        sensor.execute(sensorContext);

        verify(sensorContext).addContextProperty(ScannerProperties.PULL_REQUEST_KEY, "key");
        verify(sensorContext).addContextProperty(ScannerProperties.PULL_REQUEST_BRANCH, "feature/new-feature");
        verify(sensorContext).addContextProperty(ScannerProperties.PULL_REQUEST_BASE, "master");
    }

}
