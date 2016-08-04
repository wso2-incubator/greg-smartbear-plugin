# WSO2 Governance Registry SmartBear Plugin

A plugin for SmartBear that allows you to import swagger and wsdl resources directly from a WSO2 Governance Registry.

Installation
------------

Install the plugin via the integrated Plugin Repository available via the Plugin Manager in SoapUI Pro 5.X or Ready! API 1.X


Build it yourself
-----------------
You can build the plugin locally by cloning this repository locally - make sure you have java and maven 3.X correctly
installed and run

mvn clean install 

In the project folder, The plugin G-Reg-plugin-1.0.0.jar will be created in the target folder and can be installed via the
Plugin Managers' "Load from File" action.

Usage
-----
File --> New Project option will open the create a project from description file window.Once installed the plugin the window will display an option
"Import wsdl/swagger resources from WSO2 Governance Registry".

By choosing the option you will be prompted for the G-reg server host name, port, username, password and tenant domain.
You have to choose option swagger or wsdl.
The last login details will be cache and display in the window.
The next screen display the list of resources published in the G-reg. User can choose a resource.

