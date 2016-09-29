/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/

package org.wso2.greg.plugin.worker

import com.eviware.soapui.config.CredentialsConfig
import com.eviware.soapui.config.TestStepConfig
import com.eviware.soapui.impl.AuthRepository.AuthEntries
import com.eviware.soapui.impl.AuthRepository.Impl.AuthRepositoryImpl
import com.eviware.soapui.impl.rest.RestMethod
import com.eviware.soapui.impl.rest.RestRequest
import com.eviware.soapui.impl.rest.RestResource
import com.eviware.soapui.impl.rest.RestService
import com.eviware.soapui.impl.wsdl.*
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase
import com.eviware.soapui.impl.wsdl.teststeps.registry.RestRequestStepFactory
import com.eviware.soapui.impl.wsdl.teststeps.registry.WsdlTestRequestStepFactory
import com.eviware.soapui.support.StringUtils
import com.eviware.soapui.support.UISupport
import com.eviware.x.dialogs.Worker
import com.eviware.x.dialogs.XProgressDialog
import com.eviware.x.dialogs.XProgressMonitor
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.wso2.greg.plugin.Utils
import org.wso2.greg.plugin.constants.ResourceConstants
import org.wso2.greg.plugin.dataObjects.ResourceInfo
import org.wso2.greg.plugin.dataObjects.ResourceSelectionResult

import java.nio.file.Files
import java.nio.file.Paths

public class ResourceImporterWorker implements Worker {

    String errors = "";

    private XProgressDialog waitDialog;
    private boolean cancelled = false;
    private List<ResourceInfo> links;
    private boolean isTestSuiteSelected = false;
    private boolean isLoadTestSelected = false;
    private WsdlProject project;

    private List<RestService> addedRestServices = new ArrayList<RestService>();
    private List<WsdlInterface> addedSoapServices = new ArrayList<WsdlInterface>();
    private boolean isTestSuiteRequired;
    private boolean isLoadTestRequired;

    private static final Log log = LogFactory.getLog(ResourceImporterWorker.class);


    private ResourceImporterWorker(XProgressDialog waitDialog, ResourceSelectionResult apiSelectionResult,
                                   WsdlProject project) {
        this.waitDialog = waitDialog;
        this.links = apiSelectionResult.getResourceInfoList();
        this.isLoadTestSelected = apiSelectionResult.isLoadTestSelected();
        this.isTestSuiteSelected = apiSelectionResult.isTestSuiteSelected();
        this.project = project;
        this.isTestSuiteRequired = apiSelectionResult.isTestSuiteSelected();
        this.isLoadTestRequired = apiSelectionResult.isLoadTestSelected();
    }

    public static List<RestService> importServices(ResourceSelectionResult selectionResult, WsdlProject project) {
        ResourceImporterWorker worker = new ResourceImporterWorker(
                UISupport.getDialogs().createProgressDialog("Importing Resources...", 100, "", true),
                selectionResult, project);
        try {
            worker.waitDialog.run(worker);
        } catch (Exception e) {
            UISupport.showErrorMessage(e.getMessage());
            log.error(e);
        }
        if (worker.addedRestServices != null && worker.addedRestServices.size() > 0) {
            return worker.addedRestServices;
        } else if (worker.addedSoapServices != null && worker.addedSoapServices.size() > 0) {
            return worker.addedSoapServices;
        }
    }

    @Override
    public Object construct(XProgressMonitor monitor) {

        for (ResourceInfo apiInfo : links) {
            if (cancelled) {
                break;
            }
            RestService[] restServices;
            WsdlInterface[] wsdlInterfaces;
            try {

                boolean isSwagger2 = false;

                // We are importing the Resource definition from the swagger resource here.
                // Once imported, we get an array of RestServices as the result.
                if (apiInfo.getResType().equals(ResourceConstants.RESOURCE_TYPE_SWAGGER)) {
                    restServices = Utils.importResourcesProject(apiInfo, project);
                    deleteSwaggerContentFile(apiInfo.getContentFilePath());
                    isSwagger2 = checkIsSwagger2(apiInfo, isSwagger2);

                    if (restServices != null) {
                        if (isTestSuiteRequired) {
                            createTestSuiteForResetServices(restServices, isSwagger2, apiInfo);
                        }
                    }
                    // We set the Auth Profile here.
                    setAuthProfile();
                } else {
                    wsdlInterfaces = Utils.importSOAPServiceToProject(apiInfo, project);
                    if (addedSoapServices != null && isTestSuiteRequired) {
                        createTestSuiteForSoapServices(wsdlInterfaces);
                    }
                }

            } catch (Throwable e) {
                if (errors.length() > 0) {
                    errors += "\n";
                }

                errors = String.format("Failed to read Resource description for[%s] - [%s]",
                        apiInfo.getName(), e.getMessage());
                continue;
            }
            if (restServices != null) {
                addedRestServices.addAll(Arrays.asList(restServices));
            }

            if (wsdlInterfaces != null) {
                addedSoapServices.addAll(Arrays.asList(wsdlInterfaces));
            }

        }

        if (errors.length() > 0) {
            errors += "\nPlease contact WSO2 support for assistance";
        }

        return null;
    }

    private void deleteSwaggerContentFile(String filePath) {
        try {
            Files.delete(Paths.get(filePath));
        } catch (IOException ioEx) {
            log.error("Error occured deleting swagger content temp file " + filePath + ioEx);
        }
    }

