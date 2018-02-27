Node registration
=================

Before joining a Corda network, a Corda node requires the ``truststore.jks`` keystore and ``nodekeystore.jks`` keystore
documented in :doc:`permissioning`. The node has a built-in utlity for requesting these keystores from the certificate
signing service.

Requesting the keystores
------------------------
You request the required keystores for a node by running:

    ``java -jar corda.jar --initial-registration --base-dir <<optional>> --config-file <<optional>>``

Where:

* The ``--config-file`` flag allows you to specify a configuration file with a different name, or at different file
  location. Paths are relative to the current working directory

* The ``--base-directory`` command line option allows you to specify the node’s workspace location. A ``node.conf``
  configuration file is then expected in the root of this workspace

WHAT HAPPENS IF YOU SPECIFY BOTH?

The node creates a new keypair for the request. Once the process is completed, a ``certificates`` folder containing the
two keystores will be created in the node's base directory.

This process only needs to be run when the node connects to the network for the first time, or when the certificate
expires.

.. warning:: The keystore is protected by the keystore password from the node configuration file. The password should
   kept safe to protect the private key and certificate.

.. note:: You can exit the utility at any time if the approval process is taking longer than expected. The request
   process will resume on restart.

Request parameters
------------------
The certificate signing request will be parameterised using the following settings in the ``node.conf`` file:

:myLegalName: Your company's legal name as an X.500 string. X.500 allows differentiation between entities with the same
  name as the legal name needs to be unique on the network. If another node has already been permissioned with this
  name then the permissioning server will automatically reject the request. The request will also be rejected if it
  violates legal name rules, see :doc:`generating-a-node` for more information.

:emailAddress: e.g. "admin@company.com". Used to contact the node's owner in case of issues.

:compatibilityZoneURL: Corda compatibility zone network management service root URL.

Protocol specification
----------------------
Below is the specification of the client and server sides of the protocol for a node to request the keystores.

See ``NodeRegistrationTest``, ``NetworkRegistrationHelper`` and ``X509Utilities`` for examples of certificate signing
request creation and certificate signing using Bouncy Castle.

Client (node)
^^^^^^^^^^^^^
From the client (node)'s perspective, the node registration protocol is as follows:

* The node generates a keypair and saves it to disk.

* The node generates a certificate signing request (CSR) using Bouncy Castle or the Java crypto APIs containing the
  ``myLegalName`` and ``emailAddress`` from the node configuration file.

* The node signs the CSR with the new keypair.

* The node sends the CSR to the certificate signing service by making a HTTPS POST request to ``/api/certificate``. The
  certificate signing service responds with a HTTP redirect to a URL of the form ``/api/certificate/{requestId}`` that
  contains the request ID.

* The node stores the URL to disk.

* The node goes into a slow polling loop, making an HTTP GET request the URL every 10 minutes. Initially, the URL will
  respond with 204 No Content.

* If during the polling loop the URL response changes from 204 No Content to 200 OK, the node downloads the signed
  certificate from the URL in binary form. The node stores the signed certificate in its local keystore.

* If during the polling loop the URL response changes from 204 No Content to 401 Unauthorised, the CSR has been
  rejected.

HOW DOES IT GET THE OTHER KEYSTORE?

Server (registration service)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
From the server (registration service)'s perspective, the node registration protocol is as follows:

* Respond to HTTPS POST requests to ``/api/certificate`` by WHAT?

* Respond to HTTPS GET requests to ``/api/certificate/{requestId}`` by WHAT?