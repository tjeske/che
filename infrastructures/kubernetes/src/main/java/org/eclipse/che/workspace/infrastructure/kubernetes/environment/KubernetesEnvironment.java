/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.workspace.infrastructure.kubernetes.environment;

import com.google.common.collect.ImmutableMap;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.extensions.Ingress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.che.api.core.model.workspace.Warning;
import org.eclipse.che.api.core.model.workspace.config.Command;
import org.eclipse.che.api.workspace.server.spi.environment.InternalEnvironment;
import org.eclipse.che.api.workspace.server.spi.environment.InternalMachineConfig;
import org.eclipse.che.api.workspace.server.spi.environment.InternalRecipe;

/**
 * Holds objects of Kubernetes environment.
 *
 * @author Sergii Leshchenko
 */
public class KubernetesEnvironment extends InternalEnvironment {

  public static final String TYPE = "kubernetes";

  private final Map<String, Pod> pods;
  private final Map<String, Deployment> deployments;
  /**
   * Stores abstracted spec and meta from either a deployment or pod.
   *
   * <p>{@link PodData}
   */
  private final Map<String, PodData> podData;

  private final Map<String, Service> services;
  private final Map<String, Ingress> ingresses;
  private final Map<String, PersistentVolumeClaim> persistentVolumeClaims;
  private final Map<String, Secret> secrets;
  private final Map<String, ConfigMap> configMaps;

  /** Returns builder for creating environment from blank {@link KubernetesEnvironment}. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns builder for creating environment based on specified {@link InternalEnvironment}.
   *
   * <p>It means that {@link InternalEnvironment} specific fields like machines, warnings will be
   * preconfigured in Builder.
   */
  public static Builder builder(InternalEnvironment internalEnvironment) {
    return new Builder(internalEnvironment);
  }

  public KubernetesEnvironment(KubernetesEnvironment k8sEnv) {
    this(
        k8sEnv,
        k8sEnv.getPodsCopy(),
        k8sEnv.getDeploymentsCopy(),
        k8sEnv.getServices(),
        k8sEnv.getIngresses(),
        k8sEnv.getPersistentVolumeClaims(),
        k8sEnv.getSecrets(),
        k8sEnv.getConfigMaps());
  }

  protected KubernetesEnvironment(
      InternalEnvironment internalEnvironment,
      Map<String, Pod> pods,
      Map<String, Deployment> deployments,
      Map<String, Service> services,
      Map<String, Ingress> ingresses,
      Map<String, PersistentVolumeClaim> persistentVolumeClaims,
      Map<String, Secret> secrets,
      Map<String, ConfigMap> configMaps) {
    super(internalEnvironment);
    setType(TYPE);
    this.pods = new HashMap<>(pods);
    this.deployments = new HashMap<>(deployments);
    this.services = services;
    this.ingresses = ingresses;
    this.persistentVolumeClaims = persistentVolumeClaims;
    this.secrets = secrets;
    this.configMaps = configMaps;
    this.podData = new HashMap<>();
    pods.entrySet()
        .forEach(
            e -> {
              Pod pod = e.getValue();
              podData.put(e.getKey(), new PodData(pod.getSpec(), pod.getMetadata()));
            });
    deployments
        .entrySet()
        .forEach(
            e -> {
              PodTemplateSpec podTemplate = e.getValue().getSpec().getTemplate();
              podData.put(
                  e.getKey(), new PodData(podTemplate.getSpec(), podTemplate.getMetadata()));
            });
  }

  protected KubernetesEnvironment(
      InternalRecipe internalRecipe,
      Map<String, InternalMachineConfig> machines,
      List<Warning> warnings,
      Map<String, Pod> pods,
      Map<String, Deployment> deployments,
      Map<String, Service> services,
      Map<String, Ingress> ingresses,
      Map<String, PersistentVolumeClaim> persistentVolumeClaims,
      Map<String, Secret> secrets,
      Map<String, ConfigMap> configMaps) {
    super(internalRecipe, machines, warnings);
    setType(TYPE);
    this.pods = pods;
    this.deployments = deployments;
    this.services = services;
    this.ingresses = ingresses;
    this.persistentVolumeClaims = persistentVolumeClaims;
    this.secrets = secrets;
    this.configMaps = configMaps;
    this.podData = new HashMap<>();
    pods.entrySet()
        .forEach(
            e -> {
              Pod pod = e.getValue();
              podData.put(e.getKey(), new PodData(pod.getSpec(), pod.getMetadata()));
            });
    deployments
        .entrySet()
        .forEach(
            e -> {
              PodTemplateSpec podTemplate = e.getValue().getSpec().getTemplate();
              podData.put(
                  e.getKey(), new PodData(podTemplate.getSpec(), podTemplate.getMetadata()));
            });
  }

  @Override
  public KubernetesEnvironment setType(String type) {
    return (KubernetesEnvironment) super.setType(type);
  }

  /**
   * Returns pods that should be created when environment starts.
   *
   * <p>Note: This map <b>should not</b> be changed, as it will only return pods and not
   * deployments. If objects in the map need to be changed, see {@link #getPodsData()}
   *
   * <p>If pods need to be added to the environment, then {@link #addPod(Pod)} should be used
   * instead.
   */
  public Map<String, Pod> getPodsCopy() {
    return ImmutableMap.copyOf(pods);
  }

