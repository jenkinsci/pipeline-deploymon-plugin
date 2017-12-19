/*
 * Copyright (c) 2017. Taimos GmbH http://www.taimos.de
 */

package de.taimos.pipeline.deploymon;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import de.taimos.httputils.HTTPRequest;
import de.taimos.httputils.WS;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import net.sf.json.JSONSerializer;

public class NotifyDeploymentStep extends AbstractStepImpl {
	
	private final String project;
	private final String service;
	private final String stage;
	private final String version;
	private final String credentials;
	private String url;
	
	@DataBoundConstructor
	public NotifyDeploymentStep(String credentials, String project, String service, String stage, String version) {
		this.credentials = credentials;
		this.project = project;
		this.service = service;
		this.stage = stage;
		this.version = version;
	}
	
	public String getUrl() {
		return url;
	}
	
	@DataBoundSetter
	public void setUrl(String url) {
		this.url = url;
	}
	
	public String getProject() {
		return project;
	}
	
	public String getService() {
		return service;
	}
	
	public String getStage() {
		return stage;
	}
	
	public String getVersion() {
		return version;
	}
	
	public String getCredentials() {
		return credentials;
	}
	
	@Extension
	public static class DescriptorImpl extends AbstractStepDescriptorImpl {
		
		public DescriptorImpl() {
			super(NotifyDeploymentStep.Execution.class);
		}
		
		@Override
		public String getFunctionName() {
			return "notifyDeployment";
		}
		
		@Override
		public String getDisplayName() {
			return "Notify deploymon.io about a new deployment";
		}
		
		public ListBoxModel doFillCredentialsItems(@AncestorInPath Item context) {
			
			if (context == null || !context.hasPermission(Item.CONFIGURE)) {
				return new ListBoxModel();
			}
			
			return new StandardListBoxModel()
					.includeEmptyValue()
					.includeMatchingAs(
							context instanceof Queue.Task
									? Tasks.getAuthenticationOf((Queue.Task) context)
									: ACL.SYSTEM,
							context,
							StringCredentials.class,
							Collections.<DomainRequirement>emptyList(),
							CredentialsMatchers.instanceOf(StringCredentials.class));
		}
	}
	
	public static class Execution extends AbstractSynchronousStepExecution<Void> {
		
		private static final String DEPLOYMON_ENDPOINT = "https://deploymon.io/api/projects/{projectId}/versions";
		private static final String PATHPARAM_PROJECT = "projectId";
		
		private static final String APPLICATION_JSON = "application/json";
		private static final String ENV_BUILD_URL = "BUILD_URL";
		
		@Inject
		private transient NotifyDeploymentStep step;
		@StepContextParameter
		private transient EnvVars envVars;
		@StepContextParameter
		private transient TaskListener listener;
		
		@Override
		protected Void run() throws Exception {
			String authToken;
			
			if (!StringUtils.isBlank(this.step.getCredentials())) {
				StringCredentials credential = CredentialsProvider.findCredentialById(this.step.getCredentials(), StringCredentials.class, this.getContext().get(Run.class), Collections.<DomainRequirement>emptyList());
				if (credential != null) {
					authToken = credential.getSecret().getPlainText();
				} else {
					throw new RuntimeException("Cannot find Jenkins credentials with name " + this.step.getCredentials());
				}
			} else {
				throw new RuntimeException("No Jenkins credentials were provided");
			}
			
			String url = this.step.getUrl();
			if (StringUtils.isBlank(url)) {
				url = this.envVars.get(ENV_BUILD_URL);
			}
			
			Map<String, String> bodyMap = new HashMap<>();
			bodyMap.put("service", this.step.getService());
			bodyMap.put("stage", this.step.getStage());
			bodyMap.put("version", this.step.getVersion());
			bodyMap.put("url", url);
			
			String body = JSONSerializer.toJSON(bodyMap).toString(0);
			
			HTTPRequest request = WS.url(DEPLOYMON_ENDPOINT).pathParam(PATHPARAM_PROJECT, this.step.getProject());
			request = request.contentType(APPLICATION_JSON).body(body);
			HttpResponse response = request.authBearer(authToken).post();
			
			if (!WS.isStatusOK(response)) {
				throw new RuntimeException("Failed to notify deploymon.io: " + response);
			}
			this.listener.getLogger().println("Successfully notified deploymon.io");
			return null;
		}
		
		private static final long serialVersionUID = 1L;
		
	}
	
}
