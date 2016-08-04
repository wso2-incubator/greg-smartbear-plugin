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

package org.wso2.greg.plugin.constants;

/**
 * This class contains all the help/validation messages that are used by the UIs.
 */
public class HelpMessageConstants {
    // Help messages for the ProjectModel and Import Model.
    public static final String GREG_HOST_VALIDATION_MSG = "Please enter the G-reg host name.";
    public static final String USER_NAME_VALIDATION_MSG = "Please enter user name.";
    public static final String PASSWORD_VALIDATION_MSG = "Please enter an valid password.";
    public static final String HOST_EMPTY = "G-reg Host should not empty";
    public static final String PROJECT_NAME_VALIDATION_MSG = "Please enter project name.";

    // Validation message for the ResourceModel table.
    public static final String API_SELECTION_VALIDATION_MSG = "Please select at least one Resource " +
            "to proceed";

    // Tooltip texts for the radio buttons.
    public static final String TEST_SUITE_TOOLTIP_TEXT = "Select 'Yes' if you need to generate a " +
            "set of test suites for the selected Resources";
    public static final String LOAD_TEST_TOOLTIP_TEXT = "Select 'Yes' if you need to generate a " +
            "set of load tests for the selected Resources";

    public static final String NO_RESOURCES_FOUND_MSG = "Resources not found";
    public static final String FETCH_RESOURCES_DIALOG_MSG = "Getting the list of Resources";
}
