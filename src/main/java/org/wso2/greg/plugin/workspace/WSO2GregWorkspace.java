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

package org.wso2.greg.plugin.workspace;

import com.eviware.soapui.impl.WorkspaceImpl;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.plugins.auto.PluginImportMethod;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.form.ValidationMessage;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldValidator;
import com.eviware.x.form.support.ADialogBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.greg.plugin.Utils;
import org.wso2.greg.plugin.common.GregReadyPluginException;
import org.wso2.greg.plugin.constants.HelpMessageConstants;
import org.wso2.greg.plugin.constants.ResourceConstants;
import org.wso2.greg.plugin.dataObjects.ResourceExtractionResult;
import org.wso2.greg.plugin.dataObjects.ResourceInfo;
import org.wso2.greg.plugin.dataObjects.ResourceSelectionResult;
import org.wso2.greg.plugin.ui.ProjectModel;
import org.wso2.greg.plugin.worker.ResourceExtractorWorker;
import org.wso2.greg.plugin.worker.ResourceImporterWorker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

/**
 * This class is used to generate a new workspace for the WSO2 API Manager projects
 */
@PluginImportMethod(label = "Import wsdl/swagger resources from WSO2 Governance Registry")
public class WSO2GregWorkspace extends AbstractSoapUIAction<WorkspaceImpl> {

    private ResourceExtractionResult listExtractionResult = null;
    private static final Log log = LogFactory.getLog(WSO2GregWorkspace.class);

    public WSO2GregWorkspace() {
        super("Create Project from WSO2 Governance Registry",
                "Creates new project from wsdl/swagger Resources on the G-reg Store");
    }

