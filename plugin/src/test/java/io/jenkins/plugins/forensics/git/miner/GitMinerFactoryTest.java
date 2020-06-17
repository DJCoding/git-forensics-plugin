package io.jenkins.plugins.forensics.git.miner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import edu.hm.hafner.util.FilteredLog;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.NullSCM;
import hudson.util.DescribableList;

import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link GitMinerFactory}.
 *
 * @author Ullrich Hafner
 */
class GitMinerFactoryTest {
    private static final TaskListener NULL_LISTENER = TaskListener.NULL;

    @Test
    void shouldSkipIfScmIsNotGit() {
        FilteredLog logger = createLogger();

        GitMinerFactory factory = new GitMinerFactory();
        assertThat(factory.createMiner(new NullSCM(), null, createWorkTreeStub(), NULL_LISTENER, logger)).isEmpty();

        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("SCM 'hudson.scm.NullSCM' is not of type GitSCM");
    }

    @Test
    void shouldCreateBlamerForGit() throws IOException, InterruptedException {
        GitSCM gitSCM = Mockito.mock(GitSCM.class);
        Mockito.when(gitSCM.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP));

        Run<?, ?> run = Mockito.mock(Run.class);
        EnvVars envVars = new EnvVars();
        envVars.put("GIT_COMMIT", "test_commit");
        Mockito.when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);

        FilePath workspace = createWorkTreeStub();
        GitClient gitClient = Mockito.mock(GitClient.class);
        Mockito.when(gitSCM.createClient(NULL_LISTENER, envVars, run, workspace)).thenReturn(gitClient);
        ObjectId commit = Mockito.mock(ObjectId.class);
        Mockito.when(gitClient.revParse(ArgumentMatchers.anyString())).thenReturn(commit);

        FilteredLog logger = createLogger();

        GitMinerFactory factory = new GitMinerFactory();
        Optional<RepositoryMiner> blamer = factory.createMiner(gitSCM, run, workspace, NULL_LISTENER, logger);

        assertThat(blamer).isNotEmpty().containsInstanceOf(GitRepositoryMiner.class);
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("-> Git miner successfully created in working tree '/'");
    }

    @Test
    void shouldCreateNullBlamerOnShallowGit() {
        CloneOption shallowCloneOption = Mockito.mock(CloneOption.class);
        Mockito.when(shallowCloneOption.isShallow()).thenReturn(true);

        GitSCM gitSCM = Mockito.mock(GitSCM.class);
        Mockito.when(gitSCM.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP, Lists.list(shallowCloneOption)));

        FilteredLog logger = createLogger();

        GitMinerFactory gitChecker = new GitMinerFactory();

        assertThat(gitChecker.createMiner(gitSCM, Mockito.mock(Run.class), createWorkTreeStub(), NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getInfoMessages()).contains(GitRepositoryValidator.INFO_SHALLOW_CLONE);
        assertThat(logger.getErrorMessages()).isEmpty();
    }

    @Test
    void shouldCreateNullBlamerOnError() throws IOException, InterruptedException {
        GitMinerFactory gitChecker = new GitMinerFactory();
        Run<?, ?> run = Mockito.mock(Run.class);
        List<GitSCMExtension> extensions = new ArrayList<>();
        GitSCM gitSCM = new GitSCM(null, null, false, null, null, null, extensions);

        Mockito.when(run.getEnvironment(NULL_LISTENER)).thenThrow(new IOException());

        FilteredLog logger = createLogger();

        assertThat(gitChecker.createMiner(gitSCM, run, createWorkTreeStub(), NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("Exception while creating a GitClient instance for work tree '/'");
    }

    private FilePath createWorkTreeStub() {
        File mock = Mockito.mock(File.class);
        Mockito.when(mock.getPath()).thenReturn("/");
        return new FilePath(mock);
    }

    private FilteredLog createLogger() {
        return new FilteredLog("errors");
    }
}
