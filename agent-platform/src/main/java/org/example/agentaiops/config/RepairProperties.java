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
        private String baseUrl = "http://localhost:9910";
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

        /** Returns the running target-service base URL used by demo scenarios. */
        public String getBaseUrl() {
            return baseUrl;
        }

        /** Updates the running target-service base URL used by demo scenarios. */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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
        private int maxPatchAttempts = 2;
        private int processTimeoutSeconds = 120;

        /** Returns how many times a repair may retry tests. */
        public int getMaxRepairAttempts() {
            return maxRepairAttempts;
        }

        /** Updates how many times a repair may retry tests. */
        public void setMaxRepairAttempts(int maxRepairAttempts) {
            this.maxRepairAttempts = maxRepairAttempts;
        }

        /** Returns how many times the patch agent may regenerate after failing tests. */
        public int getMaxPatchAttempts() {
            return maxPatchAttempts;
        }

        /** Updates how many times the patch agent may regenerate after failing tests. */
        public void setMaxPatchAttempts(int maxPatchAttempts) {
            this.maxPatchAttempts = maxPatchAttempts;
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
        private String baseBranch = "demo/fault/quantity-division-by-zero";

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
        private String client = "rest";
        private String token = "";
        private String owner = "";
        private String repo = "";
        private String apiBaseUrl = "https://api.github.com";

        /** Returns whether GitHub PR creation is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Updates whether GitHub PR creation is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns rest or cli; rest uses the GitHub REST API and cli falls back to gh CLI. */
        public String getClient() {
            return client;
        }

        /** Updates the GitHub client implementation. */
        public void setClient(String client) {
            this.client = client;
        }

        /** Returns the GitHub personal access token used for REST authentication. */
        public String getToken() {
            return token;
        }

        /** Updates the GitHub personal access token used for REST authentication. */
        public void setToken(String token) {
            this.token = token;
        }

        /** Returns the optional repository owner override. Empty string means auto-detect from git remote. */
        public String getOwner() {
            return owner;
        }

        /** Updates the optional repository owner override. */
        public void setOwner(String owner) {
            this.owner = owner;
        }

        /** Returns the optional repository name override. Empty string means auto-detect from git remote. */
        public String getRepo() {
            return repo;
        }

        /** Updates the optional repository name override. */
        public void setRepo(String repo) {
            this.repo = repo;
        }

        /** Returns the GitHub REST API base URL. Override for GitHub Enterprise. */
        public String getApiBaseUrl() {
            return apiBaseUrl;
        }

        /** Updates the GitHub REST API base URL. */
        public void setApiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
        }
    }

    public static class Feishu {
        private boolean enabled;
        private String webhookUrl = "";
        private String signingSecret = "";

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

        /** Returns the optional Feishu signing secret used for webhook signature verification. */
        public String getSigningSecret() {
            return signingSecret;
        }

        /** Updates the optional Feishu signing secret used for webhook signature verification. */
        public void setSigningSecret(String signingSecret) {
            this.signingSecret = signingSecret;
        }
    }

    public static class Llm {
        private boolean enabled;
        private String provider = "openai";
        private float temperature = 0.1f;
        private int maxTokens = 2048;
        private int timeoutSeconds = 90;
        private int maxRetries = 1;
        private String diagnosisModel = "";
        private String planModel = "";
        private String patchModel = "";
        private String reflectionModel = "";

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

        /** Returns the optional model override for post-repair reflection. */
        public String getReflectionModel() {
            return reflectionModel;
        }

        /** Updates the optional model override for post-repair reflection. */
        public void setReflectionModel(String reflectionModel) {
            this.reflectionModel = reflectionModel;
        }
    }

    public static class Agentic {
        private boolean fileReadCacheEnabled = true;

        /** Returns whether Agentic readFile results are cached within one repair session. */
        public boolean isFileReadCacheEnabled() {
            return fileReadCacheEnabled;
        }

        /** Updates whether Agentic readFile results are cached within one repair session. */
        public void setFileReadCacheEnabled(boolean fileReadCacheEnabled) {
            this.fileReadCacheEnabled = fileReadCacheEnabled;
        }
    }
}