    public void perform(WorkspaceImpl workspace, Object params) {

        final XFormDialog dialog = ADialogBuilder.buildDialog(ProjectModel.class);
        setProfileValuesToUI(dialog);
        /*
         * The purpose of this listener is to validate the API Store URL and the Project name upon submitting the form
         */
        dialog.getFormField(ProjectModel.GREG_HOST).addFormFieldValidator(new XFormFieldValidator() {
            public ValidationMessage[] validateField(XFormField formField) {
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.GREG_HOST))) {
                    return new ValidationMessage[] {
                            new ValidationMessage(HelpMessageConstants.HOST_EMPTY,
                                    dialog.getFormField(ProjectModel.GREG_HOST))
                    };
                }
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.PROJECT_NAME))) {
                    return new ValidationMessage[] {
                            new ValidationMessage(HelpMessageConstants.PROJECT_NAME_VALIDATION_MSG,
                                    dialog.getFormField(ProjectModel.PROJECT_NAME))
                    };
                }
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.USER_NAME))) {
                    return new ValidationMessage[] {
                            new ValidationMessage(HelpMessageConstants.USER_NAME_VALIDATION_MSG,
                                    dialog.getFormField(ProjectModel.USER_NAME))
                    };
                }
                if (StringUtils.isNullOrEmpty(dialog.getValue(ProjectModel.PASSWORD))) {
                    return new ValidationMessage[] {
                            new ValidationMessage(HelpMessageConstants.PASSWORD_VALIDATION_MSG,
                                    dialog.getFormField(ProjectModel.PASSWORD))
                    };
                }

                String hostName = dialog.getValue(ProjectModel.GREG_HOST);
                String port = dialog.getValue(ProjectModel.GREG_PORT);
                String resourceType = dialog.getValue(ProjectModel.RESOURCETYPE);

                try {
                    saveLoginProfileData(hostName, port, dialog.getValue(ProjectModel.USER_NAME),
                            dialog.getValue(ProjectModel.TENANT_DOMAIN));
                } catch (GregReadyPluginException e) {
                    log.error("Error occurred while saving profile data ", e);
                }

                listExtractionResult = ResourceExtractorWorker
                        .downloadResourcesList(hostName, port, resourceType, dialog.getValue(ProjectModel.USER_NAME),
                                dialog.getValue(ProjectModel.PASSWORD).toCharArray(),
                                dialog.getValue(ProjectModel.TENANT_DOMAIN),
                                dialog.getValue(ProjectModel.PRODUCT_VERSION));

                if (StringUtils.hasContent(listExtractionResult.getError())) {
                    return new ValidationMessage[] {
                            new ValidationMessage(listExtractionResult.getError(), formField)
                    };
                }
                return new ValidationMessage[0];
            }
        });

        if (dialog.show() && listExtractionResult != null && !listExtractionResult.isCanceled()) {
            ResourceSelectionResult selectionResult = Utils
                    .showSelectAPIDefDialog(listExtractionResult.getResourceInfos());
            if (selectionResult == null) {
                return;
            }

            List<ResourceInfo> selectedAPIs = selectionResult.getResourceInfoList();
            if (selectedAPIs != null) {
                WsdlProject project;
                try {
                    project = workspace.createProject(dialog.getValue(ProjectModel.PROJECT_NAME), null);
                } catch (Exception e) {
                    log.error("Error occured while creating a project", e);
                    UISupport.showErrorMessage(
                            String.format("Unable to create Project because of %s exception with " + "\"%s\" message",
                                    e.getClass().getName(), e.getMessage()));
                    return;
                }
                List<RestService> services = ResourceImporterWorker.importServices(selectionResult, project);
                if (services != null && !services.isEmpty()) {
                    UISupport.select(services.get(0));
                } else {
                    workspace.removeProject(project);
                }

            }
        }
    }

    private void saveLoginProfileData(String host, String port, String userName, String tenantDomain)
            throws GregReadyPluginException {

        File profileDataFile;

        profileDataFile = new File(System.getProperty("java.io.tmpdir") + File.separator +
                ResourceConstants.LOGIN_PROFILE_FILE_PREFIX +
                ResourceConstants.LOGIN_PROFILE_FILE_SUFFIX);

        if (profileDataFile.exists()) {
            profileDataFile.delete();
        }
        try (
                BufferedWriter out = new BufferedWriter(new FileWriter(profileDataFile));
        ) {
            profileDataFile.createNewFile();

            String data = ResourceConstants.URLS.HOST_NAME + ":" + host + "\n" +
                    ResourceConstants.URLS.PORT + ":" + port + "\n" +
                    ResourceConstants.URLS.USER_NAME + ":" + userName + "\n" +
                    ResourceConstants.TENANT_DOMAIN + ":" + tenantDomain + "\n";

            out.write(data);
            out.flush();

        } catch (IOException ioEx) {
            log.error("IO Exception occured while saving login profile", ioEx);
            throw new GregReadyPluginException("IO Exception occurred while saving login profile", ioEx);
        }
    }

    private void setProfileValuesToUI(XFormDialog dialog) {

        File loginPrfileSettingsFile = new File(System.getProperty("java.io.tmpdir") + File.separator +
                ResourceConstants.LOGIN_PROFILE_FILE_PREFIX + ResourceConstants.LOGIN_PROFILE_FILE_SUFFIX);

        Map profileMap = new HashMap();

        if (loginPrfileSettingsFile.exists()) {

            try (
                    FileInputStream fis = new FileInputStream(loginPrfileSettingsFile);
                    BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            ) {
                String line;

                while ((line = br.readLine()) != null) {
                    try {
                        profileMap.put(line.split(":")[0], line.split(":")[1]);
                    } catch (PatternSyntaxException patEx) {
                        log.error("Pattern syntax error in setting profile data" + line, patEx);
                    } catch (ArrayIndexOutOfBoundsException arrEx) {
                        log.error("Array Indexout of bound Ex error in setting profile data" + line, arrEx);
                    }
                }

                br.close();

                if (profileMap.containsKey(ResourceConstants.URLS.HOST_NAME)) {
                    dialog.setValue(ProjectModel.GREG_HOST,
                            profileMap.get(ResourceConstants.URLS.HOST_NAME).toString());
                }

                if (profileMap.containsKey(ResourceConstants.URLS.PORT)) {
                    dialog.setValue(ProjectModel.GREG_PORT, profileMap.get(ResourceConstants.URLS.PORT).toString());
                }

                if (profileMap.containsKey(ResourceConstants.URLS.USER_NAME)) {
                    dialog.setValue(ProjectModel.USER_NAME,
                            profileMap.get(ResourceConstants.URLS.USER_NAME).toString());
                }

                if (profileMap.containsKey(ResourceConstants.TENANT_DOMAIN)) {
                    dialog.setValue(ProjectModel.TENANT_DOMAIN,
                            profileMap.get(ResourceConstants.TENANT_DOMAIN).toString());
                }

            } catch (IOException ioex) {
                log.error("Error occurred while retrieving save profile data", ioex);
            }
        }

    }
}