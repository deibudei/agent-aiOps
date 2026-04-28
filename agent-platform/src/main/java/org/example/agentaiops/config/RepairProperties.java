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
    private Agentic agentic = new Agentic();

    /** Returns the repository root used by repair tools. */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    /** Updates the repository root used by repair tools. */
    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /** Returns target-service configuration. */
    public TargetProject getTargetProject() {
        return targetProject;
    }

    /** Updates target-service configuration. */
    public void setTargetProject(TargetProject targetProject) {
        this.targetProject = targetProject;
    }

    /** Returns repair workflow limits. */
    public Workflow getWorkflow() {
        return workflow;
    }

    /** Updates repair workflow limits. */
    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    /** Returns local Git automation settings. */
    public Git getGit() {
        return git;
    }

    /** Updates local Git automation settings. */
    public void setGit(Git git) {
        this.git = git;
    }

    /** Returns GitHub PR automation settings. */
    public Github getGithub() {
        return github;
    }

    /** Updates GitHub PR automation settings. */
    public void setGithub(Github github) {
        this.github = github;
    }

    /** Returns Feishu notification settings. */
    public Feishu getFeishu() {
        return feishu;
    }

    /** Updates Feishu notification settings. */
    public void setFeishu(Feishu feishu) {
        this.feishu = feishu;
    }

    /** Returns LLM repair settings. */
    public Llm getLlm() {
        return llm;
    }

    /** Updates LLM repair settings. */
    public void setLlm(Llm llm) {
        this.llm = llm;
    }

    /** Returns LangChain4j agentic workflow settings. */
    public Agentic getAgentic() {
        return agentic;
    }

    /** Updates LangChain4j agentic workflow settings. */
    public void setAgentic(Agentic agentic) {
        this.agentic = agentic;
    }

    public static class TargetProject {
        private String name = "target-service";
        private String rootPath = "target-service";
        private String logPath = "target-service/logs";
        private String testCommand = "mvn -pl target-service test";

        /** Returns the logical target project name. */
        public String getName() {
            return name;
        }

        /** Updates the logical target project name. */
        public void setName(String name) {
            this.name = name;
        }

        /** Returns the target project root path. */
        public String getRootPath() {
            return rootPath;
        }

        /** Updates the target project root path. */
        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        /** Returns the target-service log path. */
        public String getLogPath() {
            return logPath;
        }

        /** Updates the target-service log path. */
        public void setLogPath(String logPath) {
            this.logPath = logPath;
        }

        /** Returns the Maven command used for validation. */
        public String getTestCommand() {
            return testCommand;
        }

        /** Updates the Maven command used for validation. */
        public void setTestCommand(String testCommand) {
            this.testCommand = testCommand;
        }
    }

    public static class Workflow {
        private int maxRepairAttempts = 3;
        private int processTimeoutSeconds = 120;

        /** Returns how many times a repair may retry tests. */
        public int getMaxRepairAttempts() {
            return maxRepairAttempts;
        }

        /** Updates how many times a repair may retry tests. */
        public void setMaxRepairAttempts(int maxRepairAttempts) {
            this.maxRepairAttempts = maxRepairAttempts;
        }

        /** Returns the external process timeout in seconds. */
        public int getProcessTimeoutSeconds() {
            return processTimeoutSeconds;
        }

        /** Updates the external process timeout in seconds. */
        public void setProcessTimeoutSeconds(int processTimeoutSeconds) {
            this.processTimeoutSeconds = processTimeoutSeconds;
        }
    }

    public static class Git {
        private boolean enabled;
        private String remote = "origin";
        private String baseBranch = "main";

        /** Returns whether local Git automation is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Updates whether local Git automation is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the Git remote name. */
        public String getRemote() {
            return remote;
        }

        /** Updates the Git remote name. */
        public void setRemote(String remote) {
            this.remote = remote;
        }

        /** Returns the PR base branch. */
        public String getBaseBranch() {
            return baseBranch;
        }

        /** Updates the PR base branch. */
        public void setBaseBranch(String baseBranch) {
            this.baseBranch = baseBranch;
        }
    }

    public static class Github {
        private boolean enabled;

        /** Returns whether GitHub PR creation is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Updates whether GitHub PR creation is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Feishu {
        private boolean enabled;
        private String webhookUrl = "";

        /** Returns whether Feishu notification is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Updates whether Feishu notification is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the Feishu webhook URL. */
        public String getWebhookUrl() {
            return webhookUrl;
        }

        /** Updates the Feishu webhook URL. */
        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }

    public static class Llm {
        private boolean enabled;
        private String provider = "openai";
        private float temperature = 0.1f;
        private int maxTokens = 2048;
        private int timeoutSeconds = 90;
        private int maxRetries = 1;
        private String supervisorModel = "";
        private String diagnosisModel = "";
        private String planModel = "";
        private String patchModel = "";

        /** Returns whether LLM planning and patch proposal are enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Updates whether LLM planning and patch proposal are enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the configured LLM provider name. */
        public String getProvider() {
            return provider;
        }

        /** Updates the configured LLM provider name. */
        public void setProvider(String provider) {
            this.provider = provider;
        }

        /** Returns the model temperature for repair generation. */
        public float getTemperature() {
            return temperature;
        }

        /** Updates the model temperature for repair generation. */
        public void setTemperature(float temperature) {
            this.temperature = temperature;
        }

        /** Returns the maximum model response token count. */
        public int getMaxTokens() {
            return maxTokens;
        }

        /** Updates the maximum model response token count. */
        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        /** Returns the per-request LLM timeout in seconds. */
        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        /** Updates the per-request LLM timeout in seconds. */
        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        /** Returns how many provider retries LangChain4j should perform. */
        public int getMaxRetries() {
            return maxRetries;
        }

        /** Updates how many provider retries LangChain4j should perform. */
        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        /** Returns the optional model override for the Agentic supervisor. */
        public String getSupervisorModel() {
            return supervisorModel;
        }

        /** Updates the optional model override for the Agentic supervisor. */
        public void setSupervisorModel(String supervisorModel) {
            this.supervisorModel = supervisorModel;
        }

        /** Returns the optional model override for diagnosis. */
        public String getDiagnosisModel() {
            return diagnosisModel;
        }

        /** Updates the optional model override for diagnosis. */
        public void setDiagnosisModel(String diagnosisModel) {
            this.diagnosisModel = diagnosisModel;
        }

        /** Returns the optional model override for repair planning. */
        public String getPlanModel() {
            return planModel;
        }

        /** Updates the optional model override for repair planning. */
        public void setPlanModel(String planModel) {
            this.planModel = planModel;
        }

        /** Returns the optional model override for patch proposal generation. */
        public String getPatchModel() {
            return patchModel;
        }

        /** Updates the optional model override for patch proposal generation. */
        public void setPatchModel(String patchModel) {
            this.patchModel = patchModel;
        }
    }

    public static class Agentic {
        private int maxSupervisorInvocations = 24;

        /** Returns the maximum number of sub-agent calls the supervisor may make. */
        public int getMaxSupervisorInvocations() {
            return maxSupervisorInvocations;
        }

        /** Updates the maximum number of sub-agent calls the supervisor may make. */
        public void setMaxSupervisorInvocations(int maxSupervisorInvocations) {
            this.maxSupervisorInvocations = maxSupervisorInvocations;
        }
    }
}
