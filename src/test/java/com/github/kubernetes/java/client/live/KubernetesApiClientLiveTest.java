/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package com.github.kubernetes.java.client.live;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.github.kubernetes.java.client.exceptions.KubernetesClientException;
import com.github.kubernetes.java.client.exceptions.Status;
import com.github.kubernetes.java.client.interfaces.KubernetesAPIClientInterface;
import com.github.kubernetes.java.client.model.AbstractKubernetesModel;
import com.github.kubernetes.java.client.model.Container;
import com.github.kubernetes.java.client.model.Manifest;
import com.github.kubernetes.java.client.model.Pod;
import com.github.kubernetes.java.client.model.PodList;
import com.github.kubernetes.java.client.model.Port;
import com.github.kubernetes.java.client.model.ReplicationController;
import com.github.kubernetes.java.client.model.Selector;
import com.github.kubernetes.java.client.model.Service;
import com.github.kubernetes.java.client.model.ServiceList;
import com.github.kubernetes.java.client.model.State;
import com.github.kubernetes.java.client.model.StateInfo;
import com.github.kubernetes.java.client.v2.KubernetesApiClient;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@Category(com.github.kubernetes.java.client.LiveTests.class)
public class KubernetesApiClientLiveTest {

    private static final Log log = LogFactory.getLog(KubernetesApiClientLiveTest.class);

    private String dockerImage;
    protected String endpoint;
    protected String username;
    protected String password;

    protected Pod pod;
    protected ReplicationController contr;
    protected Service serv;

    private KubernetesAPIClientInterface client;

    protected Class getExceptionClass() {
        return KubernetesClientException.class;
    }

    protected KubernetesAPIClientInterface getClient() {
        if (client == null) {
            client = new KubernetesApiClient(endpoint, username, password);
        }
        return client;
    }

    @Before
    public void setUp() {
        endpoint = System.getProperty("kubernetes.api.endpoint", "http://192.168.1.100:8080/api/"
                + KubernetesAPIClientInterface.VERSION + "/");
        username = System.getProperty("kubernetes.api.username", "vagrant");
        password = System.getProperty("kubernetes.api.password", "vagrant");
        log.info("Provided Kubernetes endpoint using system property [kubernetes.api.endpoint] : " + endpoint);

        // image should be pre-downloaded for ease of testing.
        dockerImage = System.getProperty("docker.image", "busybox");

        pod = getPod();
        contr = getReplicationController();
        serv = getService();

        // cleanup
        cleanup();
    }

    @After
    public void cleanup() {
        Function<AbstractKubernetesModel, Status> delete = new Function<AbstractKubernetesModel, Status>() {
            public Status apply(AbstractKubernetesModel o) {
                switch (o.getKind()) {
                case POD:
                    return getClient().deletePod(pod.getId());
                case REPLICATIONCONTROLLER:
                    if (getClient().getReplicationController(contr.getId()) != null) {
                        getClient().updateReplicationController(contr.getId(), 0);
                    }
                    PodList pods = getClient().getSelectedPods(contr.getLabels());
                    for (Pod pod : pods.getItems()) {
                        getClient().deletePod(pod.getId());
                    }
                    return getClient().deleteReplicationController(contr.getId());
                case SERVICE:
                    return getClient().deleteService(serv.getId());
                default:
                    throw new IllegalArgumentException(o.toString());
                }
            }
        };

        for (AbstractKubernetesModel model : ImmutableList.of(pod, contr, serv)) {
            try {
                delete.apply(model);
            } catch (KubernetesClientException e) {
                if ((e.getStatus() == null) || (e.getStatus().getCode() != 404)) {
                    e.printStackTrace();
                }
            }
        }
    }

    private Pod getPod() {
        Pod pod = new Pod();
        pod.setId("kubernetes-test-pod");
        pod.setLabels(ImmutableMap.of("name", "kubernetes-test-pod-label", "label1", "value1"));
        State desiredState = new State();
        Manifest m = new Manifest();
        m.setId(pod.getId());
        Container c = new Container();
        c.setName("master");
        c.setImage(dockerImage);
        c.setCommand("tail", "-f", "/dev/null");
        Port p = new Port(8379, new Random().nextInt((65535 - 49152) + 1) + 49152, "0.0.0.0");
        c.setPorts(Collections.singletonList(p));
        m.setContainers(Collections.singletonList(c));
        desiredState.setManifest(m);
        pod.setDesiredState(desiredState);
        return pod;
    }

