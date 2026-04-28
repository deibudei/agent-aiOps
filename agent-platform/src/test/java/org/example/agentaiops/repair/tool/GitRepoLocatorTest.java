package org.example.agentaiops.repair.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GitRepoLocatorTest {

    @Test
    void parsesHttpsRemote() {
        Optional<GitRepoLocator.RepoCoordinate> coordinate =
                GitRepoLocator.parseRemote("https://github.com/deibudei/agent-aiOps.git");

        assertThat(coordinate).isPresent();
        assertThat(coordinate.get().owner()).isEqualTo("deibudei");
        assertThat(coordinate.get().repo()).isEqualTo("agent-aiOps");
    }

    @Test
    void parsesHttpsRemoteWithoutDotGit() {
        Optional<GitRepoLocator.RepoCoordinate> coordinate =
                GitRepoLocator.parseRemote("https://github.com/deibudei/agent-aiOps");

        assertThat(coordinate).isPresent();
        assertThat(coordinate.get().owner()).isEqualTo("deibudei");
        assertThat(coordinate.get().repo()).isEqualTo("agent-aiOps");
    }

    @Test
    void parsesSshRemote() {
        Optional<GitRepoLocator.RepoCoordinate> coordinate =
                GitRepoLocator.parseRemote("git@github.com:deibudei/agent-aiOps.git");

        assertThat(coordinate).isPresent();
        assertThat(coordinate.get().owner()).isEqualTo("deibudei");
        assertThat(coordinate.get().repo()).isEqualTo("agent-aiOps");
    }

    @Test
    void rejectsBlankRemote() {
        assertThat(GitRepoLocator.parseRemote("")).isEmpty();
        assertThat(GitRepoLocator.parseRemote(null)).isEmpty();
        assertThat(GitRepoLocator.parseRemote("file:///tmp/repo")).isEmpty();
    }
}
