Calimero Graphical User Interface [![CI with Gradle](https://github.com/calimero-project/calimero-gui/actions/workflows/gradle.yml/badge.svg)](https://github.com/calimero-project/calimero-gui/actions/workflows/gradle.yml) [![](https://jitpack.io/v/calimero-project/calimero-gui.svg)](https://jitpack.io/#calimero-project/calimero-gui) [![](https://img.shields.io/badge/jitpack-master-brightgreen?label=JitPack)](https://jitpack.io/#calimero-project/calimero-gui/master)
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

* _mandatory_ dependencies: calimero-core, calimero-tools, calimero-rxtx, SWT
* _optional_ dependencies: serial-native

In the following commands, use your specific library versions.

* MacOS: add the `-XstartOnFirstThread` option for Cocoa thread restrictions

Either, relying on the classpath in the MANIFEST of the `.jar` file (requires exact match of names and versions of all dependencies)

	java -jar calimero-gui-3.0-SNAPSHOT.jar

If all dependencies are resolved, you can also directly start the GUI by opening it in Nautilus, Windows File Explorer, etc.

Or, assuming all dependencies (of any compliant version) are in the current working directory (replacing `Main` with `SwtChecker` will automatically check and download the required SWT library for your platform during startup)

	java -cp "./*" io.calimero.gui.Main

Or, as example of using the JRE `-classpath`/`-cp` option to qualify all dependencies

	java -cp "calimero-gui-3.0-SNAPSHOT.jar:calimero-core-3.0-SNAPSHOT.jar\
	:calimero-tools-3.0-SNAPSHOT.jar:org.eclipse.swt.gtk.linux.x86_64-3.123.0.jar"\
	io.calimero.gui.Main

### Run As Standalone Application
* Run `gradlew build`
* In the `build/distributions` directory, extract either the `.zip` or `.tar` file
* Open `<extracted folder>/bin`
* Start `calimero-gui` (Linux/MacOS) or `calimero-gui.bat` (Windows)