    private ReplicationController getReplicationController() {
        ReplicationController contr = new ReplicationController();
        contr.setId("kubernetes-test-controller");
        State desiredState = new State();
        desiredState.setReplicas(2);

        Selector selector = new Selector();
        selector.setName("kubernetes-test-controller-selector");
        desiredState.setReplicaSelector(selector);

        Pod podTemplate = new Pod();
        State podState = new State();
        Manifest manifest = new Manifest();
        manifest.setId(contr.getId());
        Container container = new Container();
        container.setName("kubernetes-test");
        container.setImage(dockerImage);
        Port p = new Port();
        p.setContainerPort(80);
        container.setPorts(Collections.singletonList(p));
        container.setCommand("tail", "-f", "/dev/null");
        manifest.setContainers(Collections.singletonList(container));
        podState.setManifest(manifest);
        podTemplate.setDesiredState(podState);
        podTemplate.setLabels(ImmutableMap.of("name", selector.getName()));

        desiredState.setPodTemplate(podTemplate);
        contr.setDesiredState(desiredState);
        contr.setLabels(podTemplate.getLabels());
        return contr;
    }

    private Service getService() {
        Service serv = new Service();
        serv.setContainerPort("8379");
        serv.setPort(5000);
        serv.setId("kubernetes-test-service");
        serv.setLabels(ImmutableMap.of("name", "kubernetes-test-service-label"));
        serv.setName("kubernetes-test-service-name");
        Selector selector = new Selector();
        selector.setName(serv.getLabels().get("name"));
        serv.setSelector(selector);
        return serv;
    }

    @Test
    public void testCreatePod() throws Exception {
        log.info("Testing Pods ....");

        if (log.isDebugEnabled()) {
            log.debug("Creating a Pod " + pod);
        }
        Pod createPod = getClient().createPod(pod);
        assertEquals(pod.getId(), createPod.getId());
        assertNotNull(getClient().getPod(pod.getId()));
        assertEquals("Waiting", createPod.getCurrentState().getStatus());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Pod> future = executor.submit(new Callable<Pod>() {
            public Pod call() throws Exception {
                Pod newPod;
                do {
                    log.info("Waiting for Pod to be ready: " + pod.getId());
                    Thread.sleep(1000);
                    newPod = getClient().getPod(pod.getId());
                    StateInfo info = newPod.getCurrentState().getInfo("master");
                    if (info.getState("waiting") != null) {
                        throw new RuntimeException("Pod is waiting due to " + info.getState("waiting"));
                    }
                } while (!"Running".equals(newPod.getCurrentState().getStatus()));
                return newPod;
            }
        });

        try {
            createPod = future.get(90, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertNotNull(createPod.getCurrentState().getInfo("master").getState("running"));
        assertNotNull(createPod.getCurrentState().getNetInfo().getState("running"));

        // test recreation from same id
        try {
            getClient().createPod(pod);
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }
        assertNotNull(getClient().getPod(pod.getId()));
    }

    @Test
    public void testGetNonExistantPod() throws Exception {
        assertNull(getClient().getPod("non-existant"));
    }

    @Test
    public void testGetAllPods() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Get all Pods ");
        }
        getClient().createPod(pod);
        PodList podList = getClient().getAllPods();
        assertNotNull(podList);
        List<Pod> currentPods;
        assertNotNull(currentPods = podList.getItems());
        boolean match = false;
        for (Pod pod2 : currentPods) {
            if (pod.getId().equals(pod2.getId())) {
                match = true;
                break;
            }
        }
        assertEquals(true, match);
    }

    @Test
    public void testGetSelectedPods() throws Exception {
        getClient().createPod(pod);
        PodList selectedPods = getClient().getSelectedPods(pod.getLabels());
        assertNotNull(selectedPods);
        assertNotNull(selectedPods.getItems());
        assertEquals(1, selectedPods.getItems().size());
    }

    @Test
    public void testGetSelectedPodsEmpty() {
        PodList selectedPods = getClient().getSelectedPods(pod.getLabels());
        assertNotNull(selectedPods);
        assertNotNull(selectedPods.getItems());
        assertEquals(0, selectedPods.getItems().size());
    }

    @Test
    public void testGetSelectedPodsWithNonExistantLabel() throws Exception {
        PodList selectedPods = getClient().getSelectedPods(ImmutableMap.of("name", "no-match"));
        assertNotNull(selectedPods);
        assertNotNull(selectedPods.getItems());
        assertEquals(0, selectedPods.getItems().size());
    }

    @Test
    public void testGetSelectedPodsWithEmptyLabel() throws Exception {
        PodList selectedPods = getClient().getSelectedPods(Collections.<String, String> emptyMap());
        PodList allPods = getClient().getAllPods();
        assertNotNull(selectedPods);
        assertNotNull(selectedPods.getItems());
        assertEquals(allPods.getItems().size(), selectedPods.getItems().size());
    }

