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

package org.wso2.greg.plugin.client;

import com.eviware.soapui.support.StringUtils;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.wso2.greg.plugin.Utils;
import org.wso2.greg.plugin.common.GregReadyPluginException;
import org.wso2.greg.plugin.constants.HelpMessageConstants;
import org.wso2.greg.plugin.constants.ResourceConstants;
import org.wso2.greg.plugin.dataObjects.ResourceInfo;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * This class is responsible for connecting to WSO2 G-reg and fetching swagger and wsdl resources
 */
public class GregManagerClient {

    private static GregManagerClient gregManagerClient;
    private static final Log log = LogFactory.getLog(GregManagerClient.class);

    public static GregManagerClient getInstance() {
        if (gregManagerClient == null) {
            gregManagerClient = new GregManagerClient();
        }
        return gregManagerClient;
    }

    /**
     * This method will generate artifact list in json format.
     * JSON objects contain information required to display artifact information in the plugin UI.
     */
    public List<ResourceInfo> generateArtifactList(String hostName, String port, String resourceType, String userName,
            char[] password, String tenantDomain, String productVersion) throws GregReadyPluginException {

        if (StringUtils.isNullOrEmpty(tenantDomain)) {
            tenantDomain = ResourceConstants.CARBON_SUPER;
        }

        JSONArray resourcesArray = getRegistryResources(hostName, port, resourceType, userName, password, tenantDomain);

        List<ResourceInfo> resourceInfos = new ArrayList();

        for (Object apiJsonObject : resourcesArray) {

            JSONObject assetJson = (JSONObject) apiJsonObject;

            String resourceName = assetJson.get(ResourceConstants.ArtifactInfo.NAME).toString();
            String version = assetJson.get(ResourceConstants.ArtifactInfo.VERSION).toString();
            String description = "";

            if (assetJson.get(ResourceConstants.ArtifactInfo.DESCRIPTION) != null) {
                description = assetJson.get(ResourceConstants.ArtifactInfo.DESCRIPTION).toString();
            }

            ResourceInfo resourceInfo = new ResourceInfo();
            resourceInfo.setName(resourceName);
            resourceInfo.setVersion(version);
            resourceInfo.setDescription(description);
            resourceInfo.setArtifactId(assetJson.get(ResourceConstants.ArtifactInfo.ID).toString());
            resourceInfo.setProductVersion(productVersion);

            String contentDocLink = "https://" + userName + ":" + String.valueOf(password) +
                    "@" + hostName + ":" +
                    port + ResourceConstants.URLS.GOVERNANCE_API_URL;

            if (resourceType.equals(ResourceConstants.RESOURCE_TYPE_SWAGGER)) {
                contentDocLink += ResourceConstants.URLS.RESOURCE_SWAGGERS + "/";
            } else {
                contentDocLink += ResourceConstants.URLS.RESOURCES_WSDLS + "/";
            }
            contentDocLink += resourceInfo.getArtifactId() + ResourceConstants.URLS.CONTENT_URL + "?" +
                    ResourceConstants.URLS.TENANT + "=" + tenantDomain;
            resourceInfo.setResourceContentDocLink(contentDocLink);
            resourceInfo.setResType(resourceType);
            resourceInfos.add(resourceInfo);
        }
        Arrays.fill(password, '*');
        return resourceInfos;
    }

    /**
     * This method will return all registry resources of the given tenant domain
     */
    private JSONArray getRegistryResources(String hostName, String port, String resourceType, String userName,
            char[] password, String tenantDomain) throws GregReadyPluginException {

        HttpEntity entity;

        String registryUrl = "https://" + hostName + ":" + port + ResourceConstants.URLS.GOVERNANCE_API_URL;

        if (resourceType.equals(ResourceConstants.RESOURCE_TYPE_WSDL)) {
            registryUrl += ResourceConstants.URLS.RESOURCES_WSDLS;
        } else if (resourceType.equals(ResourceConstants.RESOURCE_TYPE_SWAGGER)) {
            registryUrl += ResourceConstants.URLS.RESOURCE_SWAGGERS;
        }

        if (tenantDomain != null && !tenantDomain.isEmpty()) {
            registryUrl += "?" + ResourceConstants.URLS.TENANT + "=" + tenantDomain;
        }

        HttpGet request = new HttpGet(registryUrl);
        String auth = userName + ":" + String.valueOf(password);
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("ISO-8859-1")));
        String authHeader = "Basic " + new String(encodedAuth);
        request.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        request.setHeader(HttpHeaders.ACCEPT, "application/json");

        HttpClient httpClient = Utils.getHttpClient();
        String responseString;
        HttpResponse response = null;

        try {
            response = httpClient.execute(request);
            entity = response.getEntity();
            responseString = EntityUtils.toString(entity, ResourceConstants.UTF_8);
        } catch (IOException ioEx) {
            log.error("Error occurred while fetch resource list", ioEx);
            throw new GregReadyPluginException("Error occurred while fetch resource list", ioEx);
        }

        if (HttpStatus.SC_NOT_FOUND == response.getStatusLine().getStatusCode()) {
            log.info(HelpMessageConstants.NO_RESOURCES_FOUND_MSG);
            throw new GregReadyPluginException(HelpMessageConstants.NO_RESOURCES_FOUND_MSG, null);
        }

        String[] errorSection = responseString.split(",");
        boolean isError = Boolean.parseBoolean(errorSection[0].split(":")[1].split("}")[0].trim());

        if (isError) {
            String errorMsg = errorSection[1].split(":")[1].split("}")[0].trim();
            log.info("Error occurred while getting the list of APIs " + errorMsg);
            throw new GregReadyPluginException("Error occurred while getting the list of Artifacts " + errorMsg);
        }

        JSONObject jsonObject = (JSONObject) JSONValue.parse(responseString);

        // We expect an JSON array for assets list
        JSONArray resourcesArray = (JSONArray) jsonObject.get("assets");
        return resourcesArray;
    }

}
