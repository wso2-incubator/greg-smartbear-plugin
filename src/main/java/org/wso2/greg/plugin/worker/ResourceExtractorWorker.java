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

package org.wso2.greg.plugin.worker;

import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.x.dialogs.Worker;
import com.eviware.x.dialogs.XProgressDialog;
import com.eviware.x.dialogs.XProgressMonitor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.greg.plugin.client.GregManagerClient;
import org.wso2.greg.plugin.constants.HelpMessageConstants;
import org.wso2.greg.plugin.dataObjects.ResourceExtractionResult;

/**
 * This class acts as the worker class to fetch Resources from WSO2 Governance Registry.
 */
public class ResourceExtractorWorker implements Worker {

    private XProgressDialog waitDialog;
    private ResourceExtractionResult result = new ResourceExtractionResult();

    private String hostName = null;
    private String port = null;
    private String resourceType = null;

    private String userName;
    private char[] password;
    private String tenantDomain;
    private String productVersion;

    private String apiRetrievingError = null;
    private static final Log log = LogFactory.getLog(ResourceExtractorWorker.class);

    public ResourceExtractorWorker(String hostName, String port, String resourceType, String userName, char[] password,
            String tenantDomain, String productVersion, String dialogMsg) {

        this.waitDialog = UISupport.getDialogs().createProgressDialog(dialogMsg, 0, "", true);
        this.hostName = hostName;
        this.port = port;
        this.resourceType = resourceType;
        this.userName = userName;
        this.password = password;
        this.tenantDomain = tenantDomain;
        this.productVersion = productVersion;
    }

    public static ResourceExtractionResult downloadResourcesList(String hostName, String port, String resourceType,
            String userName, char[] password, String tenantDomain, String productVersion) {

        ResourceExtractorWorker worker = new ResourceExtractorWorker(hostName, port, resourceType, userName, password,
                tenantDomain, productVersion, HelpMessageConstants.FETCH_RESOURCES_DIALOG_MSG);
        try {

            worker.waitDialog.run(worker);
        } catch (Exception e) {
            // waitDialog method throws generic exception.
            worker.waitDialog.setVisible(false);
            worker.result.setError(e.getMessage());
            log.error("Error Occurred while extracting resources:", e);
        }
        return worker.result;
    }

    @Override
    public Object construct(XProgressMonitor xProgressMonitor) {
        try {
            result.setResourceInfos(GregManagerClient.getInstance()
                    .generateArtifactList(hostName, port, resourceType, userName, password, tenantDomain,
                            productVersion));
        } catch (Exception e) {
            log.error("Error Occurred while adding resources to dialog :", e);
            apiRetrievingError = e.getMessage();
            if (StringUtils.isNullOrEmpty(apiRetrievingError)) {
                apiRetrievingError = e.getClass().getName();
            }
        }
        return null;
    }

    @Override
    public void finished() {
        if (result.isCanceled()) {
            return;
        }
        waitDialog.setVisible(false);
        if (StringUtils.hasContent(apiRetrievingError)) {
            result.setError("Unable to read resources from the specified WSO2 Governance Registry because of the " +
                    "following error:\n" + apiRetrievingError);
            return;
        }
        if (result.getResourceInfos() == null || result.getResourceInfos().isEmpty()) {
            result.setError("No Resources are accessible at the specified URL.");
        }
    }

    @Override
    public boolean onCancel() {
        waitDialog.setVisible(false);
        result.setCanceled();
        return true;
    }
}
