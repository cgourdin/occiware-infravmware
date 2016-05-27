# occiware-infravmware
This connector plug a VMWare infrastructure with infrastructure extension.

It will be soon integrated on Clouddesigner project as it is in early development.

## How to use this connector 
For use, you must replace "dummy connector" by this one.

Infrastructure extension is the extension model for this connector, you can design a compute and launch it on VMWare VCenter.

Two modes are supported now, the creation of virtual machine without virtual machine template and with a template.

Usage with template : set the attribute "summary" with the name of your template and the connector will clone the template on your compute without taking account of other fields (like cpu, memory etc.), set the title as it represent the name of your virtual machine.

Don't forget to add your credentials in src/resources/credential.properties before using this connector.

## Supported version of VMWare vcenter
The version 6.0, and all older version 5.5, 5.0, 4.1 etc. (ESX and ESXi).
