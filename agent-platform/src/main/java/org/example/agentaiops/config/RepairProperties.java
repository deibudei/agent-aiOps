package org.example.agentaiops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "repair")
public class RepairProperties {

    private String workspaceRoot = ".";
    private TargetProject targetProject = new TargetProject();
    private Workflow workflow = new Workflow();
    private Git git = new Git();
    private Github github = new Github();
    private Feishu feishu = new Feishu();
    private Llm llm = new Llm();

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public TargetProject getTargetProject() {
        return targetProject;
    }

    public void setTargetProject(TargetProject targetProject) {
        this.targetProject = targetProject;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    public Git getGit() {
        return git;
    }

    public void setGit(Git git) {
        this.git = git;
    }

    public Github getGithub() {
        return github;
    }

    public void setGithub(Github github) {
        this.github = github;
    }

    public Feishu getFeishu() {
        return feishu;
    }

    public void setFeishu(Feishu feishu) {
        this.feishu = feishu;
    }

    public Llm getLlm() {
        return llm;
    }

    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    public static class TargetProject {
        private String name = "target-service";
        private String rootPath = "target-service";
        private String logPath = "target-service/logs/target-service.log";
        private String testCommand = "mvn -pl target-service test";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getLogPath() {
            return logPath;
        }

        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }

        public String getTestCommand() {
            return testCommand;
        }

        public void setTestCommand(String testCommand) {
            this.testCommand = testCommand;
        }
    }

    public static class Workflow {
        private int maxRepairAttempts = 3;
        private int processTimeoutSeconds = 120;

        public int getMaxRepairAttempts() {
            return maxRepairAttempts;
        }

        public void setMaxRepairAttempts(int maxRepairAttempts) {
            this.maxRepairAttempts = maxRepairAttempts;
        }

        public int getProcessTimeoutSeconds() {
            return processTimeoutSeconds;
        }

        public void setProcessTimeoutSeconds(int processTimeoutSeconds) {
            this.processTimeoutSeconds = processTimeoutSeconds;
        }
    }

    public static class Git {
        private boolean enabled;
        private String remote = "origin";
        private String baseBranch = "main";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRemote() {
            return remote;
        }

        public void setRemote(String remote) {
            this.remote = remote;
        }

        public String getBaseBranch() {
            return baseBranch;
        }

        public void setBaseBranch(String baseBranch) {
            this.baseBranch = baseBranch;
        }
    }

    public static class Github {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Feishu {
        private boolean enabled;
        private String webhookUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }

    public static class Llm {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