    @Test
    public void testDeletePod() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Deleting a Pod " + pod);
        }
        getClient().createPod(pod);
        getClient().deletePod(pod.getId());
        assertNull(getClient().getPod(pod.getId()));
    }

    @Test(expected = KubernetesClientException.class)
    public void testDeleteNonExistantPod() throws Exception {
        // delete a non-existing pod
        getClient().deletePod("xxxxxx");
    }

    @Test
    public void testCreateReplicationController() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Creating a Replication Controller: " + contr);
        }
        getClient().createReplicationController(contr);
        assertNotNull(getClient().getReplicationController(contr.getId()));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<PodList> future = executor.submit(new Callable<PodList>() {
            public PodList call() throws Exception {
                PodList pods;
                do {
                    log.info("Waiting for Pods to be ready");
                    Thread.sleep(1000);
                    pods = getClient().getSelectedPods(ImmutableMap.of("name", "kubernetes-test-controller-selector"));
                    if (pods.isEmpty()) {
                        continue;
                    }

                    StateInfo info = pods.get(0).getCurrentState().getInfo("kubernetes-test");
                    if ((info != null) && info.getState("waiting") != null) {
                        throw new RuntimeException("Pod is waiting due to " + info.getState("waiting"));
                    }
                } while (pods.isEmpty() || !FluentIterable.from(pods).allMatch(new Predicate<Pod>() {
                    public boolean apply(Pod pod) {
                        return "Running".equals(pod.getCurrentState().getStatus());
                    }
                }));
                return pods;
            }
        });

        PodList pods;
        try {
            pods = future.get(90, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        for (Pod pod : pods) {
            assertNotNull(pod.getCurrentState().getInfo("kubernetes-test").getState("running"));
            assertNotNull(pod.getCurrentState().getNetInfo().getState("running"));
        }

        // test recreation using same id
        try {
            getClient().createReplicationController(contr);
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }
        assertNotNull(getClient().getReplicationController(contr.getId()));

        PodList podList = getClient().getSelectedPods(contr.getLabels());
        assertNotNull(podList);
        assertNotNull(podList.getItems());
        assertEquals(contr.getDesiredState().getReplicas(), podList.getItems().size());
    }

    @Test
    public void testGetAllReplicationControllers() throws Exception {
        getClient().createReplicationController(contr);
        assertThat(getClient().getAllReplicationControllers().getItems().size(), greaterThan(0));
    }

    @Test(expected = KubernetesClientException.class)
    public void testUpdateReplicationControllerWithBadCount() throws Exception {
        // test incorrect replica count
        getClient().createReplicationController(contr);
        getClient().updateReplicationController(contr.getId(), -1);
    }

    @Test
    public void testUpdateReplicationControllerToZero() throws Exception {
        getClient().createReplicationController(contr);
        getClient().updateReplicationController(contr.getId(), 0);

        Thread.sleep(10000);

        PodList podList = getClient().getSelectedPods(contr.getLabels());
        assertNotNull(podList);
        assertNotNull(podList.getItems());
        assertEquals(0, podList.getItems().size());
    }

    @Test
    public void testDeleteReplicationController() throws Exception {
        getClient().createReplicationController(contr);
        getClient().deleteReplicationController(contr.getId());
        assertNull(getClient().getReplicationController(contr.getId()));
    }

    @Test(expected = KubernetesClientException.class)
    public void testCreateInvalidReplicationController() throws Exception {
        // create an invalid Controller
        ReplicationController bogusContr = new ReplicationController();
        bogusContr.setId("xxxxx");
        getClient().createReplicationController(bogusContr);
    }

    @Test
    public void testGetInvalidReplicationController() throws Exception {
        assertNull(getClient().getReplicationController("xxxxx"));
    }

    @Test(expected = KubernetesClientException.class)
    public void testUpdateInvalidReplicationController() throws Exception {
        getClient().updateReplicationController("xxxxx", 3);
    }

    @Test(expected = KubernetesClientException.class)
    public void testDeleteInvalidReplicationController() throws Exception {
        getClient().deleteReplicationController("xxxxx");
    }

    @Test
    public void testCreateService() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Creating a Service Proxy: " + serv);
        }
        getClient().createService(serv);
        assertNotNull(getClient().getService(serv.getId()));

        // test recreation using same id
        try {
            getClient().createService(serv);
            fail("Should have thrown exception");
        } catch (Exception e) {
            // ignore
        }

        assertNotNull(getClient().getService(serv.getId()));
    }

    @Test
    public void testGetAllServices() throws Exception {
        getClient().createService(serv);
        ServiceList serviceList = getClient().getAllServices();
        assertNotNull(serviceList);
        assertNotNull(serviceList.getItems());
        assertThat(serviceList.getItems().size(), greaterThan(0));
    }

    @Test
    public void testDeleteService() throws Exception {
        getClient().createService(serv);
        Status status = getClient().deleteService(serv.getId());
        assertNull(getClient().getService(serv.getId()));
    }

    @Test(expected = KubernetesClientException.class)
    public void testCreateInvalidService() throws Exception {
        // create an invalid Service
        Service bogusServ = new Service();
        bogusServ.setId("xxxxxx");
        getClient().createService(bogusServ);
    }

    @Test(expected = KubernetesClientException.class)
    public void testDeleteInvalidService() throws Exception {
        getClient().deleteService("xxxxx");
    }
}
