Calimero Graphical User Interface [![CI with Gradle](https://github.com/calimero-project/calimero-gui/actions/workflows/gradle.yml/badge.svg)](https://github.com/calimero-project/calimero-gui/actions/workflows/gradle.yml)
=================================

~~~ sh
git clone https://github.com/calimero-project/calimero-gui.git
~~~

A graphical user interface based on the [Standard Widget Toolkit](https://www.eclipse.org/swt/) for device discovery, process communication, monitoring, and management.

Supported Features
------------------

* KNXnet/IP discovery & self-description
* KNX process communication, read or write KNX datapoints
* Group monitor for KNX datapoints, decode datapoint values, filter KNX messages
* Network monitor (busmonitor raw frames on the network, completely passive), filter KNX messages
* Show KNX device information (PL110 BCU1, TP1 BCU1/2, KNX IP, Interface Objects)
* Read the IP configuration of a KNXnet/IP server (Local Device Management) or KNX device (Remote Property Services) using KNX properties
* Scan KNX devices in a KNX subnet area/line, or check whether a specific KNX individual address is currently assigned to a KNX device
* Show KNX devices in programming mode
* KNX property editor for KNX devices that implement an Interface Object Server (IOS)
* KNX device memory editor
* KNX IP Secure & KNX Data Secure communication
* Data export

Supported Access Protocols
--------------------------

* KNXnet/IP Tunneling & Routing, KNX IP
* KNXnet/IP Local Device Management
* KNX RF USB
* KNX USB
* KNX FT1.2 Protocol (serial connections)
* TP-UART (serial connections)

Execution
---------

### Using Gradle

	./gradlew run

### Using Maven

On macOS (takes care of the Cocoa thread restrictions)

	mvn exec:exec

On Linux/Windows

	mvn exec:java

### Using Java

The graphical user interface has the following 

* _mandatory_ dependencies: calimero-core, calimero-tools, calimero-rxtx, SWT, slf4j-api
* _optional_ dependencies: serial-native, slf4j-simple (strongly recommended to view log output in the GUI)

In the following commands, use your specific library versions.

* MacOS: add the `-XstartOnFirstThread` option for Cocoa thread restrictions

Either, relying on the classpath in the MANIFEST of the `.jar` file (requires exact match of names and versions of all dependencies)

	java -jar calimero-gui-2.6-SNAPSHOT.jar

If all dependencies are resolved, you can also directly start the GUI by opening it in Nautilus, Windows File Explorer, etc.

Or, assuming all dependencies (of any compliant version) are in the current working directory (replacing `Main` with `SwtChecker` will automatically check and download the required SWT library for your platform during startup)

	java -cp "./*" tuwien.auto.calimero.gui.Main

Or, as example of using the JRE `-classpath`/`-cp` option to qualify all dependencies

	java -cp "calimero-gui-2.6-SNAPSHOT.jar:calimero-core-2.6-SNAPSHOT.jar\
	:calimero-tools-2.6-SNAPSHOT.jar:org.eclipse.swt.gtk.linux.x86_64-3.116.100.jar\
	:slf4j-api-1.7.36.jar:slf4j-simple-1.7.36.jar" tuwien.auto.calimero.gui.Main

### Run As Standalone Application
* Run `gradlew build`
* In the `build/distributions` directory, extract either the `.zip` or `.tar` file
* Open `<extracted folder>/bin`
* Start `calimero-gui` (Linux/MacOS) or `calimero-gui.bat` (Windows)