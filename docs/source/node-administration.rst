Node administration
===================

When a node is running, it exposes an embedded database server, an embedded web server that lets you monitor it,
you can upload and download attachments, access a REST API and so on.

Logging
-------

In the default configuration logs are stored to the logs subdirectory of the node directory and are rotated from time to time. You can
have logging printed to the console as well by passing the ``--log-to-console`` command line flag. Corda
uses the SL4J logging façade which is configured with the log4j2 binding framework to manage its logging,
so you can also configure it in more detail by writing a custom log4j2 logging configuration file and passing ``-Dlog4j.configurationFile=my-config-file.xml``
on the command line as well. The default configuration is copied during the build from ``config/dev/log4j2.xml``, or for the test sourceSet from ``config/test/log4j2.xml``.

In corda code a logger is typically instantiated via the ``net.corda.core.utilities.loggerFor`` utility method which will create an SL4J ``Logger`` with a name based on the type parameter.
Also, available in ``net.corda.core.utilities``, are extension methods to take a lazily evaluated logging lambda for trace and debug level, which will not evaluate the lambda if the LogLevel threshold is higher.

Database access
---------------

The node exposes its internal database over a socket which can be browsed using any tool that can use JDBC drivers.
The JDBC URL is printed during node startup to the log and will typically look like this:

     ``jdbc:h2:tcp://192.168.0.31:31339/node``

The username and password can be altered in the :doc:`corda-configuration-file` but default to username "sa" and a blank
password.

Any database browsing tool that supports JDBC can be used, but if you have IntelliJ Ultimate edition then there is
a tool integrated with your IDE. Just open the database window and add an H2 data source with the above details.
You will now be able to browse the tables and row data within them.

Command line control
--------------------

You can send arbitrary RPCs to the node from the command line using ``corda-tool``. This program supports both
commands provided on the command line or using an interactive console. To compile it, use ``gradle tools:corda-tool:install``
and you can then find an install under ``tools/corda-tool/build/install/corda-tool``: this directory contains
``bin`` and ``lib`` subdirectories which can be copied elsewhere, or you can simply add the bin directory to your
path.

.. warning:: The tool is experimental and has not been tested yet on Windows. Some commands may not work yet.

To see a list of available commands, you can run ``corda-tool --help``. More detail on each command can be found
in the `API docs for the RPC interface <api/net.corda.node.services.messaging/-corda-r-p-c-ops/index.html>`_. You
need to provide login details in the following way:

* Pass ``--user=your_username`` or set the ``$CORDA_USER`` environment variable.
* Pass ``--password=your_password``, set the ``$CORDA_PASSWORD`` environment variable, or type it in when the
  program runs at the terminal.
* Pass ``--node=servername:12345`` to specify the host and port: you need the messaging address, not the HTTP
  server address.
* Pass ``--certs-dir=a/b/c`` to specify where the server SSL certificates can be found (this is in the ``certificates``
  directory of the node itself), or set the ``$CORDA_CERTS_DIR`` environment variable.

.. note:: The way SSL certificates are handled will change in a future release.

You can also specify the ``--console`` option to drop into the console and issue commands interactively. This may
be a better way to learn the tool.

The command itself is a textual representation of a regular Java method call. The tool uses YAML syntax to concisely
express objects and parameters to the method call, with a minor tweak to make interactive input easier.

The simplest case is a method that takes no parameters at all. In that case, you just specify the name. These
examples are all from the console mode:

    >>> currentNodeTime
    --- 1482335737.275000000

Hmm. That ``---`` in front of the answer is a bit odd: it's because the default output is in YAML. Let's try
changing that to a different output format:

    >>> use json
    >>> currentNodeTime
    1482335804.929000000

A bit better. That's still a format meant more for machines than humans though. How about:

    >>> use tostring
    >>> currentNodeTime
    2016-12-21T15:57:24.340Z

The ``use tostring`` command, or alternatively ``--format=tostring`` option on the command line, tells the tool
to print answers using the Java ``toString()`` method which is sometimes (but not always) designed for
consumption by technical humans:

    >>> nodeIdentity
    NodeInfo(address=NetworkMapAddress(hostAndPort=localhost:10002), legalIdentity=Notary,
    advertisedServices=[ServiceEntry(info=corda.notary.validating, identity=corda.notary.validating|Notary),
    ServiceEntry(info=corda.network_map, identity=corda.network_map|Notary)], physicalLocation=PhysicalLocation(
    coordinate=WorldCoordinate(latitude=51.52, longitude=-0.1), description=London))