    private void createTestSuiteForResetServices(RestService[] restServices, boolean isSwagger2,
                                                 ResourceInfo apiInfo) {

        WsdlTestSuite testSuite;
        WsdlTestCase testCase;

        for (RestService restService : restServices) {
            // We change the restServices name to the apiName/apiVersion
            restService.setName(constructServiceName(apiInfo, restService.getName(), isSwagger2));
            //Check to see if the default test suite is there
            testSuite = project.getTestSuiteByName(restService.getName());
            if (testSuite == null) {
                testSuite = project.addNewTestSuite(restService.getName());
            }
            List<RestResource> resources = restService.getAllResources();

            for (RestResource resource : resources) {
                if (testSuite != null) {
                    testCase = testSuite.addNewTestCase(resource.getName());
                }
                List<RestMethod> methods = resource.getRestMethodList();
                for (RestMethod method : methods) {
                    List<RestRequest> restRequests = method.getRequestList();
                    for (RestRequest restRequest : restRequests) {
                        // Selecting the Auth profile

                        if (restRequest.metaClass.respondsTo(restRequest, "setSelectedAuthProfileAndAuthType")) {
                            restRequest.setSelectedAuthProfileAndAuthType(ResourceConstants.WSO2_GREG_DEFAULT,
                                    CredentialsConfig.AuthType.O_AUTH_2_0);
                        } else {
                            restRequest.selectedAuthProfile = ResourceConstants.WSO2_GREG_DEFAULT;
                        }

                        // This will rename the request name to something similar to
                        // get_repos/{user_name}/{repo_name} - default_request
                        restRequest.setName(constructRequestName(method.getName()));

                        // Add a test step for each request
                        if (isTestSuiteSelected && testCase != null) {
                            TestStepConfig testStepConfig = RestRequestStepFactory.createConfig(restRequest, restRequest.getName());
                            testCase.addTestStep(testStepConfig);
                            //  testSuite.fireTestCaseAdded(testCase);

                        }
                    }
                }
                //  project.fireTestSuiteAdded(testSuite);
                if (isLoadTestSelected) {
                    if (testCase != null) {
                        testCase.addNewLoadTest(testCase.getName());
                    }
                }
            }
        }
    }

    private void createTestSuiteForSoapServices(WsdlInterface[] wsdlInterfaces) {

        WsdlTestSuite testSuite;
        WsdlTestCasePro testCase;

        for (WsdlInterface wsdlInterface : wsdlInterfaces) {

            //Check to see if the default test suite is there
            testSuite = project.getTestSuiteByName(wsdlInterface.getName());
            if (testSuite == null) {
                testSuite = project.addNewTestSuite(wsdlInterface.getName());
            }
            List<WsdlOperation> operations = wsdlInterface.getOperationList();

            for (WsdlOperation operation : operations) {
                if (testSuite != null) {
                    testCase = testSuite.addNewTestCase(operation.getName());

                    WsdlTestRequestStepFactory wsdlTestRequestStepFactory = new WsdlTestRequestStepFactory();
                    TestStepConfig testStepConfig = wsdlTestRequestStepFactory.createConfig(operation, operation.getName());
                    testCase.addTestStep(testStepConfig);
                }

                if (isLoadTestSelected) {
                    if (testCase != null) {
                        testCase.addNewLoadTest(testCase.getName());
                    }
                }
            }
        }
    }

    @Override
    public void finished() {
        if (cancelled) {
            return;
        }
        waitDialog.setVisible(false);
        if (StringUtils.hasContent(errors)) {
            UISupport.showErrorMessage(errors);
        }
    }

    private boolean checkIsSwagger2(ResourceInfo apiInfo, boolean isSwagger2) {
        if (project.getPropertyValue(apiInfo.getResourceContentDocLink()).equals("2.0")) {
            isSwagger2 = true;
        }
        project.removeProperty(apiInfo.getResourceContentDocLink());
        return isSwagger2;
    }

    @Override
    public boolean onCancel() {
        cancelled = true;
        waitDialog.setVisible(false);
        return true;
    }

    private void setAuthProfile() {
        if (project.metaClass.hasProperty(project, 'authRepository')) {
            def authRepository = project.authRepository
            if (authRepository instanceof AuthRepositoryImpl) {
                boolean hasDefaultProfile = false;
                AuthRepositoryImpl authRepositoryImpl = (AuthRepositoryImpl) authRepository;
                List<AuthEntries.OAuth20AuthEntry> oAuth2ProfileList = authRepositoryImpl.getOAuth2ProfileList();
                for (AuthEntries.OAuth20AuthEntry oAuth20AuthEntry : oAuth2ProfileList) {
                    if (ResourceConstants.WSO2_GREG_DEFAULT.equals(oAuth20AuthEntry.getName())) {
                        hasDefaultProfile = true;
                        break;
                    }
                }
                if (!hasDefaultProfile) {
                    authRepositoryImpl.addNewOAuth2Profile(ResourceConstants.WSO2_GREG_DEFAULT);
                }
            }
        } else if (project.metaClass.hasProperty(project, 'oAuth2ProfileContainer')) {
            // This is the older code that was in use
            def profileContainer = project.oAuth2ProfileContainer
            if (profileContainer != null
                    && profileContainer.getProfileByName(ResourceConstants.WSO2_GREG_DEFAULT) == null) {
                profileContainer.addNewOAuth2Profile(ResourceConstants.WSO2_GREG_DEFAULT);
            }
        }
    }

    private static String constructServiceName(ResourceInfo apiInfo, String resourceName, Boolean isSwagger2) {
        String serviceName = apiInfo.getName() + "/" + apiInfo.getVersion();
        if (!isSwagger2) {
            serviceName = serviceName + resourceName;
        }
        return serviceName;
    }

    private static String constructRequestName(String methodName) {
        return methodName + " - " + "default_request";
    }
}
