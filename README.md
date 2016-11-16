# WSO2 Governance Registry SmartBear Plugin

A plugin for SmartBear that allows you to import swagger and wsdl resources directly from a WSO2 Governance Registry.

Installation
------------
Install the plugin via the integrated Plugin Repository available via the Plugin Manager in SoapUI Pro 5.X or Ready! API 1.X.


Build it yourself
-----------------
Please go through prerequisites before start.
You can build the plugin locally by cloning this repository locally - make sure you have java and maven 3.X correctly
installed and run

mvn clean install 

In the project folder, The plugin G-Reg-plugin-1.0.0.jar will be created in the target folder and can be installed via the
Plugin Managers' "Load from File" action.

Prerequisites
-----------------
Before build the G-Reg plugin you need to build the readyapi-swagger-plugin and swagger-parser.

Build swagger-parser 
--------------------
git clone or download from 
https://github.com/swagger-api/swagger-parser.git
Check out tag 1.0.8 using following command.
git checkout tags/v1.0.8 

Build with java 7 with following command
mvn clean install

Build readyapi-swagger-plugin
-----------------------------
git clone or download from
https://github.com/SmartBear/readyapi-swagger-plugin.git

Fetch from branch version-2.1.3.

Open pom.xml file change following dependency to 1.0.8 by replacing 1.0.8-SNAPSHOT.

        <dependency>
            <groupId>io.swagger</groupId>
            <artifactId>swagger-parser</artifactId>
            <version>1.0.8</version>
        </dependency
Build in Java 8 with following command
mvn clean install




Usage
-----
File --> New Project option will open the create a project from description file window.Once installed the plugin the window will display an option
"Import wsdl/swagger resources from WSO2 Governance Registry".

By choosing the option you will be prompted for the G-reg server host name, port, username, password and tenant domain.
You have to choose option swagger or wsdl.
The last login details will be cache and display in the window.
The next screen display the list of resources published in the G-reg. User can choose a resource.