  /**
   * Returns deployments that should be created when environment starts.
   *
   * <p>Note: This map <b>should not</b> be changed. If objects in the map need to be changed, see
   * {@link #getPodsData()}
   *
   * <p>If pods need to be added to the environment, then {@link #addPod(Pod)} should be used
   * instead.
   */
  public Map<String, Deployment> getDeploymentsCopy() {
    return ImmutableMap.copyOf(deployments);
  }

  /**
   * Returns {@link PodData} representing the metadata and pod spec of objects (pods or deployments)
   * that should be created when environment starts. The data returned by this method represents all
   * deployment and pod objects that form the workspace, and should be used when provisioning or
   * performing any action that needs to see every object in the environment.
   *
   * <p>If pods need to be added to the environment, then {@link #addPod(Pod)} should be used
   * instead.
   */
  public Map<String, PodData> getPodsData() {
    return ImmutableMap.copyOf(podData);
  }

  /**
   * Add a pod to the current environment. This method is necessary as the map returned by {@link
   * #getPodsCopy()} is a copy. This method also adds the relevant data to {@link #getPodsData()}.
   */
  public void addPod(Pod pod) {
    String podName = pod.getMetadata().getName();
    pods.put(podName, pod);
    podData.put(podName, new PodData(pod.getSpec(), pod.getMetadata()));
  }

  /** Returns services that should be created when environment starts. */
  public Map<String, Service> getServices() {
    return services;
  }

  /** Returns ingresses that should be created when environment starts. */
  public Map<String, Ingress> getIngresses() {
    return ingresses;
  }

  /** Returns PVCs that should be created when environment starts. */
  public Map<String, PersistentVolumeClaim> getPersistentVolumeClaims() {
    return persistentVolumeClaims;
  }

  /** Returns secrets that should be created when environment starts. */
  public Map<String, Secret> getSecrets() {
    return secrets;
  }

  /** Returns config maps that should be created when environment starts. */
  public Map<String, ConfigMap> getConfigMaps() {
    return configMaps;
  }

  public static class Builder {
    protected final InternalEnvironment internalEnvironment;

    protected final Map<String, Pod> pods = new HashMap<>();
    protected final Map<String, Deployment> deployments = new HashMap<>();
    protected final Map<String, PodData> podData = new HashMap<>();
    protected final Map<String, Service> services = new HashMap<>();
    protected final Map<String, Ingress> ingresses = new HashMap<>();
    protected final Map<String, PersistentVolumeClaim> pvcs = new HashMap<>();
    protected final Map<String, Secret> secrets = new HashMap<>();
    protected final Map<String, ConfigMap> configMaps = new HashMap<>();
    protected final Map<String, String> attributes = new HashMap<>();

    protected Builder() {
      this.internalEnvironment = new InternalEnvironment() {};
    }

    public Builder(InternalEnvironment internalEnvironment) {
      this.internalEnvironment = internalEnvironment;
    }

    public Builder setInternalRecipe(InternalRecipe internalRecipe) {
      internalEnvironment.setRecipe(internalRecipe);
      return this;
    }

    public Builder setMachines(Map<String, InternalMachineConfig> machines) {
      internalEnvironment.setMachines(new HashMap<>(machines));
      return this;
    }

    public Builder setWarnings(List<Warning> warnings) {
      internalEnvironment.setWarnings(new ArrayList<>(warnings));
      return this;
    }

    public Builder setCommands(List<? extends Command> commands) {
      internalEnvironment.setCommands(new ArrayList<>(commands));
      return this;
    }

    public Builder setPods(Map<String, Pod> pods) {
      this.pods.putAll(pods);
      return this;
    }

    public Builder setDeployments(Map<String, Deployment> deployments) {
      this.deployments.putAll(deployments);
      return this;
    }

    public Builder setServices(Map<String, Service> services) {
      this.services.putAll(services);
      return this;
    }

    public Builder setIngresses(Map<String, Ingress> ingresses) {
      this.ingresses.putAll(ingresses);
      return this;
    }

    public Builder setPersistentVolumeClaims(Map<String, PersistentVolumeClaim> pvcs) {
      this.pvcs.putAll(pvcs);
      return this;
    }

    public Builder setSecrets(Map<String, Secret> secrets) {
      this.secrets.putAll(secrets);
      return this;
    }

    public Builder setConfigMaps(Map<String, ConfigMap> configMaps) {
      this.configMaps.putAll(configMaps);
      return this;
    }

    public Builder setAttributes(Map<String, String> attributes) {
      this.attributes.putAll(attributes);
      return this;
    }

    public KubernetesEnvironment build() {
      return new KubernetesEnvironment(
          internalEnvironment, pods, deployments, services, ingresses, pvcs, secrets, configMaps);
    }
  }

  /**
   * Abstraction of pod, since deployments store pod spec and meta within a PodSpecTemplate instead
   * of a pod object. This class allows us to use one class to support passing of the relevant parts
   * of a pod or deployment when it comes to provisioning.
   *
   * <p>The methods for accessing metadata and spec are identical to that of the Pod class (i.e.
   * {@code getSpec()} and {@code getMetadata()}
   */
  public static class PodData {
    private PodSpec podSpec;
    private ObjectMeta podMeta;

    public PodData(PodSpec podSpec, ObjectMeta podMeta) {
      this.podSpec = podSpec;
      this.podMeta = podMeta;
    }

    public PodSpec getSpec() {
      return podSpec;
    }

    public void setSpec(PodSpec podSpec) {
      this.podSpec = podSpec;
    }

    public ObjectMeta getMetadata() {
      return podMeta;
    }

    public void setMetadata(ObjectMeta podMeta) {
      this.podMeta = podMeta;
    }
  }
}
