Calimero Graphical User Interface
=================================

A graphical user interface based on the [Standard Widget Toolkit](https://www.eclipse.org/swt/) for device discovery, process communication, and monitoring.

Download
--------

~~~ sh
# Either using git
git clone https://github.com/calimero-project/calimero-gui.git

# Or using hub
hub clone calimero-project/calimero-gui
~~~


Supported Features
------------------

* KNXnet/IP discovery and self description
* KNX process communication, read or write a KNX datapoint
* Group monitor for KNX datapoints, decode datapoint values, filter/export KNX messages to file
* Network monitor (busmonitor raw frames on the network, completely passive), filter/export KNX messages to file
* Show device information of a device in a KNX network
* Read the IP configuration of a KNXnet/IP server (Local Device Management) or KNX device (Remote Property Services) using KNX properties
* Scan KNX devices in a KNX subnet area/line, or check whether a specific KNX individual address is currently assigned to a KNX device
* KNX properties viewer for KNX devices that implement an Interface Object Server (IOS)

Supported Access Protocols
--------------------------

* KNXnet/IP Tunneling
* KNXnet/IP Routing (KNX IP)
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

On MacOS (takes care of the Cocoa thread restrictions)

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

	java -jar calimero-gui-2.4-SNAPSHOT.jar

If all dependencies are resolved, you can also directly start the GUI by opening it in Nautilus, Windows File Explorer, etc.

Or, assuming all dependencies (of any compliant version) are in the current working directory (replacing `Main` with `SwtChecker` will automatically check and download the required SWT library for your platform during startup)

	java -cp "./*" tuwien.auto.calimero.gui.Main

Or, as example of using the JRE `-classpath`/`-cp` option to qualify all dependencies

	java -cp "calimero-gui-2.4-SNAPSHOT.jar:calimero-core-2.4-SNAPSHOT.jar\
	:calimero-tools-2.4-SNAPSHOT.jar:org.eclipse.swt.gtk.linux.x86_64-4.6.1.jar\
	:slf4j-api-1.7.22.jar:slf4j-simple-1.7.22.jar" tuwien.auto.calimero.gui.Main

### Run As Standalone Application
* Run `gradlew build`
* In the `build/distributions` directory, extract either the `.zip` or `.tar` file
* Open `<extracted folder>/bin`
* Start `calimero-gui` (Linux/MacOS) or `calimero-gui.bat` (Windows)