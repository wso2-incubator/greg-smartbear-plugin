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

package org.wso2.greg.plugin;

import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.wsdl.WsdlInterface;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.support.wsdl.UrlWsdlLoader;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlImporter;
import com.eviware.soapui.impl.wsdl.support.wsdl.WsdlLoader;
import com.eviware.x.form.ValidationMessage;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldListener;
import com.eviware.x.form.XFormFieldValidator;
import com.eviware.x.form.support.ADialogBuilder;
import com.eviware.x.form.support.XFormRadioGroup;
import com.eviware.x.impl.swing.JTableFormField;
import com.smartbear.swagger.SwaggerImporter;
import com.smartbear.swagger.SwaggerUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jdesktop.swingx.JXTable;
import org.wso2.greg.plugin.common.GregReadyPluginException;
import org.wso2.greg.plugin.constants.HelpMessageConstants;
import org.wso2.greg.plugin.constants.ResourceConstants;
import org.wso2.greg.plugin.dataObjects.ResourceInfo;
import org.wso2.greg.plugin.dataObjects.ResourceSelectionResult;
import org.wso2.greg.plugin.ui.ResourceModel;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final Log log = LogFactory.getLog(Utils.class);


    /**
     * This method is used to create the Resources List UI from the given list of Resources(s).
     *
     * @param resourceInfos The list of Resources that the table is constructed.
     * @return ResourceSelectionResult which contains all the selected APIs and whether test suites and load
     * are needed to be generated.
     */
    public static ResourceSelectionResult showSelectAPIDefDialog(List<ResourceInfo> resourceInfos) {

        final XFormDialog dialog = ADialogBuilder.buildDialog(ResourceModel.class);
        final Object[][] tableData = convertToTableData(resourceInfos);

        // --------------- start of API List table population section ------------------
        // We create a table model here with the converted data.
        TableModel tableModel = new AbstractTableModel() {
            Object[][] data = tableData;
            String[] columnNames = {"Name", "Version", "Provider", "Description"};

            @Override
            public int getRowCount() {
                return data.length;
            }

            @Override
            public int getColumnCount() {
                // We have a hardcoded set of columns
                return columnNames.length;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                return data[rowIndex][columnIndex];
            }

            @Override
            public String getColumnName(int column) {
                return columnNames[column];
            }
        };

        // We get the table that was ghttps://youtu.be/Vycy3GxbxUYenerated in the form and we set
        // some properties there.
        XFormField resourcesListFormField = dialog.getFormField(ResourceModel.RESOURCE_LIST);
        final JXTable table = ((JTableFormField) resourcesListFormField).getTable();
        table.setCellSelectionEnabled(false);
        table.setColumnSelectionAllowed(false);
        table.setRowSelectionAllowed(true);
        table.setFillsViewportHeight(true);

        // We set the preferred size of all the parent forms until we get to the JScrollPane
        table.setPreferredScrollableViewportSize(new Dimension(600, 200));
        table.getParent().setPreferredSize(new Dimension(600, 200));
        table.getParent().getParent().setPreferredSize(new Dimension(600, 200));

        // Setting the table model
        table.setModel(tableModel);

        // This is to show a toolTip when hovering over the table cells. We need this because there could be long
        // descriptions and api names.
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Point p = e.getPoint();
                int row = table.rowAtPoint(p);
                int col = table.columnAtPoint(p);

                if (row == -1 || col == -1) {
                    return;
                }
                table.setToolTipText(tableData[row][col].toString());
            }
        });

        // The purpose of this validator is the check whether there are at least one API selected from the table.
        resourcesListFormField.addFormFieldValidator(new XFormFieldValidator() {
            @Override
            public ValidationMessage[] validateField(XFormField xFormField) {
                if (table.getSelectedRowCount() <= 0) {
                    return new ValidationMessage[]{
                            new ValidationMessage(HelpMessageConstants.API_SELECTION_VALIDATION_MSG,
                                    dialog.getFormField(ResourceModel.RESOURCE_LIST))};
                }
                return new ValidationMessage[0];
            }
        });
        // --
        // ----------------- End of Resource(s) List Table population section ----------------------

        // We get the radio button group and add a listener there. The purpose of the listener is to 'enable',
        // 'disable' the Load test radio button group based on the selected value of this group.
        // The reason is that, there is no meaning to create a Load test without a test suite.
        XFormRadioGroup testSuiteSelection =
                (XFormRadioGroup) dialog.getFormField(ResourceModel.TEST_SUITE);
        testSuiteSelection.setValue(ResourceConstants.RADIO_BUTTON_OPTIONS_NO);
        testSuiteSelection.setToolTip(HelpMessageConstants.TEST_SUITE_TOOLTIP_TEXT);

        testSuiteSelection.addFormFieldListener(new XFormFieldListener() {
            @Override
            public void valueChanged(XFormField xFormField, String newValue, String oldValue) {
                XFormRadioGroup loadTestSelection =
                        (XFormRadioGroup) dialog.getFormField(ResourceModel.LOAD_TEST);
                if (ResourceConstants.RADIO_BUTTON_OPTIONS_YES.equals(newValue)) {
                    loadTestSelection.setEnabled(true);
                } else if (ResourceConstants.RADIO_BUTTON_OPTIONS_NO.equals(newValue)) {
                    loadTestSelection.setEnabled(false);
                }
            }
        });

        XFormRadioGroup loadTestSelection =
                (XFormRadioGroup) dialog.getFormField(ResourceModel.LOAD_TEST);
        loadTestSelection.setToolTip(HelpMessageConstants.LOAD_TEST_TOOLTIP_TEXT);
        loadTestSelection.setValue(ResourceConstants.RADIO_BUTTON_OPTIONS_NO);

        if (dialog.show()) {
            int[] selected = table.getSelectedRows();
            ArrayList<ResourceInfo> selectedAPIs = new ArrayList<ResourceInfo>();
            for (int no : selected) {
                selectedAPIs.add(resourceInfos.get(no));
            }
            ResourceSelectionResult selectionResult = new ResourceSelectionResult();
            selectionResult.setResourceInfoList(selectedAPIs);
            selectionResult.setTestSuiteSelected(ResourceConstants.RADIO_BUTTON_OPTIONS_YES.equals(testSuiteSelection
                    .getValue()));
            selectionResult.setLoadTestSelected(ResourceConstants.RADIO_BUTTON_OPTIONS_YES.equals(loadTestSelection
                    .getValue()));

            return selectionResult;
        } else {
            return null;
        }

    }

    /**
     * This method will create a set of rest services by reading the swagger definitions resource from the given URL
     * in ResourceInfo.
     *
     * @param apiLink The ResourceInfo object which contains the API details.
     * @param project The WsdlProject
     * @return an array of RestService
     */
    public static RestService[] importResourcesProject(ResourceInfo apiLink, WsdlProject project)
            throws GregReadyPluginException {

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(Utils.class.getClassLoader());

        SwaggerImporter importer = null;

        try {
            String content = getResourceContent(apiLink.getResourceContentDocLink());
            File temp = File.createTempFile(apiLink.getArtifactId() + "_swaggar", ".json");

            try (
                    FileWriter fileWriter = new FileWriter(temp);
                    BufferedWriter out = new BufferedWriter(fileWriter);
            ) {
                out.write(content);
                out.flush();
            }

            apiLink.setContentFilePath(temp.getPath());

            importer = SwaggerUtils.createSwaggerImporter("file://" + apiLink.getContentFilePath(), project);
            log.info("Importing Swagger from [" + apiLink.getName() + "]");

            if (importer.getClass().getName().contains("Swagger2Importer")) {
                project.setPropertyValue(apiLink.getResourceContentDocLink(), "2.0");
            } else if (importer.getClass().getName().contains("Swagger1XImporter")) {
                project.setPropertyValue(apiLink.getResourceContentDocLink(), "1.x");
            } else {
                log.info("Unable to determine the Swagger version of [" + apiLink.getResourceContentDocLink() + "]");
            }

        } catch (Exception ex) {
            // swagger importer throws generic exception
            String errorMsg = "Error occured while importing the swagger resource " + apiLink.getName();
            log.error(errorMsg, ex);
            throw new GregReadyPluginException(errorMsg, ex);


        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
        return importer.importSwagger("file://" + apiLink.getContentFilePath());
    }

    public static WsdlInterface[] importSOAPServiceToProject(ResourceInfo resourceInfo,
                                                             WsdlProject project) throws Exception {

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {

            Thread.currentThread().setContextClassLoader(Utils.class.getClassLoader());
            WsdlLoader wsdlLoader = new UrlWsdlLoader(resourceInfo.getResourceContentDocLink());
            WsdlInterface[] wsdls = WsdlImporter.importWsdl(project, resourceInfo.getResourceContentDocLink(),
                    null, wsdlLoader);
            return wsdls;
        } finally {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
        }
    }

    private static String getResourceContent(String swaggerDocLink) throws IOException {

        HttpGet request = new HttpGet(swaggerDocLink);
        HttpClient client = getHttpClient();
        HttpResponse response = client.execute(request);

        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity, ResourceConstants.UTF_8);

        return responseString;
    }


    private static Object[][] convertToTableData(List<ResourceInfo> apiList) {
        Object[][] convertedData = new Object[apiList.size()][4];

        for (int i = 0; i < apiList.size(); i++) {
            ResourceInfo resourceInfo = apiList.get(i);

            convertedData[i][0] = resourceInfo.getName();
            convertedData[i][1] = resourceInfo.getVersion();
            convertedData[i][2] = resourceInfo.getProvider();
            convertedData[i][3] = resourceInfo.getDescription();
        }
        return convertedData;
    }


    /**
     * Method to initialize the http client. We use only one instance of http client since there can not be concurrent
     * invocations
     *
     * @return @link{HttpClient} httpClient instance
     */
    public static HttpClient getHttpClient() {

        HttpClient httpClient = null;
        try {
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(builder.build());
            httpClient = HttpClients.custom().setSSLSocketFactory(sslConnectionSocketFactory).build();

        } catch (NoSuchAlgorithmException e) {
            log.error("Unable to load the trust store", e);
        } catch (KeyStoreException e) {
            log.error("Unable to get the key store instance", e);
        } catch (KeyManagementException e) {
            log.error("Unable to load trust store material", e);
        }
        return httpClient;
    }
}