Some commands take parameters, and those must be explicitly named. Any text after the name of the method to invoke
is wrapped in curly braces and then parsed as a one-line YAML object. You can get a feel for YAML syntax using
this `handy online tool <http://yaml-online-parser.appspot.com/>`_.

    >>> attachmentExists id: 01ba4719c80b6fe911b091a7c05124b64eeece964e09c058ef8f9805daca546b
    false

Some commands return _observables_. These are objects that represent data pushed from the node to the client.
The tool has some support for listening to observables if they are in certain places in the responses: if the
method returns a plain observable, or a pair that contains an observable, the tool will sit and print out
the things pushed by the server until you press Ctrl-C or (on UNIX) send SIGINT. This can be useful to get an
activity stream of what the node is doing.

Monitoring your node
--------------------

Like most Java servers, the node exports various useful metrics and management operations via the industry-standard
`JMX infrastructure <https://en.wikipedia.org/wiki/Java_Management_Extensions>`_. JMX is a standard API
for registering so-called *MBeans* ... objects whose properties and methods are intended for server management. It does
not require any particular network protocol for export. So this data can be exported from the node in various ways:
some monitoring systems provide a "Java Agent", which is essentially a JVM plugin that finds all the MBeans and sends
them out to a statistics collector over the network. For those systems, follow the instructions provided by the vendor.

Sometimes though, you just want raw access to the data and operations itself. So nodes export them over HTTP on the
``/monitoring/json`` HTTP endpoint, using a program called `Jolokia <https://jolokia.org/>`_. Jolokia defines the JSON
and REST formats for accessing MBeans, and provides client libraries to work with that protocol as well.

Here are a few ways to build dashboards and extract monitoring data for a node:

* `JMX2Graphite <https://github.com/logzio/jmx2graphite>`_ is a tool that can be pointed to /monitoring/json and will
  scrape the statistics found there, then insert them into the Graphite monitoring tool on a regular basis. It runs
  in Docker and can be started with a single command.
* `JMXTrans <https://github.com/jmxtrans/jmxtrans>`_ is another tool for Graphite, this time, it's got its own agent
  (JVM plugin) which reads a custom config file and exports only the named data. It's more configurable than
  JMX2Graphite and doesn't require a separate process, as the JVM will write directly to Graphite.
* *Java Mission Control* is a desktop app that can connect to a target JVM that has the right command line flags set
  (or always, if running locally). You can explore what data is available, create graphs of those metrics, and invoke
  management operations like forcing a garbage collection.
* Cloud metrics services like New Relic also understand JMX, typically, by providing their own agent that uploads the
  data to their service on a regular schedule.

Uploading and downloading attachments
-------------------------------------

Attachments are files that add context to and influence the behaviour of transactions. They are always identified by
hash and they are public, in that they propagate through the network to wherever they are needed.

All attachments are zip files. Thus to upload a file to the ledger you must first wrap it into a zip (or jar) file. Then
you can upload it by running this command from a UNIX terminal:

.. sourcecode:: shell

   curl -F myfile=@path/to/my/file.zip http://localhost:31338/upload/attachment

The attachment will be identified by the SHA-256 hash of the contents, which you can get by doing:

.. sourcecode:: shell

   shasum -a 256 file.zip

on a Mac or by using ``sha256sum`` on Linux. Alternatively, the hash will be returned to you when you upload the
attachment.

An attachment may be downloaded by fetching:

.. sourcecode:: shell

   http://localhost:31338/attachments/DECD098666B9657314870E192CED0C3519C2C9D395507A238338F8D003929DE9

where DECD... is of course replaced with the hash identifier of your own attachment. Because attachments are always
containers, you can also fetch a specific file within the attachment by appending its path, like this:

.. sourcecode:: shell

   http://localhost:31338/attachments/DECD098666B9657314870E192CED0C3519C2C9D395507A238338F8D003929DE9/path/within/zip.txt

Uploading interest rate fixes
-----------------------------

If you would like to operate an interest rate fixing service (oracle), you can upload fix data by uploading data in
a simple text format to the ``/upload/interest-rates`` path on the web server.

The file looks like this::

    # Some pretend noddy rate fixes, for the interest rate oracles.

    LIBOR 2016-03-16 1M = 0.678
    LIBOR 2016-03-16 2M = 0.655
    EURIBOR 2016-03-15 1M = 0.123
    EURIBOR 2016-03-15 2M = 0.111

The columns are:

* Name of the fix
* Date of the fix
* The tenor / time to maturity in days
* The interest rate itself