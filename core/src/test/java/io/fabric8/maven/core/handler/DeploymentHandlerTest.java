/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.handler;

import java.util.ArrayList;
import java.util.List;

import io.fabric8.kubernetes.api.model.extensions.Deployment;
import io.fabric8.maven.core.config.ResourceConfig;
import io.fabric8.maven.core.config.VolumeConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import mockit.Mocked;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class DeploymentHandlerTest {

    @Mocked
    EnvVarHandler envVarHandler;

    @Mocked
    ProbeHandler probeHandler;

    MavenProject project = new MavenProject();

    List<String> mounts = new ArrayList<>();
    List<VolumeConfig> volumes1 = new ArrayList<>();

    List<ImageConfiguration> images = new ArrayList<>();

    List<String> ports = new ArrayList<>();

    List<String> tags = new ArrayList<>();

    @Before
    public void before(){

        //volume config with name and multiple mount
        mounts.add("/path/system");
        mounts.add("/path/sys");

        ports.add("8080");
        ports.add("9090");

        tags.add("latest");
        tags.add("test");

        VolumeConfig volumeConfig1 = new VolumeConfig.Builder()
                .name("test").mounts(mounts).type("hostPath").path("/test/path").build();
        volumes1.add(volumeConfig1);

        //container name with alias
        BuildImageConfiguration buildImageConfiguration = new BuildImageConfiguration.Builder().
                ports(ports).from("fabric8/maven:latest").cleanup("try")
                .tags(tags).compression("gzip").build();

        ImageConfiguration imageConfiguration = new ImageConfiguration.Builder().
                name("test").alias("test-app").buildConfig(buildImageConfiguration)
                .registry("docker.io").build();

        images.add(imageConfiguration);
    }

    @Test
    public void deploymentTemplateHandlerTest() {

        ContainerHandler containerHandler =
                new ContainerHandler(project, envVarHandler, probeHandler);

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        DeploymentHandler deploymentHandler = new DeploymentHandler(podTemplateHandler);

        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("testing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        Deployment deployment = deploymentHandler.getDeployment(config,images);

        //Assertion
        assertNotNull(deployment.getSpec());
        assertNotNull(deployment.getMetadata());
        assertEquals(5,deployment.getSpec().getReplicas().intValue());
        assertNotNull(deployment.getSpec().getTemplate());
        assertEquals("testing",deployment.getMetadata().getName());
        assertEquals("test-account",deployment.getSpec().getTemplate()
                .getSpec().getServiceAccountName());
        assertFalse(deployment.getSpec().getTemplate().getSpec().getVolumes().isEmpty());
        assertEquals("test",deployment.getSpec().getTemplate().getSpec().
                getVolumes().get(0).getName());
        assertEquals("/test/path",deployment.getSpec().getTemplate()
                .getSpec().getVolumes().get(0).getHostPath().getPath());
        assertNotNull(deployment.getSpec().getTemplate().getSpec().getContainers());

    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentTemplateHandlerWithInvalidNameTest() {
        //invalid controller name
        ContainerHandler containerHandler =
                new ContainerHandler(project, envVarHandler, probeHandler);

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        DeploymentHandler deploymentHandler = new DeploymentHandler(podTemplateHandler);

        //with invalid controller name
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .controllerName("TesTing")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        deploymentHandler.getDeployment(config, images);
    }

    @Test(expected = IllegalArgumentException.class)
    public void deploymentTemplateHandlerWithoutControllerTest() {
        //without controller name
        ContainerHandler containerHandler = new
                ContainerHandler(project, envVarHandler, probeHandler);

        PodTemplateHandler podTemplateHandler = new PodTemplateHandler(containerHandler);

        DeploymentHandler deploymentHandler = new DeploymentHandler(podTemplateHandler);

        //without controller name
        ResourceConfig config = new ResourceConfig.Builder()
                .imagePullPolicy("IfNotPresent")
                .withServiceAccount("test-account")
                .withReplicas(5)
                .volumes(volumes1)
                .build();

        deploymentHandler.getDeployment(config, images);
    }
}